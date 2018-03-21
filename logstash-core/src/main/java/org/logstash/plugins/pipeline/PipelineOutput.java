package org.logstash.plugins.pipeline;

import org.logstash.ext.JrubyEventExtLibrary;

import java.util.function.Function;

public interface PipelineOutput {
    void updateAddressReceiver(String address, Function<JrubyEventExtLibrary.RubyEvent, Boolean> receiverFn);

    void removeAddressReceiver(String address);
}
