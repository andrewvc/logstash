package com.logstash.codegen;

import com.logstash.Event;

import java.util.List;

/**
 * Created by andrewvc on 6/12/16.
 */

// Convenience class to make jruby easier. Interfaces are weird in pure JRuby
public class PluginExpression implements IExpression {
    @Override
    public List<Event> executeMulti(List<Event> events) throws Exception {
        return null;
    }

    @Override
    public List<Event> execute(Event event) throws Exception {
        return null;
    }
}
