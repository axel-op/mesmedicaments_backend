package app.mesmedicaments.azure.tables.adapteurs;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;

import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;

public
class AdapteurSubstance
extends Adapteur<Substance<?>, EntiteDynamique> {

    public AdapteurSubstance() {}

    @Override
    public EntiteDynamique fromObject(Substance<?> substance) {
        final String partitionKey = substance.getPays().code;
        final String rowKey = String.valueOf(substance.getCode());
        final EntiteDynamique entite = new EntiteDynamique(partitionKey, rowKey);
        entite.getProperties().put("Noms", new EntityProperty(substance.getNoms().toJSONString()));
        return entite;
    }

    @Override
    public Substance<?> toObject(EntiteDynamique entite) {
        final Pays pays = Pays.fromCode(entite.getPartitionKey());
        final int code = Integer.parseInt(entite.getRowKey());
        final Noms noms = new Noms(new JSONObject(entite.getProperties().get("Noms").getValueAsString()));
        return new Substance<>(pays, code, noms);
    }

}