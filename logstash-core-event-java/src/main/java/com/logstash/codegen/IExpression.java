package com.logstash.codegen;

import com.logstash.Event;

import java.util.List;

/**
 * Created by andrewvc on 6/12/16.
 */
public interface IExpression {
    public List<Event> executeMulti(List<Event> events) throws Exception;
    public List<Event> execute(Event event) throws Exception;
}
