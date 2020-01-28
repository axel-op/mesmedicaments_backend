package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.Langue;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.Utils;

public final class MiseAJourInteractions {

    private static final String URL_FICHIER_INTERACTIONS = 
            Environnement.MISEAJOUR_INTERACTIONS_URL;
    private static final Charset CHARSET_1252 = Charset.forName("cp1252");
    private static final float TAILLE_NOM_SUBSTANCE = (float) 10;
    private static final float TAILLE_INTERACTION_SUBSTANCE = (float) 8;
    private static final float TAILLE_DESCRIPTION_PT = (float) 6;
    private static final String REGEX_RISQUE1 = "((?i:((a|à) prendre en compte))|(.*APEC))";
    private static final String REGEX_RISQUE2 = "((?i:pr(e|é)caution d'emploi)|(.*PE))";
    private static final String REGEX_RISQUE3 =
            "((?i:(association d(e|é)conseill(e|é)e))|(.*ASDEC))";
    private static final String REGEX_RISQUE4 = "((?i:(contre(-| )indication))|(.*CI))";
    private static final String[] regexRisques =
            new String[] {REGEX_RISQUE1, REGEX_RISQUE2, REGEX_RISQUE3, REGEX_RISQUE4};

    /*
    private static final Map<String, Set<EntiteSubstance>> correspondancesSubstances =
            new HashMap<>();
    private static final Map<String, EntiteInteraction> entitesCreeesParCle = new HashMap<>();
    
    
    private static final Map<String, Set<EntiteSubstance>> nomsSubImporteesNormalises =
            new HashMap<>();
    */

    private String ajoutSubstances;
    private String substance1EnCours;
    private final List<String> substances2EnCours = new ArrayList<>(); // doit maintenir l'ordre
    private Integer risqueEnCours;
    private String descriptifEnCours;
    private String conduiteATenirEnCours;
    private Float valeurInconnueDesc;
    private Float valeurInconnueCond;
    private String classeEnCours;
    private final Map<String, Set<String>> classesSubstances = new HashMap<>();

    private boolean ignorerLigne = false;

    private final Logger logger;
    private final Set<Substance<?>> substances;
    private final Set<InteractionBrouillon> interactionsBrouillon;

    public MiseAJourInteractions(Logger logger, Set<Substance<?>> substances) {
        this.logger = logger;
        this.substances = substances;
        this.interactionsBrouillon = new HashSet<>();
    }

    public Set<Interaction> getInteractions() throws IOException {
        final long startTime = System.currentTimeMillis();
        final PDDocument fichier = recupererFichier();
        parserFichier(fichier);
        logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
        logger.info(interactionsBrouillon.size() + " potentielles interactions trouvées");
        return creerInteractions();          
    }

    private PDDocument recupererFichier() throws IOException {
        logger.info(
                "Récupération du fichier des interactions (url = "
                        + URL_FICHIER_INTERACTIONS
                        + ")");
        return PDDocument.load(new ClientHttp().get(URL_FICHIER_INTERACTIONS));
    }

