package app.mesmedicaments.azure.recherche;

import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import org.json.JSONObject;

import app.mesmedicaments.azure.recherche.ClientRecherche.ModeRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.NiveauRecherche;

public class Requeteur {

    private static final String[] caracteresAEchapper =
            "\\ + - && || ! ( ) { } [ ] ^ \" ~ * ? : /"
            .split(" ");

    static private List<String> separerTermes(String recherche) {
        return Lists.newArrayList(recherche.split(" ", 0))
                .stream()
                .filter(t -> !t.matches(" *"))
                .collect(Collectors.toList());
    }
    
    static private String echapperCaracteresSpeciaux(String recherche) {
        for (String caractere : caracteresAEchapper) {
            String quotedCaractere = Pattern.quote(caractere);
            recherche = recherche.replaceAll(String.format("(%s)", quotedCaractere), "\\\\$1");
        }
        return recherche;
    }

    private final Logger logger;
    private final Function<JSONObject, Resultats> queryDocuments;
    private final List<String> termes;
    private NiveauRecherche niveau = NiveauRecherche.Exacte;
    private ModeRecherche searchMode = ModeRecherche.NimporteQuelMot;
    private int top = 30;

    protected Requeteur(
        String recherche, 
        Function<JSONObject, Resultats> queryDocuments, 
        Logger logger
    ) {
        this.logger = logger;
        this.queryDocuments = queryDocuments;
        this.termes = separerTermes(echapperCaracteresSpeciaux(recherche));
    }

    public Requeteur mode(ModeRecherche mode) {
        this.searchMode = mode;
        return this;
    }

    public Requeteur niveau(NiveauRecherche niveau) {
        this.niveau = niveau;
        return this;
    }

    public Requeteur avecNombreMaxResultats(int max) {
        this.top = max;
        return this;
    }

    public Resultats getResultats() {
        final JSONObject query = fabriquerRequete();
        final Resultats resultats = queryDocuments.apply(query);
        logger.info(
            resultats.length() + " résultats trouvés à la recherche "
                + niveau.toString().toLowerCase());
        return resultats;
    }

    private String fabriquerRecherche() {
        final boolean approx = niveau == NiveauRecherche.Approximative;
        String recherche = termes
            .stream()
            .map(terme -> approx ? "(" + terme + "~)" : terme)
            .collect(Collectors.joining(approx ? "&&" : " "))
            .trim();
        if (niveau == NiveauRecherche.AvecCompletion)
            recherche += "*";
        return recherche;
    }

    private JSONObject fabriquerRequete() {
        return new JSONObject()
            .put("search", fabriquerRecherche())
            .put("searchMode",
                searchMode == ModeRecherche.TousLesMots
                    ? "all"
                    : "any")
            .put("queryType", "full")
            .put("top", top);
    }
}
