package app.mesmedicaments.azure.recherche;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.MultiMap;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public class ClientRecherche {

    public enum NiveauRecherche {
        Exacte,
        /**
         * Le dernier terme de la recherche est supposé incomplet.
         * Les mots l'ayant comme préfixe seront des correspondances pour ce terme.
         */ 
        AvecCompletion,
        /**
         * Chaque terme est supposé pouvoir être mal orthographié,
         * et tous les mots ayant une distance de Levenshtein <= 2
         * sont considérés comme des correspondances.
         */
        Approximative;

        /**
         * Du plus strict au moins strict.
         * @return
         */
        static public NiveauRecherche[] ordonnes() {
            return new NiveauRecherche[]{
                Exacte,
                AvecCompletion,
                Approximative
            };
        }
    }

    public enum ModeRecherche {
        /**
         * Tous les mots doivent être présents dans chaque résultat.
         */
        TousLesMots,
        /**
         * Un seul mot suffit pour que le résultat soit accepté.
         */
        NimporteQuelMot;

        /**
         * Du plus strict au moins strict.
         * @return
         */
        static public ModeRecherche[] ordonnees() {
            return new ModeRecherche[]{
                TousLesMots,
                NimporteQuelMot
            };
        }
    }

    private static final String baseUrl = Environnement.RECHERCHE_BASEURL;
    private static final String adminKey = Environnement.RECHERCHE_ADMINKEY;
    private static final String queryKey = Environnement.RECHERCHE_QUERYKEY;
    private static final String indexName = Environnement.RECHERCHE_INDEXNAME;
    private static final String apiVersion = Environnement.RECHERCHE_APIVERSION;

    private final Logger logger;

    public ClientRecherche(Logger logger) {
        this.logger = logger;
    }

    public Requeteur search(String search) throws IOException {
        logger.info("Recherche de \"" + search + "\"");
        return new Requeteur(
            search,
            Unchecker.panic((JSONObject query) -> new Resultats(queryDocuments(query))),
            logger);
    }

    public int getDocumentCount() throws IOException {
        final String url = baseUrl + "/indexes/" + indexName + "/stats?api-version=" + apiVersion;
        final JSONObject response = new JSONObject(send("GET", url, adminKey, null));
        return response.getInt("documentCount");
    }

    public void index(Set<MedicamentFrance> medicamentsAIndexer) throws IOException {
        final Indexeur indexeur = new Indexeur(medicamentsAIndexer, logger);
        final JSONObject body = indexeur.getRequestBody();
        final JSONArray reponses = new JSONObject(uploadDocuments(body.toString()))
                                        .getJSONArray("value");
        for (int i = 0; i < reponses.length(); i++) {
            final JSONObject reponse = reponses.getJSONObject(i);
            final int statusCode = reponse.getInt("statusCode");
            if (statusCode != 200)
                logger.warning("Erreur avec l'indexation du document "
                        + reponse.getString("key")
                        + " : "
                        + reponse.getString("errorMessage"));
        }
        
    }

    protected JSONArray queryDocuments(JSONObject query) throws IOException {
        final String url =
                baseUrl + "/indexes/" + indexName + "/docs/search?api-version=" + apiVersion;
        final JSONArray results = new JSONArray();
        String content = query.toString();
        boolean toContinue = true;
        while (toContinue) {
            final JSONObject response = new JSONObject(send("POST", url, queryKey, content));
            JSONArrays.append(results, response.getJSONArray("value"));
            final String keyNextPage = "@search.nextPageParameters";
            toContinue = response.has(keyNextPage);
            if (toContinue) {
                content = response.getJSONObject(keyNextPage).toString();
            }
        }
        return results;
    }

    protected String uploadDocuments(String documents) throws IOException {
        logger.info("Envoi des documents pour indexation...");
        final String url =
                baseUrl + "/indexes/" + indexName + "/docs/index?api-version=" + apiVersion;
        final String rep = send("POST", url, adminKey, documents);
        return rep;
    }

    private String send(String method, String url, String key, String contents) throws IOException {
        contents = contents == null ? "" : contents;
        final MultiMap<String, String> requestProperties = new MultiMap<>();
        requestProperties.add("api-key", key);
        requestProperties.add("content-type", "application/json");
        final ClientHttp client = new ClientHttp();
        final InputStream responseStream =
                method.equals("GET")
                        ? client.get(url, requestProperties)
                        : client.post(url, requestProperties, contents);
        final String corpsRep =
                Utils.stringify(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
        return corpsRep;
    }
}
