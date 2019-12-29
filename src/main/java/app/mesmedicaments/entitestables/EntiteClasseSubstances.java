package app.mesmedicaments.entitestables;

import app.mesmedicaments.JSONArrays;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public class EntiteClasseSubstances extends AbstractEntite {

    private static final String TABLE = System.getenv("tableazure_classes");
    private static final String PARTITION = "classe";

    public static void mettreAJourEntitesBatch(Iterable<EntiteClasseSubstances> entites)
            throws StorageException, InvalidKeyException, URISyntaxException {
        CloudTable cloudTable = obtenirCloudTable(TABLE);
        TableBatchOperation batchOp = new TableBatchOperation();
        int c = 0;
        for (EntiteClasseSubstances entite : entites) {
            batchOp.insertOrMerge(entite);
            if ((c++) % 100 == 0) {
                cloudTable.execute(batchOp);
                batchOp.clear();
            }
        }
        if (!batchOp.isEmpty()) cloudTable.execute(batchOp);
    }

    String substances;
    private Map<String, Set<String>> substancesMap = new HashMap<>();

    public EntiteClasseSubstances(String nomClasse) {
        super(TABLE, PARTITION, nomClasse);
    }

    /** NE PAS UTILISER */
    public EntiteClasseSubstances() {
        super(TABLE);
    }

    @Override
    public boolean conditionsARemplir() {
        return true;
    }

    public String getSubstances() {
        JSONObject json = new JSONObject();
        substancesMap.forEach(json::put);
        return json.toString();
    }

    public void setSubstances(String substances) {
        JSONObject json = substances != null ? new JSONObject(substances) : new JSONObject();
        for (String cle : json.keySet()) {
            Set<String> rowKeys = JSONArrays.toSetString(json.getJSONArray(cle));
            substancesMap.put(cle, rowKeys);
        }
        this.substances = substances;
    }

    public void ajouterSubstance(EntiteSubstance entite) {
        substancesMap
                .computeIfAbsent(entite.getPartitionKey(), k -> new HashSet<>())
                .add(entite.getRowKey());
    }

    public void ajouterSubstances(Iterable<EntiteSubstance> entites) {
        entites.forEach(this::ajouterSubstance);
    }
}
