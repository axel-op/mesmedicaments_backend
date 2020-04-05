package app.mesmedicaments.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Sets {

    private Sets() {
    }

    /**
     * Renvoie l'ensemble des combinaisons de taille 2. Si la taille de set est
     * inférieure à 2, renvoie un ensemble vide.
     * 
     * @param <T>
     * @param set
     * @return
     */
    static public <T> Set<List<T>> combinations(Set<T> set) {
        final Set<List<T>> combinaisons = new HashSet<>();
        for (T element1 : set) {
            for (T element2 : set) {
                if (!element1.equals(element2)) {
                    final List<T> combinaison = new ArrayList<>(2);
                    combinaison.add(element1);
                    combinaison.add(element2);
                    combinaisons.add(combinaison);
                }
            }
        }
        return combinaisons;
    }

    static public <T> Set<List<T>> cartesianProduct(Iterable<? extends T> iterable1, Iterable<? extends T> iterable2) {
        final Set<List<T>> combinaisons = new HashSet<>();
        for (T element1 : iterable1) {
            for (T element2 : iterable2) {
                final List<T> combinaison = new ArrayList<>(2);
                combinaison.add(element1);
                combinaison.add(element2);
                combinaisons.add(combinaison);
            }
        }
        return combinaisons;
    }

    /**
     * Sépare le set en des listes de taille partitionSize
     * (la dernière liste peut être plus petite).
     * @param <T>
     * @param set
     * @param partitionSize
     * @return
     */
    static public <E> Set<List<E>> partition(Set<E> set, int partitionSize) {
        final Set<List<E>> partitions = new HashSet<>();
        if (partitionSize == 0) return partitions;
        List<E> partition = new ArrayList<>(partitionSize);
        for (E element : set) {
            if (partition.size() == partitionSize) {
                partitions.add(partition);
                partition = new ArrayList<>(partitionSize);
            }
            partition.add(element);
        }
        if (!partition.isEmpty()) {
            partitions.add(partition);
        }
        return partitions;
    }

    static public <E> HashSet<E> fromIterable(Iterable<E> iterable) {
        final HashSet<E> set = new HashSet<>();
        for (E e : iterable) set.add(e);
        return set;
    }

    static public <E> HashSet<E> fromArray(E[] array) {
        final HashSet<E> set = new HashSet<>();
        for (E e : array) set.add(e);
        return set;
    }

}