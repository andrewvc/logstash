package com.logstash.pipeline;


import com.logstash.pipeline.graph.Vertex;

import java.util.Map;

/**
 * Created by andrewvc on 2/20/16.
 */
public class PipelineGraph {
    private final Map<String, Vertex> vertices;

    public PipelineGraph(Map<String, Vertex> vertices) {
        this.vertices = vertices;
    }
}
