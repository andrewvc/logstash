package com.logstash.pipeline.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by andrewvc on 2/20/16.
 */
public class Vertex {
    private final Type type;
    private final String name;
    private volatile Object instance;
    private final List<Vertex> outVertices;

    public enum Type { INPUT, QUEUE, FILTER, OUTPUT }

    Vertex(String name, Type type) {
        this.type = type;
        this.name = name;
        this.outVertices = new CopyOnWriteArrayList<>();
    }

    public List<Vertex> getOutVertices() {
        return outVertices;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
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
