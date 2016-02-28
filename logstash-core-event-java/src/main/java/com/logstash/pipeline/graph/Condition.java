package com.logstash.pipeline.graph;

import org.jruby.runtime.builtin.IRubyObject;

/**
 * Created by andrewvc on 2/24/16.
 */
public class Condition {
    public static final Condition elseCondition = new Condition("__ELSE__");
    final String source;

    public Condition(String source) {
        this.source = source;
    }
}
