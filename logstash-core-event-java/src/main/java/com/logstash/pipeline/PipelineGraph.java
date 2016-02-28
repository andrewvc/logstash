package com.logstash.pipeline;


import com.logstash.Event;
import com.logstash.pipeline.graph.Vertex;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public void processWorker(Batch batch) {
        System.out.println("PWorker" + workerVertices().count());
        workerVertices().forEach(wv -> {
            processVertex(wv, batch.getEvents());
        });
    }

    public void processVertex(Vertex v, List<Event> inEvents) {
        System.out.println("Traversing vertex" + v.getComponent().getId());
        Component component = v.getComponent();
        List<Event> outEvents = componentProcessor.process(component, inEvents);

        v.getOutVertices().forEach(outV -> processVertex(outV, outEvents));
    }

    // Vertices that occur after the queue
    // This is a bit hacky and only supports one queue at the moment
    // for our current pipeline
    public Stream<Vertex> workerVertices() {
        return queueVertex().getOutVertices();
    }

    public Vertex queueVertex() {
        return this.vertices.get("main-queue");
    }

    public Component[] getComponents() {
        return this.vertices.values().stream().map(Vertex::getComponent).toArray(Component[]::new);
    }

    public Stream<Component> componentStream() {
        return this.vertices.values().stream().map(Vertex::getComponent);
    }

    public Map<String, Vertex> getVertexMapping() {
        return this.vertices;
    }

    public Collection<Vertex> getVertices() {
        return this.vertices.values();
    }

    public void flush(boolean shutdown) {
        this.componentStream().forEach(c -> componentProcessor.flush(c, shutdown));
    }
}
