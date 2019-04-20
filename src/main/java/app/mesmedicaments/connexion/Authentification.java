package app.mesmedicaments.connexion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.JSONObjectUneCle;
import app.mesmedicaments.Utils;
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

	public Authentification () {} ////uniquement pour le test
	
    @FunctionName("testMaintienConnexion")
    public void testMaintien (
        @TimerTrigger(
            name = "timerTestMaintien",
            schedule = "0 */20 * * * *"
        )
        final String timerInfo,
        final ExecutionContext context
    ) throws IOException, StorageException {
		Logger logger = context.getLogger();
		try {
			EntiteConnexion entite = EntiteConnexion.obtenirEntite(System.getenv("id_test_maintien"));
			HashMap<String, String> cookies = entite.obtenirCookiesMap();
			//String sid = entite.getSid();
			//String tformdata = entite.getTformdata();
			Connection connexion = Jsoup.connect("https://mondmp3.dmp.gouv.fr/dmp/documents/liste/raz");
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			Document doc = connexion.response().parse();
			String attribut = doc.getElementsContainingOwnText("de remboursement").attr("href");
			connexion = Jsoup.connect("https://mondmp3.dmp.gouv.fr" + attribut);
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			doc = connexion.response().parse();
			attribut = doc.getElementById("docView").attr("src");
			connexion = Jsoup.connect(attribut);
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			PDDocument pdf = PDDocument.load(connexion.response().bodyStream());
			PDFTextStripper stripper = new PDFTextStripper();
			StringReader sr = new StringReader(new String(stripper.getText(pdf).getBytes(), Charset.forName("ISO-8859-1")));
			BufferedReader br = new BufferedReader(sr);
			String ligne;
			boolean balise = false;
			while ((ligne = br.readLine()) != null && !balise) {
				if (ligne.matches(".*P.riode.*")) {
					logger.info("Fichier récupéré : ligne période trouvée : ");
					logger.info(ligne);
					balise = true;
				}
			}
			if (!balise) {
				logger.info("Le test a échoué à partir de "
					+ LocalDateTime.now(TIMEZONE).toString());
			} else {
				logger.info("Test OK à "
				+ LocalDateTime.now(TIMEZONE));
			}
			logger.info(connexion.response().parse().body().text());
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			throw e;
		}
    }

	public static String getIdFromToken (String jwt) 
		throws SignatureException, 
		ExpiredJwtException,
		MalformedJwtException,
		UnsupportedJwtException
	{
		return (String) Jwts.parser()
			.setSigningKey(JWT_SIGNING_KEY)
			.parseClaimsJws(jwt)
			.getBody()
			.get("id");
	}

	public static boolean checkRefreshToken (String refreshJwt) throws StorageException {
		Claims claims = Jwts.parser()
			.setSigningKey(JWT_SIGNING_KEY)
			.parseClaimsJws(refreshJwt)
			.getBody();
		String id = (String) claims.get("id");
		String type = (String) claims.get("type");
		byte[] sel = Base64.getDecoder().decode((String) claims.get("sel"));
		if (id.length() != 8 
			|| !type.equals("refresh")) 
		{ return false; }
		EntiteUtilisateur entite = EntiteUtilisateur.obtenirEntite(id);
		if (entite == null) { return false; }
		Date derniereConnexion = entite.getDerniereConnexion();
		if (derniereConnexion != null) {
			LocalDateTime derniereConnexionLocale = LocalDateTime.ofInstant(
				derniereConnexion.toInstant(), TIMEZONE);
			if (derniereConnexionLocale.plusYears(1).isBefore(LocalDateTime.now())
				|| !Arrays.equals(entite.getJwtSalt(), sel))
			{ return false; }
		}
		entite.setDerniereConnexion(new Date());
		EntiteUtilisateur.mettreAJourEntite(entite);
		return true;
	}
	
	private Logger logger;
	private String id;
	
	public Authentification (Logger logger, String id) {
		if (id.length() != 8) { throw new IllegalArgumentException(); }
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

	public String createRefreshToken () throws StorageException {
		byte[] sel = new byte[16];
		new SecureRandom().nextBytes(sel);
		EntiteUtilisateur entite = EntiteUtilisateur.obtenirEntite(id);
		entite.setJwtSalt(sel);
		EntiteUtilisateur.mettreAJourEntite(entite);
		Claims claims = Jwts.claims();
		claims.put("id", id);
		claims.put("type", "refresh");
		claims.put("sel", Base64.getEncoder().encodeToString(sel));
		return Jwts.builder()
			.signWith(JWT_SIGNING_ALG, JWT_SIGNING_KEY)
			.setClaims(claims)
			.setIssuedAt(new Date())
			.compact();
	}

	public void inscription (String prenom, String email, String genre) 
		throws StorageException
	{
		if (prenom.length() > 30
			|| email.length() > 128
			|| genre.length() != 1
		) { throw new IllegalArgumentException(); }
		EntiteUtilisateur entiteUtilisateur = EntiteUtilisateur.obtenirEntite(id);
		if (entiteUtilisateur == null) { throw new IllegalArgumentException(); }
		entiteUtilisateur.setPrenom(prenom);
		entiteUtilisateur.setEmail(email);
		entiteUtilisateur.setGenre(genre);
		entiteUtilisateur.setDateInscription(
			Date.from(Instant.now(Clock.system(TIMEZONE))));
		EntiteUtilisateur.mettreAJourEntite(entiteUtilisateur);
	}

	public JSONObject connexionDMP (String mdp) {
		if (mdp.length() > 128) { throw new IllegalArgumentException(); }
		Document pageReponse;
		Connection connexion;
		HashMap<String, String> cookies;
		EntiteUtilisateur entiteUtilisateur;
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
			// Vérification de l'existence de l'utilisateur
			entiteUtilisateur = EntiteUtilisateur.obtenirEntite(id);
			//boolean inscriptionRequise = entiteUtilisateur == null;
			if (entiteUtilisateur == null) {
				entiteUtilisateur = new EntiteUtilisateur(id);
				entiteUtilisateur.setMotDePasse(mdp);
				EntiteUtilisateur.definirEntite(entiteUtilisateur);
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
			//entiteConnexion.setInscriptionRequise(inscriptionRequise);
			EntiteConnexion.definirEntite(entiteConnexion);
		} catch (IOException | StorageException e) {
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
			entiteConnexion = EntiteConnexion.obtenirEntite(id);
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
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
				Document page = reponse.parse();
				entiteConnexion.setSid(obtenirSid(page));
				entiteConnexion.setTformdata(obtenirTformdata(page));
				entiteConnexion.definirCookiesMap(cookies);
				EntiteConnexion.mettreAJourEntite(entiteConnexion);
				return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
			}
			recupererInfosUtilisateur(cookies, retour);
		}
		catch (IOException | StorageException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	private void recupererInfosUtilisateur (HashMap<String, String> cookies, JSONObject retour) 
		throws IOException, StorageException
	{
		Connection connexion;
		Document pageInfos;
		EntiteUtilisateur entiteUtilisateur = EntiteUtilisateur.obtenirEntite(id);
		Date inscription = entiteUtilisateur.getDateInscription();
		if (inscription == null) {
			retour.put(CLE_INSCRIPTION_REQUISE, true);
			connexion = Jsoup.connect(URL_INFOS_DMP);
			connexion.method(Connection.Method.GET)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			pageInfos = connexion.response().parse();
			retour.put(CLE_PRENOM, pageInfos.getElementById("firstNameValue")
				.text());
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
}