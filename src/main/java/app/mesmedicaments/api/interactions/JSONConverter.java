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
        final var json = new JSONObject().put("source", is.getSource())
                .put("severite", is.getSeverite()).put("description", is.getDescription());
        final var im = new InteractionAvecMedicaments<>(is, interaction.getMedicaments());
        final var sms = im.joindreSubstancesInteragissantesAuxMedicaments();
        final var elements = new JSONArray(sms.size());
        for (var sm : sms) {
            final var el = new JSONObject().put("medicament", toJSON(sm.getMedicament()))
                    .put("substance", toJSON(sm.getSubstance()));
            elements.put(el);
        }
        json.put("elements", elements);
        return json;
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
        return new JSONObject().put("id", substance.getId()).put("source", source);
    }

    private JSONObject toJSON(Medicament<?, ?, ?> medicament) {
        return new JSONObject().put("id", medicament.getCode()).put("source", "bdpm");
    }

}
