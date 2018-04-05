package org.logstash.dependencies;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SpdxLicense {
    public String licenseId;

    @JsonProperty("detailsUrl")
    public String licenseUrl;

}