    private void parserFichier(PDDocument fichier) throws IOException {
        nouveauxChamps();
        logger.info("Début du parsing");
        final long startTime = System.currentTimeMillis();
        final PDFTextStripper stripper =
                new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> textPositions)
                            throws IOException {
                        analyserLigne(text, textPositions);
                        super.writeString(text, textPositions);
                    }
                };
        final int nombrePages = fichier.getNumberOfPages();
        for (int page = 2; page <= nombrePages; page++) {
            logger.info("Parsing de la page " + page + "/" + nombrePages + "...");
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(fichier);
        }
        ajouterInteractionsBrouillon();
        nouveauxChamps();
        fichier.close();
        logger.info("Fin du parsing en " + Utils.tempsDepuis(startTime) + " ms");
    }

    private void analyserLigne(String texte, List<TextPosition> textPositions) {
        final Float taille = textPositions.get(0).getFontSize();
        final Float tailleInPt = textPositions.get(0).getFontSizeInPt();
        if (ignorerLigne) {
            ignorerLigne = false;
            return;
        }
        String ligne = Texte.normaliser(texte);
        if (ligne.matches("(?i:thesaurus .*)")) {
            ignorerLigne = true;
            return;
        }
        if (ligne.matches("(?i:ansm .*)")) return;
        if (taille.equals(TAILLE_NOM_SUBSTANCE)) {
                ajouterInteractionsBrouillon();
                nouveauxChamps();
                substances2EnCours.add(ligne.trim());
        } else if (taille.equals(TAILLE_INTERACTION_SUBSTANCE)) {
            if (!ligne.matches("\\+")) {
                ajouterInteractionsBrouillon();
                final List<String> sauvegarde = new ArrayList<>(substances2EnCours);
                nouveauxChamps();
                substances2EnCours.addAll(sauvegarde);
                substance1EnCours = ligne;
            }
        } else if (tailleInPt.equals(TAILLE_DESCRIPTION_PT)) {
            if (substance1EnCours != null) {
                if (risqueEnCours == null) {
                    for (int i = regexRisques.length - 1; i >= 0; i--) {
                        final String regex = regexRisques[i];
                        if (texte.matches(regex + ".*")) {
                            risqueEnCours = i + 1;
                            if (texte.matches(regex)) texte = "";
                            else if (texte.matches(regex + "[a-zA-Z].*"))
                                texte = texte.replaceFirst(regex, "");
                            break;
                        }
                    }
                }
                if (!texte.equals("")) {
                    float xposition = textPositions.get(0).getX();
                    float[][] matrice = textPositions.get(0).getTextMatrix().getValues();
                    float colonneGauche = (float) 97.68;
                    // float colonneDroite = (float) 317.3999;
                    if (Float.compare(xposition, colonneGauche) <= 0) {
                        if (valeurInconnueDesc == null) valeurInconnueDesc = matrice[2][1];
                        if (valeurInconnueCond != null
                                && valeurInconnueDesc.compareTo(valeurInconnueCond) < 0) {
                            descriptifEnCours = conduiteATenirEnCours + descriptifEnCours;
                            conduiteATenirEnCours = "";
                        }
                        if (!descriptifEnCours.equals("") && texte.matches("[-A-Z].*")) {
                            texte = "\n" + texte;
                        }
                        descriptifEnCours += texte + " ";
                    } else {
                        if (valeurInconnueCond == null) valeurInconnueCond = matrice[2][1];
                        if (!conduiteATenirEnCours.equals("")
                                && ligne.matches("[-A-Z].*")) {
                            texte = "\n" + texte;
                        }
                        conduiteATenirEnCours += texte + " ";
                    }
                }
            } else if (ajoutSubstances != null
                    || (texte.startsWith("(") && !texte.matches("\\( ?(V|v)oir aussi.*"))) {
                if (ajoutSubstances == null && ligne.startsWith("(")) {
                    String classe = String.join("", substances2EnCours);
                    nouveauxChamps();
                    classeEnCours = classe;
                    ligne = ligne.substring(1);
                }
                int d = 0;
                if (!ligne.endsWith(")")) ajoutSubstances = "";
                else {
                    ligne = ligne.substring(0, ligne.length() - 1);
                    if (ajoutSubstances != null) {
                        String aAjouter = "";
                        String[] decoupe = ligne.split(",");
                        if (decoupe.length > 0) aAjouter = decoupe[0];
                        substances2EnCours.add(ajoutSubstances + " " + aAjouter);
                        d = 1;
                    }
                    ajoutSubstances = null;
                }
                String[] substances = ligne.split(",");
                for (int i = d; i < substances.length; i++) {
                    String substance = substances[i].trim();
                    if (!substance.matches(" *")) {
                        if (i == substances.length - 1 && ajoutSubstances != null) {
                            ajoutSubstances = substance;
                        } else substances2EnCours.add(substance);
                    }
                }
            }
        } else {
            /* Ici ne doit se trouver aucune ligne du pdf */
            logger.warning(
                    "LIGNE IGNOREE (il ne devrait pas y en avoir) : "
                            + Utils.NEWLINE
                            + "\""
                            + texte
                            + "\"");
        }
    }

    private void nouveauxChamps() {
        if (classeEnCours != null) {
            classesSubstances.put(
                    Texte.normaliser(classeEnCours).toLowerCase(),
                    new HashSet<>(substances2EnCours));
        }
        substances2EnCours.clear();
        substance1EnCours = null;
        risqueEnCours = null;
        descriptifEnCours = "";
        conduiteATenirEnCours = "";
        valeurInconnueDesc = null;
        valeurInconnueCond = null;
        ajoutSubstances = null;
        classeEnCours = null;
    }

    private void ajouterInteractionsBrouillon() {
        if (risqueEnCours == null) return;
        for (String substance2 : substances2EnCours) {
            interactionsBrouillon.add(
                    new InteractionBrouillon(
                            substance1EnCours,
                            substance2,
                            risqueEnCours,
                            descriptifEnCours,
                            conduiteATenirEnCours));
        }
    }

    private Set<Interaction> creerInteractions() {
        // TODO Si le descriptif commence par "ainsi que", "et pendant", remplacer "ainsi
        // que" par "Cette interaction se poursuit"
        // Voir aussi ceux qui commencent avec "Dans l'indication..."
        logger.info("Création des interactions...");
        final long startTime = System.currentTimeMillis();
        final Map<String, Interaction> parCles = new HashMap<>(); // pour gérer les doublons
        for (InteractionBrouillon i : interactionsBrouillon) {
            String descriptif;
            String conduite;
            if (i.descriptif.matches(" *")) {
                descriptif = Texte.corrigerApostrophes.apply(i.conduite);
                conduite = "";
            } else {
                descriptif = Texte.corrigerApostrophes.apply(i.descriptif);
                conduite =
                        Texte.corrigerApostrophes.apply(
                                i
                                        .conduite
                                        .replaceAll(
                                                "((CI ?)" + "|(ASDEC ?)" + "|(PE )" + "|(APEC ?))",
                                                "")
                                        .replaceFirst(" ?(- )+\n", ""));
            }
            final Recherche r = new Recherche();
            final Set<Substance<?>> substances1 =
                    r.obtenirCorrespondances(i.substance1);
            final Set<Substance<?>> substances2 =
                    r.obtenirCorrespondances(i.substance2);
            final Set<List<Substance<?>>> combinaisons =
                    Sets.cartesianProduct(substances1, substances2);
            for (List<Substance<?>> comb : combinaisons) {
                final Interaction interaction = new Interaction(i.risque, descriptif, conduite, comb);
                final String cleUnique = comb.stream()
                    .map(s -> s.getPays().code + String.valueOf(s.getCode()))
                    .reduce("", (s1, s2) -> s1.compareTo(s2) < 0 ? s1 + s2 : s2 + s1);
                final Interaction doublon = parCles.get(cleUnique);
                if (doublon == null || doublon.risque < interaction.risque)
                    parCles.put(cleUnique, interaction);
            }
        }
        logger.info(parCles.size()
                        + " entités créées en "
                        + Utils.tempsDepuis(startTime)
                        + " ms");
        return new HashSet<>(parCles.values());
    }

    private static class InteractionBrouillon {
        final String substance1;
        final String substance2;
        final int risque;
        final String descriptif;
        final String conduite;

        InteractionBrouillon(
                String substance1,
                String substance2,
                int risque,
                String descriptif,
                String conduite
        ) {
            this.substance1 = substance1;
            this.substance2 = substance2;
            this.risque = risque;
            this.descriptif = Optional.ofNullable(descriptif).orElse("");
            this.conduite = Optional.ofNullable(conduite).orElse("");
        }
    }

    private class Recherche {

        private final Map<String, Set<Substance<?>>> cacheCorrespondances = new HashMap<>();
        private final Map<String, Set<Substance<?>>> cacheMeilleuresSubstances = new HashMap<>();
        private final Map<String, Set<Substance<?>>> cacheRechercheSimple = new HashMap<>();

        protected Set<Substance<?>> obtenirCorrespondances(String recherche) {
            if (recherche == null) return new HashSet<>();
            return cacheCorrespondances.computeIfAbsent(recherche, r -> {
                 r = r.toLowerCase();
                if (r.startsWith("autres "))
                    r = r.replaceFirst("autres ", "");
                if (classesSubstances.containsKey(r)) {
                    return classesSubstances
                        .get(r)
                        .stream()
                        .flatMap(s -> obtenirCorrespondances(s).stream())
                        .collect(Collectors.toSet());
                }
                return rechercherMeilleuresSubstances(r);
            });           
        }

        private Set<Substance<?>> rechercherMeilleuresSubstances(final String recherche) {
            if (cacheMeilleuresSubstances.containsKey(recherche))
                return cacheMeilleuresSubstances.get(recherche);
            if (recherche.matches(".+\\(.+\\)")) {
                final String debut = recherche.split("\\(")[0];
                final Set<Substance<?>> resultats1 = rechercherMeilleuresSubstances(debut);
                if (resultats1.isEmpty()) return resultats1;
                return rechercherMeilleuresSubstances(recherche.replaceFirst(debut, ""))
                        .stream()
                        .filter(resultats1::contains)
                        .collect(Collectors.toSet());
            }
            final String regexExclus =
                                "(?i:"
                                        + "(fruit)"
                                        + "|(acide)"
                                        + "|(alpha))"
                                        + "|(?i:.*par voie.*)";
            final List<String> sousExpr = 
                obtenirSousExpressions(
                    recherche
                        .replaceAll("[,\\(\\)]", "")
                        .trim())
                .stream()
                .map(Texte::normaliser)
                .map(String::toLowerCase)
                .filter(e -> !e.matches(regexExclus))
                .filter(e -> e.equals("fer") || !e.matches("([^ ]{1,3} ?\\b)+"))
                .collect(Collectors.toList());
            final HashMap<Substance<?>, Double> classement = new HashMap<>();
            for (String exp : sousExpr) {
                if (exp.matches("(?i:(sauf)|(hors))"))
                    break;
                if (exp.matches("(?i:[^ ]*s)"))
                    exp = exp.substring(0, exp.length() - 1);
                final Set<Substance<?>> resultats = rechercheSimple(exp);
                final double score = (1.0 * exp.length()) / resultats.size();
                resultats.forEach(s -> classement.put(s, score + classement.getOrDefault(s, 0.0)));
            }
            final Set<Substance<?>> meilleures = trouverMeilleurs(classement);
            cacheMeilleuresSubstances.put(recherche, meilleures);
            return meilleures;
        }

        private List<String> obtenirSousExpressions(String expression) {
            final List<String> sousExpressions = new ArrayList<>();
            final String[] mots = expression.split(" ");
            for (int k = mots.length; k >= 1; k--) {
                for (int i = 0; i + k <= mots.length; i++) {
                    sousExpressions.add(String.join(" ", Arrays.copyOfRange(mots, i, i + k)));
                }
            }
            return sousExpressions;
        }

        private Set<Substance<?>> rechercheSimple(String recherche) {
            return cacheRechercheSimple.computeIfAbsent(
                recherche,
                mot -> {
                    for (String regex : new String[]{
                            "(?i:.*" + mot + "\\b.*)", 
                            "(?i:.*" + mot + ".*)"
                    }) {
                        final Set<Substance<?>> resultats = substances
                            .stream()
                            .filter(s -> s.getNoms()
                                            .get(Langue.Francais)
                                            .stream()
                                            .map(Texte::normaliser)
                                            .anyMatch(n -> n.matches(regex)))
                            .collect(Collectors.toSet());
                        if (!resultats.isEmpty()) return resultats;
                    }
                    return new HashSet<>();
                }
            );
        }

        private <T> Set<T> trouverMeilleurs(HashMap<T, Double> classement) {
            if (classement.isEmpty()) return new HashSet<>();
            if (classement.size() == 1) return classement.keySet();
            final double scoremax = classement.values().stream().max(Double::compare).get();
            return classement.keySet().stream()
                    .filter(cle -> classement.get(cle) == scoremax)
                    .collect(Collectors.toSet());
        }
    }

    static private class Texte {

        private static final Map<String, String> cacheNormalisation = new HashMap<>();
        private static final Map<String, String> cacheApostrophes = new HashMap<>();

        protected static String normaliser(String texte) {
            return cacheNormalisation.computeIfAbsent(
                texte,
                mot -> {
                    mot = Utils.normaliser(mot).replaceAll("  ", " ").trim();
                    byte[] ancien = mot.getBytes(CHARSET_1252);
                    byte[] nouveau = new byte[ancien.length];
                    for (int i = 0; i < ancien.length; i++) {
                        switch (Integer.valueOf(ancien[i])) {
                                // a
                            case -32:
                            case -30:
                                nouveau[i] = 97;
                                break;
                                // e
                            case -23:
                            case -24:
                            case -22:
                                nouveau[i] = 101;
                                break;
                                // i
                            case -17:
                            case -18:
                                nouveau[i] = 105;
                                break;
                                // o
                            case -12:
                            case -10:
                                nouveau[i] = 111;
                                break;
                                // u
                            case -4:
                                nouveau[i] = 117;
                                break;
                                // œ
                            case -100:
                                nouveau =
                                        Arrays.copyOf(nouveau, nouveau.length + 1);
                                nouveau[i] = 111;
                                nouveau[i + 1] = 101;
                                i++;
                                break;
                                // apostrophe
                            case -110:
                                nouveau[i] = 39;
                                break;
                            default:
                                nouveau[i] = ancien[i];
                        }
                    }
                    return new String(nouveau, CHARSET_1252);
                }
            );
        }   

        protected static Function<String, String> corrigerApostrophes =
                original ->
                        cacheApostrophes.computeIfAbsent(
                                original,
                                mot -> {
                                    byte[] ancien = mot.getBytes(CHARSET_1252);
                                    byte[] nouveau = new byte[ancien.length];
                                    for (int i = 0; i < ancien.length; i++) {
                                        switch (Integer.valueOf(ancien[i])) {
                                            case -110:
                                                nouveau[i] = 39;
                                                break;
                                            default:
                                                nouveau[i] = ancien[i];
                                        }
                                    }
                                    return new String(nouveau, CHARSET_1252)
                                            .trim()
                                            .replaceAll("  ", " ");
                                });
    }
}
