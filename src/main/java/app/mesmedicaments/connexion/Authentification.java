package app.mesmedicaments.connexion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
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
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import io.jsonwebtoken.Claims;
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
	public static final String CLE_PRENOM;
	public static final String CLE_GENRE;
	public static final String CLE_EMAIL;
	public static final String CLE_INSCRIPTION_REQUISE;
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
	private static final ZoneId TIMEZONE;
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
		CLE_PRENOM = "prenom";
		CLE_EMAIL = "email";
		CLE_GENRE = "genre";
		CLE_INSCRIPTION_REQUISE = "inscription requise";
		TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);
		JWT_SIGNING_ALG = SignatureAlgorithm.HS512;
		JWT_SIGNING_KEY = "SuperSecretTest";
		///////////////// définir un secret et le lier à KEYVAULT
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

	public static boolean checkRefreshToken (String refreshJwt, String deviceIdHeader) 
		throws StorageException,
		URISyntaxException,
		InvalidKeyException
	{
		Claims claims = Jwts.parser()
			.setSigningKey(JWT_SIGNING_KEY)
			.parseClaimsJws(refreshJwt)
			.getBody();
		String id = (String) claims.get("id");
		String type = (String) claims.get("type");
		String deviceId = (String) claims.get("deviceId");
		byte[] sel = Base64.getDecoder().decode((String) claims.get("sel"));
		if (id.length() != 8 
			|| !type.equals("refresh")
			|| !deviceId.equals(deviceIdHeader)) 
		{ return false; }
		EntiteUtilisateur entite = EntiteUtilisateur.obtenirEntite(id);
		if (entite == null) { return false; }
		if (!entite.getDeviceId().equals(deviceId)) { return false; }
		Date derniereConnexion = entite.getDerniereConnexion();
		if (derniereConnexion != null) {
			LocalDateTime derniereConnexionLocale = LocalDateTime.ofInstant(
				derniereConnexion.toInstant(), TIMEZONE);
			if (derniereConnexionLocale.plusYears(1).isBefore(LocalDateTime.now())
				|| !Arrays.equals(entite.getJwtSalt(), sel))
			{ return false; }
		}
		entite.setDerniereConnexion(new Date());
		entite.mettreAJourEntite();
		return true;
	}
	
	private Logger logger;
	private String id;
	
	public Authentification (Logger logger, String id) {
		if (id.length() != 8) { throw new IllegalArgumentException("Format de l'id incorrect"); }
		this.logger = logger;
		this.id = id;
	}

    public String createAccessToken () {
        return Jwts.builder()
            .signWith(JWT_SIGNING_ALG, JWT_SIGNING_KEY)
            .setClaims(new JSONObjectUneCle("id", id).toMap())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // une heure
            .setIssuedAt(new Date())
            .compact();            
	}

	public String createRefreshToken (String deviceIdHeader) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (deviceIdHeader == null || deviceIdHeader.equals("")) {
			throw new IllegalArgumentException();
		}
		byte[] sel = new byte[16];
		new SecureRandom().nextBytes(sel);
		EntiteUtilisateur entite = EntiteUtilisateur.obtenirEntite(id);
		entite.setJwtSalt(sel);
		entite.setDeviceId(deviceIdHeader);
		Claims claims = Jwts.claims();
		claims.put("id", id);
		claims.put("type", "refresh");
		claims.put("sel", Base64.getEncoder().encodeToString(sel));
		claims.put("deviceId", deviceIdHeader);
		entite.mettreAJourEntite();
		return Jwts.builder()
			.signWith(JWT_SIGNING_ALG, JWT_SIGNING_KEY)
			.setClaims(claims)
			.setIssuedAt(new Date())
			.compact();
	}

	public void inscription (String prenom, String email, String genre) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (prenom.length() > 30
			|| email.length() > 128
			|| genre.length() != 1
		) { throw new IllegalArgumentException(); }
		EntiteUtilisateur entiteUtilisateur = new EntiteUtilisateur(id);
		entiteUtilisateur.setPrenom(prenom);
		entiteUtilisateur.setEmail(email);
		entiteUtilisateur.setGenre(genre);
		entiteUtilisateur.setMedicaments(null);
		entiteUtilisateur.setDateInscription(
			Date.from(Instant.now(Clock.system(TIMEZONE))));
		entiteUtilisateur.creerEntite();
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
			entiteConnexion.setMotDePasse(mdp);
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
		LocalDateTime maintenant = LocalDateTime.now(TIMEZONE);
		try {
			entiteConnexion = EntiteConnexion.obtenirEntiteNonAboutie(id);
			if (entiteConnexion == null) {
				logger.info("Appel API connexion étape 2 mais pas d'élément de l'étape 1 trouvé");
				throw new IllegalArgumentException("Pas d'élément de l'étape 1 trouvé"); 
			}
			LocalDateTime timestamp = LocalDateTime.ofInstant(
				entiteConnexion.getTimestamp().toInstant(), TIMEZONE);
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
			recupererInfosUtilisateur(cookies, retour);
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

	private void recupererInfosUtilisateur (Map<String, String> cookies, JSONObject retour) 
		throws IOException, StorageException, URISyntaxException, InvalidKeyException
	{
		Connection connexion;
		Document pageInfos;
		EntiteUtilisateur entiteUtilisateur = EntiteUtilisateur.obtenirEntite(id);
		if (entiteUtilisateur == null) {
			retour.put(CLE_INSCRIPTION_REQUISE, true);
			connexion = Jsoup.connect(URL_INFOS_DMP);
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			pageInfos = connexion.response().parse();
			retour.put(CLE_PRENOM, formaterPrenom(
				pageInfos.getElementById("firstNameValue")
					.text()));
			retour.put(CLE_EMAIL, pageInfos.getElementById("email")
				.val());
			retour.put(CLE_GENRE, pageInfos.getElementById("genderValue")
				.text());
		}
		else {
			retour.put(CLE_INSCRIPTION_REQUISE, false);
			retour.put(CLE_PRENOM, entiteUtilisateur.getPrenom());
			retour.put(CLE_EMAIL, entiteUtilisateur.getEmail());
			retour.put(CLE_GENRE, entiteUtilisateur.getGenre());
		}
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

	/**
	 * Garde une majuscule au début du prénom et après chaque tiret, le reste en minuscule
	 */
	private String formaterPrenom (String prenom) {
		String prenomFormate = "";
		boolean mettreEnMaj = true;
		for (char c : prenom.toCharArray()) {
			if (mettreEnMaj) { 
				prenomFormate += String.valueOf(c).toUpperCase();
				mettreEnMaj = false;
			} else {
				prenomFormate += String.valueOf(c).toLowerCase();
			}
			if (c == '-' || c == ' ') { mettreEnMaj = true; }
		}
		return prenomFormate;
	}
}