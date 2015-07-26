# encoding: utf-8
require "logstash/namespace"
require "logstash/logging"

require "thread" # for SizedQueue
class EphemeralQueue < SizedQueue
  # TODO(sissel): Soon will implement push/pop stats, etc

  def initialize(name, size)
    super(size)
  end

  def ack
    #noop
  end
end
