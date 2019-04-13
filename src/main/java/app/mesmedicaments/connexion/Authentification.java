package app.mesmedicaments.connexion;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.BaseDeDonnees;
import app.mesmedicaments.Utils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

public final class Authentification {

	public static final String CLE_ERREUR;
	public static final String CLE_ENVOI_CODE;
	public static final String CLE_SID;
	public static final String CLE_TFORMDATA;
	public static final String CLE_COOKIES;
	public static final String CLE_PRENOM;
	public static final String CLE_GENRE;
	public static final String CLE_EMAIL;
	public static final String CLE_EXISTENCE_DB;
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
	private static final String ID_MSI;
	private static final Jedis CONN_REDIS; 

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
		ID_MSI = System.getenv("msi_auth");
		CLE_ERREUR = "erreur";
		CLE_ENVOI_CODE = "envoiCode";
		CLE_COOKIES = "cookies";
		CLE_SID = "sid";
		CLE_TFORMDATA = "tformdata";
		CLE_PRENOM = "prenom";
		CLE_EMAIL = "email";
		CLE_GENRE = "genre";
		CLE_EXISTENCE_DB = "existence";
		JedisShardInfo shardInfo = new JedisShardInfo(System.getenv("redis_hostname"), 6380, true);
		shardInfo.setPassword(System.getenv("redis_key"));
		CONN_REDIS = new Jedis(shardInfo);
	}

	private HashMap<String, String> cookies;
	private JSONObject retour;
	private Logger logger;
	private String id;

	public Authentification (Logger logger) {
		this.logger = logger;
		cookies = new HashMap<>();
		retour = new JSONObject();
	}

	public JSONObject connexionDMP (String id, String mdp) {
		Document pageSaisieCode;
		Connection connexion;
		String requete = "{call projetdmp.authentifierUtilisateur (?, ?, ?)}";
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
			this.id = id;
			logger.info("id = " + this.id);
			logger.info("mdp = " + mdp);
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
			stockerElementsConnexion(pageSaisieCode);
		} catch (IOException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		try (
			java.sql.Connection connDB = BaseDeDonnees.nouvelleConnexion(ID_MSI, logger);
			SQLServerCallableStatement cs = (SQLServerCallableStatement) connDB.prepareCall(requete);
		) {
			cs.setString(1, id);
			cs.setString(2, mdp);
			cs.registerOutParameter(3, java.sql.Types.BIT);
			cs.execute();
			retour.put(CLE_EXISTENCE_DB, cs.getInt(3) == 1);
		} catch (SQLException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_SQL);
		}
		return retour;
	}

	public JSONObject doubleAuthentification (String id, String code) throws IllegalArgumentException {
		/*** Instaurer un contrôle pour mdp ***/
		String cle = "auth:" + id;
		if (!CONN_REDIS.exists(cle)) { throw new IllegalArgumentException(); }
		HashMap<String, String> cache = new HashMap<>(CONN_REDIS.hgetAll(cle));
		JSONObject cookiesJson = new JSONObject(cache.get(CLE_COOKIES));
		Iterator<String> iterCookies = cookiesJson.keys();
		while (iterCookies.hasNext()) {
			String cookie = iterCookies.next();
			cookies.put(cookie, cookiesJson.getString(cookie));
		}
		try {
			Connection formCode = Jsoup.connect(URL_ENVOI_CODE);
			formCode.method(Connection.Method.POST)
				.data("sid", cache.get(CLE_SID))
				.data("t:formdata", cache.get(CLE_TFORMDATA))
				.userAgent(USERAGENT)
				.cookies(cookies)
				.data("ipCode", code)
				.execute();
			Connection.Response reponse = formCode.response();
			logger.info("URL de la réponse suite à la tentative de DA : " 
				+ reponse.url().toString());
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
				retour = new JSONObject();
				stockerElementsConnexion(reponse.parse());
				retour.put(CLE_ERREUR, ERR_ID);
				return retour;
			}
			recupererInfosPerso();
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	private void stockerElementsConnexion (Document page) {
		HashMap<String, String> cache = new HashMap<>();
		cache.put(CLE_SID, page.getElementsByAttributeValue("name", "sid")
				.first()
				.val());
		cache.put(CLE_TFORMDATA, page.getElementsByAttributeValue("name", "t:formdata")
			.first()
			.val());
		JSONObject cookiesJson = new JSONObject();
		for (String cookie : cookies.keySet()) {
			cookiesJson.put(cookie, cookies.get(cookie));
		}
		cache.put(CLE_COOKIES, cookiesJson.toString());
		String cle = "auth:" + id;
		CONN_REDIS.hmset(cle, cache);
		CONN_REDIS.expire(cle, 600);
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