package app.mesmedicaments.connexion;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import app.mesmedicaments.*;

public class Authentification {

	private static String userAgent;
	private HashMap<String, String> cookies;
	private Logger logger;
	private String id;
	private String mdp;

	static {
		userAgent = System.getenv("user_agent");
		System.out.println(userAgent);
	}

	public Authentification (Logger logger, String id, String mdp) {
		this.logger = logger;
		this.id = id;
		this.mdp = mdp;
	}

	public Document connexionDMP () {
		String urlChoixCode = System.getenv("url_post_choix_code");
		String urlConnexionDMP = System.getenv("url_connexion_dmp");
		String urlPostFormDMP = System.getenv("url_post_form_dmp");
		Document pageSaisieCode;
		try {
			Connection connexion;
			connexion = Jsoup.connect(urlConnexionDMP);
			connexion.method(Connection.Method.GET)
				.execute(); 
			Connection.Response reponse = connexion.response();
			cookies = new HashMap<String, String>(reponse.cookies());
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
			//Vérifier si connexion réussie puis stocker identifiants
			/////////////à faire////////
			Connection.Response deuxiemeReponse = connexion.response();
			if (reponse.url().equals(deuxiemeReponse.url())) { 
				logger.info("La connexion a échoué (mauvais identifiants)");
				return null;
			}
			Document pageEnvoiCode = deuxiemeReponse.parse();
			connexion = Jsoup.connect(urlChoixCode);
			connexion.method(Connection.Method.POST);
			if (pageEnvoiCode.getElementById("bySMS") != null) { 
				connexion.data("mediaValue", "0"); 
			}
			else if (pageEnvoiCode.getElementById("byEmailMessage") != null) {
				connexion.data("mediaValue", "1"); 
			}
			else { 
				logger.info("Aucun choix d'envoi du second code disponible");
				return null;
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
			pageSaisieCode = reponse.parse();
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
			return null;
		}
		return pageSaisieCode;
	}

	public boolean doubleAuthentification (String sid, String tformdata, String baseUri) {
		/*** Instaurer un contrôle pour mdp ***/
		String urlEnvoiCode = System.getenv("url_post_envoi_code");
		try {
			/*boolean reussite = false;
			int tentatives = 1;
			if (tentatives > 3) { throw new IOException("Trop de tentatives infructueuses"); }
			if (tentatives > 1) { System.out.println("Le code est incorrect"); }*/
			Connection formCode = Jsoup.connect(urlEnvoiCode);
			formCode.method(Connection.Method.POST)
				.data("sid", sid)
				/*.data("sid", pageSaisieCode
					.getElementsByAttributeValue("name", "sid")
					.first()
					.val())*/
				.data("t:formdata", tformdata)
				/*.data("t:formdata", pageSaisieCode
					.getElementsByAttributeValue("name", "t:formdata")
					.first()
					.val())*/
				.userAgent(userAgent)
				.cookies(cookies)
				.data("ipCode", mdp)
				.execute();
			Connection.Response reponse = formCode.response();
			//System.out.println(quat_rep.body());
			if (baseUri.equals(reponse.url().toString())) {
				return false;
			}
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
		}
		return true;
	}
}