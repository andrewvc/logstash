package com.logstash.pipeline;

import com.logstash.Event;

import java.util.List;

/**
 * Created by andrewvc on 2/22/16.
 */
public interface ComponentProcessor {
    public List<Event> process(Component component, List<Event> inEvents);

    void setup(Component component);
}
