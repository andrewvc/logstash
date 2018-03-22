module ::LogStash; module Plugins; module Builtin; module Pipeline; class Output < ::LogStash::Outputs::Base
  include org.logstash.plugins.pipeline.PipelineOutput

  config_name "pipeline"

  concurrency :shared

  config :send_to, :validate => :string, :required => true, :list => true

  attr_reader :pipeline_bus

  def register
    @pipeline_bus = execution_context.agent.pipeline_bus
    @address_receivers = java.util.concurrent.ConcurrentHashMap.new
    pipeline_bus.registerSender(self, @send_to)
  end

  def updateAddressReceiver(address, function)
    @address_receivers[address] = function
  end

  def removeAddressReceiver(address)
    @address_receivers.remove(address)
  end

  NO_LISTENER_LOG_MESSAGE = "Internal output to address waiting for listener to start"
  def multi_receive(events)
    @send_to.each do |address|
      events.each do |e|
        event_clone = e.clone;
        while !apply_address_receiver(address, event_clone)
          byRunState = pipeline_bus.addressesByRunState
          @logger.info(
            NO_LISTENER_LOG_MESSAGE,
            :destination_address => address,
            :registered_addresses => {:running => byRunState.running, :not_running => byRunState.notRunning}
          )
          sleep 1
        end
      end
    end
  end

  def apply_address_receiver(address, event)
    receiver = @address_receivers[address]
    receiver.nil? ? nil : receiver.apply(event)
  end

  def pipeline_shutting_down?
    execution_context.pipeline.inputs.all? {|input| input.stop?}
  end

  def close
    pipeline_bus.unregisterSender(self, @send_to)
  end
end; end; end; end; end