package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
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

import org.json.JSONObject;

import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class PrivateTriggers {

	private static final String connectionStorage = "AzureWebJobsStorage";

	/*
	@FunctionName("indexation")
	public HttpResponseMessage indexation (
		@HttpTrigger(
			name = "indexationTrigger",
			methods = {HttpMethod.GET},
			authLevel = AuthorizationLevel.FUNCTION,
			route = "indexation/{etape:int}"
		) final HttpRequestMessage<Optional<String>> request,
		@BindingName("etape") final int etape,
		final ExecutionContext context
	) {
		Logger logger = context.getLogger();
		try {
			CloudFileShare fileShare = CloudStorageAccount.parse(System.getenv(connectionStorage))
				.createCloudFileClient()
				.getShareReference("filesharejava");
			fileShare.createIfNotExists();
			final String nomFichier = "indexationRecherche";
			if (etape == 1) {
				ConcurrentMap<String, JSONArray> index = new ConcurrentHashMap<>();
				Iterable<EntiteMedicament> entitesM = EntiteMedicament.obtenirToutesLesEntites();
				final int total = Iterables.size(entitesM);
				logger.info("Nombre d'entités : " + total);
				final AtomicInteger compteur = new AtomicInteger(0);
				StreamSupport.stream(entitesM.spliterator(), true)
					.forEach((entiteM) -> {
						try { 
							JSONObject medJson = Utils.medicamentEnJson(entiteM, logger);
							String entStr = entiteM
								.obtenirNomsJArray().join(" ") + " " 
								+ entiteM.getForme() + " "
								+ entiteM.getMarque();
							JSONObject substances = medJson.getJSONObject("substances");
							for (String key : substances.keySet()) {
								entStr += " " + substances.getJSONArray(key).join(" ");
							}
							entStr = Utils.normaliser(entStr)
								.toLowerCase()
								.replaceAll("[^\\p{IsAlphabetic}0-9]", " ");
							Sets.newHashSet(entStr.split(" ")).stream().parallel()
								.flatMap((terme) -> {
									Set<String> sousMots = new HashSet<>();
									for (int i = 0; i <= terme.length(); i++) {
										sousMots.add(terme.substring(0, i));
									}
									return sousMots.stream();
								})
								.forEach((sousMot) -> {
									if (!sousMot.equals("")) {
										index.computeIfAbsent(sousMot, k -> new JSONArray())
											.put(medJson);
									}
								});
						} catch (Exception e) { Utils.logErreur(e, logger); }
						logger.info("Entités analysées : " + compteur.incrementAndGet() + "/" + total);
					});
				File tempFile = File.createTempFile(nomFichier, null);
				//tempFile.deleteOnExit();
				//int lignes = index.keySet().size();
				int c = 0;
				try (BufferedWriter br = new BufferedWriter(new FileWriter(tempFile))) {
					for (Entry<String, JSONArray> entree : index.entrySet()) {
						br.write(c + "\t" + entree.getKey() + "\t" + entree.getValue().toString());
						br.newLine();
						c += 1;
						//logger.info(c + "/" + lignes + " lignes écrites");
					}
					logger.info("Envoi du fichier...");
					fileShare.getRootDirectoryReference()
						.getFileReference(nomFichier)
						.uploadFromFile(tempFile.getPath());
				}
				catch (Exception e) {
					logger.warning("Erreur lors de l'écriture du fichier");
				}
			}
			else if (etape == 2) {
				Set<String> erreurs = ConcurrentHashMap.newKeySet();
				final AtomicInteger compteur = new AtomicInteger(0);
				final ConcurrentMap<String, String> index = new ConcurrentHashMap<>();
				new BufferedReader(
					new InputStreamReader(
						new FileInputStream(
							new File("C:\\Users\\axelp\\AppData\\Local\\Temp\\indexationRecherche")
						)
						// fileShare.getRootDirectoryReference()
						//	.getFileReference(nomFichier)
						//	.openRead()
					)
				).lines()
					.parallel()
					.forEach((ligne) -> {
						//if (compteur.get() <= 10000) {
						//String[] entree = ligne.split("\t", 3);
						Iterator<String> iter = Splitter.on("\t").limit(3).split(ligne).iterator();
						iter.next();
						String terme = iter.next();
						try {
							EntiteCacheRecherche.mettreEnCache(terme, iter.next());
							//index.put(terme, entree[2]);
						} catch (Exception e) { 
							logger.warning("Erreur pour le terme " + terme);
							erreurs.add(terme);
							Utils.logErreur(e, logger); 
						}
						//logger.info("ligne " + entree[0] + " faite (" + compteur.incrementAndGet() + ")");
						//}
					});
				logger.info(erreurs.size() + " erreurs : ");
				erreurs.forEach((t) -> logger.info("\t" + t));
				//CloudFirestore.envoyerDocuments(index, logger);
			}
			else { return request.createResponseBuilder(HttpStatus.NOT_FOUND).build(); }
			return request.createResponseBuilder(HttpStatus.OK).build();
		}
		catch (Exception e) {
			Utils.logErreur(e, logger);
		}
		return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).build();
	}
	*/
	
	@FunctionName("nettoyageConnexions")
	public void nettoyageConnexions (
		@TimerTrigger(
			name = "nettoyageConnexionsTrigger",
			schedule = "0 0 */1 * * *"
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
}