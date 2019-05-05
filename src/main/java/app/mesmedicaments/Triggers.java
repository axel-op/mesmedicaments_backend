package app.mesmedicaments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.storage.StorageException;

import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;
import io.jsonwebtoken.JwtException;

public final class Triggers {

	private static final String CLE_CAUSE;
	private static final String CLE_HEURE;
	private static final String CLE_ERREUR_AUTH;
	private static final String CLE_ENVOI_CODE;
	private static final String CLE_PRENOM;
	private static final String CLE_EMAIL;
	private static final String CLE_GENRE;
	private static final String CLE_INSCRIPTION_REQUISE;
	private static final String ERR_INTERNE;
	private static final String HEADER_AUTHORIZATION;
	private static final String HEADER_DEVICEID;

	static {
		CLE_CAUSE = "cause";
		CLE_HEURE = "heure";
		CLE_ERREUR_AUTH = Authentification.CLE_ERREUR;
		CLE_ENVOI_CODE = Authentification.CLE_ENVOI_CODE;
		CLE_PRENOM = Authentification.CLE_PRENOM;
		CLE_GENRE = Authentification.CLE_GENRE;
		CLE_EMAIL = Authentification.CLE_EMAIL;
		CLE_INSCRIPTION_REQUISE = Authentification.CLE_INSCRIPTION_REQUISE;
		ERR_INTERNE = Authentification.ERR_INTERNE;
		HEADER_AUTHORIZATION = "jwt";
		HEADER_DEVICEID = "deviceid";
	}

	// TODO : ajouter un trigger de type Queue lorsque les utilisateurs se connectent pour effectuer une mise à jour de leurs médicaments
	@FunctionName("nouvelleConnexion")
	public void nouvelleConnexion (
		@QueueTrigger(
			name = "nouvelleConnexionTrigger",
			queueName = "nouvelles-connexions",
			connection = "AzureWebJobsStorage")
		final String message, 
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		try {
			Optional<EntiteConnexion> entiteC = EntiteConnexion.obtenirEntiteAboutie(message);
			if (entiteC.isPresent()) {
				DMP.majConnexion(entiteC.get(), logger);
			}
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
		}
	}

	@FunctionName("maintienConnexion")
	public void maintienConnexion (
		@TimerTrigger(
			name = "maintienConnexionTrigger",
			schedule = "0 */5 * * * *")
		final String timerInfo,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		logger.info("Timer info = " + timerInfo);
		int minuteArrondie = LocalDateTime.now().getMinute();
		int decalage = minuteArrondie % 5;
		if (decalage < 3) { minuteArrondie -= decalage; }
		else { minuteArrondie += 5 - decalage; }
		logger.info("Debug : minuteArrondie = " + minuteArrondie);
		// routine à effectuer en partant de minuteArrondie % 20 et de 20 en 20
		for (int i = minuteArrondie % 20; i < 60; i += 20) {
			String partition = String.valueOf(i);
			if (partition.length() == 1) { partition = "0" + partition; }
			logger.info("Debug : partition = " + partition);
			try { DMP.majConnexions(partition, logger); }
			catch (StorageException | URISyntaxException | InvalidKeyException e) {
				logger.warning("Echec de la MAJ pour la partition " + partition);
				Utils.logErreur(e, logger);
			}
		}
	}

