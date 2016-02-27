package com.logstash.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.logstash.pipeline.Component;
import com.logstash.pipeline.ComponentProcessor;
import com.logstash.pipeline.PipelineGraph;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

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

    private void connectVertices() throws InvalidGraphConfigFile {
        Iterator<Map.Entry<String, JsonNode>> geFields = graphElement.fields();
        while(geFields.hasNext()) {
            Map.Entry<String, JsonNode> field = geFields.next();
            String name = field.getKey();

            JsonNode propsNode = field.getValue();
            JsonNode toNode = propsNode.get("to");
            if (toNode == null || !toNode.isArray()) {
                return;
            }

            Vertex v = vertices.get(name);
            if (v == null) throw new IllegalArgumentException("Could not connect unknown vertex: " + name);

            Iterator<JsonNode> toNodeElements = toNode.elements();
            while (toNodeElements.hasNext()) {
                JsonNode toElem = toNodeElements.next();
                if (v.getComponent().getType() == Component.Type.PREDICATE) {
                    createPredicateVertices(v, toElem);
                } else if (toElem.isTextual()) {
                    createStandardVertices(v, toElem);
                } else {
                    throw new IllegalArgumentException(("Non-textual 'out' vertex"));
                }
            };
        };
    }

    private void createStandardVertices(Vertex v, JsonNode toElem) throws InvalidGraphConfigFile {
        String toElemVertexName = toElem.asText();
        Vertex toElemVertex = vertices.get(toElemVertexName);
        if (toElemVertex == null) {
            throw new InvalidGraphConfigFile("Could not find vertex: " + toElemVertexName);
        }

        v.addOutEdge(toElemVertex);
    }

    private void createPredicateVertices(Vertex v, JsonNode toElem) throws InvalidGraphConfigFile {
        if (!toElem.isArray()) {
            throw new InvalidGraphConfigFile("Expected array for predicate! Got: " + toElem.asText());
        }

        Map<Condition, List<Vertex>> conditionsToVertices = new HashMap<>();
        Iterator<JsonNode> clauseNodes = toElem.elements();
        while (clauseNodes.hasNext()) {
            createToVertices(v, clauseNodes.next());
        }
    }

    private void createToVertices(Vertex v, JsonNode clauseNode) throws InvalidGraphConfigFile {
        if (!clauseNode.isArray()) {
            throw new InvalidGraphConfigFile("Expected predicate clause to be an array! Got: " + clauseNode.asText());
        }

        Condition currentCondition;
        Iterator<JsonNode> cnElems = clauseNode.elements();

        if (!cnElems.hasNext()) {
            throw new InvalidGraphConfigFile("Expected predicate clause to have at least one element! Got: " + clauseNode);
        }

        JsonNode condElem = cnElems.next();
        if (!condElem.isTextual()) throw new InvalidGraphConfigFile("Expected a textual condition element! Got: " + condElem.asText());
        currentCondition = new Condition(cnElems.next().asText());

        if (!cnElems.hasNext()) {
            throw new InvalidGraphConfigFile("Expected a list of vertices following the predicate clause!");
        }

        JsonNode condToElem = cnElems.next();
        if (!condToElem.isArray()) throw new InvalidGraphConfigFile("Predicate to list must be a list of vertex names! Got: " + condToElem.asText());

        Iterator<JsonNode> condToElemNameElems = condToElem.elements();
        while(condToElemNameElems.hasNext()) {
            JsonNode condtoNameElem = condToElemNameElems.next();
            String condToVertexName = condtoNameElem.asText();
            Vertex condToVertex = vertices.get(condToVertexName);
            if (condToVertex == null) {
                throw new InvalidGraphConfigFile("Could not find vertex: " + condToVertexName);
            }
            v.addOutEdge(condToVertex, currentCondition);
        }
    }
}
