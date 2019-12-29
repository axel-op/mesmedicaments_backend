package app.mesmedicaments.entitestables;

import app.mesmedicaments.JSONArrays;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.Ignore;
import com.microsoft.azure.storage.table.TableBatchOperation;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class EntiteSubstance extends AbstractEntite {

    private static final String TABLE = System.getenv("tableazure_substances");

    public static Optional<EntiteSubstance> obtenirEntite(Pays pays, long codeSubstance)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirEntite(
                TABLE, pays.code, String.valueOf(codeSubstance), EntiteSubstance.class);
    }

    public static Iterable<EntiteSubstance> obtenirToutesLesEntites(Pays pays)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirToutesLesEntites(TABLE, pays.code, EntiteSubstance.class);
    }

    /**
     * Effectue l'opération insertOrMerge pour toutes les entités. Possibilité de mélanger les
     * partitions, car un tri est effectué.
     *
     * @param entites Les entités à mettre à jour, éventuellement de partitions différentes
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InvalidKeyException
     */
    public static void mettreAJourEntitesBatch(Iterable<EntiteSubstance> entites)
            throws StorageException, URISyntaxException, InvalidKeyException {
        CloudTable cloudTable = obtenirCloudTable(TABLE);
        Map<String, Set<EntiteSubstance>> parPartition = new ConcurrentHashMap<>();
        for (EntiteSubstance entite : entites) {
            entite.checkConditions();
            parPartition
                    .computeIfAbsent(entite.getPartitionKey(), k -> new HashSet<>())
                    .add(entite);
        }
        for (Entry<String, Set<EntiteSubstance>> entree : parPartition.entrySet()) {
            Set<EntiteSubstance> entitesPartition = entree.getValue();
            TableBatchOperation batchOp = new TableBatchOperation();
            for (EntiteSubstance entite : entitesPartition) {
                batchOp.insertOrMerge(entite);
                if (batchOp.size() >= 100) {
                    cloudTable.execute(batchOp);
                    batchOp.clear();
                }
            }
            if (!batchOp.isEmpty()) cloudTable.execute(batchOp);
        }
    }

    String noms;
    private Map<Langue, Set<String>> nomsParLangue = new HashMap<>();

    public EntiteSubstance(Pays pays, long codeSubstance) {
        super(TABLE, pays.code, String.valueOf(codeSubstance));
    }

    /** NE PAS UTILISER */
    public EntiteSubstance() {
        super(TABLE);
    }

    @Override
    public boolean conditionsARemplir() {
        return !getNomsParLangue().isEmpty();
    }

    /* Getters */

    public String getNoms() {
        JSONObject jsonNoms = new JSONObject();
        nomsParLangue.entrySet().forEach(e -> jsonNoms.put(e.getKey().code, e.getValue()));
        return jsonNoms.toString();
    }

    @Ignore
    public Map<Langue, Set<String>> getNomsParLangue() {
        return new HashMap<>(nomsParLangue);
    }

    @Ignore
    public Pays getPays() {
        return Pays.obtenirPays(getPartitionKey());
    }

    @Ignore
    public long getCode() {
        return Long.parseLong(getRowKey());
    }

    /* Setters */

    public void setNoms(String noms) {
        this.noms = noms;
        nomsParLangue.clear();
        JSONObject jsonNoms = new JSONObject(noms);
        jsonNoms.keySet()
                .forEach(
                        k -> {
                            Set<String> nomsLangue =
                                    JSONArrays.toSetString(jsonNoms.getJSONArray(k));
                            nomsParLangue.put(Langue.obtenirLangue(k), nomsLangue);
                        });
    }

    public void ajouterNom(Langue langue, String nom) {
        nomsParLangue.computeIfAbsent(langue, k -> new HashSet<>()).add(nom);
    }

    public void ajouterNoms(Langue langue, Set<String> noms) {
        nomsParLangue.computeIfAbsent(langue, k -> new HashSet<>()).addAll(noms);
    }
}
