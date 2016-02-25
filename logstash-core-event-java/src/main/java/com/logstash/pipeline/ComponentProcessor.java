package com.logstash.pipeline;

import com.logstash.Event;
import com.logstash.pipeline.graph.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by andrewvc on 2/22/16.
 */
public interface ComponentProcessor {
    List<Event> process(Component component, List<Event> events);
    void flush(Component c, boolean shutdown);
    void setup(Component component);
}
