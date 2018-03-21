package org.logstash.plugins.pipeline;

import org.logstash.ext.JrubyEventExtLibrary;

public interface PipelineInput {
    /**
     * Accepts an event
     * It might be rejected if the input is stopping
     * @param event
     * @return true if the event was successfully received
     */
    boolean internalReceive(JrubyEventExtLibrary.RubyEvent event);

    /**
     *
     * @return true if the input is running
     */
    boolean isRunning();
}
