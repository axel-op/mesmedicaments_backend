package app.mesmedicaments.connexion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.JSONObjectUneCle;
import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteConnexion;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

public final class Authentification {

	public static final String CLE_ERREUR;
	public static final String CLE_ENVOI_CODE;
	public static final String CLE_SID;
	public static final String CLE_TFORMDATA;
	public static final String CLE_COOKIES;
	public static final String ERR_INTERNE;
	public static final String ERR_ID;
	public static final String ERR_SQL;
	public static final String ENVOI_SMS;
	public static final String ENVOI_MAIL;

	private static final String REGEX_ACCUEIL;
	private static final String USERAGENT;
	private static final String URL_CHOIX_CODE;
	private static final String URL_CONNEXION_DMP;
	private static final String URL_POST_FORM_DMP;
	private static final String URL_ENVOI_CODE;
	private static final String URL_INFOS_DMP;
	//private static final String ID_MSI;
	private static SignatureAlgorithm JWT_SIGNING_ALG;
    private static String JWT_SIGNING_KEY;
	
	static {
		ERR_INTERNE = "interne";
		ERR_ID = "mauvais identifiants";
		ERR_SQL = "erreur lors de l'enregistrement BDD";
		ENVOI_SMS = "SMS";
		ENVOI_MAIL = "Courriel";
		REGEX_ACCUEIL = System.getenv("regex_reussite_da");
		USERAGENT = System.getenv("user_agent");
		URL_CHOIX_CODE = System.getenv("url_post_choix_code");
		URL_CONNEXION_DMP = System.getenv("url_connexion_dmp");
		URL_POST_FORM_DMP = System.getenv("url_post_form_dmp");
		URL_ENVOI_CODE = System.getenv("url_post_envoi_code");
		URL_INFOS_DMP = System.getenv("url_infos_dmp");
		//ID_MSI = System.getenv("msi_auth");
		CLE_ERREUR = "erreur";
		CLE_ENVOI_CODE = "envoiCode";
		CLE_COOKIES = "cookies";
		CLE_SID = "sid";
		CLE_TFORMDATA = "tformdata";
		JWT_SIGNING_ALG = SignatureAlgorithm.HS512;
		JWT_SIGNING_KEY = "SuperSecretTest";
		///////////////// TODO définir un secret et le lier à une variable d'environnement
	}

	public static String getIdFromToken (String jwt) 
		throws SignatureException, 
		ExpiredJwtException,
		MalformedJwtException,
		UnsupportedJwtException
	{
		if (jwt == null) { throw new MalformedJwtException("Le token est absent"); }
		return (String) Jwts.parser()
			.setSigningKey(JWT_SIGNING_KEY)
			.parseClaimsJws(jwt)
			.getBody()
			.get("id");
	}
	
	private Logger logger;
	private String id;
	
	public Authentification (Logger logger, String id) {
		if (id.length() != 8) { throw new IllegalArgumentException("Format de l'id incorrect"); }
		this.logger = logger;
		this.id = id;
	}

	/**
	 * Crée le seul token nécessaire pour modifier/accéder aux médicaments de l'utilisateur
	 * @return
	 */
    public String createAccessToken () {
        return Jwts.builder()
            .signWith(JWT_SIGNING_ALG, JWT_SIGNING_KEY)
            .setClaims(new JSONObjectUneCle("id", id).toMap())
            .setIssuedAt(new Date())
            .compact();            
	}

