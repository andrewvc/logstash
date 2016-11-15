package org.logstash.config.ir.serializers;

import org.junit.Test;
import org.logstash.config.ir.IRHelpers;
import org.logstash.config.ir.InvalidIRException;
import org.logstash.config.serializers.SerializationException;
import org.logstash.config.serializers.lsui.LSUIPipelineSerializer;

import java.io.IOException;

/**
 * Created by andrewvc on 11/15/16.
 */
public class LSUIPipelineSerializerTest {
    @Test
    public void itSerializes() throws InvalidIRException, IOException, SerializationException {
        String output = LSUIPipelineSerializer.serialize(IRHelpers.samplePipeline());
        assert(output.length() > 0); // Assert something gets written
    }
}
