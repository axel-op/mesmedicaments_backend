package app.mesmedicaments;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.QueueOutput;
import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import io.jsonwebtoken.JwtException;

public final class PublicTriggers {

	private static final String CLE_CAUSE = "cause";
	private static final String CLE_HEURE = "heure";
	private static final String CLE_ERREUR_AUTH = Authentification.CLE_ERREUR;
	private static final String CLE_ENVOI_CODE = Authentification.CLE_ENVOI_CODE;
	private static final String ERR_INTERNE = Authentification.ERR_INTERNE;
	private static final String HEADER_AUTHORIZATION = "jwt";

	@FunctionName("legal")
	public HttpResponseMessage legal (
		@HttpTrigger(
			name = "legalTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "legal/{fichier}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("fichier") String fichier,
		final ExecutionContext context
	) {
		String ressource;
		switch (fichier) {
			case "confidentialite":
				ressource = "/PolitiqueConfidentialite.txt";
				break;
			default:
				return request.createResponseBuilder(HttpStatus.NOT_FOUND)
					.build();
		}
		try {
			return request.createResponseBuilder(HttpStatus.OK)
				.body(new BufferedReader(new InputStreamReader(
					getClass().getResourceAsStream(ressource), "UTF-8"))
					.lines()
					.collect(Collectors.joining(Utils.NEWLINE)))
				.build();
		} catch (UnsupportedEncodingException e) {
			Utils.logErreur(e, context.getLogger());
			return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				.build();
		}
	}

	@FunctionName("recherche")
	public HttpResponseMessage recherche (
		@HttpTrigger(
			name = "rechercheTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "api/recherche/{recherche}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("recherche") String recherche,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.OK;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (recherche.length() > 100) throw new IllegalArgumentException();
			recherche = Utils.normaliser(recherche).toLowerCase();
			logger.info("Recherche de \"" + recherche + "\"");
			JSONArray resultats = EntiteCacheRecherche.obtenirResultatsCache(recherche);
			corpsReponse.put("resultats", resultats);
			logger.info(resultats.length() + " résultats trouvés");
		}	
		catch (IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (StorageException 
			| URISyntaxException 
			| InvalidKeyException e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
		
	}

	// mettre une doc
	@FunctionName("dmp")
	public HttpResponseMessage dmp (
		@HttpTrigger(
			name = "dmpTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "api/dmp/{categorie:alpha}")
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
			if (categorie.equals("medicaments")) { 
				Optional<EntiteUtilisateur> optEntiteU = Optional.empty();
				Optional<JSONObject> optMedicaments = Optional.empty();
				long startTime = System.currentTimeMillis();
				while ((!optEntiteU.isPresent() || !optMedicaments.isPresent()) 
					&& System.currentTimeMillis() - startTime < 70000
				) {
					optEntiteU = EntiteUtilisateur.obtenirEntite(id);
					if (optEntiteU.isPresent()) {
						optMedicaments = optEntiteU.get().obtenirMedicamentsJObject();
					}
					TimeUnit.MILLISECONDS.sleep(500);
				}
				if (!optMedicaments.isPresent()) { 
					throw new Exception("Impossible de récupérer les médicaments de l'utilisateur"); 
				}
				corpsReponse.put("medicaments", optMedicaments.orElseGet(() -> new JSONObject()));
				codeHttp = HttpStatus.OK;
			}
		}
		catch (JwtException
			| IllegalArgumentException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (Exception e)
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
			methods = {HttpMethod.GET, HttpMethod.POST},
			route = "api/interaction/{codecis1:int?}/{codecis2:int?}"
		) final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String[] parametres = request.getUri().getPath().split("/");
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (parametres.length == 3) {
				JSONArray interactions = new JSONArray();
				Set<Long> codesCis = new HashSet<>();
				JSONArray codes = new JSONObject(request.getBody().get())
					.getJSONArray("medicaments");
				for (int i = 0; i < codes.length(); i++) codesCis.add(codes.getLong(i));
				Set<Set<Long>> combinaisons = Sets.combinations(codesCis, 2);
				combinaisons.stream().parallel()
					.map((comb) -> comb.toArray(new Long[2]))
					.map((comb) -> {
						try {
							EntiteMedicament entiteM1 = Utils.obtenirEntiteMedicament(comb[0]);
							EntiteMedicament entiteM2 = Utils.obtenirEntiteMedicament(comb[1]);
							if (entiteM1 == null || entiteM2 == null) 
								throw new RuntimeException("Codes CIS incorrects " + comb[0] + comb[1]);
							return Utils.obtenirInteractions(entiteM1, entiteM2, logger); 
						}
						catch (StorageException | InvalidKeyException | URISyntaxException e) {
							Utils.logErreur(e, logger);
							throw new RuntimeException();
						}
					})
					.forEach((ja) -> ja.forEach((i) -> interactions.put(i)));
				corpsReponse.put("interactions", interactions);
				codeHttp = HttpStatus.OK;
			}
			else if (parametres.length == 5) {
				long codeCis1 = Long.parseLong(parametres[3]);
				long codeCis2 = Long.parseLong(parametres[4]);
				EntiteMedicament entiteMed1 = Utils.obtenirEntiteMedicament(codeCis1);
				EntiteMedicament entiteMed2 = Utils.obtenirEntiteMedicament(codeCis2);
				if (entiteMed1 == null || entiteMed2 == null)
					throw new IllegalArgumentException("Codes CIS incorrects" + codeCis1 + codeCis2);
				corpsReponse.put("interactions", Utils.obtenirInteractions(entiteMed1, entiteMed2, logger));
				codeHttp = HttpStatus.OK;
			}
		}
		catch (IllegalArgumentException | JSONException | NoSuchElementException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
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
			route = "api/produits/{categorie:alpha}/{codeproduit:int?}")
		final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String[] parametres = request.getUri().getPath().split("/");
		String categorie = parametres[3];
		String codeProduit = null;
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (parametres.length > 4) { 
				codeProduit = parametres[4]; 
				if (codeProduit.length() > 10) throw new IllegalArgumentException("Code produit invalide"); 
			}
			if (categorie.equals("substances")) {
				if (codeProduit == null) codeHttp = HttpStatus.FORBIDDEN;
				else {
					EntiteSubstance entite = EntiteSubstance
						.obtenirEntite(Long.parseLong(codeProduit))
						.get();
					JSONObject infosSub = new JSONObject().put("noms", entite.obtenirNomsJArray());
					corpsReponse.put(entite.getRowKey(), infosSub);
					codeHttp = HttpStatus.OK;
				}
			}
			if (categorie.equals("medicaments")) {
				if (codeProduit != null) {
					EntiteMedicament entiteM = Utils.obtenirEntiteMedicament(Long.parseLong(codeProduit));
					if (entiteM == null) throw new IllegalArgumentException("Code CIS incorrect");
					corpsReponse.put(entiteM.getRowKey(), Utils.medicamentEnJson(entiteM, logger));
					codeHttp = HttpStatus.OK;
				} 
				else {
					/*JSONObject tousLesMeds = new JSONObject();
					int compteur = 0;
					for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
						tousLesMeds.put(entite.getRowKey(), entite.getNoms());
						compteur++;
					}
					corpsReponse.put("medicaments", tousLesMeds);
					corpsReponse.put("total", compteur);*/
					codeHttp = HttpStatus.FORBIDDEN;
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
			Utils.logErreur(e, logger);
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
			route = "api/connexion/{etape:int}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("etape") int etape,
		@QueueOutput(
			name = "connexionQueueOutput",
			queueName = "nouvelles-connexions",
			connection = "AzureWebJobsStorage"
		) final OutputBinding<String> queue,
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
			if (etape == 1) { // Première étape de la connexion
				final String id = corpsRequete.getString("id");
				final String mdp = corpsRequete.getString("mdp");
				auth = new Authentification(logger, id);
				resultat = auth.connexionDMP(mdp);
				if (!resultat.isNull(CLE_ERREUR_AUTH)) {
					codeHttp = HttpStatus.CONFLICT;
					if (resultat.get(CLE_ERREUR_AUTH).equals("interne")) {
						codeHttp = HttpStatus.INTERNAL_SERVER_ERROR; // TODO à revoir
					}
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
					logger.info("Ajout de la connexion à la file nouvelles-connexions");
					queue.setValue(new JSONObject().put("id", id).toString());
					corpsReponse.put("accessToken", auth.createAccessToken());
					if (resultat.has("genre")) corpsReponse.put("genre", resultat.get("genre"));
					codeHttp = HttpStatus.OK;
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