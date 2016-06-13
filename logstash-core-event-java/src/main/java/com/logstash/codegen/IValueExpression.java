package com.logstash.codegen;

import com.logstash.Event;

/**
 * Created by andrewvc on 6/12/16.
 */
public interface IValueExpression {
    public Object get(Event e);
}
