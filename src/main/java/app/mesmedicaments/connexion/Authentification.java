package app.mesmedicaments.connexion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.JSONObjectUneCle;
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
	//private static final String ID_MSI;
	//private static final String TABLE_UTILISATEURS;
	
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
		//TABLE_UTILISATEURS = System.getenv("table_utilisateurs");
		CLE_ERREUR = "erreur";
		CLE_ENVOI_CODE = "envoiCode";
		CLE_COOKIES = "cookies";
		CLE_SID = "sid";
		CLE_TFORMDATA = "tformdata";
		CLE_PRENOM = "prenom";
		CLE_EMAIL = "email";
		CLE_GENRE = "genre";
		CLE_EXISTENCE_DB = "existence";
	}
	
	//private final CloudTableClient clientTablesAzure;
	/*private final CloudTable CLOUDTABLE_UTILISATEURS;
	private final String CLE_PARTITION_CONNEXIONS;
	private final String CLE_PARTITION_INSCRIPTIONS;
	private final String CLE_PARTITION_INFORMATIONS;*/
	private Logger logger;
	
	public Authentification (Logger logger) throws InvalidKeyException, URISyntaxException, StorageException {
		this.logger = logger;
		/*CLOUDTABLE_UTILISATEURS = CloudStorageAccount
			.parse(System.getenv("connexion_tablesazure"))
			.createCloudTableClient()
			.getTableReference(System.getenv("tableazure_utilisateurs")); 
		CLE_PARTITION_CONNEXIONS = System.getenv("clepartition_connexions"); 
		CLE_PARTITION_INSCRIPTIONS = System.getenv("clepartition_inscriptions"); 
		CLE_PARTITION_INFORMATIONS = System.getenv("clepartition_informations");*/
	}

	public boolean inscription (String id, String prenom, String email) {
		/*try (
            SQLServerConnection conn = BaseDeDonnees.nouvelleConnexion(System.getenv("msi_auth"), logger);
            SQLServerPreparedStatement ps = (SQLServerPreparedStatement) conn.prepareStatement(
                "INSERT INTO " + TABLE_UTILISATEURS
                + " (prenom, mail) VALUES (?, ?)"
                + " WHERE id = ?");
        ) {
			ps.setString(1, prenom);
            ps.setString(2, email);
            ps.setString(3, id);
			ps.executeUpdate();
		}
		catch (SQLException e) {
			Utils.logErreur(e, logger);
			return false;
		}*/
		return true;
	}

	public JSONObject connexionDMP (String id, String mdp) {
		Document pageReponse;
		Connection connexion;
		HashMap<String, String> cookies;
		JSONObject retour = new JSONObject();
		//String requete = "{call projetdmp.authentifierUtilisateur (?, ?, ?)}";
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
			connexion
				.data("sid", obtenirSid(pageReponse))
				.data("t:formdata", obtenirTformdata(pageReponse))
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			pageReponse = reponse.parse();
			logger.info("Connexion étape 1 effectuée");
			if (!EntiteConnexion.creerEntite(
				id,
				obtenirSid(pageReponse),
				obtenirTformdata(pageReponse),
				cookies)
			) { 
				throw new InvalidKeyException("Impossible d'ajouter l'entité connexion"); 
			}
		} catch (IOException 
			| InvalidKeyException
			| URISyntaxException
			| StorageException e) {
			System.out.println("Erreur catch dans connexionDMP()");
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	public JSONObject doubleAuthentification (String id, String code) 
		throws IllegalArgumentException, 
		TimeoutException
	{
		/*** Instaurer un contrôle pour mdp ***/
		Connection connexion;
		Connection.Response reponse;
		HashMap<String, String> cookies;
		EntiteConnexion entite;
		JSONObject retour = new JSONObject();
		ZoneId timezone = ZoneId.of("ECT", ZoneId.SHORT_IDS);
		LocalDateTime maintenant = LocalDateTime.now(timezone);
		try {
			entite = EntiteConnexion.obtenirEntite(id);
			if (entite == null) { 
				throw new IllegalArgumentException("Pas d'élément de l'étape 1 trouvé"); 
			}
			logger.info(entite.toString());
			LocalDateTime timestamp = LocalDateTime.ofInstant(
				entite.getTimestamp().toInstant(), timezone);
			if (maintenant.minusMinutes(10).isAfter(timestamp)) { 
				throw new TimeoutException(); 
			}
			cookies = entite.obtenirCookiesMap();
			connexion = Jsoup.connect(URL_ENVOI_CODE);
			connexion.method(Connection.Method.POST)
				.data("sid", entite.getSid())
				.data("t:formdata", entite.getTformdata())
				.data("ipCode", code)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
				Document page = reponse.parse();
				EntiteConnexion.creerEntite(
					id, 
					obtenirSid(page), 
					obtenirTformdata(page), 
					cookies);
				return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
			}
			recupererInfosPerso(cookies, retour);
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

	private void recupererInfosPerso (HashMap<String, String> cookies, JSONObject retour) throws IOException {
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