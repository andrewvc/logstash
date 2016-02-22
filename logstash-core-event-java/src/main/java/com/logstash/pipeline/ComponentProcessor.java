package com.logstash.pipeline;

import com.logstash.Event;
import com.logstash.pipeline.graph.Vertex;

import java.util.List;

/**
 * Created by andrewvc on 2/22/16.
 */
public interface ComponentProcessor {
    public List<Event> process(String id, Vertex.Type type, String componentName, List<Event> inEvents);
}
