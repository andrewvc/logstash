package org.logstash.common;

import org.jruby.RubyString;

import java.util.*;
import java.util.stream.Collectors;

public final class COWMap<K, V> implements Map<K,V> {
    private final Map<K, V> parentMap;
    private final Map<K, V> internalMap;
    private final HashSet<K> inheritedKeys;
    private Set<K> keyset;

    public COWMap(Map<K, V> parentMap) {
        this.parentMap = parentMap;
        this.inheritedKeys = new HashSet<>(parentMap.keySet());
        this.internalMap = new HashMap<>();
    }

    @Override
    public int size() {
        return internalMap.size();
    }

    @Override
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public V get(Object key) {
        V value = internalMap.get(key);
        if (value == null && inheritedKeys.contains(key)) {
            value = parentMap.get(key);
        }

        if (value instanceof Map && !(value instanceof COWMap)) {
            put((K) key, (V) new COWMap<K,V>((Map<K,V>) value));
        } else if (value instanceof List) {
            put((K) key, (V) new ArrayList((List) value));
        } else if (value instanceof Set) {
            put((K) key, (V) new HashSet((Set) value));
        } else if (value instanceof RubyString) {
            RubyString rString = (RubyString) value;
            put((K) key, (V) rString.doClone());
        }

        return value;
    }

    @Override
    public V put(K key, V value) {
        inheritedKeys.remove(value);
        return internalMap.put(key, value);
    }

    @Override
    public V remove(Object key) {
        V value = internalMap.remove(key);
        if (value != null) return value;

        if (inheritedKeys.contains(key)) {
            inheritedKeys.remove(key);
            return parentMap.remove(key);
        }

        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        internalMap.putAll(m);
        inheritedKeys.removeAll(m.entrySet());
    }

    @Override
    public void clear() {
        internalMap.clear();
        inheritedKeys.clear();
    }

    @Override
    public Set<K> keySet() {
        return entrySet().stream().map(es -> es.getKey()).collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return entrySet().stream().map(es -> es.getValue()).collect(Collectors.toList());
    }

    @Override
    public Set<Map.Entry<K,V>> entrySet() {
       return new AbstractSet<Entry<K, V>>() {
           @Override
           public Iterator<Entry<K, V>> iterator() {
               Iterator<Entry<K, V>> internalSet = internalMap.entrySet().iterator();
               Iterator<K> parentSet = inheritedKeys.iterator();

               return new Iterator<Entry<K, V>>() {
                   @Override
                   public boolean hasNext() {
                       return internalSet.hasNext() || parentSet.hasNext();
                   }

                   @Override
                   public Entry<K, V> next() {
                       if (internalSet.hasNext()) {
                           return internalSet.next();
                       } else if (parentSet.hasNext()) {
                           K pKey = parentSet.next();
                            return new AbstractMap.SimpleImmutableEntry<>(pKey, parentMap.get(pKey));
                       }
                       return null;
                   }
               };
           }

           @Override
           public int size() {
               return COWMap.this.size();
           }
       };
    }

}
