package com.logstash.pipeline;

import com.logstash.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 2/22/16.
 */
public interface ComponentProcessor {
    List<Event> process(Component component, List<Event> events);
    void flush(Component c, boolean shutdown);
    void setup(Component component);
}
