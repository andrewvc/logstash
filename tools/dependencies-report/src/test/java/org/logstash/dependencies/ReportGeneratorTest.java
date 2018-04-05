package org.logstash.dependencies;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.logstash.dependencies.Main.LICENSE_OVERRIDES_PATH;
import static org.logstash.dependencies.Main.LICENSE_SYNONYMS_PATH;
import static org.logstash.dependencies.Main.SPDX_LICENSES_PATH;

public class ReportGeneratorTest {

    @Test
    public void testReport() throws IOException {
        String expectedOutput = ReportGenerator.getStringFromStream(
                Main.getResourceAsStream("/expectedOutput.txt"));
        StringWriter output = new StringWriter();
        ReportGenerator rg = new ReportGenerator();
        rg.generateReport(
                Main.getResourceAsStream(SPDX_LICENSES_PATH),
                Main.getResourceAsStream(LICENSE_OVERRIDES_PATH),
                Main.getResourceAsStream(LICENSE_SYNONYMS_PATH),
                Main.getResourceAsStream("/rubyDependencies.csv"),
                new InputStream[]{
                        Main.getResourceAsStream("/javaLicenses1.csv"),
                        Main.getResourceAsStream("/javaLicenses2.csv"),
                },
                output
        );

        assertEquals(expectedOutput, output.toString());
    }

}

