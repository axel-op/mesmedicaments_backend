package app.mesmedicaments.utils;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConcurrentHashSet<E> extends AbstractSet<E> {

    final ConcurrentHashMap<E, Object> map;

    /**
     * Creates a new ConcurrentHashSet with a default capacity of 16.
     */
    public ConcurrentHashSet() {
        map = new ConcurrentHashMap<>();
    }

    public ConcurrentHashSet(int initialCapacity) {
        map = new ConcurrentHashMap<>(initialCapacity);
    }

    public ConcurrentHashSet(Collection<? extends E> c) {
        this(c.size());
        addAll(c);
    }

    public ConcurrentHashSet(Iterable<? extends E> i) {
        this();
        i.forEach(this::add);
    }

    @Override
    public boolean add(E e) {
        return map.keySet(Boolean.TRUE).add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return map.keySet(Boolean.TRUE).addAll(c);
    }

    @Override
    public boolean remove(Object o) {
        return map.keySet().remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return map.keySet().removeAll(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return map.keySet().removeIf(filter);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public Spliterator<E> spliterator() {
        return map.keySet().spliterator();
    }

    @Override
    public Stream<E> stream() {
        return map.keySet().stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return map.keySet().parallelStream();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        map.keySet().forEach(action);
    }
}