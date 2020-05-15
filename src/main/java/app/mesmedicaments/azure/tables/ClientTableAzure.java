package app.mesmedicaments.azure.tables;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.basededonnees.ClientBDD;
import app.mesmedicaments.basededonnees.ExceptionTable;

public
abstract class ClientTableAzure<O>
extends ClientBDD<ClientTableAzure.KeysEntite, EntiteDynamique, O> {

    static public
    class KeysEntite {
        public final String partitionKey;
        public final String rowKey;

        static protected void test() {}

        protected KeysEntite(String partitionKey, String rowKey) {
            this.partitionKey = partitionKey;
            this.rowKey = rowKey;
        }

        protected KeysEntite(EntiteDynamique entite) {
            this.partitionKey = entite.getPartitionKey();
            this.rowKey = entite.getRowKey();
        }
    }

    // méthode pour pouvoir être invoquée par les classes qui héritent de celle-ci
    static protected KeysEntite getKeysEntite(String partitionKey, String rowKey) {
        return new KeysEntite(partitionKey, rowKey);
    }

    protected ClientTableAzure(String table, Adapteur<O, EntiteDynamique> adapteur) {
        super(
            new TableAzure(table),
            new CacheTableAzure(table),
            adapteur
        );
    }

    protected Optional<O> get(String partitionKey, String rowKey) throws ExceptionTable {
        return super.get(new KeysEntite(partitionKey, rowKey));
    }

    protected Set<O> getAll(String partition) throws ExceptionTable {
        final Set<O> objects = new HashSet<>();
        for (EntiteDynamique entite : ((TableAzure) table).getAll(partition)) {
            cache.put(new KeysEntite(entite), entite);
            objects.add(adapteur.toObject(entite));
        }
        return objects;
    }
}