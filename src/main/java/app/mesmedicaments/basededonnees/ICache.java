package app.mesmedicaments.basededonnees;

import java.util.HashMap;
import java.util.Optional;

/**
 * 
 * @param <D> Le type représentant un document de la base de données.
 */
public
interface ICache<K, D> {

    /**
     * Renvoie un {@link Optional} vide si le document n'a pas été mis en cache.
     * @param ids
     * @return
     * @throws ExceptionTable
     */
    Optional<D> get(K key) throws ExceptionTable;

    /**
     * Attention, [element] peut être null.
     * @param key
     * @param element
     */
    void put(K key, D document);

    // HashMap autorise les valeurs nulles.
    void put(HashMap<K, D> documents);

    //void remove(K key);

}