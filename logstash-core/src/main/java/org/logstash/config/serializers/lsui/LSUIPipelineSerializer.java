package org.logstash.config.serializers.lsui;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.logstash.config.ir.InvalidIRException;
import org.logstash.config.ir.Pipeline;
import org.logstash.config.ir.PluginDefinition;
import org.logstash.config.ir.graph.*;
import org.logstash.config.serializers.SerializationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by andrewvc on 11/15/16.
 */
public class LSUIPipelineSerializer {
    public static String serialize(Pipeline pipeline) throws SerializationException {
        try {
            JsonFactory factory = new JsonFactory();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JsonGenerator jgen = null;
            jgen = factory.createGenerator(outputStream);
            serialize(pipeline, jgen);
            jgen.close();
            return outputStream.toString("UTF-8");
        } catch (Exception e) {
            throw new SerializationException("Unexpected error serializing JSON! Pipeline: " + pipeline + "orig message: " + e.getMessage(), e);
        }
    }

    public static String indentJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Object readJson = new ObjectMapper().readValue(json, Object.class);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(readJson);
    }


    public static void serialize(Pipeline pipeline, JsonGenerator jgen) throws IOException, InvalidIRException, SerializationException {
        jgen.writeStartObject();
        logstashSection(jgen);
        processorsSection(pipeline, jgen);
        jgen.writeEndObject();
    }

    private static void processorsSection(Pipeline pipeline, JsonGenerator jgen) throws InvalidIRException, IOException, SerializationException {
        jgen.writeObjectFieldStart("processors");
        for (Vertex v : pipeline.getGraph().getSortedVertices()) {
            processor(v, jgen);
        }
        jgen.writeEndObject();
    }

    private static void processor(Vertex v, JsonGenerator jgen) throws IOException, SerializationException {
            jgen.writeObjectFieldStart(v.getId());
            jgen.writeStringField("type", v.specialTypeString());
            if (v instanceof PluginVertex) {
                pluginProperties((PluginVertex) v, jgen);
            } else if (v instanceof IfVertex) {
                ifProperties((IfVertex) v, jgen);
            } else if (v instanceof SpecialVertex) {
                specialProperties((SpecialVertex) v, jgen);
            } else {
                throw new SerializationException("Unexpected vertex class! Vertex: " + v.toString());
            }

            if (v.getMeta() != null) {
                writeComplexObject("source_metadata", v.getMeta(), jgen);
            }


            toVertices(v, jgen);
            jgen.writeEndObject();
    }

    private static void toVertices(Vertex v, JsonGenerator jgen) throws IOException {
        jgen.writeArrayFieldStart("to");

        for (Edge e : v.getOutgoingEdges()) {
            Vertex to = e.getTo();
            jgen.writeStartObject();
            jgen.writeStringField("id", to.getId());

            if (e instanceof BooleanEdge) {
                BooleanEdge be = (BooleanEdge) e;
                jgen.writeBooleanField("when", be.getEdgeType().booleanValue());
            }

            jgen.writeEndObject();
        }

        jgen.writeEndArray();

    }

    private static void ifProperties(IfVertex v, JsonGenerator jgen) throws IOException {
        jgen.writeStringField("condition", v.humanReadableExpression());
    }

    private static void specialProperties(SpecialVertex v, JsonGenerator jgen) throws IOException {
        jgen.writeStringField("special_type", v.getType().toString().toLowerCase().replace(" ", "_"));
    }

    private static void pluginProperties(PluginVertex v, JsonGenerator jgen) throws IOException {
        PluginDefinition pluginDefinition = v.getPluginDefinition();

        jgen.writeStringField("config_name", v.getPluginDefinition().getName());
        jgen.writeStringField("type", v.getPluginDefinition().getType().toString().toLowerCase());

        writeComplexObject("arguments", pluginDefinition.getArguments(), jgen);
    }

    public static void logstashSection(JsonGenerator jgen) throws IOException {
        jgen.writeObjectFieldStart("logstash");
        jgen.writeStringField("version", "1.2.3-fake");
        jgen.writeEndObject();
    }

    public static void writeComplexObject(String fieldName, Object o, JsonGenerator jgen) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        jgen.writeFieldName(fieldName);
        objectMapper.writeValue(jgen, o);
    }
}
