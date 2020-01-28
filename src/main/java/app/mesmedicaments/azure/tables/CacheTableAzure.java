package app.mesmedicaments.azure.tables;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import app.mesmedicaments.basededonnees.ICache;

/**
 * Représente le cache d'une table.
 * Il doit être mis à jour à chaque opération sur la table.
 */
public
class CacheTableAzure implements ICache<EntiteDynamique, CacheTableAzure.CachableTable> {

    static private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Optional<EntiteDynamique>>>> caches = 
        new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Optional<EntiteDynamique>>> cache;

    public CacheTableAzure(String table) {
        cache = caches.computeIfAbsent(table, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Optional<CachableTable> get(String... ids) throws ExceptionTableAzure {
        if (ids.length != 2) throw new IllegalArgumentException("Nombre identifiants incorrect");
        final String partitionKey = ids[0];
        final String rowKey = ids[1];
        final Map<String, Optional<EntiteDynamique>> partition = cache.get(partitionKey);
        if (partition == null) return Optional.empty();
        final Optional<EntiteDynamique> cached = partition.get(rowKey); // cached est null si l'entité n'a pas été mise en cache
        if (cached == null) return Optional.empty();
        final CachableTable cachable = new CachableTable(cached, partitionKey, rowKey);
        return Optional.of(cachable);
    }

    @Override
    public void put(CachableTable cachable) {
        cache.computeIfAbsent(cachable.getPartitionKey(), k -> new ConcurrentHashMap<>())
            .put(cachable.getRowKey(), cachable.toOptional());
    }

    @Override
    public void put(Optional<EntiteDynamique> entite, String... ids) {
        if (ids.length != 2) throw new IllegalArgumentException("Nombre identifiants incorrect");
        put(new CacheTableAzure.CachableTable(entite, ids[0], ids[1]));
    }

    @Override
    public void remove(CachableTable cachable) {
        final Map<String, Optional<EntiteDynamique>> partition = cache.get(cachable.getPartitionKey());
        if (partition != null) {
            partition.remove(cachable.getRowKey());
        }

    }

    static public class CachableTable extends ICache.Cachable<EntiteDynamique> {

        public CachableTable(Optional<EntiteDynamique> document, String partitionKey, String rowKey) {
            super(document, partitionKey, rowKey);
        }

        public String getPartitionKey() {
            return super.ids.get(0);
        }

        public String getRowKey() {
            return super.ids.get(1);
        }
    }
}