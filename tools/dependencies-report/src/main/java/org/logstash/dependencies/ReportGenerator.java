package org.logstash.dependencies;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Generates Java dependencies report for Logstash.
 *
 * See:
 * https://github.com/elastic/logstash/issues/8725
 * https://github.com/elastic/logstash/pull/8837
 * https://github.com/elastic/logstash/pull/9331
 */
public class ReportGenerator {

    static final String UNKNOWN_LICENSE = "UNKNOWN";
    static final Collection<Dependency> UNKNOWN_LICENSES = new ArrayList<Dependency>();

    public boolean generateReport(
            InputStream spdxLicensesStream,
            InputStream licenseOverridesStream,
            InputStream licenseSynonymsStream,
            InputStream rubyDependenciesStream,
            InputStream[] javaDependenciesStreams,
            Writer output) throws IOException {

        SortedSet<Dependency> dependencies = new TreeSet<>();
        readRubyDependenciesReport(rubyDependenciesStream, dependencies);

        for (InputStream stream : javaDependenciesStreams) {
            readJavaDependenciesReport(stream, dependencies);
        }

        Map<String, String> licenseOverrides = new HashMap<>();
        readLicenseOverrides(licenseOverridesStream, licenseOverrides);
        SpdxLicenseList spdxLicenseList = getSpdxLicenseList(spdxLicensesStream);
        Map<String, String> licenseNameSynonyms = new HashMap<>();
        readLicenseSynonyms(licenseSynonymsStream, licenseNameSynonyms);
        for (Dependency dependency : dependencies) {
            String nameAndVersion = dependency.name + ":" + dependency.version;
            if (licenseOverrides.containsKey(nameAndVersion)) {
                // check first for override
                dependency.spdxLicense = licenseOverrides.get(nameAndVersion);
            } else {
                String matchingLicenses =
                        Dependency.matchingSpdxLicense(dependency.license, spdxLicenseList);
                if (matchingLicenses.length() > 0) {
                    dependency.spdxLicense = matchingLicenses;
                } else if (licenseNameSynonyms.containsKey(dependency.license)) {
                    // check last for license synonym
                    dependency.spdxLicense = licenseNameSynonyms.get(dependency.license);
                }
            }

            // mark unknown if none of the above
            if (dependency.spdxLicense == null || dependency.spdxLicense.equals("")) {
                dependency.spdxLicense = UNKNOWN_LICENSE;
                UNKNOWN_LICENSES.add(dependency);
            }
        }

        try (CSVPrinter csvPrinter = new CSVPrinter(output,
                CSVFormat.DEFAULT.withHeader("dependencyName", "dependencyVersion", "license"))) {
            for (Dependency dependency : dependencies) {
                csvPrinter.printRecord(dependency.name, dependency.version, dependency.spdxLicense);
            }
            csvPrinter.flush();
        }

        System.out.println(
                String.format("Generated report with %d dependencies (%d unknown licenses). Add licenses for the libraries listed below to" +
                        "tools/dependencies-report/src/main/resources/licenseOverrides.csv",
                        dependencies.size(), UNKNOWN_LICENSES.size()));

        for (Dependency dependency : UNKNOWN_LICENSES) {
            System.out.println(
                String.format("\"%s:%s\"", dependency.name, dependency.version));
        }

        return UNKNOWN_LICENSES.size() == 0;
    }

    private SpdxLicenseList getSpdxLicenseList(InputStream spdxLicensesStream) throws IOException {
        final String json = getStringFromStream(spdxLicensesStream);
        final ObjectMapper mapper = new ObjectMapper();
        final SpdxLicenseList spdxLicenseList = mapper.readValue(json, SpdxLicenseList.class);
        return spdxLicenseList;
    }

    private void readRubyDependenciesReport(InputStream stream, SortedSet<Dependency> dependencies)
            throws IOException {
        Reader in = new InputStreamReader(stream);
        for (CSVRecord record : CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in)) {
            dependencies.add(Dependency.fromRubyCsvRecord(record));
        }
    }

    private void readJavaDependenciesReport(InputStream stream, SortedSet<Dependency> dependencies)
            throws IOException {
        Reader in = new InputStreamReader(stream);
        for (CSVRecord record : CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in)) {
            dependencies.add(Dependency.fromJavaCsvRecord(record));
        }
    }

    private void readLicenseSynonyms(InputStream stream, Map<String, String> licenseNameSynonyms)
            throws IOException {
        Reader in = new InputStreamReader(stream);
        for (CSVRecord record : CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in)) {
            String licenseNameSynonym = record.get(0);
            if (licenseNameSynonym != null && !licenseNameSynonym.equals("")) {
                licenseNameSynonyms.put(licenseNameSynonym, record.get(2));
            }
        }
    }

    private void readLicenseOverrides(InputStream stream, Map<String, String> licenseNameSynonyms)
            throws IOException {
        Reader in = new InputStreamReader(stream);
        for (CSVRecord record : CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in)) {
            String dependencyNameAndVersion = record.get(0);
            if (dependencyNameAndVersion != null && !dependencyNameAndVersion.equals("")) {
                licenseNameSynonyms.put(dependencyNameAndVersion, record.get(1));
            }
        }
    }

    static String getStringFromStream(InputStream stream) {
        return new Scanner(stream, "UTF-8").useDelimiter("\\A").next();
    }

}
