package com.logstash.pipeline;

import com.logstash.Event;

import java.util.List;

/**
 * Created by andrewvc on 2/24/16.
 */
public class PipelineComponent {
    public PipelineComponent() {

    }

    public void start() {

    }

    private List<Event> preAccept(List<Event> events) {
        return events;
    }

    public List<Event> accept(List<Event> events) {
        return events;
    }

    public void stop() {

    }
}
