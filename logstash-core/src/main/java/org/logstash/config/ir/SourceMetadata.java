package org.logstash.config.ir;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Created by andrewvc on 9/6/16.
 */
public class SourceMetadata {
    private final String sourceFile;

    @JsonProperty("source_file")
    public String getSourceFile() {
        return sourceFile;
    }

    @JsonProperty("source_line")
    public Integer getSourceLine() {
        return sourceLine;
    }

    @JsonProperty("source_column")
    public Integer getSourceColumn() {
        return sourceColumn;
    }

    @JsonProperty("source_text")
    public String getSourceText() {
        return sourceText;
    }

    private final Integer sourceLine;
    private final Integer sourceColumn;
    private final String sourceText;

    public SourceMetadata(String sourceFile, Integer sourceLine, Integer sourceChar, String sourceText) {
        this.sourceFile = sourceFile;
        this.sourceLine = sourceLine;
        this.sourceColumn = sourceChar;
        this.sourceText = sourceText;
    }

    public SourceMetadata() {
        this.sourceFile = null;
        this.sourceLine = null;
        this.sourceColumn = null;
        this.sourceText = null;
    }

    public int hashCode() {
        return Objects.hash(this.sourceFile, this.sourceLine, this.sourceColumn, this.sourceText);
    }

    public String toString() {
        return sourceFile + ":" + sourceLine + ":" + sourceColumn + ":```\n" + sourceText + "\n```";
    }
}
