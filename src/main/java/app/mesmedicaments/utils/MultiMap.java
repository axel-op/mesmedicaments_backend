package app.mesmedicaments.utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class MultiMap<K, V> implements Iterable<Entry<K, V>> {

    private final Map<K, Set<V>> map;

    public MultiMap() {
        map = new HashMap<>();
    }

    public MultiMap(MultiMap<K, V> multiMap) {
        this();
        merge(multiMap);
    }

    public boolean merge(MultiMap<K, V> other) {
        boolean changed = false;
        for (K key : other.keySet()) {
            changed = addAll(key, other.map.get(key)) || changed;
        }
        return changed;
    }

    public boolean add(K key, V value) {
        if (value == null) return false;
        return map.computeIfAbsent(key, k -> new HashSet<>()).add(value);
    }

    public boolean addAll(K key, Collection<? extends V> values) {
        if (values == null) return false;
        return map.computeIfAbsent(key, k -> new HashSet<>()).addAll(values);
    }

    public boolean remove(K key, V value) {
        if (map.containsKey(key)) {
            final Set<V> set = map.get(key);
            final boolean removed = set.remove(value);
            if (set.isEmpty()) map.remove(key);
            return removed;
        }
        return false;
    }

    public Set<Entry<K, V>> entrySet() {
        final Set<Entry<K, V>> set = new HashSet<>();
        for (Entry<K, V> e : this) {
            set.add(e);
        }
        return set;
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    /**
     * Number of key/value entries
     * 
     * @return
     */
    public int size() {
        var size = 0;
        for (K key : map.keySet()) {
            size += map.get(key).size();
        }
        return size;
    }

    public void forEach(BiConsumer<K, V> action) {
        Iterable.super.forEach((e) -> action.accept(e.getKey(), e.getValue()));
    }


    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new Iterator<Entry<K, V>>() {

            final List<K> keys = new ArrayList<>(map.keySet());
            int currentKey = -1;
            List<V> currentValues = new ArrayList<>();
            int currentValue = -1;

            @Override
            public boolean hasNext() {
                return currentValue + 1 < currentValues.size()
                    || currentKey + 1 < keys.size();
            }

            @Override
            public Entry<K, V> next() {
                currentValue += 1;
                if (currentValue >= currentValues.size()) {
                    currentKey += 1;
                    if (currentKey >= keys.size()) {
                        throw new NoSuchElementException();
                    }
                    currentValues = new ArrayList<>(map.get(keys.get(currentKey)));
                    currentValue = 0;
                }
                return new AbstractMap.SimpleEntry<K, V>(
                    keys.get(currentKey),
                    currentValues.get(currentValue));
            }
        };
    }

}