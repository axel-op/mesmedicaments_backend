package app.mesmedicaments.api.interactions;

import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import app.mesmedicaments.terminologies.substances.ConceptSubstanceBDPM;
import app.mesmedicaments.terminologies.substances.ConceptSubstanceRxNorm;
import lombok.experimental.PackagePrivate;

@PackagePrivate
class JSONConverter {

    JSONArray toJSON(
            List<InteractionAvecMedicaments<Medicament<?, Substance<?>, ?>>> interactions) {
        return new JSONArray(interactions.stream().map(this::toJSON).collect(Collectors.toList()));
    }

    private JSONObject toJSON(
            InteractionAvecMedicaments<Medicament<?, Substance<?>, ?>> interaction) {
        final var is = interaction.getInteractionSubstances();
        return new JSONObject().put("source", is.getSource()).put("severite", is.getSeverite())
                .put("description", is.getDescription())
                .put("substances",
                        is.getSubstances().stream().map(this::toJSON).collect(Collectors.toList()))
                .put("medicaments",
                        interaction.getMedicaments().stream()
                                .map(MedicamentAvecSubstances::getMedicament).map(this::toJSON)
                                .collect(Collectors.toList()));
    }

    private JSONObject toJSON(ConceptSubstance substance) {
        String source;
        if (substance instanceof ConceptSubstanceBDPM)
            source = "bdpm";
        else if (substance instanceof ConceptSubstanceRxNorm)
            source = "rxnorm";
        else
            throw new IllegalArgumentException(
                    "ConceptSubstance inconnu : " + substance.getClass().getSimpleName());
        return new JSONObject().put("id", substance.getId()).put("source", source).put("noms",
                new JSONObject().put("en", substance.getNames()));
    }

    private JSONObject toJSON(Medicament<?, ?, ?> medicament) {
        return new JSONObject().put("id", medicament.getCode()).put("source", "bdpm");
    }

}
