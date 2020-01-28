package app.mesmedicaments.azure.tables.adapteurs;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;

import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;

public class AdapteurSubstanceFrance extends Adapteur<Substance<Pays.France>, EntiteDynamique> {

    public AdapteurSubstanceFrance() {}
    
    @Override
    public Substance<Pays.France> toObject(EntiteDynamique entite) {
        if (!entite.getPartitionKey().equals(Pays.France.instance.code)) {
            throw new IllegalArgumentException("Le pays de l'entité Substance ne correspond pas à celui attendu");
        }
        final int code = Integer.parseInt(entite.getRowKey());
        return new Substance<Pays.France>(
            Pays.France.instance, 
            code, 
            new Noms(new JSONObject(entite.getProperties().get("Noms").getValueAsString()))
        );
    }

    @Override
    public EntiteDynamique fromObject(Substance<Pays.France> substance) {
        final String partitionKey = substance.getPays().code;
        final String rowKey = String.valueOf(substance.getCode());
        final EntiteDynamique entite = new EntiteDynamique(partitionKey, rowKey);
        entite.getProperties().put("Noms", new EntityProperty(substance.getNoms().toJSONString()));
        return entite;
    }
}