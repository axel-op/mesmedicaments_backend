package app.mesmedicaments.basededonnees;

import java.util.Optional;

/**
 * 
 * @param <D> Le type Java représentant un document de la base de données.
 */
public
interface ITable<D> {

    void set(Iterable<D> documents) throws ExceptionTable;
    void set(D document) throws ExceptionTable;
    void remove(D document) throws ExceptionTable;

    Optional<D> get(String... ids) throws ExceptionTable;

}