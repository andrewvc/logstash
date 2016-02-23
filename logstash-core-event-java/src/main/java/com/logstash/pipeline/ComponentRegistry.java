package com.logstash.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by andrewvc on 2/22/16.
 */
public class ComponentRegistry {
    public static class DuplicateComponentException extends Exception {
        public DuplicateComponentException(String s) {
            super(s);
        }
    }

    public final Map<String, Component> mapping = new HashMap<>();

    public ComponentRegistry() {
    }

    public void addComponent(Component component) throws DuplicateComponentException {
        if (mapping.containsKey(component.getId())) {
            throw new DuplicateComponentException("Component is a duplicate:  " + component.getId());
        }

        mapping.put(component.getId(), component);
    }

    public Component getComponent(String id) {
        return mapping.get(id);
    }

    public Component[] getInputs() {
        return getByType(Component.Type.INPUT);
    }

    public Component[] getFilters() {
        return getByType(Component.Type.FILTER);
    }

    public Component[] getOutputs() {
        return getByType(Component.Type.OUTPUT);
    }

    public Component[] getByType(Component.Type type) {
        return mapping.values().stream().filter(c -> c.getType() == type).toArray(Component[]::new);
    }
}
