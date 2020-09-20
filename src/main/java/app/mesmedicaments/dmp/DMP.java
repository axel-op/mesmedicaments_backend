package app.mesmedicaments.dmp;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.recherche.ClientRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.ModeRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.NiveauRecherche;
import app.mesmedicaments.azure.tables.clients.ClientTableMedicamentsFrance;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.ConcurrentHashSet;
import app.mesmedicaments.utils.Sets;
import app.mesmedicaments.utils.unchecked.Unchecker;

public class DMP {

    static private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static private final ConcurrentMap<String, String> cacheTransformationMot = new ConcurrentHashMap<>();

    private final Logger logger;
    private final Map<String, String> cookies;

    public DMP(DonneesConnexion donneesConnexion, Logger logger) {
        this.logger = logger;
        this.cookies = donneesConnexion.cookies;
    }

    public Map<LocalDate, Set<MedicamentFrance>> obtenirMedicaments() throws IOException {
        final Map<LocalDate, Set<String>> aChercher = parserFichier();
        final Map<LocalDate, Set<MedicamentFrance>> resultats = new ConcurrentHashMap<>();
        aChercher.entrySet()
            .parallelStream()
            .forEach(e -> {
                final Set<MedicamentFrance> medicaments =
                    e.getValue()
                        .parallelStream()
                        .map(Unchecker.panic(this::chercher))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());
                resultats.put(e.getKey(), medicaments);
            });
        return resultats;
    }

    private Optional<MedicamentFrance> chercher(String ligne) throws IOException, ExceptionTable {
        if (ligne.matches(" *")) return Optional.empty();
        final Set<String> mots = Sets.fromArray(ligne.split(" "))
            .stream()
            .map(this::transformerMot)
            .filter(m -> !m.equals(""))
            .collect(Collectors.toSet());
        if (mots.isEmpty()
            || mots.contains("verre") 
            || mots.contains("monture"))
            return Optional.empty();
        final String recherche = String.join(" ", mots);
        final ClientRecherche client = new ClientRecherche(logger);
        Optional<JSONObject> resultat = Optional.empty();
        for (ModeRecherche mode : ModeRecherche.ordonnees()) {
            if (resultat.isPresent()) break;
            resultat = client.search(recherche)
                            .niveau(NiveauRecherche.Exacte)
                            .mode(mode)
                            .avecNombreMaxResultats(1)
                            .getResultats()
                            .getBest();
        }
        if (!resultat.isPresent()) return Optional.empty();
        return new ClientTableMedicamentsFrance()
            .get(resultat.get().getInt("code"));
    }

    private String transformerMot(String motATransformer) {
        return cacheTransformationMot.computeIfAbsent(
            motATransformer,
            mot -> {
                mot = mot.toLowerCase();
                if (mot.matches("[^a-z0-9,]+")) return "";
                if (mot.matches("[0-9,].*")) mot = mot.split("[^0-9,]")[0];
                if (mot.matches("[^0-9]+[0-9].*")) mot = mot.split("[0-9]")[0];
                if (mot.equals("mg") || mot.equals("-")) return "";
                switch (mot) {
                    case "myl":
                        return "mylan";
                    case "sdz":
                        return "sandoz";
                    case "bga":
                        return "biogaran";
                    case "tvc":
                        return "teva";
                    case "sol":
                        return "solution";
                    case "solbu":
                        return "solution buvable";
                    case "cpr":
                        return "comprimé";
                    case "eff":
                        return "effervescent";
                    case "inj":
                        return "injectable";
                    case "ser":
                        return "seringue";
                    case "fl":
                        return "flacon";
                    case "susp":
                        return "suspension";
                }
                return mot;
            }
        );
    }

    private Map<LocalDate, Set<String>> parserFichier() throws IOException {
        final Document document = getDocument();
        if (document.selectFirst("th:contains(Médicaments)") == null) {
            throw new RuntimeException("Pas de section \"Médicaments\" dans le fichier des remboursements");
        }
        final Elements rows = document
            .getElementById("doc-auto-0")
            .getElementsByTag("tbody")
            .first()
            .getElementsByTag("tr");
        final Map<LocalDate, Set<String>> aChercher = new HashMap<>();
        for (Element row : rows) {
            final Elements els = row.getElementsByTag("td");
            final LocalDate date = LocalDate.parse(els.get(0).text(), DATE_FORMATTER);
            final String name = els.get(1).text();
            aChercher
                .computeIfAbsent(date, k -> new ConcurrentHashSet<>())
                .add(name);
        }
        return aChercher;
    }

    private Document getDocument() throws IOException {
        final Response connListe = Jsoup.connect(Environnement.DMP_URL_LISTE_DOCS)
            .method(Connection.Method.GET)
            .cookies(cookies)
            .execute();
        final Document liste = connListe.parse();
        final var href = liste
            .getElementsMatchingText("Donn.+ de remboursement$")
            .attr("href");
        final Response connDoc = Jsoup.connect(Environnement.DMP_URL_BASE + href)
            .method(Connection.Method.GET)
            .cookies(cookies)
            .execute();
        final var doc = connDoc.parse();
        return doc;
    }

}
