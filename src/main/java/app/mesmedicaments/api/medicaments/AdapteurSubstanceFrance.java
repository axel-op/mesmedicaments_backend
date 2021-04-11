package app.mesmedicaments.api.medicaments;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;
import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;
import lombok.experimental.PackagePrivate;

public class AdapteurSubstanceFrance
        extends DBAdaptor<DBDocumentTableAzure, Substance<Pays.France>> {

    @PackagePrivate AdapteurSubstanceFrance() {
    }

    @Override
    public Substance<Pays.France> fromDocumentToObject(DBDocumentTableAzure entite) {
        if (!entite.getPartitionKey().equals(Pays.France.instance.code)) {
            throw new IllegalArgumentException(
                    "Le pays de l'entité Substance ne correspond pas à celui attendu");
        }
        final int code = Integer.parseInt(entite.getRowKey());
        return new Substance<Pays.France>(Pays.France.instance, code,
                new Noms(new JSONObject(entite.getProperties().get("Noms").getValueAsString())));
    }

    @Override
    public DBDocumentTableAzure fromObjectToDocument(Substance<Pays.France> substance) {
        final String partitionKey = substance.getPays().code;
        final String rowKey = String.valueOf(substance.getCode());
        final var entite = new DBDocumentTableAzure(partitionKey, rowKey);
        entite.getProperties().put("Noms", new EntityProperty(substance.getNoms().toJSONString()));
        return entite;
    }
}