	// mettre une doc
	@FunctionName("dmp")
	public HttpResponseMessage dmp (
		@HttpTrigger(
			name = "dmpTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "dmp/{categorie:alpha?}")
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		String[] parametres = request.getUri().getPath().split("/");
		String categorie = null;
		if (parametres.length > 3) { categorie = parametres[3]; }
		String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String id = Authentification.getIdFromToken(accessToken);
			DMP dmp = new DMP(id, context.getLogger());
			EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id);
			if (categorie != null) {
				if (categorie.equals("medicaments")) { 
					corpsReponse.put("medicaments", entiteU.obtenirMedicamentsJObject());
					codeHttp = HttpStatus.OK;
				} 
				else if (categorie.equals("interactions")) {
					//corpsReponse.put("interactions", dmp.obtenirInteractions()); 
					codeHttp = HttpStatus.NOT_IMPLEMENTED;
				}
				else { throw new IllegalArgumentException("Catégorie incorrecte"); }
			}
			else {
				/*corpsReponse
					.put("medicaments", entiteU.obtenirMedicamentsJObject())
					.put("interactions", dmp.obtenirInteractions());*/
				codeHttp = HttpStatus.NOT_IMPLEMENTED;
			}
		}
		catch (JwtException
			| IllegalArgumentException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (StorageException
			| URISyntaxException
			| InvalidKeyException e)
		{
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("produits")
	public HttpResponseMessage produits (
		@HttpTrigger(
			name = "produitsTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "produits/{categorie:alpha}/{codeproduit:int?}")
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		// Si codecis spécifié : fichier JSON sur le medicament
		// Sinon : liste des codecis associés à leur noms
		String[] parametres = request.getUri().getPath().split("/");
		String categorie = parametres[3];
		String codeProduit = null;
		if (parametres.length > 4) { codeProduit = parametres[4]; }
		String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (categorie == null) { throw new IllegalArgumentException(); }
			if (categorie.equals("medicaments")) {
				if (codeProduit != null) {
					EntiteMedicament entite = EntiteMedicament.obtenirEntite(
						Long.parseLong(codeProduit));
					if (entite == null) { throw new IllegalArgumentException("Ce médicament n'existe pas"); }
					JSONObject infosMed = new JSONObject();
					infosMed
						.put("noms", entite.getNoms())
						.put("forme", entite.getForme())
						.put("marque", entite.getMarque())
						.put("autorisation", entite.getAutorisation())
						.put("substances", entite.getSubstancesActives());
					corpsReponse.put(entite.getRowKey(), infosMed);
					codeHttp = HttpStatus.OK;
				} 
				else {
					JSONObject tousLesMeds = new JSONObject();
					int compteur = 0;
					for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
						tousLesMeds.put(entite.getRowKey(), entite.getNoms());
						compteur++;
					}
					corpsReponse.put("medicaments", tousLesMeds);
					corpsReponse.put("total", compteur);
					codeHttp = HttpStatus.OK;
				}
			}
		}
		catch (JwtException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
			corpsReponse.put(CLE_CAUSE, e.getMessage());
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, context.getLogger());
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("connexion")
	public HttpResponseMessage connexion (
		@HttpTrigger(
			name = "connexionTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.POST, HttpMethod.GET},
			dataType = "string",
			route = "connexion/{etape:int}")
		final HttpRequestMessage<Optional<String>> request,
		@BindingName("etape") int etape,
		final ExecutionContext context
	) {
		JSONObject corpsRequete = null;
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		JSONObject resultat = new JSONObject();
		Logger logger = context.getLogger();
		Authentification auth;
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (request.getHttpMethod() == HttpMethod.POST) {
				corpsRequete = new JSONObject(request.getBody().get());
			}
			final String deviceId = request.getHeaders().get(HEADER_DEVICEID);
			if (etape == 0) { // Renouvellement du token d'accès
				final String jwt = request.getHeaders().get(HEADER_AUTHORIZATION);
				if (Authentification.checkRefreshToken(jwt, deviceId)) { 
					final String id = Authentification.getIdFromToken(jwt);
					auth = new Authentification(logger, id);
					corpsReponse.put("accessToken", auth.createAccessToken()); 
					codeHttp = HttpStatus.OK;
				}
				else { throw new JwtException("Le token de rafraîchissement n'est pas valide"); }
			}
			else if (etape == 1) { // Première étape de la connexion
				final String id = corpsRequete.getString("id");
				final String mdp = corpsRequete.getString("mdp");
				auth = new Authentification(logger, id);
				resultat = auth.connexionDMP(mdp);
				if (!resultat.isNull(CLE_ERREUR_AUTH)) {
					codeHttp = HttpStatus.CONFLICT;
					corpsReponse.put(CLE_CAUSE, resultat.get(CLE_ERREUR_AUTH));
				} else {
					codeHttp = HttpStatus.OK;
					corpsReponse.put(CLE_ENVOI_CODE, resultat.getString(CLE_ENVOI_CODE));
				}
			}
			else if (etape == 2) { // Deuxième étape de la connexion
				final String id = corpsRequete.getString("id");
				auth = new Authentification(logger, id);
				String code = String.valueOf(corpsRequete.getInt("code"));
				resultat = auth.doubleAuthentification(code);
				if (!resultat.isNull(CLE_ERREUR_AUTH)) {
					codeHttp = HttpStatus.CONFLICT;
					corpsReponse.put(CLE_CAUSE, resultat.get(CLE_ERREUR_AUTH));
				} else {
					boolean inscriptionRequise = resultat.getBoolean(CLE_INSCRIPTION_REQUISE);
					codeHttp = HttpStatus.OK;
					corpsReponse
						.put(CLE_PRENOM, resultat.get(CLE_PRENOM))
						.put(CLE_EMAIL, resultat.get(CLE_EMAIL))
						.put(CLE_GENRE, resultat.get(CLE_GENRE))
						.put(CLE_INSCRIPTION_REQUISE, inscriptionRequise)
						.put("accessToken", auth.createAccessToken());
					if (!inscriptionRequise) {
						corpsReponse.put("refreshToken", auth.createRefreshToken(deviceId));
					}
				}
			} else { throw new IllegalArgumentException(); }
		}
		catch (JSONException 
			| NullPointerException 
			| NoSuchElementException
			| IllegalArgumentException e) 
		{
			codeHttp = HttpStatus.BAD_REQUEST;
			corpsReponse = new JSONObjectUneCle(CLE_CAUSE, e.getMessage());
		}
		catch (JwtException e) 
		{
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
			corpsReponse = new JSONObjectUneCle(CLE_CAUSE, ERR_INTERNE);
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("inscription")
	public HttpResponseMessage inscription (
		@HttpTrigger(
			name = "inscriptionTrigger",
			dataType = "string",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.POST})
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		Logger logger = context.getLogger();
		Authentification auth;
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			JSONObject corpsRequete = new JSONObject(request.getBody().get());
			final String jwt = request.getHeaders().get(HEADER_AUTHORIZATION);
			final String deviceId = request.getHeaders().get(HEADER_DEVICEID);
			final String id = Authentification.getIdFromToken(jwt);
			final String prenom = corpsRequete.getString("prenom");
			final String email = corpsRequete.getString("email");
			final String genre = corpsRequete.getString("genre");
			auth = new Authentification(logger, id);
			auth.inscription(prenom, email, genre);
			corpsReponse.put("refreshToken", auth.createRefreshToken(deviceId));
			codeHttp = HttpStatus.OK;
		}
		catch (NullPointerException 
			| IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (JSONException
			| NoSuchElementException
			| JwtException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
			corpsReponse = new JSONObjectUneCle(CLE_CAUSE, ERR_INTERNE);
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("mettreAJourBases")
	public HttpResponseMessage mettreAJourBases (
		@HttpTrigger(
			name = "majTrigger", 
			dataType = "string", 
			authLevel = AuthorizationLevel.FUNCTION,
			methods = {HttpMethod.GET},
			route = "mettreAJourBases/{etape:int}")
		final HttpRequestMessage<Optional<String>> request,
		@BindingName("etape") int etape,
		final ExecutionContext context
	) {
		long startTime = System.currentTimeMillis();
		HttpStatus codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		String corps = "";
		Logger logger = context.getLogger();
		switch (etape) {
			case 1:
				if (MiseAJourBDPM.majSubstances(logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour des substances terminée.";
				}
				break;
			case 2:
				if (MiseAJourBDPM.majMedicaments(logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour des médicaments terminée.";
				}
				break;
			case 3:
				if (MiseAJourClassesSubstances.handler(logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour des classes de substances terminée.";
				}
				break;
			case 4:
				if (MiseAJourInteractions.handler(logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour des interactions terminée.";
				}
				break;
			default:
				codeHttp = HttpStatus.BAD_REQUEST;
				corps = "Le paramètre maj de la requête n'est pas reconnu.";
		}
		return request.createResponseBuilder(codeHttp)
			.body(corps 
				+ " Durée totale : " 
				+ String.valueOf(System.currentTimeMillis() - startTime) 
				+ " ms")
			.build();
	}

	private HttpResponseMessage construireReponse (HttpStatus codeHttp, JSONObject corpsReponse, HttpRequestMessage<Optional<String>> request) {
		corpsReponse.put("heure", obtenirHeure().toString());
		return request.createResponseBuilder(codeHttp)
			.body(corpsReponse.toString())
			.build();
	}

	private LocalDateTime obtenirHeure () {
		return LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
	}

	private void verifierHeure (String heure, long intervalle) throws IllegalArgumentException {
		LocalDateTime heureObtenue;
		LocalDateTime maintenant = obtenirHeure();
		if (heure != null && heure.length() <= 50) {
			try {
				heureObtenue = LocalDateTime.parse(heure);
				if (heureObtenue.isAfter(maintenant.minusMinutes(intervalle))
					&& heureObtenue.isBefore(maintenant.plusMinutes(2))
				) { return; }
			}
			catch (DateTimeParseException e) {}
		}
		throw new IllegalArgumentException("Heure incorrecte");
	}
}