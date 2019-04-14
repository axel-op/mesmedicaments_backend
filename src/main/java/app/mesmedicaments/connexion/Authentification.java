package app.mesmedicaments.connexion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
//import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
//import java.util.Iterator;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.DynamicTableEntity;
//import com.microsoft.azure.storage.table.EntityProperty;
import com.microsoft.azure.storage.table.TableOperation;
//import com.microsoft.azure.storage.table.TableResult;
//import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
//import com.microsoft.sqlserver.jdbc.SQLServerConnection;
//import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;

import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

//import app.mesmedicaments.BaseDeDonnees;
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
	private final CloudTable CLOUDTABLE_UTILISATEURS;
	private final String CLE_PARTITION_CONNEXIONS;
	private final String CLE_PARTITION_INSCRIPTIONS;
	private final String CLE_PARTITION_INFORMATIONS;
	private Logger logger;
	
	public Authentification (Logger logger) throws InvalidKeyException, URISyntaxException, StorageException {
		this.logger = logger;
		CLOUDTABLE_UTILISATEURS = CloudStorageAccount
			.parse(System.getenv("connexion_tablesazure")) //////////
			.createCloudTableClient()
			.getTableReference(System.getenv("tableazure_utilisateurs")); //////////
		CLE_PARTITION_CONNEXIONS = System.getenv("clepartition_connexions"); //////////
		CLE_PARTITION_INSCRIPTIONS = System.getenv("clepartition_inscriptions"); //////////
		CLE_PARTITION_INFORMATIONS = System.getenv("clepartition_informations"); //////////
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
		Document pageSaisieCode;
		Connection connexion;
		JSONObject retour = new JSONObject();
		//String requete = "{call projetdmp.authentifierUtilisateur (?, ?, ?)}";
		try {
			connexion = Jsoup.connect(URL_CONNEXION_DMP);
			connexion.method(Connection.Method.GET)
				.execute(); 
			Connection.Response reponse = connexion.response();
			HashMap<String, String> cookies = new HashMap<>(reponse.cookies());
			Document htmlId = reponse.parse();
			connexion = Jsoup.connect(URL_POST_FORM_DMP);
			connexion.method(Connection.Method.POST)
				.data("login", id)
				.data("password", mdp)
				.data("sid", obtenirSid(htmlId)) 
				.data("t:formdata", obtenirTformdata(htmlId))
				.cookies(cookies)
				.userAgent(USERAGENT)
				.execute();
			Connection.Response deuxiemeReponse = connexion.response();
			if (reponse.url().equals(deuxiemeReponse.url())) { 
				logger.info("La connexion a échoué (mauvais identifiants)");
				return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
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
				//Envoyer une notification et enregistrer le HTML
				logger.severe("Aucun choix d'envoi du second code disponible");
				return new JSONObjectUneCle(CLE_ERREUR, ERR_INTERNE);
			}
			connexion
				.data("sid", obtenirSid(pageEnvoiCode))
				.data("t:formdata", obtenirTformdata(pageEnvoiCode))
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			reponse = connexion.response();
			pageSaisieCode = reponse.parse();
			stockerElementsSession(pageSaisieCode, id, cookies);
		} catch (IOException | StorageException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		/*try (
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
		}*/
		try {
			retour.put(CLE_EXISTENCE_DB, stockerInfosUtilisateur(id, mdp, null, null));
		}
		catch (StorageException e) {
			Utils.logErreur(e, logger);
			retour.put(CLE_ERREUR, ERR_INTERNE);
		}
		return retour;
	}

	public JSONObject doubleAuthentification (String id, String code) throws IllegalArgumentException, TimeoutException {
		/*** Instaurer un contrôle pour mdp ***/
		JSONObject retour = new JSONObject();
		EntiteConnexion entite = obtenirEntiteConnexion(id);
		if (entite == null) { throw new IllegalArgumentException(); }
		ZoneId timezone = ZoneId.of("ECT", ZoneId.SHORT_IDS);
		LocalDateTime timestamp = LocalDateTime.ofInstant(
			entite.getTimestamp().toInstant(), timezone);
		LocalDateTime maintenant = LocalDateTime.now(timezone);
		if (maintenant.minusMinutes(10).isAfter(timestamp)) { 
			throw new TimeoutException(); 
		}
		HashMap<String, String> cookies = entite.getCookies();
		/*Iterator<String> iterCookies = cookiesJson.keys();
		while (iterCookies.hasNext()) {
			String cookie = iterCookies.next();
			cookies.put(cookie, cookiesJson.getString(cookie));
		}*/
		try {
			Connection formCode = Jsoup.connect(URL_ENVOI_CODE);
			formCode.method(Connection.Method.POST)
				.data("sid", entite.getSid())
				.data("t:formdata", entite.getTformdata())
				.data("ipCode", code)
				.userAgent(USERAGENT)
				.cookies(cookies)
				.execute();
			Connection.Response reponse = formCode.response();
			if (!reponse.url().toString().matches(REGEX_ACCUEIL)) {
				stockerElementsSession(reponse.parse(), id, cookies);
				return new JSONObjectUneCle(CLE_ERREUR, ERR_ID);
			}
			recupererInfosPerso(cookies, retour);
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

	private boolean stockerInfosUtilisateur (String id, String mdp, String prenom, String email) throws StorageException {
		EntiteUtilisateur entite = obtenirEntiteUtilisateur(id);
		boolean existeDeja = entite != null;
		if (!existeDeja
			|| !entite.getPrenom().equals(prenom)
			|| !entite.getEmail().equals(email)
		) {
			EntiteUtilisateur nouvelleEntite = new EntiteUtilisateur(id, mdp, prenom, email);
			CLOUDTABLE_UTILISATEURS.execute(
				TableOperation.insertOrMerge(nouvelleEntite));
		}
		return existeDeja;
	}

	private void stockerElementsSession (Document page, String id, HashMap<String, String> cookies) throws StorageException {
		EntiteConnexion entite = new EntiteConnexion(
			id, 
			obtenirSid(page), 
			obtenirTformdata(page), 
			cookies);
		CLOUDTABLE_UTILISATEURS.execute(
			TableOperation.insertOrMerge(entite));
		/*HashMap<String, String> cache = new HashMap<>();
		cache.put(CLE_SID, obtenirSid(page));
		cache.put(CLE_TFORMDATA, obtenirTformdata(page));
		JSONObject cookiesJson = new JSONObject();
		for (String cookie : cookies.keySet()) {
			cookiesJson.put(cookie, cookies.get(cookie));
		}
		cache.put(CLE_COOKIES, cookiesJson.toString());
		//String cle = "auth:" + id;
		CONN_REDIS.hmset(cle, cache);
		CONN_REDIS.expire(cle, 600);*/
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

	private EntiteConnexion obtenirEntiteConnexion (String id) throws IllegalArgumentException {
		try {
			TableOperation operation = TableOperation.retrieve(
				CLE_PARTITION_CONNEXIONS, 
				id, 
				EntiteConnexion.class);
			return CLOUDTABLE_UTILISATEURS
				.execute(operation)
				.getResultAsType();
		}
		catch (StorageException e) {
			throw new IllegalArgumentException("Cet utilisateur n'existe pas");
		}
	}

	private EntiteUtilisateur obtenirEntiteUtilisateur (String id) throws StorageException {
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION_INFORMATIONS, 
			id, 
			EntiteUtilisateur.class);
		return CLOUDTABLE_UTILISATEURS
			.execute(operation)
			.getResultAsType();
	}

	private class EntiteConnexion extends DynamicTableEntity {
		private String sid;
		private String tformdata;
		private HashMap<String, String> cookies;

		EntiteConnexion (String id, String sid, String tformdata, HashMap<String, String> cookies) {
			super(CLE_PARTITION_CONNEXIONS, id);
			this.sid = sid;
			this.tformdata = tformdata;
			this.cookies = cookies;
		}

		String getSid() { return sid; }
		//private void setSid(String sid) { this.sid = sid; }
		String getTformdata() { return tformdata; }
		//private void setTformdata(String tformdata) { this.tformdata = tformdata; }
		HashMap<String, String> getCookies() { return cookies; }
	}

	private class EntiteUtilisateur extends DynamicTableEntity {
		private String prenom;
		private String email;
		private String motDePasseTemporaire;

		EntiteUtilisateur (String id, String mdp, String prenom, String email) {
			super(CLE_PARTITION_INFORMATIONS, id);
			this.prenom = prenom;
			this.email = email;
			motDePasseTemporaire = mdp;
		}

		String getPrenom() { return prenom; }
		String getEmail() { return email; }
	}
}