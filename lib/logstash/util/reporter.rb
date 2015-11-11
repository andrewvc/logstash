# encoding: utf-8
class InflightEventsReporter
  def self.logger=(logger)
    @logger = logger
  end

  def self.start(pipeline)
    Thread.new do
      loop do
        sleep 5
        begin
          report(pipeline)
        rescue Exception => e
          @logger.error("Could not generate report for pipeline",
                        :pipeline => pipeline.object_id,
                        :message => e.message,
                        :class => e.class.name,
                        :backtrace => e.backtrace)
        end
      end
    end
  end

  def self.worker_states(pipeline)
    pipeline.inflight_batches_synchronize do |batch_map|
      pipeline.worker_threads.map.with_index do |thread,idx|
        status = thread.status || "dead"
        {
          :status => status,
          :alive => thread.alive?,
          :index => idx,
          :events_inflight => batch_map[thread].size
        }
      end
    end
  end

  def self.output_states(outputs)
    outputs.map do |output|
      is_multi_worker = output.workers > 1

      idle, busy = if is_multi_worker
                     aw_size = output.available_workers.size
                     [aw_size, output.workers - aw_size]
                   else
                     output.single_worker_mutex.locked? ? [0,1] : [1,0]
                   end

      {
        :type => output.class.config_name,
        :config => output.config,
        :is_multi_worker => is_multi_worker,
        :workers => output.workers,
        :busy_workers => busy,
        :idle_workers => idle
      }
    end
  end

  def self.report(pipeline)
    worker_threads = pipeline.worker_threads
    states = worker_states(pipeline)
    report = {
      "events_emitted" => pipeline.events_emitted.value,
      "events_consumed" => pipeline.events_consumed.value,
      "total_workers" => worker_threads.count,
      "total_events_inflight" => states.map {|s| s[:events_inflight] }.reduce(:+),
      "worker_states" => states,
      "output_info" => output_states(pipeline.outputs)
    }

    @logger.warn ["INFLIGHT_EVENTS_REPORT", Time.now.iso8601, report]
  end
end
