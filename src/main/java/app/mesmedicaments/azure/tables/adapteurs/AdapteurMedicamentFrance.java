package app.mesmedicaments.azure.tables.adapteurs;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.azure.tables.clients.ClientTableSubstances;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.objets.presentations.PresentationFrance;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.objets.substances.SubstanceActiveFrance;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.unchecked.Unchecker;

public
class AdapteurMedicamentFrance
extends AdapteurMedicament<
    Pays.France, 
    SubstanceActiveFrance, 
    PresentationFrance, 
    MedicamentFrance
>
{
    final ClientTableSubstances clientSubstances = new ClientTableSubstances();

    public AdapteurMedicamentFrance() {}

    @Override
    public EntiteDynamique fromObject(MedicamentFrance m) {
        final EntiteDynamique entite = super.fromObject(m);
        entite.getProperties().put("Forme", new EntityProperty(m.getForme()));
        return entite;
    }

    @Override
    public MedicamentFrance toObject(EntiteDynamique entite) {
        final int code = Integer.parseInt(entite.getRowKey());
        final Map<String, EntityProperty> props = entite.getProperties();
        final Noms noms = new Noms(new JSONObject(props.get("Noms").getValueAsString()));
        final String marque = props.get("Marque").getValueAsString();
        final String effets = props.get("EffetsIndesirables").getValueAsString();
        final String forme = props.get("Forme").getValueAsString();
        final Set<PresentationFrance> presentations = JSONArrays.toSetJSONObject(
            new JSONArray(props.get("Presentations").getValueAsString()))
                .stream()
                .map(PresentationFrance::new)
                .collect(Collectors.toSet());
        final Set<SubstanceActiveFrance> subActives = JSONArrays.toSetJSONObject(
            new JSONArray(props.get("SubstancesActives").getValueAsString()))
                .parallelStream()
                .map(Unchecker.panic(this::toSubstanceActive))
                .filter(s -> s != null)
                .collect(Collectors.toSet());
        return new MedicamentFrance(
            code, 
            noms, 
            subActives,
            marque, 
            effets,
            presentations,
            forme
        );
    }

    private SubstanceActiveFrance toSubstanceActive(JSONObject json) throws ExceptionTable {
        final int code = json.getInt("code");
        final String dosage = json.getString("dosage");
        final String refDosage = json.getString("referenceDosage");
        final Optional<Substance<?>> substance = clientSubstances.get(Pays.France.instance, code);
        if (!substance.isPresent()) return null;
        final Noms noms = substance.get().getNoms();
        return new SubstanceActiveFrance(code, noms, dosage, refDosage);
    }
}