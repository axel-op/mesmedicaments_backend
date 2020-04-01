package app.mesmedicaments.azure.tables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.basededonnees.ITable;
import app.mesmedicaments.utils.ConcurrentHashSet;
import app.mesmedicaments.utils.Sets;
import app.mesmedicaments.utils.unchecked.Unchecker;

public
class TableAzure implements ITable<EntiteDynamique> {

    public final String nom;

    protected TableAzure(String nomTable) {
        this.nom = nomTable;
    }

    private CloudTable obtenirCloudTable() throws ExceptionTableAzure {
        return ExceptionTableAzure.tryCatch(() -> 
            CloudStorageAccount.parse(Environnement.AZUREWEBJOBSSTORAGE)
                    .createCloudTableClient()
                    .getTableReference(nom));
    }

    @Override
    public Optional<EntiteDynamique> get(String... ids)
            throws ExceptionTableAzure {
        if (ids.length != 2) throw new IllegalArgumentException("Nombre d'identifiants incorrect");
        final TableOperation tableOperation = TableOperation.retrieve(ids[0], ids[1], EntiteDynamique.class);
        return ExceptionTableAzure.tryCatch(() -> Optional.ofNullable(
                obtenirCloudTable().execute(tableOperation).getResultAsType()));
    }

    /**
     * Renvoie toutes les entités appartenant à une même partition sur la table.
     * @param partition
     * @return
     * @throws ExceptionTable
     */
    public Iterable<EntiteDynamique> getAll(String partition)
            throws ExceptionTableAzure {
        final String filtrePK =
                TableQuery.generateFilterCondition(
                        "PartitionKey", QueryComparisons.EQUAL, partition);
        return obtenirCloudTable()
            .execute(new TableQuery<>(EntiteDynamique.class)
                .where(filtrePK));
    }

    //
    // Opérations sur les entités
    //

    /*
    @Override
    public void creer(E entite) throws ExceptionTableAzure, RuntimeException {
        entite.checkConditions(); // déplacer
        execute(TableOperation.insertOrReplace(entite));
    }
    */

    /**
     * Met à jour l'entité ou la crée si elle n'existe pas déjà
     * @param entite
     * @throws ExceptionTable
     * @throws RuntimeException
     */
    @Override
    public void set(EntiteDynamique entite) throws ExceptionTableAzure, RuntimeException {
        execute(TableOperation.insertOrMerge(entite));
    }

    @Override
    public void remove(EntiteDynamique entite) throws ExceptionTableAzure, RuntimeException {
        execute(TableOperation.delete(entite));
    }

    private void execute(TableOperation operation) throws ExceptionTableAzure {
        ExceptionTableAzure.tryCatch(() -> obtenirCloudTable()
            .execute(operation)
        );
    }

    //
    // Opérations par paquets (batch)
    //
    
    @Override
    public void set(Iterable<EntiteDynamique> entites)
    throws ExceptionTableAzure
    {
        final int maxSizePerBatch = 100;
        final CloudTable cloudTable = obtenirCloudTable();
        final ConcurrentMap<String, Set<List<EntiteDynamique>>> parBatchs = regrouperParPartition(entites)
            .entrySet()
            .stream()
            .collect(Collectors.toConcurrentMap(
                e -> e.getKey(), 
                e -> new ConcurrentHashSet<>(Sets.partition(e.getValue(), maxSizePerBatch))
            ));
        parBatchs.keySet().parallelStream()
            .flatMap(partition -> parBatchs.get(partition).stream())
            .map(batch -> {
                final TableBatchOperation op = new TableBatchOperation();
                op.addAll(batch.stream().map(e -> TableOperation.insertOrMerge(e)).collect(Collectors.toSet()));
                return op;
            })
            .forEach(Unchecker.panic(op -> { cloudTable.execute(op); }));
    }

    private Map<String, Set<EntiteDynamique>> regrouperParPartition(Iterable<EntiteDynamique> entites) {
        final Map<String, Set<EntiteDynamique>> map = new HashMap<>();
        for (EntiteDynamique e : entites) {
            map.computeIfAbsent(e.getPartitionKey(), k -> new HashSet<>()).add(e);
        }
        return map;
    }

    
}