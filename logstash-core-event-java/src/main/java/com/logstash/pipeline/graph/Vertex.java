package com.logstash.pipeline.graph;

import com.logstash.pipeline.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 2/20/16.
 */
public class Vertex {
    private final List<Vertex> outVertices;
    private final Component component;

    Vertex(String id, Component component) {
        this.component = component;
        this.outVertices = new ArrayList<>();
    }

    public Component getComponent() {
        return component;
    }

    public List<Vertex> getOutVertices() {
        return outVertices;
    }
    public void addOutVertex(Vertex v) {
        this.outVertices.add(v);
    }
}
