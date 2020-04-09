package app.mesmedicaments.dmp;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.utils.Utils;

public final class Authentificateur {

    private static final String REGEX_ACCUEIL = Environnement.DMP_REGEX_ACCUEIL;
    private static final String USERAGENT = Environnement.USERAGENT;
    private static final String URL_CHOIX_CODE = Environnement.DMP_URL_CHOIX_CODE;
    private static final String URL_CONNEXION_DMP = Environnement.DMP_URL_CONNEXION_DMP;
    private static final String URL_POST_FORM_DMP = Environnement.DMP_URL_POST_FORM_DMP;
    private static final String URL_ENVOI_CODE = Environnement.DMP_URL_ENVOI_CODE;
    private static final String URL_INFOS_DMP = Environnement.DMP_URL_INFOS_DMP;
    private static final String URL_LISTE_DOCS = Environnement.DMP_URL_LISTE_DOCS;
    private static final String URL_BASE = Environnement.DMP_URL_BASE;

    private Logger logger;
    private String id;

    public Authentificateur(Logger logger, String id) {
        if (id.length() != 8) {
            throw new IllegalArgumentException("Format de l'id incorrect");
        }
        this.logger = logger;
        this.id = id;
    }

    public ReponseConnexion1 connexionDMPPremiereEtape(String mdp) {
        try {
            PageReponseDMP pageReponse;
            Connection connexion;
            connexion = Jsoup.connect(URL_CONNEXION_DMP);
            connexion.method(Connection.Method.GET).execute();
            Connection.Response reponse = connexion.response();
            final Map<String, String> cookies = new HashMap<>(reponse.cookies());
            pageReponse = new PageReponseDMP(reponse.parse());
            connexion = Jsoup.connect(URL_POST_FORM_DMP);
            connexion.method(Connection.Method.POST)
                    .data("login", id)
                    .data("password", mdp)
                    .data("sid", pageReponse.getSid())
                    .data("t:formdata", pageReponse.getTformdata())
                    .cookies(cookies)
                    .userAgent(USERAGENT)
                    .execute();
            final Connection.Response deuxiemeReponse = connexion.response();
            if (reponse.url().equals(deuxiemeReponse.url())) {
                logger.info("La connexion a échoué (mauvais identifiants)");
                return new ReponseConnexion1(CodeReponse.erreurIds, null, null);
            }
            // Récupération du moyen d'envoi du code
            pageReponse = new PageReponseDMP(deuxiemeReponse.parse());
            connexion = Jsoup.connect(URL_CHOIX_CODE);
            connexion.method(Connection.Method.POST);
            String modeEnvoi;
            if (pageReponse.document.getElementById("bySMS") != null) {
                connexion.data("mediaValue", "0");
                modeEnvoi = "SMS";
            } else if (pageReponse.document.getElementById("byEmailMessage") != null) {
                connexion.data("mediaValue", "1");
                modeEnvoi = "courriel";
            } else {
                logger.severe("Aucun choix d'envoi du second code disponible");
                logger.severe(pageReponse.document.text());
                return new ReponseConnexion1(CodeReponse.erreurInterne, null, null);
            }
            // Envoi du code
            connexion.data("sid", pageReponse.getSid())
                    .data("t:formdata", pageReponse.getTformdata())
                    .userAgent(USERAGENT)
                    .cookies(cookies)
                    .execute();
            pageReponse = new PageReponseDMP(connexion.response().parse());
            return new ReponseConnexion1(
                CodeReponse.ok, 
                new DonneesConnexion(cookies, pageReponse),
                modeEnvoi);
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            return new ReponseConnexion1(CodeReponse.erreurInterne, null, null);
        }
    }

