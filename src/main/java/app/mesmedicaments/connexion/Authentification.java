package app.mesmedicaments.connexion;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.Utils;

public final class Authentification {

	public static final String CLE_ERREUR;
	public static final String CLE_ENVOI_CODE;
	public static final String CLE_SID;
	public static final String CLE_TFORMDATA;
	public static final String CLE_COOKIES;
	public static final String CLE_PRENOM;
	public static final String CLE_GENRE;
	public static final String CLE_EMAIL;
	public static final String ERR_INTERNE;
	public static final String ERR_ID;
	public static final String ENVOI_SMS;
	public static final String ENVOI_MAIL;

	private static final String REGEX_ACCUEIL;
	private static final String USERAGENT;
	private static final String URL_CHOIX_CODE;
	private static final String URL_CONNEXION_DMP;
	private static final String URL_POST_FORM_DMP;
	private static final String URL_ENVOI_CODE;
	private static final String URL_INFOS_DMP;

	// Champs d'instance
	private HashMap<String, String> cookies;
	private JSONObject retour;

	static {
		CLE_ERREUR = "erreur";
		ERR_INTERNE = "interne";
		ERR_ID = "mauvais identifiants";
		ENVOI_SMS = "SMS";
		ENVOI_MAIL = "Courriel";
		REGEX_ACCUEIL = System.getenv("regex_reussite_da");
		USERAGENT = System.getenv("user_agent");
		URL_CHOIX_CODE = System.getenv("url_post_choix_code");
		URL_CONNEXION_DMP = System.getenv("url_connexion_dmp");
		URL_POST_FORM_DMP = System.getenv("url_post_form_dmp");
		URL_ENVOI_CODE = System.getenv("url_post_envoi_code");
		URL_INFOS_DMP = System.getenv("url_infos_dmp");
		CLE_ENVOI_CODE = "envoiCode";
		CLE_COOKIES = "cookies";
		CLE_SID = "sid";
		CLE_TFORMDATA = "tformdata";
		CLE_PRENOM = "prenom";
		CLE_EMAIL = "email";
		CLE_GENRE = "genre";
	}

	public Authentification () {
		cookies = new HashMap<>();
		retour = new JSONObject();
	}

	public JSONObject connexionDMP (Logger logger, String id, String mdp) {
		Document pageSaisieCode;
		Connection connexion;
		try {
			connexion = Jsoup.connect(URL_CONNEXION_DMP);
			connexion.method(Connection.Method.GET)
				.execute(); 
			Connection.Response reponse = connexion.response();
			cookies = new HashMap<String, String>(reponse.cookies());
			Document htmlId = reponse.parse();
			connexion = Jsoup.connect(URL_POST_FORM_DMP);
			connexion.method(Connection.Method.POST)
				.data("login", id)
				.data("password", mdp)
				.data("sid", htmlId
					.getElementsByAttributeValue("name", "sid")
					.first()
					.val()) 
				.data("t:formdata", htmlId
					.getElementsByAttributeValue("name", "t:formdata")
					.first()
					.val())
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			Connection.Response deuxiemeReponse = connexion.response();
			if (reponse.url().equals(deuxiemeReponse.url())) { 
				logger.info("La connexion a échoué (mauvais identifiants)");
				retour = new JSONObject();
				retour.put(CLE_ERREUR, ERR_ID);
				return retour;
			}
			Document pageEnvoiCode = deuxiemeReponse.parse();
			connexion = Jsoup.connect(URL_CHOIX_CODE);
			connexion.method(Connection.Method.POST);
			if (pageEnvoiCode.getElementById("bySMS") != null) { 
				connexion.data("mediaValue", "0"); 
				retour.put(CLE_ENVOI_CODE, ENVOI_SMS);
			}
			else if (pageEnvoiCode.getElementById("byEmailMessage") != null) {
				connexion.data("mediaValue", "1"); 
				retour.put(CLE_ENVOI_CODE, ENVOI_MAIL);
			}
			else { 
				logger.info("Aucun choix d'envoi du second code disponible");
				retour = new JSONObject();
				//Envoyer une notification et enregistrer le HTML
				logger.severe("Aucun choix d'envoi du second code disponible");
				retour.put(CLE_ERREUR, ERR_INTERNE);
				return retour;
			}
			connexion.data("sid", pageEnvoiCode
					.getElementsByAttributeValue("name", "sid")
					.first()
					.val())
				.data("t:formdata", pageEnvoiCode
					.getElementsByAttributeValue("name", "t:formdata")
					.first()
					.val())
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			pageSaisieCode = reponse.parse();
			recupererElementsConnexion(pageSaisieCode);
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	public JSONObject doubleAuthentification (
		Logger logger,
		String code,
		String sid, 
		String tformdata, 
		JSONObject cookiesJson
	) {
		/*** Instaurer un contrôle pour mdp ***/
		Iterator<String> iterCookies = cookiesJson.keys();
		while (iterCookies.hasNext()) {
			String cookie = iterCookies.next();
			cookies.put(cookie, cookiesJson.getString(cookie));
		}
		try {
			Connection formCode = Jsoup.connect(URL_ENVOI_CODE);
			formCode.method(Connection.Method.POST)
				.data("sid", sid)
				.data("t:formdata", tformdata)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.data("ipCode", code)
				.execute();
			Connection.Response reponse = formCode.response();
			logger.info("URL de la réponse suite à la tentative de DA : " 
				+ reponse.url().toString());
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
				retour = new JSONObject();
				recupererElementsConnexion(reponse.parse());
				retour.put(CLE_ERREUR, ERR_ID);
				return retour;
			}
			recupererInfosPerso();
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	private void recupererElementsConnexion (Document page) {
		retour.put(CLE_SID, page.getElementsByAttributeValue("name", "sid")
				.first()
				.val());
		retour.put(CLE_TFORMDATA, page.getElementsByAttributeValue("name", "t:formdata")
			.first()
			.val());
		JSONObject cookiesJson = new JSONObject();
		for (String cookie : cookies.keySet()) {
			cookiesJson.put(cookie, cookies.get(cookie));
		}
		retour.put(CLE_COOKIES, cookiesJson);
	}

	private void recupererInfosPerso () throws IOException {
		Connection connexion;
		Document pageInfos;
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
}