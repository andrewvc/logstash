package org.logstash.dependencies;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point for {@link ReportGenerator}.
 */
public class Main {

    static final String LICENSE_SYNONYMS_PATH = "/licenseSynonyms.csv";
    static final String LICENSE_OVERRIDES_PATH = "/licenseOverrides.csv";
    static final String SPDX_LICENSES_PATH = "/spdxLicenses.json";

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: org.logstash.dependencies.Main <pathToRubyDependencies.csv> <pathToJavaLicenseReportFolders.txt> <output.csv>");
            System.exit(1);
        }

        InputStream rubyDependenciesStream = new FileInputStream(args[0]);
        List<String> javaDependencyReports = Files.readAllLines(Paths.get(args[1]));
        InputStream[] javaDependenciesStreams = new InputStream[javaDependencyReports.size()];
        for (int k = 0; k < javaDependencyReports.size(); k++) {
            javaDependenciesStreams[k] = new FileInputStream(javaDependencyReports.get(k) + "/licenses.csv");
        }
        FileWriter outputWriter = new FileWriter(args[2]);

        new ReportGenerator().generateReport(
                getResourceAsStream(SPDX_LICENSES_PATH),
                getResourceAsStream(LICENSE_OVERRIDES_PATH),
                getResourceAsStream(LICENSE_SYNONYMS_PATH),
                rubyDependenciesStream,
                javaDependenciesStreams,
                outputWriter
        );

    }

    static InputStream getResourceAsStream(String resourcePath) {
        return ReportGenerator.class.getResourceAsStream(resourcePath);
    }
}
