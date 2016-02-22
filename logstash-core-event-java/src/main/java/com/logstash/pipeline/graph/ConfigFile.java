package com.logstash.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.logstash.pipeline.PipelineGraph;

import java.io.IOException;
import java.util.*;

/**
 * Created by andrewvc on 2/20/16.
 */
public class ConfigFile {
    private final JsonNode graphElement;
    private final String source;
    private final JsonNode tree;
    private final Map<String, Vertex> vertices = new HashMap<>();

    private final PipelineGraph pipelineGraph;

    static class InvalidGraphConfigFile extends Throwable {
        InvalidGraphConfigFile(String message) {
            super(message);
        }
    }

    public static ConfigFile fromString(String source) throws IOException, InvalidGraphConfigFile {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode tree = mapper.readTree(source);
        return new ConfigFile(source, tree);
    }

    ConfigFile(String source, JsonNode tree) throws InvalidGraphConfigFile {
        this.source = source;
        this.tree = tree;

        this.graphElement = tree.get("graph");
        buildVertices();
        connectVertices();
        this.pipelineGraph = new PipelineGraph(vertices);
    }

    public PipelineGraph getPipelineGraph() {
        return pipelineGraph;
    }

    public String source() {
        return source;
    }

    public void buildVertices() throws InvalidGraphConfigFile {
        if (graphElement == null) {
            throw new InvalidGraphConfigFile("Missing vertices element in config: " + source);
        }

        graphElement.fields().forEachRemaining(e -> {
            JsonNode propsNode = e.getValue();
            String name = e.getKey();

            Vertex.Type type = Vertex.Type.valueOf(propsNode.get("type").asText().toUpperCase());
            vertices.put(name, new Vertex(name, type));
        });
    }

    private void connectVertices() {
        graphElement.fields().forEachRemaining(field -> {
            String name = field.getKey();

            JsonNode propsNode = field.getValue();
            JsonNode toNode = propsNode.get("to");
            if (toNode == null || !toNode.isArray()) {
                return;
            }

            Vertex v = vertices.get(name);
            if (v == null) {
                throw new IllegalArgumentException("Could not connect unknown vertex: " + name);
            }

            Iterator<JsonNode> elems = toNode.elements();
            elems.forEachRemaining(toName -> {
                if (toName.isTextual()) {
                    v.addOutVertex(vertices.get(toName.asText()));
                } else {
                    throw new IllegalArgumentException(("Non-textual 'out' vertex"));
                }

                v.getOutVertices().add(v);
            });
        });
    }
}
