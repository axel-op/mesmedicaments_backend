package app.mesmedicaments;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONObject;

public class AnalyseTexte {

    private static final String ADRESSE_API = System.getenv("analysetexte_adresseapi");
    private static final String CLE_API = System.getenv("analysetexte_cleapi");

    public static Set<String> obtenirExpressionsCles(String texte) throws IOException {
        if (texte == null || texte.equals(""))
            return new HashSet<>();
        int limiteParDoc = 5110 - " ".length();
        String[] tokens = texte.split(" ");
        List<String> decoupes = new ArrayList<>();
        String decoupeEnCours = "";
        for (String token : tokens) {
            if (token.length() > limiteParDoc)
                throw new RuntimeException(" (AnalyseTexte) Un token à lui seul dépasse la limite de taille");
            if (decoupeEnCours.length() + token.length() < limiteParDoc) {
                decoupeEnCours += token + " ";
            } else {
                decoupes.add(decoupeEnCours);
                decoupeEnCours = token;
            }
        }
        decoupes.add(decoupeEnCours);
        JSONArray documents = new JSONArray();
        for (int i = 0; i < decoupes.size(); i++) {
            documents.put(new JSONObject().put("language", "fr").put("id", String.valueOf(i + 1)).put("text",
                    decoupes.get(i)));
        }
        final Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Content-Type", "text/json");
        requestProperties.put("Ocp-Apim-Subscription-Key", CLE_API);
        final HttpClient client = new HttpClient();
        final InputStream responseStream = client.post(ADRESSE_API, requestProperties,
                new JSONObjectUneCle("documents", documents).toString());
        String corpsRep = CharStreams.toString(new InputStreamReader(responseStream, "UTF-8"));
        Set<String> expressions = new HashSet<>();
        JSONArray reponse = new JSONObject(corpsRep).getJSONArray("documents");
        for (int i = 0; i < reponse.length(); i++) {
            JSONArray keyPhrases = reponse.getJSONObject(i).getJSONArray("keyPhrases");
            for (int j = 0; j < keyPhrases.length(); j++) {
                expressions.add(keyPhrases.getString(j));
            }
        }
        return expressions;
    }
}
