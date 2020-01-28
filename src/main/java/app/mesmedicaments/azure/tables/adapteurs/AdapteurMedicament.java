package app.mesmedicaments.azure.tables.adapteurs;

import java.util.HashMap;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.presentations.Presentation;
import app.mesmedicaments.objets.substances.Substance;

public abstract
class AdapteurMedicament<P extends Pays, S extends Substance<P>, Pr extends Presentation<P>, M extends Medicament<P, S, Pr>>
extends Adapteur<M, EntiteDynamique> {

    public AdapteurMedicament() {}

    @Override
    public EntiteDynamique fromObject(M m) {
        final String partition = m.getPays().toString();
        final String row = String.valueOf(m.getCode());
        final String presentations = new JSONArray(m.getPresentations()).toString();
        final String substancesActives = new JSONArray(m.getSubstances()
            .stream()
            .map(S::toJSON)
            .map(this::filtrerObjetSubstance)
            .collect(Collectors.toSet())
        ).toString();
        final EntiteDynamique entite = new EntiteDynamique(partition, row);
        final HashMap<String, EntityProperty> props = entite.getProperties();
        props.put("Noms", new EntityProperty(m.getNoms().toJSONString()));
        props.put("Marque", new EntityProperty(m.getMarque()));
        props.put("EffetsIndesirables", new EntityProperty(m.getEffetsIndesirables()));
        props.put("Presentations", new EntityProperty(presentations));
        props.put("SubstancesActives", new EntityProperty(substancesActives));
        entite.setProperties(props);
        return entite;
    }

    private final JSONObject filtrerObjetSubstance(JSONObject substance) {
        substance.remove("pays");
        substance.remove("noms");
        return substance;
    }
}