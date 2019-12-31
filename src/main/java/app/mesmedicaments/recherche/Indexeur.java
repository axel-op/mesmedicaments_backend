package app.mesmedicaments.recherche;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.JSONObjectUneCle;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.unchecked.Unchecker;

public final class Indexeur {

    private final Set<AbstractEntiteMedicament<?>> medicamentsAIndexer;
    private final Logger logger;

    public Indexeur(Set<AbstractEntiteMedicament<?>> medicamentsAIndexer, Logger logger) {
        this.medicamentsAIndexer = medicamentsAIndexer;
        this.logger = logger;
    }

    public void indexer() throws IOException {
        new SearchClient(logger).uploadDocuments(preparerLesDocuments().toString());
    }

    private JSONObject preparerLesDocuments() {
        final List<JSONObject> documents = medicamentsAIndexer.parallelStream()
            .map(this::medicamentEnDocument)
            .collect(Collectors.toList());
        logger.info(String.valueOf(documents.size()) + " documents prêts à être envoyés pour l'indexation");
        return new JSONObjectUneCle("value", new JSONArray(documents));
    }

    private JSONObject medicamentEnDocument(AbstractEntiteMedicament<?> medicament) {
        final String paysStr = medicament.getPays().code;
        final Long code = medicament.getCodeMedicament();
        final String uniqueKey = paysStr + String.valueOf(code);
        final Set<JSONObject> substances = obtenirSubstances(medicament)
            .stream()
            .map(s -> substanceEnJson(s))
            .collect(Collectors.toSet());
        return new JSONObject()
            .put("@search.action", "upload")
            .put("Key", uniqueKey)
            .put("pays", paysStr)
            .put("code", code)
            .put("nomsParLangue", nomsParLangueEnJson(medicament.getNomsParLangue()))
            .put("substances", new JSONArray(substances))
            .put("forme", medicament.getForme())
            .put("marque", medicament.getMarque());
    }

    private Map<Long, EntiteSubstance> cacheSubstances = new ConcurrentHashMap<>();

    private Set<EntiteSubstance> obtenirSubstances(AbstractEntiteMedicament<?> medicament) {
        final Set<Long> codes = medicament
            .getSubstancesActivesSet()
            .stream()
            .map(s -> s.codeSubstance)
            .collect(Collectors.toSet());
        return codes
            .parallelStream()
            .map(code -> cacheSubstances.computeIfAbsent(
                code, 
                Unchecker.wrap(logger, (Long k) -> EntiteSubstance.obtenirEntite(medicament.getPays(), k).get())
            ))
            .collect(Collectors.toSet());
    }

    private JSONObject substanceEnJson(EntiteSubstance substance) {
        return new JSONObject()
            .put("noms", nomsParLangueEnJson(substance.getNomsParLangue()))
            .put("codeSubstance", substance.getCode());
    }

    private JSONObject nomsParLangueEnJson(Map<Langue, Set<String>> nomsParLangue) {
        final JSONObject json = new JSONObject();
        for (Entry<Langue, Set<String>> entree : nomsParLangue.entrySet()) {
            json.put(entree.getKey().code, new JSONArray(entree.getValue()));
        }
        return json;
    }
}
