package app.mesmedicaments.connexion;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.*;

public class Authentification {

	public static final String ERR_INTERNE;
	public static final String ERR_ID;
	public static final String ENVOI_SMS;
	public static final String ENVOI_MAIL;
	private static String userAgent;
	//private HashMap<String, String> cookies;

	static {
		ERR_INTERNE = "interne";
		ERR_ID = "mauvais identifiants";
		ENVOI_SMS = "SMS";
		ENVOI_MAIL = "Courriel";
		userAgent = System.getenv("user_agent");
		System.out.println(userAgent);
	}

	private Authentification () {}

	public static JSONObject connexionDMP (Logger logger, String id, String mdp) {
		String urlChoixCode = System.getenv("url_post_choix_code");
		String urlConnexionDMP = System.getenv("url_connexion_dmp");
		String urlPostFormDMP = System.getenv("url_post_form_dmp");
		JSONObject retour = new JSONObject();
		Document pageSaisieCode;
		LocalDateTime heure;
		try {
			Connection connexion;
			connexion = Jsoup.connect(urlConnexionDMP);
			connexion.method(Connection.Method.GET)
				.execute(); 
			Connection.Response reponse = connexion.response();
			HashMap<String, String> cookies = new HashMap<String, String>(reponse.cookies());
			Document htmlId = reponse.parse();
			connexion = Jsoup.connect(urlPostFormDMP);
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
				.userAgent(userAgent)
				.cookies(cookies)
				.execute();
			Connection.Response deuxiemeReponse = connexion.response();
			if (reponse.url().equals(deuxiemeReponse.url())) { 
				logger.info("La connexion a échoué (mauvais identifiants)");
				retour = new JSONObject();
				retour.put("erreur", ERR_ID);
				return retour;
			}
			Document pageEnvoiCode = deuxiemeReponse.parse();
			connexion = Jsoup.connect(urlChoixCode);
			connexion.method(Connection.Method.POST);
			if (pageEnvoiCode.getElementById("bySMS") != null) { 
				connexion.data("mediaValue", "0"); 
				retour.put("envoiCode", ENVOI_SMS);
			}
			else if (pageEnvoiCode.getElementById("byEmailMessage") != null) {
				connexion.data("mediaValue", "1"); 
				retour.put("envoiCode", ENVOI_MAIL);
			}
			else { 
				logger.info("Aucun choix d'envoi du second code disponible");
				retour = new JSONObject();
				//Envoyer une notification et enregistrer le HTML
				logger.severe("Aucun choix d'envoi du second code disponible");
				retour.put("erreur", ERR_INTERNE);
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
				.userAgent(userAgent)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			heure = LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
			pageSaisieCode = reponse.parse();
			retour.put("heure", heure.toString());
			retour.put("baseUri", pageSaisieCode.baseUri());
			retour.put("sid", pageSaisieCode.getElementsByAttributeValue("name", "sid")
				.first()
				.val());
			retour.put("tformdata", pageSaisieCode.getElementsByAttributeValue("name", "t:formdata")
				.first()
				.val());
			JSONObject cookiesJson = new JSONObject();
			for (String cookie : cookies.keySet()) {
				cookiesJson.put(cookie, cookies.get(cookie));
			}
			retour.put("cookies", cookiesJson);
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put("erreur", ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put("erreur", ERR_INTERNE);
		}
		return retour;
	}

	public static JSONObject doubleAuthentification (
		Logger logger,
		String code,
		String sid, 
		String tformdata, 
		String baseUri,
		JSONObject cookiesJson
	) {
		/*** Instaurer un contrôle pour mdp ***/
		String urlEnvoiCode = System.getenv("url_post_envoi_code");
		JSONObject retour = new JSONObject();
		HashMap<String, String> cookies = new HashMap<>();
		Iterator<String> iterCookies = cookiesJson.keys();
		while (iterCookies.hasNext()) {
			String cookie = iterCookies.next();
			cookies.put(cookie, cookiesJson.getString(cookie));
		}
		try {
			Connection formCode = Jsoup.connect(urlEnvoiCode);
			formCode.method(Connection.Method.POST)
				.data("sid", sid)
				.data("t:formdata", tformdata)
				.userAgent(userAgent)
				.cookies(cookies)
				.data("ipCode", code)
				.execute();
			Connection.Response reponse = formCode.response();
			if (baseUri.equals(reponse.url().toString())) {
				retour = new JSONObject();
				retour.put("erreur", ERR_ID);
				return retour;
			}
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put("erreur", ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour = new JSONObject();
			retour.put("erreur", ERR_INTERNE);
		}
		return retour;
	}
}