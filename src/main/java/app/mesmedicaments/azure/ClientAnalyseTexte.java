package app.mesmedicaments.azure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.JSONObjectUneCle;
import app.mesmedicaments.utils.MultiMap;
import app.mesmedicaments.utils.Utils;

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

    static public Set<String> getExpressionsCles(String texte) throws IOException {
        if (texte == null || texte.equals("")) return new HashSet<>();
        final int limiteParDoc = 5110 - " ".length();
        final List<String> decoupes = decouper(texte, limiteParDoc);
        final JSONArray documents = new JSONArray();
        int id = 0;
        for (String decoupe : decoupes) {
            documents.put(creerDocument(decoupe, String.valueOf(id)));
            id++;
        }
        final MultiMap<String, String> requestProperties = new MultiMap<>();
        requestProperties.add("Content-Type", "text/json");
        requestProperties.add("Ocp-Apim-Subscription-Key", CLE_API);
        final ClientHttp client = new ClientHttp();
        try {
            final InputStream responseStream =
                    client.post(
                            ADRESSE_API,
                            requestProperties,
                            new JSONObjectUneCle("documents", documents).toString());
            final String corpsRep = Utils.stringify(new InputStreamReader(responseStream, "UTF-8"));
            return JSONArrays.toSetJSONObject(new JSONObject(corpsRep).getJSONArray("documents"))
                .stream()
                .map(d -> d.getJSONArray("keyPhrases"))
                .flatMap(ja -> JSONArrays.toSetString(ja).stream())
                .filter(ClientAnalyseTexte::conserver)
                .collect(Collectors.toSet());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    static private boolean conserver(String expression) {
        for (String prefix : prefixes) {
            if (expression.matches(prefix + ".*")) {
                return false;
            }
        }
        for (String str : contient) {
            if (expression.matches(".*" + str + ".*")) {
                return false;
            }
        }
        for (String regex : exactMatches) {
            if (expression.matches(regex)) {
                return false;
            }
        }
        return true;
    }

    static private final String[] prefixes = {
        "avertissements?",
        "adolescent",
        "dose ",
        "phosphate",
        "glucose",
        "traitement",
        "cas d" // ex. "cas d'exposition"
    };

    static private final String[] contient = {
        "médecins?",
        "pharmaciens?",
        "infirmiers?",
        "patients?",
        "informations?",
        "rubriques?",
        "effets? secondaires?",
        "produits? de santé",
        "effets? indésirables?",
        "ANSM",
        "notice",
        "Centres Régionaux",
        "national",
        "nationaux",
        "internet",
        "médicaments?",
        "symptômes?",
        "données?",
        "fréquence indéterminée",
        "commercialisation",
        "comprimé",
        "avenir",
        "exemple",
        "tableau",
        "suivant",
        "études?",
        "prescrit",
        "recommandé",
        "pharmacovigilance",
        "activité électrique",
        "sertraline",
        "sujet",
        "régression",
        "utilisation",
        "monde",
        "avis"
    };

    static private final String[] exactMatches = {
        "femmes?",
        "personnes? âgées?",
        "sujets? âgés?",
        "difficultés?",
        "possibilités?",
        "fréquence",
        "jusqu",
        "(rares )?cas( (d|(rares?)|(isolés?)))?",
        "aggravations?",
        "paracétamol",
        "nombre",
        "ère",
        "sympt(o|ô)mes?",
        ".* d",
        "liste",
        "mise",
        "double",
        "déficit",
        "augmentation ((de la ((quantité)|(fréquence)))|(du ((taux)|(risque)))|(des concentrations))",
        "évènements? survenus?",
        "cas de reprise",
        "particulier",
        "parole",
        "mêmes? endroits?",
        "dose",
        "tissus pulmonaires",
        "propre sensibilité",
        "retard",
        "arrêt du traitement",
        "origine allergique",
        "arrêt brutal",
        "agression",
        "diminution",
        "soleil",
        "gélule",
        "perte partielle",
        "station debout",
        "signes? de difficultés?",
        "éjaculations?",
        "ouverture partielle",
        "prise",
        "électrocardiogramme",
        "niveaux? de la fonction hépatique",
        "lèvres?",
        "signes?",
        "cas d'idée",
        "nuit",
        "lumière",
        "adultes?",
        "jambes?",
        "sang",
        "bras",
        "yeux",
        "sels?",
        "mesures?",
        "bouche",
        "peau",
        "contact du produit",
        "effets locaux mineurs"
    };
}
