package app.mesmedicaments.dmp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;

import app.mesmedicaments.azure.recherche.ClientRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.ModeRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.NiveauRecherche;
import app.mesmedicaments.azure.tables.clients.ClientTableMedicamentsFrance;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.ConcurrentHashSet;
import app.mesmedicaments.utils.MultiMap;
import app.mesmedicaments.utils.Sets;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public class DMP {

    private static ConcurrentMap<String, String> cacheTransformationMot = new ConcurrentHashMap<>();
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Logger logger;
    private final String URL_FICHIER_REMBOURSEMENTS;
    private Map<String, String> cookies;

    public DMP(String urlFichierRemboursements, DonneesConnexion donneesConnexion, Logger logger) {
        this.logger = logger;
        this.URL_FICHIER_REMBOURSEMENTS = urlFichierRemboursements;
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
        final PDDocument fichierRemboursements = getFichierRemboursements().get(); // TODO : gérer le cas où Optional est vide
        final PDFTextStripper stripper = new PDFTextStripper();
        final BufferedReader br =
                new BufferedReader(
                        new StringReader(
                                new String(
                                        stripper.getText(fichierRemboursements).getBytes(),
                                        StandardCharsets.ISO_8859_1)));
        final Map<LocalDate, Set<String>> aChercher = new HashMap<>();
        String ligne;
        boolean balise = false;
        boolean alerte = true; // Si pas de section Pharmacie trouvée
        while ((ligne = br.readLine()) != null) {
            if (ligne.contains("Hospitalisation")) break;
            if (balise && ligne.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}.*")) {
                final LocalDate date = tryParseDate(ligne.substring(0, 10));
                if (date == null) continue;
                    aChercher
                        .computeIfAbsent(date, k -> new ConcurrentHashSet<>())
                        .add(ligne.substring(10));
            }
            else if (ligne.contains("Pharmacie / fournitures")) {
                alerte = false;
                balise = true;
            }
        }
        fichierRemboursements.close();
        br.close();
        if (alerte) logger.warning("Pas de ligne de fin trouvée dans le fichier des remboursements");
        return aChercher;
    }

    private Optional<PDDocument> getFichierRemboursements() {
        try {
            final MultiMap<String, String> requestProperties = new MultiMap<>();
            cookies.entrySet()
                    .forEach(e -> requestProperties.add(
                        "Cookie", e.getKey() + "=" + e.getValue() + "; "));
            final PDDocument document =
                    PDDocument.load(
                            new ClientHttp().get(URL_FICHIER_REMBOURSEMENTS, requestProperties));
            return Optional.of(document);
        } catch (IOException e) {
            logger.warning("Problème de connexion au fichier des remboursements");
            Utils.logErreur(e, logger);
        }
        return Optional.empty();
    }

    /**
     * Retourne null si la chaîne n'a pu être parsée.
     * @param date
     * @return
     * @throws DateTimeParseException
     */
    private LocalDate tryParseDate(String date) throws DateTimeParseException {
        try {
            return LocalDate.parse(date, dateFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
