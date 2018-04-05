package org.logstash.dependencies;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SpdxLicenseList {
    public List<SpdxLicense> licenses;
}
