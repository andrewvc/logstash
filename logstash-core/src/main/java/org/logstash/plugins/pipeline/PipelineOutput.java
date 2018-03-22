package org.logstash.plugins.pipeline;

import org.logstash.ext.JrubyEventExtLibrary;

import java.util.Map;
import java.util.function.Function;

public interface PipelineOutput {
    void updateAddressReceivers(Map<String, PipelineInput> map);
}
