# encoding: utf-8
require "thread"
require "stud/interval"
require "concurrent"
require "logstash/namespace"
require "logstash/errors"
require "logstash/event"
require "logstash/config/file"
require "logstash/filters/base"
require "logstash/inputs/base"
require "logstash/outputs/base"
require "logstash/util/reporter"
require "logstash/config/cpu_core_strategy"
require "logstash/util/defaults_printer"
require "logstash/util/wrapped_synchronous_queue"

class LogStash::Pipeline
  attr_reader :inputs, :filters, :outputs, :worker_threads, :events_consumed, :events_emitted

  def initialize(configstr)
    @logger = Cabin::Channel.get(LogStash)

    @inputs = nil
    @filters = nil
    @outputs = nil

    grammar = LogStashConfigParser.new
    @config = grammar.parse(configstr)
    if @config.nil?
      raise LogStash::ConfigurationError, grammar.failure_reason
    end
    # This will compile the config to ruby and evaluate the resulting code.
    # The code will initialize all the plugins and define the
    # filter and output methods.
    code = @config.compile
    # The config code is hard to represent as a log message...
    # So just print it.
    @logger.debug? && @logger.debug("Compiled pipeline code:\n#{code}")
    begin
      eval(code)
    rescue => e
      raise
    end

    @input_queue = LogStash::Util::WrappedSynchronousQueue.new
    @events_emitted = Concurrent::AtomicFixnum.new(0)
    @events_consumed = Concurrent::AtomicFixnum.new(0)

    # We generally only want one thread at a time able to access pop/take/poll operations
    # from this queue. We also depend on this to be able to block consumers while we snapshot
    # in-flight buffers
    @input_queue_pop_mutex = Mutex.new

    @input_threads = []

    @settings = {
      "filter-workers" => LogStash::Config::CpuCoreStrategy.fifty_percent
    }

    # @ready requires thread safety since it is typically polled from outside the pipeline thread
    @ready = Concurrent::AtomicBoolean.new(false)
  end # def initialize

  def ready?
    @ready.value
  end

  def configure(setting, value)
    if setting == "filter-workers" && value > 1
      # Abort if we have any filters that aren't threadsafe
      plugins = @filters.select { |f| !f.threadsafe? }.collect { |f| f.class.config_name }
      if !plugins.size.zero?
        raise LogStash::ConfigurationError, "Cannot use more than 1 filter worker because the following plugins don't work with more than one worker: #{plugins.join(", ")}"
      end
    end
    @settings[setting] = value
  end

  def filters?
    return @filters.any?
  end

  def run
    LogStash::Util.set_thread_name(">lsipeline")
    @logger.terminal(LogStash::Util::DefaultsPrinter.print(@settings))

    start_workers

    @logger.info("Pipeline started")
    @logger.terminal("Logstash startup completed")

    @logger.info("Will run till input threads stopped")
    #InflightEventsReporter.logger = @logger
    #InflightEventsReporter.start(self)
    wait_inputs
    @logger.info("Inputs stopped")

    shutdown_workers

    @worker_threads.each do |t|
      @logger.debug("Shutdown waiting for worker thread #{t}")
      t.join
    end

    @filters.each(&:do_close)
    @outputs.each(&:do_close)

    @logger.info("Pipeline shutdown complete.")
    @logger.terminal("Logstash shutdown completed")

    # exit code
    return 0
  end # def run

  def start_workers
    @inflight_batches = {}

    @worker_threads = []
    begin
      start_inputs
      @outputs.each {|o| o.register }
      @filters.each {|f| f.register}

      @settings["filter-workers"].times do |t|
        @worker_threads << Thread.new do
          thread_name = ">worker#{t}"
          LogStash::Util.set_thread_name(thread_name)

          running = true
          while running
            # We synchronize this access to ensure that we can snapshot even partially consumed
            # queues
            input_batch = @input_queue_pop_mutex.synchronize { take_event_batch }
            @events_consumed.increment(input_batch.size)
            running = !input_batch.include?(LogStash::SHUTDOWN)

            #TODO: Should we handle exceptions raised here? What should the behavior be? Just keep going?
            filtered_batch = filter_event_batch(input_batch)

            filtered_batch.reduce(Hash.new {|h,k| h[k] = []}) do |outputs_events, event|
              output_func(event).each do |output|
                outputs_events[output] << event
              end
              outputs_events
            end.each do |output,events|
              output.multi_handle(events)
            end

            inflight_batches_synchronize { set_current_thread_inflight_batch(nil) }
            @events_emitted.increment(filtered_batch.size)
          end
        end
      end
    ensure
      # it is important to garantee @ready to be true after the startup sequence has been completed
      # to potentially unblock the shutdown method which may be waiting on @ready to proceed
      @ready.make_true
    end
  end

  def dump_inflight(file_path)
    inflight_batches_synchronize do |batches|
      File.open(file_path, "w") do |f|
        batches.values.each do |batch|
          next unless batch
          batch.each do |e|
            f.write(LogStash::Json.dump(e))
          end
        end
      end
    end
  end

  def shutdown_workers
    # Each worker will receive this exactly once!
    @worker_threads.each do
      @logger.debug("Pushing shutdown")
      @input_queue.push(LogStash::SHUTDOWN)
    end
  end

  def set_current_thread_inflight_batch(batch)
    @inflight_batches[Thread.current] = batch
  end

  def inflight_batches_synchronize
    @input_queue_pop_mutex.synchronize do
      yield(@inflight_batches)
    end
  end

  def take_event_batch()
    batch = []
    # Doing this here lets us guarantee that once a 'push' onto the synchronized queue succeeds
    # it can be saved to disk in a fast shutdown
    set_current_thread_inflight_batch(batch)
    80.times do |t|
      event = t==0 ? @input_queue.take : @input_queue.poll(50)
      # Exit early so each thread only gets one copy of this
      # This is necessary to ensure proper shutdown!
      next if event.nil?
      batch << event
      break if event == LogStash::SHUTDOWN
    end
    batch
  end

  def filter_event_batch(batch)
    batch.reduce([]) do |acc,e|
      if e.is_a?(LogStash::Event)
        filtered = filter_func(e)
        filtered.each {|fe| acc << fe unless fe.cancelled?}
      end
      acc
    end
  rescue Exception => e
    # Plugins authors should manage their own exceptions in the plugin code
    # but if an exception is raised up to the worker thread they are considered
    # fatal and logstash will not recover from this situation.
    #
    # Users need to check their configuration or see if there is a bug in the
    # plugin.
    @logger.error("Exception in filterworker, the pipeline stopped processing new events, please check your filter configuration and restart Logstash.",
                  "exception" => e, "backtrace" => e.backtrace)
    raise
  end

  def wait_inputs
    @input_threads.each(&:join)
  end

  def start_inputs
    moreinputs = []
    @inputs.each do |input|
      if input.threadable && input.threads > 1
        (input.threads - 1).times do |i|
          moreinputs << input.clone
        end
      end
    end
    @inputs += moreinputs

    @inputs.each do |input|
      input.register
      start_input(input)
    end
  end

  def start_input(plugin)
    @input_threads << Thread.new { inputworker(plugin) }
  end

  def inputworker(plugin)
    LogStash::Util::set_thread_name("<#{plugin.class.config_name}")
    begin
      plugin.run(@input_queue)
    rescue => e
      # if plugin is stop
      if plugin.stop?
        @logger.debug("Input plugin raised exception during shutdown, ignoring it.",
                      :plugin => plugin.class.config_name, :exception => e,
                      :backtrace => e.backtrace)
        return
      end

      # otherwise, report error and restart
      if @logger.debug?
        @logger.error(I18n.t("logstash.pipeline.worker-error-debug",
                             :plugin => plugin.inspect, :error => e.to_s,
                             :exception => e.class,
                             :stacktrace => e.backtrace.join("\n")))
      else
        @logger.error(I18n.t("logstash.pipeline.worker-error",
                             :plugin => plugin.inspect, :error => e))
      end

      # Assuming the failure that caused this exception is transient,
      # let's sleep for a bit and execute #run again
      sleep(1)
      retry
    ensure
      plugin.do_close
    end
  end # def inputworker

  # initiate the pipeline shutdown sequence
  # this method is intended to be called from outside the pipeline thread
  # @param before_stop [Proc] code block called before performing stop operation on input plugins
  def shutdown(&before_stop)
    # shutdown can only start once the pipeline has completed its startup.
    # avoid potential race conditoon between the startup sequence and this
    # shutdown method which can be called from another thread at any time
    sleep(0.1) while !ready?

    # TODO: should we also check against calling shutdown multiple times concurently?

    before_stop.call if block_given?

    @logger.info "Closing inputs"
    @inputs.each(&:do_stop)
    @logger.info "Closed inputs"
  end # def shutdown

  def plugin(plugin_type, name, *args)
    args << {} if args.empty?
    klass = LogStash::Plugin.lookup(plugin_type, name)
    return klass.new(*args)
  end


  # for backward compatibility in devutils for the rspec helpers, this method is not used
  # in the pipeline anymore.
  def filter(event, &block)
     # filter_func returns all filtered events, including cancelled ones
     filter_func(event).each { |e| block.call(e) }
  end


  # perform filters flush and yeild flushed event to the passed block
  # @param options [Hash]
  # @option options [Boolean] :final => true to signal a final shutdown flush
  def flush_filters(options = {}, &block)
    flushers = options[:final] ? @shutdown_flushers : @periodic_flushers

    flushers.each do |flusher|
      flusher.call(options, &block)
    end
  end
end # class Pipeline
