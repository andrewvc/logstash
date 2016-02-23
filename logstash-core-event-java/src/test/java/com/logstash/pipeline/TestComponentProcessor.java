package com.logstash.pipeline;

import com.logstash.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 2/22/16.
 */
public class TestComponentProcessor implements ComponentProcessor {
    @Override
    public ArrayList<Event> process(Component component, List<Event> events) {
        return new ArrayList<Event>();
    }

    @Override
    public void flush(Component c, boolean shutdown) {

    }
    @Override
    public void setup(Component component) {

    }
}
