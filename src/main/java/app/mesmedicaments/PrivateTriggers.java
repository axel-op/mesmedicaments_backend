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

import org.json.JSONObject;

import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class PrivateTriggers {


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
		for (int i = minuteArrondie % 20; i < 60; i += 20) {
			String partition = String.valueOf(i);
			if (partition.length() == 1) { partition = "0" + partition; }
			logger.info("Debug : partition = " + partition);
			try {
				for (EntiteConnexion entiteC : EntiteConnexion.obtenirEntitesPartition(partition)) {
					if (entiteC.getMotDePasse() != null) {
						try { 
							JSONObject medicaments = new DMP(
								entiteC.getUrlFichierRemboursements(), 
								entiteC.obtenirCookiesMap(), 
								logger
							).obtenirMedicaments();
							EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(entiteC.getRowKey());
							if (!medicaments.isEmpty() || entiteU.getMedicaments() == null) {
								entiteU.definirMedicamentsJObject(medicaments);
								entiteU.mettreAJourEntite();
							}
						}
						catch (Exception e) {
							logger.info("Echec de la maj pour l'utilisateur " + entiteC.getRowKey());
							Utils.logErreur(e, logger);
							entiteC.marquerCommeEchouee();
							entiteC.mettreAJourEntite();
						}
					}
				}
			}
			catch (StorageException | URISyntaxException | InvalidKeyException e) {
				Utils.logErreur(e, logger);
				logger.info("Echec lors de la maj de la partition : " + partition);
			}
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