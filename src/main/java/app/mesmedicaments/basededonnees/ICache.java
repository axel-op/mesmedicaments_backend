package app.mesmedicaments.basededonnees;

import java.util.Optional;

import com.google.common.collect.ImmutableList;

/**
 * 
 * @param <D> Le type représentant un document de la base de données.
 */
public
interface ICache<D, C extends ICache.Cachable<D>> {

    /**
     * Renvoie un {@link Optional} vide si le document n'a pas été mis en cache.
     * @param ids
     * @return
     * @throws ExceptionTable
     */
    Optional<C> get(String... ids) throws ExceptionTable;
    void put(Optional<D> document, String...ids);
    void put(C cachable);
    void remove(C cachable);

    /***
     * Un élément mis en cache.
     * Il contient un document s'il y en avait un correspondant dans la BDD.
     * S'il est vide cela signifie qu'il n'y avait pas de document correspondant
     * dans la BDD.
     * @param <D>
     */
    static public class Cachable<D> {
        protected final Optional<D> document;
        protected final ImmutableList<String> ids;

        public Cachable(Optional<D> document, String... ids) {
            this.document = document;
            this.ids = ImmutableList.copyOf(ids);
        }

        public boolean isPresent() {
            return document.isPresent();
        }

        public Optional<D> toOptional() {
            return document;
        }
    }
}