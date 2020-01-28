package app.mesmedicaments.azure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.JSONObjectUneCle;

public class ClientAnalyseTexte {

    static private final String ADRESSE_API = Environnement.ANALYSETEXTE_ADRESSEAPI;
    static private final String CLE_API = Environnement.ANALYSETEXTE_CLEAPI;

    private ClientAnalyseTexte() {}

    static private List<String> decouper(String texte, int maxParDecoupe) {
        final String separateur = " ";
        final String[] tokens = texte.split(separateur);
        final List<String> decoupes = new ArrayList<>();
        String decoupeEnCours = "";
        for (String token : tokens) {
            if (token.length() > maxParDecoupe)
                throw new RuntimeException("(AnalyseTexte) Un token à lui seul dépasse la limite de taille");
            if (decoupeEnCours.length() + token.length() < maxParDecoupe) {
                decoupeEnCours += token + separateur;
            } else {
                decoupes.add(decoupeEnCours);
                decoupeEnCours = token;
            }
        }
        decoupes.add(decoupeEnCours);
        return decoupes;
    }

    static private JSONObject creerDocument(String texte, String id) {
        return new JSONObject()
            .put("language", "fr")
            .put("id", id)
            .put("text", texte);
    }

    static public Set<String> obtenirExpressionsCles(String texte) throws IOException {
        if (texte == null || texte.equals("")) return new HashSet<>();
        final int limiteParDoc = 5110 - " ".length();
        final List<String> decoupes = decouper(texte, limiteParDoc);
        final JSONArray documents = new JSONArray();
        int id = 0;
        for (String decoupe : decoupes) {
            documents.put(creerDocument(decoupe, String.valueOf(id)));
            id++;
        }
        final Multimap<String, String> requestProperties = HashMultimap.create();
        requestProperties.put("Content-Type", "text/json");
        requestProperties.put("Ocp-Apim-Subscription-Key", CLE_API);
        final ClientHttp client = new ClientHttp();
        final InputStream responseStream =
                client.post(
                        ADRESSE_API,
                        requestProperties,
                        new JSONObjectUneCle("documents", documents).toString());
        final String corpsRep = CharStreams.toString(new InputStreamReader(responseStream, "UTF-8"));
        return JSONArrays.toSetJSONObject(new JSONObject(corpsRep).getJSONArray("documents"))
            .stream()
            .map(d -> d.getJSONArray("keyPhrases"))
            .flatMap(ja -> JSONArrays.toSetString(ja).stream())
            .collect(Collectors.toSet());
    }
}
