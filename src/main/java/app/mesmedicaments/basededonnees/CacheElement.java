package app.mesmedicaments.basededonnees;

import java.util.Optional;

public
class CacheElement<D, Ids> {

    protected final Ids ids;
    protected final D cached;

    public CacheElement(Ids ids, D document) {
        this.ids = ids;
        this.cached = document;
    }
    
    /**
     * L'Optional est vide si l'élément n'appartient pas à la table,
     * c'est-à-dire n'y a pas été trouvé
     * (c'est cela qui a été mis en cache).
     * @return
     */
    protected Optional<D> getCached() {
        return Optional.ofNullable(cached);
    }

}