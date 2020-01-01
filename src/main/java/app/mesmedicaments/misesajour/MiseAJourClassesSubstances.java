package app.mesmedicaments.misesajour;

import app.mesmedicaments.HttpClient;
import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.EntiteClasseSubstances;
import app.mesmedicaments.entitestables.EntiteSubstance;
import com.microsoft.azure.storage.StorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public final class MiseAJourClassesSubstances {

    private static final String URL_CLASSES = System.getenv("url_classes");

    private static Logger logger;
    private static Map<String, Set<EntiteSubstance>> classes = new HashMap<>();
    private static Map<String, Set<EntiteSubstance>> nomsSubstancesNormalisesMin = new HashMap<>();
    private static Map<String, Set<EntiteSubstance>> cacheRecherche = new HashMap<>();

    private MiseAJourClassesSubstances() {}

    public static boolean handler(Logger logger) {
        MiseAJourClassesSubstances.logger = logger;
        logger.info("Début de la mise à jour des classes de substances");
        nomsSubstancesNormalisesMin = importerSubstances(logger);
        if (nomsSubstancesNormalisesMin.isEmpty()) {
            return false;
        }
        if (!importerClasses()) {
            return false;
        }
        if (!exporterClasses()) {
            return false;
        }
        return true;
    }

    private static boolean importerClasses() {
        int nbrClassesTrouvees = 0;
        try {
            logger.info(
                    "Récupération du fichier des classes de substances (url = "
                            + URL_CLASSES
                            + ")");

            PDDocument document = PDDocument.load(new HttpClient().get(URL_CLASSES));
            logger.info("Fichier récupéré");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setParagraphStart("/t");
            String[] paragraphes = stripper.getText(document).split(stripper.getParagraphStart());
            String classeencours = "";
            HashSet<EntiteSubstance> substancesEnCours = new HashSet<>();
            int c = 0;
            long startTime = System.currentTimeMillis();
            for (String paragraphe : paragraphes) {
                logger.info("Parsing du paragraphe " + (c++) + "/" + paragraphes.length + "...");
                BufferedReader br = new BufferedReader(new StringReader(paragraphe));
                String ligne = br.readLine();
                if (ligne != null
                        && !(ligne.matches("(Page .*)|(Thésaurus .*)|(Index .*)|(ANSM .*)"))) {
                    if (!classeencours.equals("")) {
                        for (EntiteSubstance entite : substancesEnCours) {
                            classes.computeIfAbsent(classeencours, k -> new HashSet<>())
                                    .add(entite);
                        }
                        nbrClassesTrouvees += 1;
                    }
                    substancesEnCours.clear();
                    classeencours = ligne.trim();
                }
                while ((ligne = br.readLine()) != null) {
                    if (!(ligne.matches("(Page .*)|(Thésaurus .*)|(Index .*)|(ANSM .*)"))) {
                        for (String substance : ligne.split(",")) {
                            substance =
                                    substance
                                            .replaceAll("\\s", " ")
                                            .replaceAll("[^- \\p{ASCII}\\p{IsLatin}]|\\(|\\)", "")
                                            .replaceAll("(acide)|(virus)", "")
                                            .replaceAll("\\brota\\b", "rotavirus")
                                            .toLowerCase()
                                            .trim();
                            if (substance.matches(".*[a-z].*")) {
                                rechercherSubstances(substance).forEach(substancesEnCours::add);
                            }
                        }
                    }
                }
            }
            logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
            document.close();
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            return false;
        }
        logger.info("Nombre total de classes de substances trouvées : " + nbrClassesTrouvees);
        return true;
    }

    private static boolean exporterClasses() {
        logger.info("Mise à jour de la base de données en cours...");
        try {
            long startTime = System.currentTimeMillis();
            Set<EntiteClasseSubstances> entitesClasses =
                    classes.entrySet().stream()
                            .map(
                                    e -> {
                                        EntiteClasseSubstances entite =
                                                new EntiteClasseSubstances(e.getKey());
                                        entite.ajouterSubstances(e.getValue());
                                        return entite;
                                    })
                            .collect(Collectors.toSet());
            EntiteClasseSubstances.mettreAJourEntitesBatch(entitesClasses);
            logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms");
        } catch (StorageException | InvalidKeyException | URISyntaxException e) {
            Utils.logErreur(e, logger);
            return false;
        }
        return true;
    }

    private static Set<EntiteSubstance> rechercherSubstances(String recherche) {
        final String rechercheNorm =
                Utils.normaliser(recherche).replaceAll("  ", " ").toLowerCase().trim();
        return cacheRecherche.computeIfAbsent(
                rechercheNorm,
                r -> {
                    Set<EntiteSubstance> resultats =
                            nomsSubstancesNormalisesMin.keySet().stream()
                                    .filter(nom -> nom.contains(r))
                                    .map(nomsSubstancesNormalisesMin::get)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toSet());
                    logger.fine(
                            resultats.size()
                                    + " substances trouvées à la recherche : "
                                    + recherche);
                    return resultats;
                });
    }

    /** Clés : noms. Valeurs : ensemble des codes des substances associées. */
    protected static Map<String, Set<EntiteSubstance>> importerSubstances(Logger logger) {
        HashMap<String, Set<EntiteSubstance>> resultats = new HashMap<>();
        try {
            for (EntiteSubstance entite : EntiteSubstance.obtenirToutesLesEntites(Pays.France)) {
                for (String nom :
                        Optional.ofNullable(entite.getNomsParLangue().get(Langue.Francais))
                                .orElseGet(HashSet::new)) {
                    resultats.computeIfAbsent(nom, k -> new HashSet<>()).add(entite);
                }
            }
        } catch (StorageException | URISyntaxException | InvalidKeyException e) {
            Utils.logErreur(e, logger);
            return new HashMap<>();
        }
        logger.info(resultats.size() + " noms de substances importés");
        return resultats;
    }
}
