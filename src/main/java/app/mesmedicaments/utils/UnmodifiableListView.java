package app.mesmedicaments.utils;

import java.util.ArrayList;
import java.util.Collection;

public final
class UnmodifiableListView<E> {

    final ArrayList<E> list;

    public UnmodifiableListView(Collection<? extends E> c) {
        list = new ArrayList<>(c);
    }

    public UnmodifiableListView(E[] elements) {
        list = new ArrayList<>();
        for (E e : elements) {
            list.add(e);
        }
    }

    public E get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

}