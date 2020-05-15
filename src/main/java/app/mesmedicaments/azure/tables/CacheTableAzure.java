package app.mesmedicaments.azure.tables;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import app.mesmedicaments.azure.tables.ClientTableAzure.KeysEntite;
import app.mesmedicaments.basededonnees.ICache;

/**
 * Représente le cache d'une table.
 * Il doit être mis à jour à chaque opération sur la table.
 */
public
class CacheTableAzure implements ICache<KeysEntite, EntiteDynamique> {

    static private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Optional<EntiteDynamique>>>> caches = 
        new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Optional<EntiteDynamique>>> cache;

    public CacheTableAzure(String table) {
        cache = caches.computeIfAbsent(table, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Optional<EntiteDynamique> get(KeysEntite keys) throws ExceptionTableAzure {
        final Map<String, Optional<EntiteDynamique>> partition = cache.get(keys.partitionKey);
        if (partition == null) return Optional.empty();
        final Optional<EntiteDynamique> cached = partition.get(keys.rowKey);
        // cached est null si l'entité n'a pas été mise en cache
        if (cached == null) return Optional.empty();
        return cached;
    }

    @Override
    public void put(KeysEntite keys, EntiteDynamique entite) {
        cache.computeIfAbsent(keys.partitionKey, k -> new ConcurrentHashMap<>())
            .put(keys.rowKey, Optional.ofNullable(entite));
    }

    @Override
    public void put(HashMap<KeysEntite, EntiteDynamique> documents) {
        documents.forEach(this::put);
    }

}