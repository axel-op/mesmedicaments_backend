package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.objets.ClasseSubstances;
import app.mesmedicaments.objets.Langue;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.Utils;

public final class MiseAJourClassesSubstances {

    private static final String URL_CLASSES = Environnement.MISEAJOUR_CLASSES_URL;

    private Logger logger;
    private Set<? extends Substance<?>> substances;
    
    public MiseAJourClassesSubstances(Logger logger, Set<? extends Substance<?>> nouvellesSubstances) {
        this.logger = logger;
        this.substances = nouvellesSubstances;
    }

    public Set<ClasseSubstances> getNouvellesClasses() throws IOException {
        final Set<ClasseSubstances> nouvellesClasses = new HashSet<>();
        logger.info(
            "Récupération du fichier des classes de substances (url = "
                + URL_CLASSES
                + ")");
        final PDDocument document = PDDocument.load(new ClientHttp().get(URL_CLASSES));
        logger.info("Fichier récupéré");
        final PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(2);
        stripper.setParagraphStart("/t");
        final String[] paragraphes = stripper.getText(document)
            .split(stripper.getParagraphStart());
        String classeencours = "";
        final Set<Substance<?>> substancesEnCours = new HashSet<>();
        final long startTime = System.currentTimeMillis();
        final String regexLigneASauter = "(Page .*)|(Thésaurus .*)|(Index .*)|(ANSM .*)";
        for (String paragraphe : paragraphes) {
            final BufferedReader br = new BufferedReader(new StringReader(paragraphe));
            String ligne = br.readLine();
            if (ligne != null && !(ligne.matches(regexLigneASauter))) {
                if (!classeencours.equals("")) {
                    nouvellesClasses.add(
                        new ClasseSubstances(classeencours, substancesEnCours));
                }
                substancesEnCours.clear();
                classeencours = ligne.trim();
            }
            while ((ligne = br.readLine()) != null) {
                if (!(ligne.matches(regexLigneASauter))) {
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
                            substancesEnCours.addAll(rechercherSubstances(substance));
                        }
                    }
                }
            }
        }
        logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
        document.close();
        logger.info("Nombre total de classes de substances trouvées : " 
            + nouvellesClasses.size());
        return nouvellesClasses;
    }

    private Set<Substance<?>> rechercherSubstances(String recherche) {
        final String rechercheNorm = Utils.normaliser(recherche)
                                            .replaceAll("  ", " ")
                                            .toLowerCase()
                                            .trim();
        return substances
                .stream()
                .filter(s -> s.getNoms()
                                .get(Langue.Francais)
                                .stream()
                                .map(Utils::normaliser)
                                .map(String::toLowerCase)
                                .map(n -> n.replaceAll("  ", " "))
                                .map(String::trim)
                                .anyMatch(n -> n.contains(rechercheNorm)))
                .collect(Collectors.toSet());
    }
}
