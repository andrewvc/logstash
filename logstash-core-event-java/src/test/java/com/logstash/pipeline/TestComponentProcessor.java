package com.logstash.pipeline;

import com.logstash.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 2/22/16.
 */
public class TestComponentProcessor implements ComponentProcessor {
    @Override
    public List<Event> process(Component component, List<Event> inEvents) {
        return new ArrayList<Event>();
    }

    @Override
    public void setup(Component component) {

    }
}
