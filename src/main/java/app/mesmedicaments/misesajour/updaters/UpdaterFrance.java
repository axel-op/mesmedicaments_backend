package app.mesmedicaments.misesajour.updaters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.misesajour.Updater;
import app.mesmedicaments.objets.Langue;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.Pays.France;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.objets.presentations.PresentationFrance;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.objets.substances.SubstanceActiveFrance;
import app.mesmedicaments.utils.ConcurrentHashSet;
import app.mesmedicaments.utils.Utils;

public class UpdaterFrance implements Updater<
    Pays.France, 
    Substance<Pays.France>,
    PresentationFrance,
    MedicamentFrance
> {

    private static final String URL_FICHIER_BDPM = Environnement.MISEAJOUR_FRANCE_URL_FICHIER_BDPM;
    private static final String URL_FICHIER_COMPO = Environnement.MISEAJOUR_FRANCE_URL_FICHIER_COMPO;
    private static final String URL_FICHIER_PRESENTATIONS = Environnement.MISEAJOUR_FRANCE_URL_FICHIER_PRESENTATIONS;

    private final Logger logger;

    public UpdaterFrance(Logger logger) {
        this.logger = logger;
    }

    @Override
    public France getPays() {
        return Pays.France.instance;
    }

    @Override
    public Set<Substance<Pays.France>> getNouvellesSubstances() throws IOException {
        final BufferedReader fichier = telechargerFichier(URL_FICHIER_COMPO);
        return fichier.lines()
                .map(LigneFichierCompo::new)
                .map(ligne -> {
                    final Noms noms = new Noms(new HashMap<>());
                    noms.ajouter(Langue.Francais, ligne.nomSubstance);
                    return new Substance<Pays.France>(Pays.France.instance, ligne.codeSubstance, noms);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Set<MedicamentIncomplet<Pays.France, MedicamentFrance>> getNouveauxMedicaments() throws IOException {
        final BufferedReader fichierMedicaments = telechargerFichier(URL_FICHIER_BDPM);
        final Set<LigneFichierBDPM> lignesBDPM = new ConcurrentHashSet<>();
        fichierMedicaments
                .lines()
                .map(LigneFichierBDPM::new)
                .forEach(lignesBDPM::add);
        final BufferedReader fichierCompo = telechargerFichier(URL_FICHIER_COMPO);
        final Map<Integer, Set<SubstanceActiveFrance>> substances = new ConcurrentHashMap<>();
        fichierCompo
                .lines()
                .map(LigneFichierCompo::new)
                .forEach(ligne -> substances.computeIfAbsent(ligne.codeCIS, k -> new HashSet<>())
                        .add(ligne.toSubstanceActive()));
        final BufferedReader fichierPresentations = telechargerFichier(URL_FICHIER_PRESENTATIONS);
        final Map<Integer, Set<PresentationFrance>> presentations = new ConcurrentHashMap<>();
        fichierPresentations
                .lines()
                .map(LigneFichierPresentations::new)
                .forEach(ligne -> presentations.computeIfAbsent(ligne.codeCIS, k -> new HashSet<>())
                        .add(ligne.toPresentation()));
        return lignesBDPM
                .stream()
                .filter(l -> substances.containsKey(l.codeCIS) && presentations.containsKey(l.codeCIS))
                .<Supplier<Optional<MedicamentFrance>>>map(ligneBDPM -> {
                    final int codeCIS = ligneBDPM.codeCIS;
                    final Set<SubstanceActiveFrance> subs = substances.get(codeCIS);
                    final Set<PresentationFrance> pres = presentations.get(codeCIS);
                    return (() -> {
                        final String effets = getEffetsIndesirables(codeCIS);
                        final Noms noms = new Noms(new HashMap<>());
                        noms.ajouter(Langue.Francais, ligneBDPM.nomMedicament);
                        return Optional.of(new MedicamentFrance(
                            codeCIS, noms, subs, ligneBDPM.marque, effets, pres, ligneBDPM.forme));
                    });
                })
                .map(MedicamentIncomplet::new)
                .collect(Collectors.toSet());
    }

    public String getEffetsIndesirables(long codeCIS) {
        final String url = "http://base-donnees-publique.medicaments.gouv.fr/affichageDoc.php?specid="
                            + String.valueOf(codeCIS)
                            + "&typedoc=N";
        try (InputStreamReader isr = new InputStreamReader(
                                            new URL(url).openStream(),
                                            StandardCharsets.ISO_8859_1)
        ) {
            final String html = Utils.stringify(isr);
            final Document document = Jsoup.parse(html, url);
            String texte = "";
            Boolean balise = null;
            for (Element el : document.getAllElements()) {
                if (balise != null && !balise) break;
                if (el.hasAttr("name") && el.attr("name").contains("EffetsIndesirables")) {
                    balise = true;
                }
                if (balise != null && balise) {
                    final String tagName = el.tagName();
                    if (!tagName.equals("h2")) {
                        if (tagName.equals("p")) {
                            if (texte.length() > 0) texte += Utils.NEWLINE;
                            texte += el.text();
                        }
                    } else balise = false;
                }
            }
            return texte;
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            return "";
        }
    }

    private BufferedReader telechargerFichier(String url) throws IOException {
        logger.info("Récupération du fichier (url = " + url + ")...");
        final HttpURLConnection connexion = (HttpURLConnection) new URL(url).openConnection();
        connexion.setRequestMethod("GET");
        return new BufferedReader(
                new InputStreamReader(connexion.getInputStream(), StandardCharsets.ISO_8859_1));
    }

    private static class LigneFichierPresentations {
        private final int codeCIS;
        private final String presentation;
        private Double prix = 0.0;
        private Double honoraires = 0.0;
        private Integer tauxRemboursement = 0;
        private final String conditions;

        private LigneFichierPresentations(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Integer.parseInt(champs[0]);
            this.presentation = champs[2];
            this.conditions = champs.length > 12 ? champs[12] : null;
            final Function<String, Double> formaterPrix =
                    str -> {
                        if (str.contains(",")) {
                            final int virCents = str.length() - 3;
                            str = str.substring(0, virCents) + "." + str.substring(virCents);
                            str = str.replaceAll(",", "");
                        }
                        if (!str.equals("")) return Double.parseDouble(str);
                        return null;
                    };
            try {
                this.tauxRemboursement =
                        champs.length > 8
                                ? Integer.parseInt(champs[8].replaceFirst(" ?%", ""))
                                : tauxRemboursement;
                this.prix = champs.length > 10 ? formaterPrix.apply(champs[10]) : prix;
                this.honoraires = champs.length > 11 ? formaterPrix.apply(champs[11]) : honoraires;
            } catch (NumberFormatException e) {
            }
        }

        public PresentationFrance toPresentation() {
            return new PresentationFrance(presentation, prix, tauxRemboursement, honoraires, conditions);
        }
    }

    private static class LigneFichierBDPM {
        private final int codeCIS;
        private final String nomMedicament;
        private final String forme;
        //private final String autorisation;
        private final String marque;

        private LigneFichierBDPM(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Integer.parseInt(champs[0]);
            this.nomMedicament = champs[1].trim();
            this.forme = champs[2].trim();
            //this.autorisation = champs[4].trim();
            this.marque = champs[10].trim();
        }
    }

    private static class LigneFichierCompo {
        private final int codeCIS;
        private final int codeSubstance;
        private final String nomSubstance;
        private final String dosage;
        private final String referenceDosage;

        private LigneFichierCompo(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Integer.parseInt(champs[0]);
            this.codeSubstance = Integer.parseInt(champs[2]);
            this.nomSubstance = champs[3].trim();
            this.dosage = champs.length > 4 ? champs[4] : null;
            this.referenceDosage = champs.length > 5 ? champs[5] : null;
        }

        public SubstanceActiveFrance toSubstanceActive() {
            final Noms noms = new Noms(new HashMap<>());
            noms.ajouter(Langue.Francais, nomSubstance);
            return new SubstanceActiveFrance(codeSubstance, noms, dosage, referenceDosage);
        }
    }
}
