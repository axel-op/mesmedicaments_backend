package app.mesmedicaments;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONObject;

public class AnalyseTexte {

    private static final String ADRESSE_API = System.getenv("analysetexte_adresseapi");
    private static final String CLE_API = System.getenv("analysetexte_cleapi");

    public static Set<String> obtenirExpressionsCles (String texte) 
        throws IOException
    {
        if (texte == null || texte.equals("")) return new HashSet<>();
        int limiteParDoc = 5110 - " ".length();
        String[] tokens =  texte.split(" ");
        List<String> decoupes = new ArrayList<>();
        String decoupeEnCours = "";
        for (String token : tokens) {
            if (token.length() > limiteParDoc) 
                throw new RuntimeException(" (AnalyseTexte) Un token à lui seul dépasse la limite de taille");
            if (decoupeEnCours.length() + token.length() < limiteParDoc) {
                decoupeEnCours += token + " ";
            }
            else {
                decoupes.add(decoupeEnCours);
                decoupeEnCours = token;
            }
        }
        decoupes.add(decoupeEnCours);
        JSONArray documents = new JSONArray();
        for (int i = 0; i < decoupes.size(); i++) {
            documents.put(new JSONObject()
                .put("language", "fr")
                .put("id", String.valueOf(i + 1))
                .put("text", decoupes.get(i))
            );
        }
        URL url = new URL(ADRESSE_API);
        HttpsURLConnection connexion = (HttpsURLConnection) url.openConnection();
        connexion.setRequestMethod("POST");
        connexion.setRequestProperty("Content-Type", "text/json");
        connexion.setRequestProperty("Ocp-Apim-Subscription-Key", CLE_API);
        connexion.setDoOutput(true);
        DataOutputStream dos = new DataOutputStream(connexion.getOutputStream());
        byte[] encodedText = new JSONObjectUneCle("documents", documents).toString().getBytes("UTF-8");
        dos.write(encodedText, 0, encodedText.length);
        dos.flush();
        dos.close();
        if (connexion.getResponseCode() != 200) throw new IOException(connexion.getResponseMessage());
        String corpsRep = CharStreams.toString(new InputStreamReader(connexion.getInputStream(), "UTF-8"));
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