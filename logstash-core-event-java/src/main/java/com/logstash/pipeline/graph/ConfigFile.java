package com.logstash.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.logstash.pipeline.Component;
import com.logstash.pipeline.ComponentProcessor;
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

    public static class InvalidGraphConfigFile extends Throwable {
        InvalidGraphConfigFile(String message) {
            super(message);
        }
    }

    public static ConfigFile fromString(String source, ComponentProcessor componentProcessor) throws IOException, InvalidGraphConfigFile {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode tree = mapper.readTree(source);
        return new ConfigFile(source, tree, componentProcessor);
    }

    public ConfigFile(String source, JsonNode tree, ComponentProcessor componentProcessor) throws InvalidGraphConfigFile {
        this.source = source;
        this.tree = tree;

        this.graphElement = tree.get("graph");
        buildVertices();
        connectVertices();
        this.pipelineGraph = new PipelineGraph(vertices, componentProcessor);
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

        // Use a for loop here since it's a little tricky with lambdas + exceptions
        for (Iterator<Map.Entry<String, JsonNode>> geFields = graphElement.fields(); geFields.hasNext();) {
            Map.Entry<String, JsonNode> e = geFields.next();

            JsonNode propsNode = e.getValue();
            String id = e.getKey();

            JsonNode componentNameNode = propsNode.get("component");
            if (componentNameNode == null) {
                throw new InvalidGraphConfigFile("Missing component declaration for: " + propsNode.asText());
            }
            String componentNameText = componentNameNode.asText();

            JsonNode optionsNode = propsNode.get("options");

            String optionsStr;
            if (optionsNode == null) {
                optionsStr = null;
            } else {
                optionsStr = optionsNode.toString();
            }

            Component component = new Component(id, componentNameText, optionsStr);
            vertices.put(id, new Vertex(id, component));
        }
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
            });
        });
    }
}
