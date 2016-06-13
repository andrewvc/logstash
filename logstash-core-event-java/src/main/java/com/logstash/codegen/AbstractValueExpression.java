package com.logstash.codegen;

import com.logstash.Event;

/**
 * Created by andrewvc on 6/12/16.
 */
public abstract class AbstractValueExpression implements IValueExpression {
    public void execute(Event e) {
        get(e);
    }
}
