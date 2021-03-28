package app.mesmedicaments.basededonnees;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import app.mesmedicaments.basededonnees.ICache.Cachable;

/**
 * Construit les requêtes à la base de données. A la responsabilité de vérifier
 * le cache avant d'envoyer une requête. Appelle un {@link Adapteur} pour
 * convertir les documents récupérés en objets avant de les retourner.
 * 
 * @param <O> L'objet en lequel le document <D> peut être converti
 * @param <D> Le type qui représente le(s) document(s)
 * @param <C> Le type Cachable
 */
public abstract class ClientBDD<O, D, C extends Cachable<D>> {

    protected final ICache<D, C> cache;
    protected final ITable<D> table;
    protected final Adapteur<O, D> adapteur;

    public ClientBDD(ITable<D> table, ICache<D, C> cache, Adapteur<O, D> adapteur) {
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
    final protected Optional<O> get(String... ids) throws ExceptionTable {
        return obtenirDocument(ids).map(adapteur::toObject);
    }

    private Optional<D> obtenirDocument(String... ids) throws ExceptionTable {
        final Optional<C> cached = cache.get(ids);
        if (cached.isPresent())
            return cached.get().toOptional();
        final Optional<D> fromTable = table.get(ids);
        cache.put(fromTable, ids); // Important : c'est ici que les éléments récupérés sont mis en cache
        return fromTable;
    }

    /**
     * Met à jour l'objet (automatiquement converti en document) ou le crée s'il n'existe pas déjà.
     * @param objet
     * @throws ExceptionTable
     */
    final protected void set(O objet, String... ids) throws ExceptionTable {
        final D document = adapteur.fromObject(objet);
        table.set(document);
        cache.put(Optional.ofNullable(document), ids);
    }

    final protected void set(Map<O, String[]> objectsAndIds) throws ExceptionTable {
        final Map<D, String[]> documentsAndIds = objectsAndIds
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                e -> adapteur.fromObject(e.getKey()),
                e -> e.getValue()));
        table.set(documentsAndIds.keySet());
        documentsAndIds.forEach((d, ids) -> cache.put(Optional.of(d), ids));
    }

}