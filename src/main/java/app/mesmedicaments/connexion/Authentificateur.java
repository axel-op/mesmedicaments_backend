package app.mesmedicaments.connexion;

import app.mesmedicaments.JSONObjectUneCle;
import app.mesmedicaments.Utils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class Authentificateur {

    public static final String ERR_INTERNE = "interne";
    public static final String ERR_ID = "mauvais identifiants";
    public static final String ENVOI_SMS = "SMS";
    public static final String ENVOI_MAIL = "Courriel";
    public static final String CLE_ERREUR = "erreur";
    public static final String CLE_ENVOI_CODE = "envoiCode";
    public static final String CLE_COOKIES = "cookies";
    public static final String CLE_SID = "sid";
    public static final String CLE_TFORMDATA = "tformdata";

    private static final String REGEX_ACCUEIL = System.getenv("regex_reussite_da");
    private static final String USERAGENT = System.getenv("user_agent");
    private static final String URL_CHOIX_CODE = System.getenv("url_post_choix_code");
    private static final String URL_CONNEXION_DMP = System.getenv("url_connexion_dmp");
    private static final String URL_POST_FORM_DMP = System.getenv("url_post_form_dmp");
    private static final String URL_ENVOI_CODE = System.getenv("url_post_envoi_code");
    private static final String URL_INFOS_DMP = System.getenv("url_infos_dmp");
    private static final String URL_LISTE_DOCS = System.getenv("url_liste_docs_dmp");
    private static final String URL_BASE = System.getenv("url_base_dmp");
    // ID_MSI = System.getenv("msi_auth");
    private static final SignatureAlgorithm JWT_SIGNING_ALG = SignatureAlgorithm.HS512;
    private static final String JWT_SIGNING_KEY = "SuperSecretTest";
    ///////////////// TODO définir un secret et le lier à une variable
    ///////////////// d'environnement

    public static String getIdFromToken(String jwt)
            throws SignatureException, ExpiredJwtException, MalformedJwtException,
                    UnsupportedJwtException {
        if (jwt == null) {
            throw new MalformedJwtException("Le token est absent");
        }
        return (String)
                Jwts.parser()
                        .setSigningKey(JWT_SIGNING_KEY)
                        .parseClaimsJws(jwt)
                        .getBody()
                        .get("id");
    }

    private Logger logger;
    private String id;

    public Authentificateur(Logger logger, String id) {
        if (id.length() != 8) {
            throw new IllegalArgumentException("Format de l'id incorrect");
        }
        this.logger = logger;
        this.id = id;
    }

    /**
     * Crée le seul token nécessaire pour modifier/accéder aux médicaments de l'utilisateur
     *
     * @return
     */
    public String createAccessToken() {
        return Jwts.builder()
                .signWith(JWT_SIGNING_ALG, JWT_SIGNING_KEY)
                .setClaims(new JSONObjectUneCle("id", id).toMap())
                .setIssuedAt(new Date())
                .compact();
    }

    public JSONObject connexionDMPPremiereEtape(String mdp) {
        final JSONObject retour = new JSONObject();
        Document pageReponse;
        Connection connexion;
        HashMap<String, String> cookies;
        try {
            connexion = Jsoup.connect(URL_CONNEXION_DMP);
            connexion.method(Connection.Method.GET).execute();
            Connection.Response reponse = connexion.response();
            cookies = new HashMap<>(reponse.cookies());
            pageReponse = reponse.parse();
            connexion = Jsoup.connect(URL_POST_FORM_DMP);
            connexion
                    .method(Connection.Method.POST)
                    .data("login", id)
                    .data("password", mdp)
                    .data("sid", obtenirSid(pageReponse))
                    .data("t:formdata", obtenirTformdata(pageReponse))
                    .cookies(cookies)
                    .userAgent(USERAGENT)
                    .execute();
            Connection.Response deuxiemeReponse = connexion.response();
            if (reponse.url().equals(deuxiemeReponse.url())) {
                logger.info("La connexion a échoué (mauvais identifiants)");
                return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
            }
            // Récupération du moyen d'envoi du code
            pageReponse = deuxiemeReponse.parse();
            connexion = Jsoup.connect(URL_CHOIX_CODE);
            connexion.method(Connection.Method.POST);
            if (pageReponse.getElementById("bySMS") != null) {
                connexion.data("mediaValue", "0");
                retour.put(CLE_ENVOI_CODE, ENVOI_SMS);
            } else if (pageReponse.getElementById("byEmailMessage") != null) {
                connexion.data("mediaValue", "1");
                retour.put(CLE_ENVOI_CODE, ENVOI_MAIL);
            } else {
                // Envoyer une notification et enregistrer le HTML
                logger.severe("Aucun choix d'envoi du second code disponible");
                return new JSONObjectUneCle(CLE_ERREUR, ERR_INTERNE);
            }
            // Envoi du code
            connexion
                    .data("sid", obtenirSid(pageReponse))
                    .data("t:formdata", obtenirTformdata(pageReponse))
                    .userAgent(USERAGENT)
                    .cookies(cookies)
                    .execute();
            reponse = connexion.response();
            pageReponse = reponse.parse();
            retour.put("donneesConnexion", convertirDonneesDeConnexion(pageReponse, cookies));
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            retour.put(CLE_ERREUR, ERR_INTERNE);
        }
        return retour;
    }

    public JSONObject connexionDMPDeuxiemeEtape(String code, JSONObject donneesConnexion) {
        final JSONObject retour = new JSONObject();
        final LocalDateTime maintenant = LocalDateTime.now(Utils.TIMEZONE);
        Connection connexion;
        Connection.Response reponse;
        Map<String, String> cookies;
        try {
            LocalDateTime timestamp = LocalDateTime.parse(donneesConnexion.getString("date"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (maintenant.minusMinutes(10).isAfter(timestamp)) {
                throw new IllegalArgumentException("L'heure ne correspond pas ou plus");
            }
            final JSONObject cookiesJSON = donneesConnexion.getJSONObject("cookies");
            cookies = cookiesJSON
                .keySet()
                .stream()
                .collect(Collectors.toMap(k -> k, k -> cookiesJSON.getString(k)));
            connexion = Jsoup.connect(URL_ENVOI_CODE);
            connexion
                    .method(Connection.Method.POST)
                    .data("sid", donneesConnexion.getString("sid"))
                    .data("t:formdata", donneesConnexion.getString("tformdata"))
                    .data("ipCode", code)
                    .userAgent(USERAGENT)
                    .cookies(cookies)
                    .execute();
            reponse = connexion.response();
            Document page = reponse.parse();
            if (!reponse.url()
            .toString()
            .matches(REGEX_ACCUEIL)) { // TODO : logger le nombre d'échecs pour être averti
            // au cas où la regex n'est plus valide
                return retour
                    .put("donneesConnexion", convertirDonneesDeConnexion(page, cookies))
                    .put(CLE_ERREUR, ERR_ID);
            }
            retour.put("urlRemboursements", obtenirURLFichierRemboursements(cookies).orElse(null));
            try {
                Optional<String> optGenre = obtenirGenre(cookies);
                if (optGenre.isPresent()) retour.put("genre", optGenre.get());
            } catch (IOException e) {
                logger.warning("Impossible de récupérer le genre");
            }
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            retour.put(CLE_ERREUR, ERR_INTERNE);
        }
        return retour;
    }

    /**
     * Renvoie un JSON contenant les données nécessaires au maintien de la connexion entre les deux étapes.
     * Cet objet doit être restitué tel quel pour la deuxième étape.
     * @param page
     * @param cookies
     * @return
     */
    private JSONObject convertirDonneesDeConnexion(Document page, Map<String, String> cookies) {
        final String sid = obtenirSid(page);
        final String tformdata = obtenirTformdata(page);
        return new JSONObject()
            .put("cookies", new JSONObject(cookies))
            .put("sid", sid)
            .put("tformdata", tformdata)
            .put("date", LocalDateTime.now(Utils.TIMEZONE).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private Optional<String> obtenirURLFichierRemboursements(Map<String, String> cookies)
            throws IOException {
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

    private String obtenirSid(Document page) {
        return page.getElementsByAttributeValue("name", "sid").first().val();
    }

    private String obtenirTformdata(Document page) {
        return page.getElementsByAttributeValue("name", "t:formdata").first().val();
    }

    private Optional<String> obtenirGenre(Map<String, String> cookies) throws IOException {
        Connection connexion = Jsoup.connect(URL_INFOS_DMP);
        connexion.method(Connection.Method.GET).userAgent(USERAGENT).cookies(cookies).execute();
        Document pageInfos = connexion.response().parse();
        return Optional.ofNullable(pageInfos.getElementById("genderValue").text());
    }
}
