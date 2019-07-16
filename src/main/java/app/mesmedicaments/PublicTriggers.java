package app.mesmedicaments;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
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
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteDateMaj;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import io.jsonwebtoken.JwtException;

public final class PublicTriggers {

	private static final String CLE_CAUSE = "cause";
	private static final String CLE_HEURE = "heure";
	private static final String CLE_VERSION = "versionapplication";
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
		try {
			String ressource;
			switch (fichier) {
				case "confidentialite":
					ressource = "/PolitiqueConfidentialite.txt";
					break;
				case "mentions":
					ressource = "/MentionsLegales.txt";
					break;
				case "datesmaj":
					LocalDate majBDPM = EntiteDateMaj.obtenirDateMajBDPM().get();
					LocalDate majInteractions = EntiteDateMaj.obtenirDateMajInteractions().get();
					return request.createResponseBuilder(HttpStatus.OK)
						.body(new JSONObject()
							.put("heure", LocalDateTime.now().toString())
							.put("dernieresMaj", new JSONObject()
								.put("bdpm", majBDPM.toString())
								.put("interactions", majInteractions.toString())
							)
							.toString()
						)
						.build();
				default:
					return request.createResponseBuilder(HttpStatus.NOT_FOUND)
						.build();
			}
			return request.createResponseBuilder(HttpStatus.OK)
				.body(new BufferedReader(new InputStreamReader(
					getClass().getResourceAsStream(ressource), "UTF-8"))
					.lines()
					.collect(Collectors.joining(Utils.NEWLINE)))
				.build();
		} catch (Exception e) {
			Utils.logErreur(e, context.getLogger());
			return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				.build();
		}
	}

	@FunctionName("synchronisation")
	public HttpResponseMessage synchronisation (
		@HttpTrigger(
			name = "synchronisationTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.POST, HttpMethod.GET},
			route = "synchronisation/{categorie:alpha}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("categorie") String categorie,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
			String id = Authentification.getIdFromToken(accessToken);
			EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
			if (categorie.equals("ajouter")) {
				JSONObject corpsRequete = new JSONObject(request.getBody().get());
				JSONArray medicaments = corpsRequete.getJSONArray("medicaments");
				for (int i = 0; i < medicaments.length(); i++) {
					Long codeCis = medicaments.getJSONObject(i).getLong("codecis");
					entiteU.ajouterMedicamentPerso(codeCis);
				}
				entiteU.mettreAJourEntite();
				codeHttp = HttpStatus.OK;
			}
			else if (categorie.equals("retirer")) {
				JSONObject corpsRequete = new JSONObject(request.getBody().get());
				JSONObject medicament = corpsRequete.getJSONObject("medicament");
				Long codeCis = medicament.getLong("codecis");
				if (categorie.equals("retirer")) {
					LocalDate date = LocalDate.parse(medicament.getString("dateAchat"), DateTimeFormatter.ISO_LOCAL_DATE);
					entiteU.retirerMedicamentPerso(codeCis, date);
				}
				entiteU.mettreAJourEntite();
				codeHttp = HttpStatus.OK;
			}
			else if (categorie.equals("obtenir")) {
				JSONObject corpsReponse = new JSONObject().put(
					"medicamentsPerso",
					Utils.convertirJsonDatesCodesEnJsonDatesDetails(
						entiteU.obtenirMedicamentsPersoJObject(), 
						logger
					)
				);
				return request.createResponseBuilder(HttpStatus.OK)
					.body(corpsReponse.toString())
					.build();
			}
			else throw new IllegalArgumentException("La catégorie de route n'existe pas");
		}
		catch (JSONException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (NoSuchElementException | IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (StorageException | InvalidKeyException | URISyntaxException e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return request.createResponseBuilder(codeHttp).build();
	}

	@FunctionName("medicament")
	public HttpResponseMessage medicament (
		@HttpTrigger(
			name = "medicamentTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "medicament/{code:int}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("code") Integer codeCis,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
		JSONObject reponse = new JSONObject();
		try {
			reponse.put(
				"medicament", 
				Utils.medicamentEnJson(
					Utils.obtenirEntiteMedicament(codeCis.longValue()).get(), 
					logger
				)
			);
			codeHttp = HttpStatus.OK;
		}
		catch (IllegalArgumentException | NoSuchElementException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, reponse, request);
	}

	@FunctionName("recherche")
	public HttpResponseMessage recherche (
		@HttpTrigger(
			name = "rechercheTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET},
			route = "recherche/{recherche}"
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
			route = "dmp/{categorie:alpha}")
		final HttpRequestMessage<Optional<String>> request,
		@BindingName("categorie") String categorie,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String id = Authentification.getIdFromToken(accessToken);
			if (categorie.equals("medicaments")) {
				Optional<EntiteConnexion> optEntiteC = EntiteConnexion.obtenirEntite(id);
				if (!optEntiteC.isPresent()) throw new IllegalArgumentException("Pas d'entité Connexion trouvée");
				EntiteConnexion entiteC = optEntiteC.get();
				if (Utils.dateToLocalDateTime(entiteC.getTimestamp()).isBefore(LocalDateTime.now().minusMinutes(30)))
					throw new IllegalArgumentException("Plus de 30 minutes se sont écoulées depuis la connexion");
				DMP dmp = new DMP(entiteC.getUrlFichierRemboursements(), entiteC.obtenirCookiesMap(), logger);
				JSONObject medicaments = dmp.obtenirMedicaments(logger);
				EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
				entiteU.ajouterMedicamentsDMPJObject(medicaments, DateTimeFormatter.ISO_LOCAL_DATE);
				entiteU.mettreAJourEntite();
				JSONObject medsEnJson = Utils.convertirJsonDatesCodesEnJsonDatesDetails(medicaments, logger);
				corpsReponse.put("medicaments", medsEnJson);
				codeHttp = HttpStatus.OK;
			}
		}
		catch (JwtException
			| IllegalArgumentException e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (Exception e)
		{
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	@FunctionName("interactions")
	public HttpResponseMessage interactions (
		@HttpTrigger(
			name = "interactionsTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET, HttpMethod.POST},
			route = "interactions/{codecis1:int?}/{codecis2:int?}"
		) final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String[] parametres = request.getUri().getPath().split("/");
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (parametres.length == 2) {
				JSONArray interactions = new JSONArray();
				Set<Long> codesCis = new HashSet<>();
				Utils.ajouterTousLong(codesCis, new JSONObject(request.getBody().get())
					.getJSONArray("medicaments"));
				Set<Set<Long>> combinaisons = Sets.combinations(codesCis, 2);
				combinaisons.stream().parallel()
					.map((comb) -> comb.toArray(new Long[2]))
					.map((comb) -> {
						try {
							EntiteMedicament entiteM1 = Utils.obtenirEntiteMedicament(comb[0]).get();
							EntiteMedicament entiteM2 = Utils.obtenirEntiteMedicament(comb[1]).get();
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
			else if (parametres.length == 4) {
				long codeCis1 = Long.parseLong(parametres[2]);
				long codeCis2 = Long.parseLong(parametres[3]);
				EntiteMedicament entiteMed1 = Utils.obtenirEntiteMedicament(codeCis1).get();
				EntiteMedicament entiteMed2 = Utils.obtenirEntiteMedicament(codeCis2).get();
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

	@FunctionName("connexion")
	public HttpResponseMessage connexion (
		@HttpTrigger(
			name = "connexionTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.POST},
			dataType = "string",
			route = "connexion/{etape:int}"
		) final HttpRequestMessage<Optional<String>> request,
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
			corpsRequete = new JSONObject(request.getBody().get());
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
					Optional<EntiteUtilisateur> optEntiteU = EntiteUtilisateur.obtenirEntite(id);
					if (!optEntiteU.isPresent()) {
						optEntiteU = Optional.of(new EntiteUtilisateur(id));
						optEntiteU.get().creerEntite();
					}
					corpsReponse.put("idAnalytics", optEntiteU.get().getIdAnalytics());
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