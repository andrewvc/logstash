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
require "logstash/config/cpu_core_strategy"
require "logstash/util/defaults_printer"
require "logstash/shutdown_watcher"
require "logstash/util/wrapped_synchronous_queue"
require "logstash/pipeline_reporter"
require "logstash/instrument/metric"
require "logstash/instrument/namespaced_metric"
require "logstash/instrument/null_metric"
require "logstash/instrument/collector"
require "logstash/output_delegator"
require "logstash/filter_delegator"

java_import 'com.logstash.pipeline.Worker'
java_import 'com.logstash.pipeline.graph.ConfigFile'
java_import 'com.logstash.pipeline.Constants'


module LogStash; class Pipeline
 attr_reader :inputs,
    :filters,
    :outputs,
    :worker_threads,
    :events_consumed,
    :events_filtered,
    :reporter,
    :pipeline_id,
    :metric,
    :logger,
    :started_at,
    :thread,
    :config_str,
    :original_settings

  DEFAULT_SETTINGS = {
    :default_pipeline_workers => LogStash::Config::CpuCoreStrategy.maximum,
    :pipeline_batch_size => 125,
    :pipeline_batch_delay => 5, # in milliseconds
    :flush_interval => 5, # in seconds
    :flush_timeout_interval => 60 # in seconds
  }
  MAX_INFLIGHT_WARN_THRESHOLD = 10_000

  def self.validate_config(config_str, settings = {})
    begin
      # There should be a better way to test this
      self.new(config_str, settings)
    rescue => e
      e.message
    end
  end

 class PipelineComponentProcessor
   include com.logstash.pipeline.ComponentProcessor

   def initialize(pipeline,&block)
     @lookup = {}
     @execution_lookup = {}
     @pipeline = pipeline
     @on_setup = block
   end

   def setup(component)
     type, name = component.getComponentName.split("-", 2)
     options = component.getOptionsStr ? LogStash::Json.load(component.getOptionsStr) : {}
     return if type == "queue" # TODO: One day this will do something...
     plugin = @pipeline.plugin(type, name, options)
     @on_setup.call(component, plugin) if @on_setup
     @lookup[component] = plugin
     @execution_lookup[component] = if plugin.is_a? LogStash::FilterDelegator
                                      proc do |events|
                                        events = plugin.multi_filter(events)
                                        @pipeline.events_filtered.increment(events.length)
                                        events
                                      end
                                      plugin.method(:multi_filter)
                                    elsif plugin.is_a? LogStash::OutputDelegator
                                      proc do |events|
                                        events = plugin.multi_receive(events)
                                        @pipeline.events_consumed.increment(events.length)
                                        events
                                      end
                                    end
   end

   def process(component, events)
     rubyified_events = rubyify_events(events.compact)
     res = @execution_lookup[component].call(rubyified_events)
     javaify_events(res)
   end

   def rubyify_events(java_events)
     java_events.map {|e| Event.from_java(e)}
   end

   def javaify_events(ruby_events)
     ruby_events.map {|e| e.to_java}
   end

   def flush(component, shutdown)
     component = @lookup[component]
     if component.respond_to?(:flush)
       component.flush()
     end
   end

 end

  def initialize(config_str, settings = {})
    @config_str = config_str
    @original_settings = settings
    @logger = Cabin::Channel.get(LogStash)
    @pipeline_id = settings[:pipeline_id] || self.object_id
    @settings = DEFAULT_SETTINGS.clone
    settings.each {|setting, value| configure(setting, value) }
    @reporter = LogStash::PipelineReporter.new(@logger, self)

    @inputs = []
    @filters = []
    @outputs = []

    @worker_threads = []

    # Metric object should be passed upstream, multiple pipeline share the same metric
    # and collector only the namespace will changes.
    # If no metric is given, we use a `NullMetric` for all internal calls.
    # We also do this to make the changes backward compatible with previous testing of the
    # pipeline.
    #
    # This need to be configured before we evaluate the code to make
    # sure the metric instance is correctly send to the plugin.
    @metric = settings.fetch(:metric, Instrument::NullMetric.new)

    component_processor = PipelineComponentProcessor.new(self) do |component, plugin|
      case plugin
        when LogStash::Inputs::Base
          @inputs << plugin
        when LogStash::FilterDelegator
          @filters << plugin
        when LogStash::OutputDelegator
          @outputs << plugin
      end
    end
    @config_file = com.logstash.pipeline.graph.ConfigFile.fromString(config_str, component_processor)
    @graph = @config_file.getPipelineGraph()

    @input_queue = LogStash::Util::WrappedSynchronousQueue.new
    @events_filtered = Concurrent::AtomicFixnum.new(0)
    @events_consumed = Concurrent::AtomicFixnum.new(0)

    # We generally only want one thread at a time able to access pop/take/poll operations
    # from this queue. We also depend on this to be able to block consumers while we snapshot
    # in-flight buffers
    @input_queue_pop_mutex = Mutex.new
    @input_threads = []
    # @ready requires thread safety since it is typically polled from outside the pipeline thread
    @ready = Concurrent::AtomicBoolean.new(false)
    @running = Concurrent::AtomicBoolean.new(false)
    @flushing = Concurrent::AtomicReference.new(false)
  end # def initialize

  def ready?
    @ready.value
  end

  def configure(setting, value)
    @settings[setting] = value
  end

  def safe_pipeline_worker_count
    default = DEFAULT_SETTINGS[:default_pipeline_workers]
    thread_count = @settings[:pipeline_workers] #override from args "-w 8" or config
    safe_filters, unsafe_filters = @filters.partition(&:threadsafe?)

    if unsafe_filters.any?
      plugins = unsafe_filters.collect { |f| f.config_name }
      case thread_count
      when nil
        # user did not specify a worker thread count
        # warn if the default is multiple

        if default > 1
          @logger.warn("Defaulting pipeline worker threads to 1 because there are some filters that might not work with multiple worker threads",
                       :count_was => default, :filters => plugins)
        end

        1 # can't allow the default value to propagate if there are unsafe filters
      when 0, 1
        1
      else
        @logger.warn("Warning: Manual override - there are filters that might not work with multiple worker threads",
                     :worker_threads => thread_count, :filters => plugins)
        thread_count # allow user to force this even if there are unsafe filters
      end
    else
      thread_count || default
    end
  end

  def filters?
    return @filters.any?
  end

  def run
    @started_at = Time.now

    LogStash::Util.set_thread_name("[#{pipeline_id}]-pipeline-manager")
    @logger.terminal(LogStash::Util::DefaultsPrinter.print(@settings))
    @thread = Thread.current

    start_workers

    @logger.info("Pipeline started")
    @logger.terminal("Logstash startup completed")

    # Block until all inputs have stopped
    # Generally this happens if SIGINT is sent and `shutdown` is called from an external thread

    transition_to_running
    start_flusher # Launches a non-blocking thread for flush events
    wait_inputs
    transition_to_stopped

    @logger.info("Input plugins stopped! Will shutdown filter/output workers.")

    shutdown_flusher
    shutdown_workers

    @logger.info("Pipeline shutdown complete.")
    @logger.terminal("Logstash shutdown completed")

    # exit code
    return 0
  end # def run

  def transition_to_running
    @running.make_true
  end

  def transition_to_stopped
    @running.make_false
  end

  def running?
    @running.true?
  end

  def stopped?
    @running.false?
  end

  def start_workers
    @inflight_batches = {}

    @worker_threads.clear # In case we're restarting the pipeline
    begin
      start_inputs
      @outputs.each {|o| o.register }
      @filters.each {|f| f.register }

      pipeline_workers = safe_pipeline_worker_count
      batch_size = @settings[:pipeline_batch_size]
      batch_delay = @settings[:pipeline_batch_delay]
      max_inflight = batch_size * pipeline_workers
      @logger.info("Starting pipeline",
                   :id => self.pipeline_id,
                   :pipeline_workers => pipeline_workers,
                   :batch_size => batch_size,
                   :batch_delay => batch_delay,
                   :max_inflight => max_inflight)
      if max_inflight > MAX_INFLIGHT_WARN_THRESHOLD
        @logger.warn "CAUTION: Recommended inflight events max exceeded! Logstash will run with up to #{max_inflight} events in memory in your current configuration. If your message sizes are large this may cause instability with the default heap size. Please consider setting a non-standard heap size, changing the batch size (currently #{batch_size}), or changing the number of pipeline workers (currently #{pipeline_workers})"
      end

      workers = com.logstash.pipeline.Worker.startWorkers(pipeline_workers, @graph, @input_queue.queue, batch_size, batch_delay)
      @worker_threads = workers.map(&:getThread)
    ensure
      # it is important to garantee @ready to be true after the startup sequence has been completed
      # to potentially unblock the shutdown method which may be waiting on @ready to proceed
      @ready.make_true
    end
  end

  # Main body of what a worker thread does
  # Repeatedly takes batches off the queue, filters, then outputs them
  def worker_loop(batch_size, batch_delay)
    running = true

    namespace_events = metric.namespace([:stats, :events])
    namespace_pipeline = metric.namespace([:stats, :pipelines, pipeline_id.to_s.to_sym, :events])

    while running
      # To understand the purpose behind this synchronize please read the body of take_batch
      input_batch, signal = @input_queue_pop_mutex.synchronize { take_batch(batch_size, batch_delay) }
      running = false if signal == LogStash::SHUTDOWN

      @events_consumed.increment(input_batch.size)
      namespace_events.increment(:in, input_batch.size)
      namespace_pipeline.increment(:in, input_batch.size)

      filtered_batch = filter_batch(input_batch)

      if signal # Flush on SHUTDOWN or FLUSH
        flush_options = (signal == LogStash::SHUTDOWN) ? {:final => true} : {}
        flush_filters_to_batch(filtered_batch, flush_options)
      end

      @events_filtered.increment(filtered_batch.size)

      namespace_events.increment(:filtered, filtered_batch.size)
      namespace_pipeline.increment(:filtered, filtered_batch.size)

      output_batch(filtered_batch)

      namespace_events.increment(:out, filtered_batch.size)
      namespace_pipeline.increment(:out, filtered_batch.size)

      inflight_batches_synchronize { set_current_thread_inflight_batch(nil) }
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
    LogStash::Util::set_thread_name("[#{pipeline_id}]<#{plugin.class.config_name}")
    begin
      plugin.run(@input_queue)
    rescue => e
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

  # After `shutdown` is called from an external thread this is called from the main thread to
  # tell the worker threads to stop and then block until they've fully stopped
  # This also stops all filter and output plugins
  def shutdown_workers
    # Each worker thread will receive this exactly once!
    @worker_threads.each do |t|
      @logger.debug("Pushing shutdown", :thread => t)
      10.times do
        @input_queue.queue.put(LogStash::SHUTDOWN)
      end
    end

    @worker_threads.each do |t|
      @logger.debug("Shutdown waiting for worker thread #{t}")
      t.join
    end

    @filters.each(&:do_close)
    @outputs.each(&:do_close)
  end

  def plugin(plugin_type, name, *args)
    args << {} if args.empty?

    pipeline_scoped_metric = metric.namespace([:stats, :pipelines, pipeline_id.to_s.to_sym, :plugins])

    klass = LogStash::Plugin.lookup(plugin_type, name)

    if plugin_type == "output"
      LogStash::OutputDelegator.new(@logger, klass, default_output_workers, pipeline_scoped_metric.namespace(:outputs), *args)
    elsif plugin_type == "filter"
      LogStash::FilterDelegator.new(@logger, klass, pipeline_scoped_metric.namespace(:filters), *args)
    else
      klass.new(*args)
    end
  end

  def default_output_workers
    @settings[:pipeline_workers] || @settings[:default_pipeline_workers]
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

  def start_flusher
    # Invariant to help detect improper initialization
    raise "Attempted to start flusher on a stopped pipeline!" if stopped?

    @flusher_thread = Thread.new do
      while Stud.stoppable_sleep(5, 0.1) { stopped? }
        flush
        break if stopped?
      end
    end
  end

  def shutdown_flusher
    @flusher_thread.join
  end

  def flush
    if @flushing.compare_and_set(false, true)
      @logger.debug? && @logger.debug("Pushing flush onto pipeline")
      @input_queue.push(LogStash::FLUSH)
    end
  end


  # Calculate the uptime in milliseconds
  #
  # @return [Fixnum] Uptime in milliseconds, 0 if the pipeline is not started
  def uptime
    return 0 if started_at.nil?
    ((Time.now.to_f - started_at.to_f) * 1000.0).to_i
  end

  # perform filters flush into the output queue
  # @param options [Hash]
  # @option options [Boolean] :final => true to signal a final shutdown flush
  def flush_filters_to_batch(batch, options = {})
    flush_filters(options) do |event|
      unless event.cancelled?
        @logger.debug? and @logger.debug("Pushing flushed events", :event => event)
        batch << event
      end
    end

    @flushing.set(false)
  end # flush_filters_to_output!

  def plugin_threads_info
    input_threads = @input_threads.select {|t| t.alive? }
    worker_threads = @worker_threads.select {|t| t.alive? }
    (input_threads + worker_threads).map {|t| LogStash::Util.thread_info(t) }
  end

  def stalling_threads_info
    plugin_threads_info
      .reject {|t| t["blocked_on"] } # known benign blocking statuses
      .each {|t| t.delete("backtrace") }
      .each {|t| t.delete("blocked_on") }
      .each {|t| t.delete("status") }
  end
end end
