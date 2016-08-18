module LogStash module OutputDelegatorStrategies class Shared
  def initialize(logger, klass, metric, xopts={}, plugin_args={})
    @output = klass.new(plugin_args)
  end
  
  def register
    @output.register
  end

  def multi_receive(events)
    @output.multi_receive(events)
  end

  def do_close    
    @output.do_close
  end

  ::LogStash::OutputDelegatorStrategyRegistry.instance.register(:shared, self)  
end; end; end

