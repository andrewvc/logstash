package com.logstash.pipeline.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 2/20/16.
 */
public class Vertex {
    private final Type type;
    private final String id;
    private final String componentName;
    private volatile Object instance;
    private final List<Vertex> outVertices;

    public enum Type { INPUT, QUEUE, FILTER, OUTPUT }

    Vertex(String id, String componentName) {
        this.id = id;
        this.type = extractTypeFromComponentName(componentName);
        this.componentName = componentName;
        this.outVertices = new ArrayList<>();
    }

    private Type extractTypeFromComponentName(String componentName) {
        String[] componentParts = componentName.split("-", 2);

        return Type.valueOf(componentParts[0].toUpperCase());
    }

    public List<Vertex> getOutVertices() {
        return outVertices;
    }

    public Type getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getComponentName() {
        return componentName;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public void addOutVertex(Vertex v) {
        this.outVertices.add(v);
    }
}
