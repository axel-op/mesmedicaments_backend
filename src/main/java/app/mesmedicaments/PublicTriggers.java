package app.mesmedicaments;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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

import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteDateMaj;
import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteMedicamentBelgique;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.unchecked.Unchecker;
import io.jsonwebtoken.JwtException;

public final class PublicTriggers {

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
					JSONObject corpsReponse = new JSONObject()
						.put("heure", LocalDateTime.now().toString())
						.put("dernieresMaj", new JSONObject()
							.put("bdpm", majBDPM.toString())
							.put("interactions", majInteractions.toString()));
					return construireReponse(HttpStatus.OK, corpsReponse, request);
				default:
					return construireReponse(HttpStatus.NOT_FOUND, request);
			}
			String corpsReponse = new BufferedReader(new InputStreamReader(
				getClass().getResourceAsStream(ressource), "UTF-8"))
				.lines()
				.collect(Collectors.joining(Utils.NEWLINE));
			return construireReponse(HttpStatus.OK, corpsReponse, request);
		} catch (Exception e) {
			Utils.logErreur(e, context.getLogger());
			return construireReponse(HttpStatus.INTERNAL_SERVER_ERROR, request);
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
		@BindingName("categorie") final String categorie,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
			String id = Authentification.getIdFromToken(accessToken);
			EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
			if (categorie.equalsIgnoreCase("obtenir")) {
				JSONObject corpsReponse = new JSONObject().put("medicamentsPerso",
					Utils.mapDatesCodesEnJsonDatesDetails(entiteU.getMedicamentsPersoMap(), logger));
				return construireReponse(HttpStatus.OK, corpsReponse, request);
			}
			JSONObject corpsRequete = new JSONObject(request.getBody().get());
			if (categorie.equalsIgnoreCase("ajouter")) {
				JSONArray medicaments = corpsRequete.getJSONArray("medicaments");
				for (int i = 0; i < medicaments.length(); i++) {
					long codeCis = medicaments.getJSONObject(i).getLong("codecis");
					entiteU.ajouterMedicamentPerso(codeCis);
				}
				entiteU.mettreAJourEntite();
				codeHttp = HttpStatus.OK;
			}
			else if (categorie.equalsIgnoreCase("retirer")) {
				JSONObject medicament = corpsRequete.getJSONObject("medicament");
				long codeCis = medicament.getLong("codecis");
				LocalDate date = LocalDate.parse(medicament.getString("dateAchat"), DateTimeFormatter.ISO_LOCAL_DATE);
				entiteU.retirerMedicamentPerso(codeCis, date);
				entiteU.mettreAJourEntite();
				codeHttp = HttpStatus.OK;
			}
			else throw new IllegalArgumentException("La catégorie de route n'existe pas");
		}
		catch (JSONException e) {
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (NoSuchElementException | IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, request);
	}

	@FunctionName("medicament")
	public HttpResponseMessage medicament (
		@HttpTrigger(
			name = "medicamentTrigger",
			authLevel = AuthorizationLevel.ANONYMOUS,
			methods = {HttpMethod.GET, HttpMethod.POST},
			route = "medicament/{code?}"
		) final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject reponse = new JSONObject();
		String[] parametres = request.getUri().getPath().split("/");
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			if (parametres.length == 3)
				reponse.put("medicament", Utils.medicamentFranceEnJsonDepreciee(
					EntiteMedicamentFrance.obtenirEntite(Long.parseLong(parametres[2])).get(), 
					logger
				));
			else {
				JSONObject med = new JSONObject(request.getBody().get()).getJSONObject("medicament");
				Pays pays = Pays.obtenirPays(med.getString("pays"));
				Long code = med.getLong("code");
				AbstractEntiteMedicament<? extends Presentation> entiteM = pays == Pays.France
					? EntiteMedicamentFrance.obtenirEntite(code).get()
					: EntiteMedicamentBelgique.obtenirEntite(code).get();
				reponse.put("medicament", Utils.medicamentEnJson(entiteM, logger));
			}
			codeHttp = HttpStatus.OK;
		}
		catch (IllegalArgumentException | NoSuchElementException e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (Exception e) {
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
			JSONArray resultats = EntiteCacheRecherche.obtenirResultatsCache(recherche, utiliserDepreciees(request));
			corpsReponse.put("resultats", resultats);
			logger.info(resultats.length() + " résultats trouvés");
		}	
		catch (IllegalArgumentException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (Exception e) {
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
		@BindingName("categorie") final String categorie,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		String accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			String id = Authentification.getIdFromToken(accessToken);
			if (categorie.equalsIgnoreCase("medicaments")) {
				Optional<EntiteConnexion> optEntiteC = EntiteConnexion.obtenirEntite(id);
				if (!optEntiteC.isPresent()) throw new IllegalArgumentException("Pas d'entité Connexion trouvée");
				EntiteConnexion entiteC = optEntiteC.get();
				if (Utils.dateToLocalDateTime(entiteC.getTimestamp()).isBefore(LocalDateTime.now().minusMinutes(30)))
					throw new IllegalArgumentException("Plus de 30 minutes se sont écoulées depuis la connexion");
				DMP dmp = new DMP(entiteC.getUrlFichierRemboursements(), entiteC.obtenirCookiesMap(), logger);
				Map<LocalDate, Set<Long>> medsParDate = dmp.obtenirMedicaments(logger);
				EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
				entiteU.ajouterMedicamentsDMP(medsParDate);
				entiteU.mettreAJourEntite();
				JSONObject medsEnJson = Utils.mapDatesCodesEnJsonDatesDetails(medsParDate, logger);
				corpsReponse.put("medicaments", medsEnJson);
				codeHttp = HttpStatus.OK;
			}
		}
		catch (JwtException | IllegalArgumentException e) {
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
			route = "interactions"
		) final HttpRequestMessage<Optional<String>> request,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			JSONObject corpsRequete = new JSONObject(request.getBody().get());
			Map<Pays, Set<Long>> codesParPays = new HashMap<>();
			boolean utiliserDepreciees = utiliserDepreciees(request);
			if (utiliserDepreciees)
				codesParPays.put(Pays.France, JSONArrays.toSetLong(corpsRequete.getJSONArray("medicaments")));
			else {
				JSONObject codesParPaysJson = corpsRequete.getJSONObject("medicaments");
				codesParPaysJson.keySet().forEach(k -> codesParPays.put(
					Pays.obtenirPays(k), 
					JSONArrays.toSetLong(codesParPaysJson.getJSONArray(k)))
				);
			}
			Set<? extends AbstractEntiteMedicament<? extends Presentation>> entitesMedicament = codesParPays
				.entrySet()
				.parallelStream()
				.flatMap(e -> {
					Pays pays = e.getKey();
					if (pays == Pays.France) 
						return EntiteMedicamentFrance.obtenirEntites(e.getValue(), logger).stream();
					else if (pays == Pays.Belgique) 
						return EntiteMedicamentBelgique.obtenirEntites(e.getValue(), logger).stream();
					else throw new NotImplementedException("Il n'est pas encore possible de détecter les interactions avec les médicaments de ce pays");
				})
				.collect(Collectors.toSet());
			Set<JSONObject> interactions = EntiteInteraction.obtenirInteractions(logger, entitesMedicament)
				.stream()
				.map(Unchecker.wrap(logger, (EntiteInteraction e) -> utiliserDepreciees 
					? Utils.interactionEnJsonDepreciee(e, logger)
					: Utils.interactionEnJson(e, logger)
				))
				.collect(Collectors.toSet());
			corpsReponse.put("interactions", interactions);
			codeHttp = HttpStatus.OK;
		}
		catch (IllegalArgumentException | JSONException | NoSuchElementException e) {
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (Exception e) {
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
		HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
		JSONObject corpsReponse = new JSONObject();
		Logger logger = context.getLogger();
		try {
			verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
			JSONObject corpsRequete = new JSONObject(request.getBody().get());
			if (etape == 1) { // Première étape de la connexion
				final String id = corpsRequete.getString("id");
				final String mdp = corpsRequete.getString("mdp");
				JSONObject resultat = new Authentification(logger, id).connexionDMP(mdp);
				if (!resultat.isNull(CLE_ERREUR_AUTH)) {
					codeHttp = resultat.get(CLE_ERREUR_AUTH).equals(ERR_INTERNE)
						? HttpStatus.INTERNAL_SERVER_ERROR
						: HttpStatus.CONFLICT;
				} else {
					codeHttp = HttpStatus.OK;
					corpsReponse.put(CLE_ENVOI_CODE, resultat.getString(CLE_ENVOI_CODE));
				}
			}
			else if (etape == 2) { // Deuxième étape de la connexion
				final String id = corpsRequete.getString("id");
				final Authentification auth = new Authentification(logger, id);
				String code = String.valueOf(corpsRequete.getInt("code"));
				JSONObject resultat = auth.doubleAuthentification(code);
				if (!resultat.isNull(CLE_ERREUR_AUTH)) codeHttp = HttpStatus.CONFLICT;
				else {
					EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntiteOuCreer(id, logger);
					corpsReponse.put("idAnalytics", entiteU.getIdAnalytics());
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
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.BAD_REQUEST;
		}
		catch (JwtException e) 
		{
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.UNAUTHORIZED;
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return construireReponse(codeHttp, corpsReponse, request);
	}

	private boolean utiliserDepreciees (HttpRequestMessage<Optional<String>> request) {
		int version = Integer.parseInt(request.getHeaders().getOrDefault(CLE_VERSION, "0"));
		return version < 25;
	}

	private HttpResponseMessage construireReponse (HttpStatus codeHttp, String corpsReponse, HttpRequestMessage<Optional<String>> request) {
		return request.createResponseBuilder(codeHttp)
			.header("Content-Type", "text/plain")
			.header("Content-Length", String.valueOf(corpsReponse.getBytes(StandardCharsets.UTF_8).length))
			.body(corpsReponse)
			.build();
	}

	private HttpResponseMessage construireReponse (HttpStatus codeHttp, JSONObject corpsReponse, HttpRequestMessage<Optional<String>> request) {
		corpsReponse.put("heure", obtenirHeure().toString());
		return request.createResponseBuilder(codeHttp)
			.header("Content-Type", "application/json")
			.header("Content-Length", String.valueOf(corpsReponse.toString().getBytes(StandardCharsets.UTF_8).length))
			.body(corpsReponse.toString())
			.build();
	}

	private HttpResponseMessage construireReponse (HttpStatus codeHttp, HttpRequestMessage<Optional<String>> request) {
		return construireReponse(codeHttp, new JSONObjectUneCle("heure", obtenirHeure().toString()), request);
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
				) return;
			}
			catch (DateTimeParseException e) {}
		}
		throw new IllegalArgumentException("Heure incorrecte");
	}
}