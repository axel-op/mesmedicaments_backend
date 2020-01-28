package app.mesmedicaments.azure.tables;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.basededonnees.ClientBDD;
import app.mesmedicaments.basededonnees.ExceptionTable;

public
abstract class ClientTableAzure<O>
extends ClientBDD<O, EntiteDynamique, CacheTableAzure.CachableTable> {

    protected ClientTableAzure(String table, Adapteur<O, EntiteDynamique> adapteur) {
        super(
            new TableAzure(table),
            new CacheTableAzure(table),
            adapteur
        );
    }

    protected Set<O> getAll(String partition) throws ExceptionTable {
        final Set<O> objects = new HashSet<>();
        for (EntiteDynamique entite : ((TableAzure) table).getAll(partition)) {
            cache.put(Optional.of(entite), entite.getPartitionKey(), entite.getRowKey());
            objects.add(adapteur.toObject(entite));
        }
        return objects;
    }
}