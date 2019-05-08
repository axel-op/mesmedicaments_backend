package app.mesmedicaments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import io.jsonwebtoken.JwtException;

public final class PublicTriggers {

	private static final String CLE_CAUSE = "cause";
	private static final String CLE_HEURE = "heure";
	private static final String CLE_ERREUR_AUTH = Authentification.CLE_ERREUR;
	private static final String CLE_ENVOI_CODE = Authentification.CLE_ENVOI_CODE;
	private static final String CLE_PRENOM = Authentification.CLE_PRENOM;
	private static final String CLE_EMAIL = Authentification.CLE_EMAIL;
	private static final String CLE_GENRE = Authentification.CLE_GENRE;
	private static final String CLE_INSCRIPTION_REQUISE = Authentification.CLE_INSCRIPTION_REQUISE;
	private static final String CLE_MEMORISER_ID = "memorisermdp";
	private static final String ERR_INTERNE = Authentification.ERR_INTERNE;
	private static final String HEADER_AUTHORIZATION = "jwt";
	private static final String HEADER_DEVICEID = "deviceid";

	// mettre une doc
	@FunctionName("utilisateur")
	public HttpResponseMessage dmp (
		@HttpTrigger(
			name = "utilisateurTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "utilisateur/{categorie:alpha}")
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String[] parametres = request.getUri().getPath().split("/");
		String categorie = null;
		if (parametres.length > 3) { categorie = parametres[3]; }
		String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String id = Authentification.getIdFromToken(accessToken);
			EntiteConnexion entiteC = EntiteConnexion.obtenirEntiteAboutie(id).get(); // TODO : gérer le cas où Optional est vide
			DMP dmp = new DMP(
				entiteC.getUrlFichierRemboursements(), 
				entiteC.obtenirCookiesMap(), 
				logger
			);
			EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id);
			if (categorie.equals("medicaments")) { 
				Optional<JSONObject> medicaments = entiteU.obtenirMedicamentsJObject();
				if (!medicaments.isPresent()) {
					medicaments = Optional.of(dmp.obtenirMedicaments());
					entiteU.definirMedicamentsJObject(medicaments.get());
					entiteU.mettreAJourEntite();
				}
				corpsReponse.put("medicaments", medicaments.orElseGet(() -> new JSONObject()));
				codeHttp = HttpStatus.OK;
			} 
			/*
			if (categorie == null || categorie.equals("interactions")) {
				// ajouter une fois implémenté
				//corpsReponse.put("interactions", dmp.obtenirInteractions()); 
				codeHttp = HttpStatus.NOT_IMPLEMENTED;
			}
			else { throw new IllegalArgumentException("Catégorie incorrecte"); }
			*/
		}
		catch (JwtException
			| IllegalArgumentException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (StorageException
			| URISyntaxException
			| InvalidKeyException
			| IOException e)
		{
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("interaction")
	public HttpResponseMessage interaction (
		@HttpTrigger(
			name = "interactionTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "interaction/{codecis1:int}/{codecis2:int}")
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		String[] parametres = request.getUri().getPath().split("/");
		long codeCis1 = Long.parseLong(parametres[3]);
		long codeCis2 = Long.parseLong(parametres[4]);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			if (codeCis1 > 99999999 || codeCis2 > 99999999 ) {
				throw new IllegalArgumentException("Codes CIS invalides");
			}
			EntiteMedicament entiteMed1 = EntiteMedicament.obtenirEntite(codeCis1).get();
			EntiteMedicament entiteMed2 = EntiteMedicament.obtenirEntite(codeCis2).get();
			Function<JSONArray, Set<Integer>> jArrayToSet = jArray -> jArray
				.toList()
				.stream()
				.mapToInt(obj -> (int) obj)
				.boxed()
				.collect(Collectors.toSet());
			Set<Integer> substances1 = jArrayToSet.apply(entiteMed1.obtenirSubstancesActivesJArray());
			Set<Integer> substances2 = jArrayToSet.apply(entiteMed2.obtenirSubstancesActivesJArray());
			JSONArray interactions = new JSONArray();
			for (int codeSubstance1 : substances1) {
				for(int codeSubstance2 : substances2) {
					EntiteInteraction entiteInt = EntiteInteraction.obtenirEntite(codeSubstance1, codeSubstance2);
					if (entiteInt != null) {
						interactions.put(
							new JSONObject()
							.put("substances", new JSONArray().put(codeSubstance1).put(codeSubstance2))
							.put("risque", entiteInt.getRisque())
							.put("descriptif", entiteInt.getDescriptif())
							.put("conduite", entiteInt.getConduite())
						);
					}
				}
			}
			corpsReponse.put("interactions", interactions);
			codeHttp = HttpStatus.OK;
		}
		catch (NoSuchElementException e) {
			codeHttp = HttpStatus.NOT_FOUND;
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, context.getLogger());
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
		String[] parametres = request.getUri().getPath().split("/");
		String categorie = parametres[3];
		String codeProduit = null;
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (parametres.length > 4) { 
				codeProduit = parametres[4]; 
				if (codeProduit.length() > 10) { 
					throw new IllegalArgumentException("Code produit invalide"); 
				}
			}
			if (categorie == null) { throw new IllegalArgumentException(); }
			if (categorie.equals("substances")) {
				if (codeProduit == null) { codeHttp = HttpStatus.FORBIDDEN; }
				else {
					EntiteSubstance entite = EntiteSubstance
						.obtenirEntite(Long.parseLong(codeProduit))
						.get();
					JSONObject infosSub = new JSONObject()
						.put("noms", entite.obtenirNomsJArray());
					corpsReponse.put(entite.getRowKey(), infosSub);
					codeHttp = HttpStatus.OK;
				}
			}
			if (categorie.equals("medicaments")) {
				if (codeProduit != null) {
					EntiteMedicament entite = EntiteMedicament
						.obtenirEntite(Long.parseLong(codeProduit))
						.get();
					JSONArray codesSub = entite.obtenirSubstancesActivesJArray();
					JSONArray nomsSub = new JSONArray();
					for (int i = 0; i < codesSub.length(); i++) {
						EntiteSubstance entiteS = EntiteSubstance
							.obtenirEntite(codesSub.getLong(i))
							.get();
						entiteS.obtenirNomsJArray()
							.forEach((nom) -> nomsSub.put(nom));
					}
					JSONObject infosMed = new JSONObject()
						.put("noms", entite.getNoms())
						.put("forme", entite.getForme())
						.put("marque", entite.getMarque())
						.put("autorisation", entite.getAutorisation())
						.put("substances", nomsSub);
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
		catch (NoSuchElementException e) {
			codeHttp = HttpStatus.NOT_FOUND;
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
				resultat = auth.connexionDMP(mdp, corpsRequete.getBoolean(CLE_MEMORISER_ID));
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