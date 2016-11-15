package org.logstash.config.serializers;

/**
 * Created by andrewvc on 11/15/16.
 */
public class SerializationException extends Exception {
    public SerializationException(String s) {
        super(s);
    }

    public SerializationException(String s, Exception e) {
        super(s,e);
    }
}
