package app.mesmedicaments.basededonnees;

import java.util.Map;
import java.util.Optional;

/**
 * 
 * @param <D> Le type Java représentant un document de la base de données.
 */
public
interface ITable<K, D> {

    void put(Map<K, D> documents) throws ExceptionTable;
    void put(K key, D document) throws ExceptionTable;
    //void remove(K key) throws ExceptionTable;
    Optional<D> get(K key) throws ExceptionTable;

}