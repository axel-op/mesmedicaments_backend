package app.mesmedicaments.recherche;

import java.io.IOException;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.JSONObjectUneCle;

public class Requeteur {

    private final Logger logger;

    public Requeteur(Logger logger) {
        this.logger = logger;
    }

    public JSONArray rechercher(String recherche) throws IOException {
        final JSONObject requete = new JSONObjectUneCle("search", recherche)
            .put("searchMode", "all")
            .put("top", 30);
        final JSONArray resultats = new SearchClient(logger).queryDocuments(requete);
        return resultats;
    }

}