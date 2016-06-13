package com.logstash.codegen;

import com.logstash.Event;

/**
 * Created by andrewvc on 6/12/16.
 */
public interface IBooleanExpression {
    public boolean execute(Event e);
}
