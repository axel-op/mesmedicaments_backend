package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

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
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class PrivateTriggers {

	private static final String connectionStorage = "AzureWebJobsStorage";

	@FunctionName("indexationAutomatique")
	public void indexationAutomatique (
		@QueueTrigger(
			name = "indexationAutomatiqueTrigger",
			connection = connectionStorage,
			queueName = "indexation-automatique"
		) String message,
		@QueueOutput(
			name = "indexationAutomatiqueQueueOutput",
			connection = connectionStorage,
			queueName = "cache-recherche"
		) final OutputBinding<String> queueCache,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		logger.info("Indexation de \"" + message + "\"");
		try {
			message = Utils.normaliser(message)
				.replaceAll("[^\\p{IsAlphabetic}0-9]", " ")
				.toLowerCase();
			for (String terme : message.split(" ")) {
				Optional<EntiteCacheRecherche> optCache = EntiteCacheRecherche.obtenirEntite(terme);
				if (!optCache.isPresent() || optCache.get().obtenirResultatsJArray().length() < 10) {
					JSONArray resultats = Recherche.rechercher(terme, logger);
					if (resultats.length() > 0) {
						queueCache.setValue(new JSONObject()
							.put("recherche", terme)
							.put("resultats", resultats)
							.toString()
						);
					}
				}
			}
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
		}
	}

	@FunctionName("cacheRecherche")
	public void cacheRecherche (
		@QueueTrigger(
			name = "cacheRechercheTrigger",
			connection = connectionStorage,
			queueName = "cache-recherche"
		) final String message,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		logger.info("Mise en cache de " + message);
		JSONObject jsonObj = new JSONObject(message);
		String recherche = jsonObj.getString("recherche");
		String resultats = jsonObj.getJSONArray("resultats").toString();
		try {
			EntiteCacheRecherche entite = EntiteCacheRecherche.obtenirEntite(recherche)
				.orElse(new EntiteCacheRecherche(recherche));
			entite.setNombre(entite.getNombre() + 1);
			entite.setResultats(resultats);
			entite.mettreAJourEntite();
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			logger.warning("Impossible de mettre en cache les résultats de la recherche : " + recherche
				+ "\n (résultats : " + resultats + ")"
			);
			Utils.logErreur(e, logger);
		}
	}

	@FunctionName("nettoyageConnexions")
	public void nettoyageConnexions (
		@TimerTrigger(
			name = "nettoyageConnexionsTrigger",
			schedule = "0 */10 * * * *"
		) final String timerInfo,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		LocalDateTime maintenant = LocalDateTime.now();
		try {
			for (EntiteConnexion entiteC : EntiteConnexion.obtenirToutesLesEntites()) {
				LocalDateTime heureEntite = LocalDateTime.ofInstant(
					entiteC.getTimestamp().toInstant(), Utils.TIMEZONE);
				if (heureEntite.isBefore(maintenant.minusHours(1))) {
					logger.info("EntiteConnexion supprimée : "
						+ entiteC.getRowKey()
						+ " (heure associée : "
						+ heureEntite.toString() + ")"
					);
					entiteC.supprimerEntite();
				}
			}
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
		}
	}

	@FunctionName("nouvelleConnexion")
	public void nouvelleConnexion (
		@QueueTrigger(
			name = "nouvelleConnexionTrigger",
			queueName = "nouvelles-connexions",
			connection = connectionStorage
		) final String message,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		logger.info("Message reçu : " + message);
		try {
			String id = new JSONObject(message).getString("id");
			logger.info("Récupération de l'EntiteConnexion...");
			EntiteConnexion entiteC = EntiteConnexion.obtenirEntite(id).get();
			DMP dmp = new DMP(
				entiteC.getUrlFichierRemboursements(),
				entiteC.obtenirCookiesMap(),
				logger
			);
			logger.info("Récupération des médicaments...");
			JSONObject medicaments = dmp.obtenirMedicaments();
			logger.info("Récupération de l'EntiteUtilisateur...");
			Optional<EntiteUtilisateur> optEntiteU = EntiteUtilisateur.obtenirEntite(id);
			EntiteUtilisateur entiteU;
			if (!optEntiteU.isPresent()) {
				logger.info("Utilisateur " + id + " non trouvé, va être créé");
				entiteU = new EntiteUtilisateur(id);
			}
			else { entiteU = optEntiteU.get(); }
			logger.info("Ajout des médicaments à l'utilisateur");
			entiteU.ajouterMedicamentsJObject(medicaments);
			entiteU.mettreAJourEntite();
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
			logger.warning("Impossible de récupérer les médicaments");
		}
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
		@QueueOutput(
			name = "mettreAJourBasesQueueOutput",
			connection = connectionStorage,
			queueName = "indexation-automatique"
		) final OutputBinding<List<String>> queue,
		@BindingName("etape") int etape,
		final ExecutionContext context
	) {
		long startTime = System.currentTimeMillis();
		HttpStatus codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		String corps = "";
		Logger logger = context.getLogger();
		Set<String> pourFile = new HashSet<>();
		//queue.setValue(new ArrayList<>());
		switch (etape) {
			case 1:
				if (MiseAJourBDPM.majSubstances(logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour des substances terminée.";
				}
				break;
			case 2:
				if (MiseAJourBDPM.majMedicaments(logger, pourFile)) {
					queue.setValue(new ArrayList<>(pourFile));
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
			/*case 5:
				if (mettreAJourCache(queue, logger)) {
					codeHttp = HttpStatus.OK;
					corps = "Mise à jour du cache pour la recherche terminée.";
				}
				break;*/
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

	/*
	private boolean mettreAJourCache (Logger logger) {
		logger.info("Mise à jour du cache pour la recherche");
		try {
			Set<String> noms = new HashSet<>();
			for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
				for (Object object : entite.obtenirNomsJArray()) {
					String nom = ((String) object).split(" ")[0];
					//if (!EntiteCacheRecherche.obtenirEntite(nom).isPresent()) {
						noms.add(Utils.normaliser(nom)
							.toLowerCase()
							.replaceAll("^\\p{IsAlphabetic}", "")
						);
					//}
				}
			}
			int total = noms.size();
			logger.info(total + " termes à mettre en cache");
			Map<String, Set<EntiteMedicament>> aCacher = new HashMap<>();
			for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
				for (String nom : noms) {
					if (Utils.normaliser(entite.getNoms() + " " + entite.getForme())
						.toLowerCase()
						.contains(nom)
					) {
						Set<EntiteMedicament> set = aCacher.computeIfAbsent(nom, k -> new HashSet<>());
						if (set.size() < 10) { set.add(entite); }
					}
				}
			}
			AtomicInteger compteur = new AtomicInteger(1);
			aCacher.entrySet().stream().parallel()
				.forEach((entree) -> {
					try {
						JSONArray resultats = new JSONArray();
						for (EntiteMedicament entite : entree.getValue()) {
							resultats.put(Utils.medicamentEnJson(entite, logger));
						}
						logger.info(compteur + "/" + total);
						EntiteCacheRecherche eC = new EntiteCacheRecherche(entree.getKey());
						eC.setResultats(resultats.toString());
						eC.creerEntite();
						compteur.incrementAndGet();
					}
					catch (StorageException | URISyntaxException | InvalidKeyException e) {
						Utils.logErreur(e, logger);
						throw new RuntimeException();
					}
			});
			return true;
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
			return false;
		}
	}
    */
}