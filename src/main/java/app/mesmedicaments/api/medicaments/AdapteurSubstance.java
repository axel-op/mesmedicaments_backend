package app.mesmedicaments.api.medicaments;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;

import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;
import lombok.experimental.PackagePrivate;

public
class AdapteurSubstance
extends DBAdaptor<DBDocumentTableAzure, Substance<?>> {

    @PackagePrivate AdapteurSubstance() {}

    @Override
    public DBDocumentTableAzure fromObjectToDocument(Substance<?> substance) {
        final String partitionKey = substance.getPays().code;
        final String rowKey = String.valueOf(substance.getCode());
        final var entite = new DBDocumentTableAzure(partitionKey, rowKey);
        entite.getProperties().put("Noms", new EntityProperty(substance.getNoms().toJSONString()));
        return entite;
    }

    @Override
    public Substance<?> fromDocumentToObject(DBDocumentTableAzure entite) {
        final Pays pays = Pays.fromCode(entite.getPartitionKey());
        final int code = Integer.parseInt(entite.getRowKey());
        final Noms noms = new Noms(new JSONObject(entite.getProperties().get("Noms").getValueAsString()));
        return new Substance<>(pays, code, noms);
    }

}