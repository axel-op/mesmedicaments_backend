package app.mesmedicaments.recherche;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.JSONObjectUneCle;

public class Requeteur {

    private static final String[] caracteresAEchapper = "\\ + - && || ! ( ) { } [ ] ^ \" ~ * ? : /".split(" ");

    private final Logger logger;

    public Requeteur(Logger logger) {
        this.logger = logger;
    }

    public JSONArray rechercher(String recherche) throws IOException {
        final String rechercheFormatee = separerTermes(echapperCaracteresSpeciaux(recherche))
            .stream()
            .map(terme -> construireSousRequete(terme))
            .collect(Collectors.joining("&&"));
        final JSONObject requete = new JSONObjectUneCle("search", rechercheFormatee)
            .put("searchMode", "all")
            .put("queryType", "full")
            .put("top", 30);
        final JSONArray resultats = new SearchClient(logger).queryDocuments(requete);
        return resultats;
    }

    private Set<String> separerTermes(String recherche) {
        return Sets.newHashSet(recherche.split(" ", 0))
            .stream()
            .filter(t -> !t.matches(" *"))
            .collect(Collectors.toSet());
    }

    private String construireSousRequete(String terme) {
        String sousRequete = "(";
        //sousRequete += terme + "~||";
        sousRequete += terme + "* || ";
        sousRequete += terme + ")";
        return sousRequete;
    }

    private String echapperCaracteresSpeciaux(String recherche) {
        for (String caractere : caracteresAEchapper) {
            String quotedCaractere = Pattern.quote(caractere);
            recherche = recherche.replaceAll(
                String.format("(%s)", quotedCaractere),
                "\\\\$1"
                
            );
        }
        return recherche;
    }

}