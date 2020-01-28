package app.mesmedicaments.azure.recherche;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.utils.JSONArrays;

/**
 * Cette classe représente les résultats d'une recherche.
 */
public
class Resultats {

    private final Set<Document> documents;

    public Resultats(JSONArray resultats) {
        documents = JSONArrays.toSetJSONObject(resultats)
            .stream()
            .map(Document::new)
            .collect(Collectors.toSet());
    }

    public int length() {
        return documents.size();
    }

    public JSONArray getAll() {
        return new JSONArray(documents);
    }

    /**
     * Retourne le document avec le meilleur score.
     * L'{@link Optional} est vide s'il n'y a pas de résultats.
     * @return
     */
    public Optional<JSONObject> getBest() {
        final Optional<Document> best = documents
            .stream()
            .max((d1, d2) -> d1.getScore().compareTo(d2.getScore()));
        if (!best.isPresent()) return Optional.empty();
        return Optional.of(best.get().toJSON());
    }

    static private class Document implements IJSONSerializable {
        private final JSONObject document;

        private Document(JSONObject document) {
            document = document
                .put("score", document.getFloat("@search.score"));
            document.remove("@search.score");
            document.remove("Key");
            this.document = document;
        }

        private Float getScore() {
            return document.getFloat("@search.score");
        }

        @Override
        public JSONObject toJSON() {
            return document;
        }
    }
}