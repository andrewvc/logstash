package com.logstash.pipeline;
import com.logstash.pipeline.graph.ConfigFile;
import com.logstash.pipeline.graph.Vertex;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * Created by andrewvc on 2/20/16.
 */
public class PipelineGraphTest {
    public static PipelineGraph loadGraph(String configName) throws IOException, ConfigFile.InvalidGraphConfigFile {
        InputStream ymlStream =  ConfigFile.class.getResourceAsStream("simple-graph-pipeline.yml");
        String ymlString = IOUtils.toString(ymlStream, "UTF-8");
        IOUtils.closeQuietly(ymlStream);

        return ConfigFile.fromString(ymlString).getPipelineGraph();
    }
    public static PipelineGraph loadSimpleGraph() throws IOException, ConfigFile.InvalidGraphConfigFile {
        return loadGraph("simple-graph-pipeline.yml");
    }

    @Test
    public void testGraphLoad() throws IOException, ConfigFile.InvalidGraphConfigFile {
        PipelineGraph graph = loadSimpleGraph();
    }

    @Test
    public void testGraphQueueGetReturnsQueue() throws IOException, ConfigFile.InvalidGraphConfigFile {
        assertEquals(loadSimpleGraph().queueVertex().getType(), Vertex.Type.QUEUE);
    }

    @Test
    public void
}
