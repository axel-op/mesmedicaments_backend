package app.mesmedicaments.misesajour.updaters;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.SubstanceActive;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance.PresentationFrance;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.misesajour.Updater;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class UpdaterFrance implements Updater<EntiteMedicamentFrance> {

    private static final String URL_FICHIER_BDPM = System.getenv("url_cis_bdpm");
    private static final String URL_FICHIER_COMPO = System.getenv("url_cis_compo_bdpm");
    private static final String URL_FICHIER_PRESENTATIONS = System.getenv("url_cis_cip_bdpm");

    private final Logger logger;

    public UpdaterFrance(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Pays getPays() {
        return Pays.France;
    }

    @Override
    public Set<EntiteSubstance> getNouvellesSubstances() throws IOException {
        final BufferedReader fichier = telechargerFichier(URL_FICHIER_COMPO);
        return fichier.lines()
                .map(LigneFichierCompo::new)
                .map(
                        ligne -> {
                            final EntiteSubstance entite =
                                    new EntiteSubstance(getPays(), ligne.codeSubstance);
                            entite.ajouterNom(Langue.Francais, ligne.nomSubstance);
                            return entite;
                        })
                .collect(Collectors.toSet());
    }

    @Override
    public Set<EntiteMedicamentFrance> getNouveauxMedicaments() throws IOException {
        final BufferedReader fichierMedicaments = telechargerFichier(URL_FICHIER_BDPM);
        final Map<Long, EntiteMedicamentFrance> entites = new ConcurrentHashMap<>();
        fichierMedicaments
                .lines()
                .map(LigneFichierBDPM::new)
                .forEach(
                        ligne -> {
                            entites.computeIfAbsent(
                                            ligne.codeCIS,
                                            code -> {
                                                final EntiteMedicamentFrance entite =
                                                        new EntiteMedicamentFrance(code);
                                                entite.setForme(ligne.forme);
                                                entite.setAutorisation(ligne.autorisation);
                                                entite.setMarque(ligne.marque);
                                                return entite;
                                            })
                                    .ajouterNom(Langue.Francais, ligne.nomMedicament);
                        });
        final BufferedReader fichierCompo = telechargerFichier(URL_FICHIER_COMPO);
        fichierCompo
                .lines()
                .map(LigneFichierCompo::new)
                .forEach(
                        ligne -> {
                            entites.computeIfAbsent(
                                            ligne.codeCIS,
                                            codeCIS -> new EntiteMedicamentFrance(codeCIS))
                                    .ajouterSubstanceActive(
                                            new SubstanceActive(
                                                    ligne.codeSubstance,
                                                    ligne.dosage,
                                                    ligne.referenceDosage));
                        });
        final BufferedReader fichierPresentations = telechargerFichier(URL_FICHIER_PRESENTATIONS);
        fichierPresentations
                .lines()
                .map(LigneFichierPresentations::new)
                .forEach(
                        ligne -> {
                            try {
                                entites.computeIfAbsent(
                                                ligne.codeCIS,
                                                codeCIS -> new EntiteMedicamentFrance(codeCIS))
                                        .ajouterPresentation(
                                                new PresentationFrance(
                                                        ligne.presentation,
                                                        ligne.prix,
                                                        ligne.tauxRemboursement,
                                                        ligne.honoraires,
                                                        ligne.conditions));
                            } catch (Exception e) {
                                Utils.logErreur(e, logger);
                            }
                        });
        return Sets.newHashSet(entites.values());
    }

    @Override
    public String getEffetsIndesirables(EntiteMedicamentFrance medicament) throws IOException {
        final String url =
                "http://base-donnees-publique.medicaments.gouv.fr/affichageDoc.php?specid="
                        + medicament.getCodeMedicament()
                        + "&typedoc=N";
        final Document document = Jsoup.parse(new URL(url).openStream(), "ISO-8859-1", url);
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
    }

    private BufferedReader telechargerFichier(String url) throws IOException {
        logger.info("Récupération du fichier (url = " + url + ")...");
        final HttpURLConnection connexion = (HttpURLConnection) new URL(url).openConnection();
        connexion.setRequestMethod("GET");
        return new BufferedReader(
                new InputStreamReader(connexion.getInputStream(), StandardCharsets.ISO_8859_1));
    }

    private static class LigneFichierPresentations {
        private final long codeCIS;
        private final String presentation;
        private Double prix = 0.0;
        private Double honoraires = 0.0;
        private Integer tauxRemboursement = 0;
        private final String conditions;

        private LigneFichierPresentations(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Long.parseLong(champs[0]);
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
    }

    private static class LigneFichierBDPM {
        private final long codeCIS;
        private final String nomMedicament;
        private final String forme;
        private final String autorisation;
        private final String marque;

        private LigneFichierBDPM(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Long.parseLong(champs[0]);
            this.nomMedicament = champs[1].trim();
            this.forme = champs[2].trim();
            this.autorisation = champs[4].trim();
            this.marque = champs[10].trim();
        }
    }

    private static class LigneFichierCompo {
        private final long codeCIS;
        private final long codeSubstance;
        private final String nomSubstance;
        private final String dosage;
        private final String referenceDosage;

        private LigneFichierCompo(String ligne) {
            final String[] champs = ligne.split("\t");
            this.codeCIS = Long.parseLong(champs[0]);
            this.codeSubstance = Long.parseLong(champs[2]);
            this.nomSubstance = champs[3].trim();
            this.dosage = champs.length > 4 ? champs[4] : null;
            this.referenceDosage = champs.length > 5 ? champs[5] : null;
        }
    }
}
