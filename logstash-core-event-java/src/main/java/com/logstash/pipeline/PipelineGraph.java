package com.logstash.pipeline;


import com.logstash.Event;
import com.logstash.pipeline.graph.Vertex;

import java.util.List;
import java.util.Map;

/**
 * Created by andrewvc on 2/20/16.
 */
public class PipelineGraph {
    private final Map<String, Vertex> vertices;

    public PipelineGraph(Map<String, Vertex> vertices) {
        this.vertices = vertices;
    }

    public void processWorker(ComponentProcessor componentProcessor, List<Event> events) {
        workerVertices().forEach(wv -> {
            processVertex(wv, componentProcessor, events);
        });
    }

    public void processVertex(Vertex v, ComponentProcessor componentProcessor, List<Event> inEvents) {
        List<Event> outEvents = componentProcessor.process(v.getId(), v.getType(), v.getComponentName(), inEvents);
        v.getOutVertices().forEach(outV -> processVertex(outV, componentProcessor, outEvents));
    }

    // Vertices that occur after the queue
    // This is a bit hacky and only supports one queue at the moment
    // for our current pipeline
    public List<Vertex> workerVertices() {
        return queueVertex().getOutVertices();
    }

    public Vertex queueVertex() {
        return this.vertices.get("main-queue");
    }
}
