package com.logstash.pipeline;


import com.logstash.Event;
import com.logstash.pipeline.graph.Vertex;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by andrewvc on 2/20/16.
 */
public class PipelineGraph {
    private final Map<String, Vertex> vertices;
    private final ComponentProcessor componentProcessor;

    public PipelineGraph(Map<String, Vertex> vertices, ComponentProcessor componentProcessor) {
        this.vertices = vertices;
        this.componentProcessor = componentProcessor;

        //TODO: Make this streamy
        Component[] components = this.getComponents();
        for (int i=0; i < components.length; i++) {
            Component component = components[i];
            componentProcessor.setup(component);
        }
    }

    public void processWorker(ComponentProcessor componentProcessor, List<Event> events) {
        workerVertices().forEach(wv -> {
            processVertex(wv, componentProcessor, events);
        });
    }

    public void processVertex(Vertex v, ComponentProcessor componentProcessor, List<Event> inEvents) {
        Component component = v.getComponent();
        List<Event> outEvents = componentProcessor.process(component, inEvents);
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

    public Component[] getComponents() {
        return this.vertices.values().stream().map(Vertex::getComponent).toArray(Component[]::new);
    }

    public Map<String, Vertex> getVertexMapping() {
        return this.vertices;
    }

    public Collection<Vertex> getVertices() {
        return this.vertices.values();
    }
}
