package app.mesmedicaments;

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

import org.json.JSONObject;

import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class PrivateTriggers {

	@FunctionName("nouvellesConnexions")
	public void nouvellesConnexions (
		@QueueTrigger(
			name = "nouvellesConnexionsTrigger",
			queueName = "nouvelles-connexions",
			connection = "AzureWebJobsStorage"
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