	public JSONObject connexionDMP (String mdp) {
		if (mdp.length() > 128) { throw new IllegalArgumentException("Format du mdp incorrect"); }
		Document pageReponse;
		Connection connexion;
		HashMap<String, String> cookies;
		EntiteConnexion entiteConnexion;
		JSONObject retour = new JSONObject();
		try {
			connexion = Jsoup.connect(URL_CONNEXION_DMP);
			connexion.method(Connection.Method.GET)
				.execute(); 
			Connection.Response reponse = connexion.response();
			cookies = new HashMap<>(reponse.cookies());
			pageReponse = reponse.parse();
			connexion = Jsoup.connect(URL_POST_FORM_DMP);
			connexion.method(Connection.Method.POST)
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
			}
			else if (pageReponse.getElementById("byEmailMessage") != null) {
				connexion.data("mediaValue", "1"); 
				retour.put(CLE_ENVOI_CODE, ENVOI_MAIL);
			}
			else { 
				//Envoyer une notification et enregistrer le HTML
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
			entiteConnexion = new EntiteConnexion(id);
			entiteConnexion.setSid(obtenirSid(pageReponse));
			entiteConnexion.setTformdata(obtenirTformdata(pageReponse));
			entiteConnexion.definirCookiesMap(cookies);
			entiteConnexion.creerEntite(); 
		} catch (IOException 
			| InvalidKeyException
			| URISyntaxException
			| StorageException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	public JSONObject doubleAuthentification (String code) {
		if (code.length() >= 10) { throw new IllegalArgumentException(); }
		/*** Instaurer un contrôle pour mdp ***/
		Connection connexion;
		Connection.Response reponse;
		HashMap<String, String> cookies;
		//boolean inscriptionRequise;
		EntiteConnexion entiteConnexion;
		JSONObject retour = new JSONObject();
		LocalDateTime maintenant = LocalDateTime.now(Utils.TIMEZONE);
		try {
			entiteConnexion = EntiteConnexion.obtenirEntite(id).get();
			if (entiteConnexion == null) {
				logger.info("Appel API connexion étape 2 mais pas d'élément de l'étape 1 trouvé");
				throw new IllegalArgumentException("Pas d'élément de l'étape 1 trouvé"); 
			}
			LocalDateTime timestamp = Utils.dateToLocalDateTime(entiteConnexion.getTimestamp());
			if (maintenant.minusMinutes(10).isAfter(timestamp)) { 
				throw new IllegalArgumentException("L'heure ne correspond pas ou plus"); 
			}
			cookies = entiteConnexion.obtenirCookiesMap();
			connexion = Jsoup.connect(URL_ENVOI_CODE);
			connexion.method(Connection.Method.POST)
				.data("sid", entiteConnexion.getSid())
				.data("t:formdata", entiteConnexion.getTformdata())
				.data("ipCode", code)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			Document page = reponse.parse();
			entiteConnexion.definirCookiesMap(cookies);
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) { // TODO : logger le nombre d'échecs pour être averti au cas où la regex n'est plus valide
				entiteConnexion.setSid(obtenirSid(page));
				entiteConnexion.setTformdata(obtenirTformdata(page));
				entiteConnexion.mettreAJourEntite();
				return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
			}
			entiteConnexion.setUrlFichierRemboursements(
				obtenirURLFichierRemboursements(cookies).orElse(null)
			);
			entiteConnexion.mettreAJourEntite();
			try {
				Optional<String> optGenre = obtenirGenre(cookies);
				if (optGenre.isPresent()) retour.put("genre", optGenre.get()); 
			}
			catch (IOException e) { logger.warning("Impossible de récupérer le genre"); }
		}
		catch (IOException 
			| InvalidKeyException
			| URISyntaxException
			| StorageException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	private Optional<String> obtenirURLFichierRemboursements (Map<String, String> cookies) throws IOException {
		Connection connexion = Jsoup.connect("https://mondmp3.dmp.gouv.fr/dmp/documents/liste/raz");
		connexion.method(Connection.Method.GET)
			.userAgent(USERAGENT)
			.cookies(cookies)
			.execute();
		Document doc = connexion.response().parse();
		String attribut;
		try {
			attribut = doc.getElementsContainingOwnText("de remboursement").attr("href");
			connexion = Jsoup.connect("https://mondmp3.dmp.gouv.fr" + attribut);
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			doc = connexion.response().parse();
			return Optional.ofNullable(doc.getElementById("docView").attr("src"));
		}
		catch (NullPointerException e) {
			// Notifier
		}
		return Optional.empty();
	}

	private String obtenirSid (Document page) {
		return page.getElementsByAttributeValue("name", "sid")
			.first()
			.val();
	}

	private String obtenirTformdata (Document page) {
		return page.getElementsByAttributeValue("name", "t:formdata")
			.first()
			.val();
	}

	private Optional<String> obtenirGenre (Map<String, String> cookies) 
		throws IOException
	{
		Connection connexion = Jsoup.connect(URL_INFOS_DMP);
		connexion.method(Connection.Method.GET)
			.userAgent(USERAGENT)
			.cookies(cookies)
			.execute();
		Document pageInfos = connexion.response().parse();
		return Optional.ofNullable(pageInfos.getElementById("genderValue").text());
	}
}