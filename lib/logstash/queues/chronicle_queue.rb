require 'logstash/ext/ls_chronicle.jar'
java_import 'co.elastic.logstash.LogstashRotatingChronicleQueue'

class ChronicleQueue
  def initialize(name, size)
    @name = name
    @size = size
    # TODO: Remove this, for testing only
    `rm -rf /tmp/ls-queue/`
    @q = Java::CoElasticLogstash::LogstashRotatingChronicleQueue.new(1000000000, "/tmp/ls-queue/")
  end

  def push(event)
    # TODO: Handle this
    return if event.is_a?(LogStash::FlushEvent)

    s = ::LogStash::Json.dump(event)
    @q.push(s.to_java_bytes())
  rescue LogStash::Json::GeneratorError => e
    puts "Could not serialize", :o => event
  end
  alias_method(:<<, :push)

  def pop()
    s = String.from_java_bytes(@q.deq())

    LogStash::Event.new(::LogStash::Json.load(s))
  rescue LogStash::Json::ParserError => e
    #TODO Fix these bugs
    puts e.message, :str => s
    LogStash::Event.new()
  end

  def ack
    @q.ack()
  end

  def size
    0
  end
end