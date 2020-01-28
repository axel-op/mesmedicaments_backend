package app.mesmedicaments.azure.recherche;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.JSONObjectUneCle;

public final class Indexeur {

    private final Set<MedicamentFrance> medicamentsAIndexer;
    private final Logger logger;

    protected Indexeur(Set<MedicamentFrance> medicamentsAIndexer, Logger logger) {
        this.medicamentsAIndexer = medicamentsAIndexer;
        this.logger = logger;
    }

    protected JSONObject getRequestBody() {
        final List<JSONObject> documents =
                medicamentsAIndexer
                        .parallelStream()
                        .map(this::toDocument)
                        .collect(Collectors.toList());
        logger.info(documents.size()
                        + " documents prêts pour l'indexation");
        return new JSONObjectUneCle("value", new JSONArray(documents));
    }

    private JSONObject toDocument(MedicamentFrance medicament) {
        final String paysStr = medicament.getPays().code;
        final long code = medicament.getCode();
        final String uniqueKey = paysStr + String.valueOf(code);
        final Set<JSONObject> substances = medicament.getSubstances()
                .stream()
                .map(this::substanceEnJson)
                .collect(Collectors.toSet());
        return new JSONObject()
                .put("@search.action", "upload")
                .put("Key", uniqueKey)
                .put("pays", paysStr)
                .put("code", code)
                .put("nomsParLangue", medicament.getNoms())
                .put("substances", new JSONArray(substances))
                .put("forme", medicament.getForme())
                .put("marque", medicament.getMarque());
    }

    private JSONObject substanceEnJson(Substance<?> substance) {
        return new JSONObject()
                .put("noms", substance.getNoms())
                .put("codeSubstance", substance.getCode());
    }
}
