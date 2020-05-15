package app.mesmedicaments.basededonnees;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import app.mesmedicaments.utils.StreamCollectors;
import app.mesmedicaments.utils.unchecked.Unchecker;

/**
 * Construit les requêtes à la base de données. A la responsabilité de vérifier
 * le cache avant d'envoyer une requête. Appelle un {@link Adapteur} pour
 * convertir les documents récupérés en objets avant de les retourner.
 * 
 * @param <K> Le type des clés
 * @param <D> Le type qui représente le(s) document(s)
 * @param <O> L'objet en lequel le document <D> peut être converti
 */
public abstract class ClientBDD<K, D, O> {

    protected final ICache<K, D> cache;
    protected final ITable<K, D> table;
    protected final Adapteur<O, D> adapteur;

    public ClientBDD(ITable<K, D> table, ICache<K, D> cache, Adapteur<O, D> adapteur) {
        this.cache = cache;
        this.table = table;
        this.adapteur = adapteur;
    }

    /**
     * Renvoie l'objet converti depuis le document ayant les identifiants donnés.
     * 
     * @param ids
     * @return Optional<D>
     * @throws ExceptionTable
     */
    final protected Optional<O> get(K key) throws ExceptionTable {
        final D document = getDocument(key);
        return Optional.ofNullable(document).map(adapteur::toObject);
    }

    private D getDocument(K key) throws ExceptionTable {
        final Optional<D> cached = cache.get(key);
        return cached.orElseGet(Unchecker.panic(() -> getDocumentFromTable(key)));
    }

    private D getDocumentFromTable(K key) throws ExceptionTable {
        final D fromTable = table.get(key).orElse(null);
        // Important : c'est ici que les éléments récupérés sont mis en cache
        cache.put(key, fromTable);
        return fromTable;
    }

    /**
     * Met à jour l'objet (automatiquement converti en document) ou le crée s'il n'existe pas déjà.
     * @param objet
     * @throws ExceptionTable
     */
    final protected void put(K key, O objet) throws ExceptionTable {
        final D document = adapteur.fromObject(objet);
        table.put(key, document);
        cache.put(key, document);
    }

    final protected void put(Map<K, O> objects) throws ExceptionTable {
        final HashMap<K, D> documents = objects
            .entrySet()
            .stream()
            .collect(StreamCollectors.toHashMap(
                e -> e.getKey(),
                e -> adapteur.fromObject(e.getValue())
            ));
        table.put(documents);
        cache.put(documents);
    }

}