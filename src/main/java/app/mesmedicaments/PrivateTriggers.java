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
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.storage.StorageException;

import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.misesajour.MiseAJourFrance;
import app.mesmedicaments.misesajour.MiseAJourBelgique;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class PrivateTriggers {

	@FunctionName("nettoyageConnexions")
	public void nettoyageConnexions(
			@TimerTrigger(name = "nettoyageConnexionsTrigger", schedule = "0 */15 * * * *") final String timerInfo,
			final ExecutionContext context) {
		Logger logger = context.getLogger();
		LocalDateTime maintenant = LocalDateTime.now();
		try {
			for (EntiteConnexion entiteC : EntiteConnexion.obtenirToutesLesEntites()) {
				LocalDateTime heureEntite = LocalDateTime.ofInstant(entiteC.getTimestamp().toInstant(), Utils.TIMEZONE);
				if (heureEntite.isBefore(maintenant.minusHours(1))) {
					logger.info("EntiteConnexion supprimée : " + entiteC.getRowKey() + " (heure associée : "
							+ heureEntite.toString() + ")");
					entiteC.supprimerEntite();
				}
			}
		} catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
		}
	}

	@FunctionName("miseAJourBases")
	public HttpResponseMessage miseAJourBases(
			@HttpTrigger(name = "majTrigger", dataType = "string", authLevel = AuthorizationLevel.FUNCTION, methods = {
					HttpMethod.GET }, route = "miseAJourBases/{etape:int}") final HttpRequestMessage<Optional<String>> request,
			@BindingName("etape") int etape, final ExecutionContext context) {
		long startTime = System.currentTimeMillis();
		HttpStatus codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		String corps = "";
		Logger logger = context.getLogger();
		switch (etape) {
		case 1:
			if (MiseAJourFrance.handler(logger)) {
				codeHttp = HttpStatus.OK;
				corps = "Mise à jour des médicaments français et des substances terminée.";
			}
			break;
		case 2:
			if (MiseAJourClassesSubstances.handler(logger)) {
				codeHttp = HttpStatus.OK;
				corps = "Mise à jour des classes de substances terminée.";
			}
			break;
		case 3:
			if (MiseAJourInteractions.handler(logger)) {
				codeHttp = HttpStatus.OK;
				corps = "Mise à jour des interactions terminée.";
			}
			break;
		case 4:
			try {
				MiseAJourFrance.importerEffetsIndesirables(logger);
				codeHttp = HttpStatus.OK;
			} catch (StorageException | URISyntaxException | InvalidKeyException e) {
				Utils.logErreur(e, logger);
			}
			break;
		case 5:
			if (MiseAJourBelgique.handler(logger)) {
				codeHttp = HttpStatus.OK;
				corps = "Mise à jour des médicaments belges terminée.";
			}
			break;
		default:
			codeHttp = HttpStatus.BAD_REQUEST;
			corps = "Le paramètre maj de la requête n'est pas reconnu.";
		}
		return request.createResponseBuilder(codeHttp)
				.body(corps + " Durée totale : " + String.valueOf(System.currentTimeMillis() - startTime) + " ms")
				.build();
	}
}