    public ReponseConnexion2 connexionDMPDeuxiemeEtape(String code, DonneesConnexion donneesConnexion) {
        final LocalDateTime maintenant = LocalDateTime.now(Utils.TIMEZONE);
        try {
            if (maintenant.minusMinutes(10).isAfter(donneesConnexion.date)) {
                throw new IllegalArgumentException("L'heure ne correspond pas ou plus");
            }
            final Connection connexion = Jsoup.connect(URL_ENVOI_CODE);
            connexion.method(Connection.Method.POST)
                    .data("sid", donneesConnexion.sid)
                    .data("t:formdata", donneesConnexion.tformdata)
                    .data("ipCode", code)
                    .cookies(donneesConnexion.cookies)
                    .userAgent(USERAGENT)
                    .execute();
            final Connection.Response reponse = connexion.response();
            final PageReponseDMP page = new PageReponseDMP(reponse.parse());
            if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
                // TODO : logger le nombre d'échecs pour être averti
                // au cas où la regex n'est plus valide
                return new ReponseConnexion2(
                        CodeReponse.erreurIds, 
                        new DonneesConnexion(donneesConnexion.cookies, page),
                        null,
                        null);
            }
            final String urlRemboursements = obtenirURLFichierRemboursements(donneesConnexion.cookies)
                                                .orElse(null);
            String genre = null;
            try {
                Optional<String> optGenre = obtenirGenre(donneesConnexion.cookies);
                if (optGenre.isPresent()) genre = optGenre.get();
            } catch (IOException e) {
                logger.warning("Impossible de récupérer le genre");
            }
            return new ReponseConnexion2(
                CodeReponse.ok,
                // il n'y a plus sid ni tformdata si la connexion est réussie
                new DonneesConnexion(donneesConnexion.cookies, null, null),
                urlRemboursements,
                genre);
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            return new ReponseConnexion2(CodeReponse.erreurInterne, donneesConnexion, null, null);
        }
    }

    private Optional<String> obtenirURLFichierRemboursements(Map<String, String> cookies) throws IOException {
        Connection connexion = Jsoup.connect(URL_LISTE_DOCS);
        connexion.method(Connection.Method.GET).userAgent(USERAGENT).cookies(cookies).execute();
        Document doc = connexion.response().parse();
        String attribut;
        try {
            attribut = doc.getElementsContainingOwnText("de remboursement").attr("href");
            // TODO idem
            connexion = Jsoup.connect(URL_BASE + attribut);
            connexion.method(Connection.Method.GET).userAgent(USERAGENT).cookies(cookies).execute();
            doc = connexion.response().parse();
            return Optional.ofNullable(doc.getElementById("docView").attr("src"));
        } catch (NullPointerException e) {
            // Notifier
        }
        return Optional.empty();
    }

    static private Optional<String> obtenirGenre(Map<String, String> cookies) throws IOException {
        Connection connexion = Jsoup.connect(URL_INFOS_DMP);
        connexion.method(Connection.Method.GET).userAgent(USERAGENT).cookies(cookies).execute();
        Document pageInfos = connexion.response().parse();
        return Optional.ofNullable(pageInfos.getElementById("genderValue").text());
    }

    static public
    abstract class ReponseConnexion {
        public final CodeReponse codeReponse;
        public final DonneesConnexion donneesConnexion;

        private ReponseConnexion(CodeReponse codeReponse, DonneesConnexion donneesConnexion) {
            this.codeReponse = codeReponse;
            this.donneesConnexion = donneesConnexion;
        }
    }

    static public
    class ReponseConnexion1
    extends ReponseConnexion {
        public final String modeEnvoiCode;

        private ReponseConnexion1(CodeReponse codeReponse, DonneesConnexion donneesConnexion, String modeEnvoiCode) {
            super(codeReponse, donneesConnexion);
            this.modeEnvoiCode = modeEnvoiCode;
        }
    }

    static public
    class ReponseConnexion2
    extends ReponseConnexion {
        public final String urlRemboursements;
        public final String genre;

        private ReponseConnexion2(CodeReponse codeReponse, DonneesConnexion donneesConnexion, String urlRemboursements, String genre) {
            super(codeReponse, donneesConnexion);
            this.urlRemboursements = urlRemboursements;
            this.genre = genre;
        }

    }

    static public enum CodeReponse {
        erreurIds,
        erreurInterne,
        ok
    }

}
