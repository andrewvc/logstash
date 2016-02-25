package com.logstash.pipeline.graph;

import org.jruby.runtime.builtin.IRubyObject;

/**
 * Created by andrewvc on 2/24/16.
 */
public class Condition {
    final String source;

    public Condition(String source) {
        this.source = source;
    }
}
