package app.mesmedicaments;

import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import app.mesmedicaments.connexion.*;
import app.mesmedicaments.misesajour.*;

public class Triggers {

    private static Logger logger;

    //private Triggers () {}

    @FunctionName("connexion")
    public HttpResponseMessage connexion (
        @HttpTrigger(
            name = "connexionTrigger",
            //dataType = "",
            dataType = "",
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.POST})
        final HttpRequestMessage<Optional<Identifiants>> request,
        final ExecutionContext context
    ) {
        HttpStatus codeHttp = null;
        String corps = "";
        logger = context.getLogger();
        /*** Ajouter des contrôles ici ***/
        codeHttp = HttpStatus.OK;
        if (!request.getBody().isPresent()) {
            codeHttp = HttpStatus.BAD_REQUEST;
        }
        else {
            Boolean doubleAuthentification = null;
            String parametre = request
                .getQueryParameters()
                .get("double");
            if (parametre == null) { codeHttp = HttpStatus.BAD_REQUEST; }
            else { parametre = parametre.toLowerCase(); }
            if (parametre.equals("true")) { doubleAuthentification = true; }
            else if (parametre.equals("false")) { doubleAuthentification = false; }
            if (doubleAuthentification == null) { 
                codeHttp = HttpStatus.BAD_REQUEST;
                corps = "Pas de paramètre spécifié pour la DA";
            }
            else {
                Identifiants identifiants = request.getBody().get();
                if (identifiants.id == null || identifiants.mdp == null) {
                    codeHttp = HttpStatus.BAD_REQUEST;
                    corps = "Mauvais format des identifiants";
                }
                else {
                    System.out.println("ID : " + identifiants.id);
                    System.out.println("CODE : " + identifiants.mdp);
                    Authentification session = new Authentification(
                        logger,
                        identifiants.id, 
                        identifiants.mdp);
                    if (!doubleAuthentification) { session.connexionDMP(); }
                    else { 
                        String baseUri = request
                            .getQueryParameters()
                            .get("baseUri");
                        String sid = request
                            .getQueryParameters()
                            .get("sid");
                        String tformdata = request
                            .getQueryParameters()
                            .get("tformdata");
                        if (baseUri == null
                            || sid == null
                            || tformdata == null)
                        { 
                            codeHttp = HttpStatus.BAD_REQUEST;
                            corps = "Mauvais format pour la DA";
                        }
                        else { 
                            session.doubleAuthentification(sid, tformdata, baseUri);
                            codeHttp = HttpStatus.OK;
                        }
                    }
                }
            }
        }
        return request.createResponseBuilder(codeHttp)
            .body(corps)
            .build();
    }

    @FunctionName("mettreAJourBases")
    public HttpResponseMessage mettreAJourBases (
        @HttpTrigger(
            name = "majTrigger", 
            dataType = "string", 
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.GET})
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
		long startTime = System.currentTimeMillis();
		HttpStatus codeHttp = null;
		String corps = "";
        logger = context.getLogger();
        String parametre = request
            .getQueryParameters()
            .get("maj")
            .toLowerCase();
        if (parametre == null) {
            codeHttp = HttpStatus.BAD_REQUEST;
            corps = "La mise à jour à effectuer doit être précisée en paramètre de la requête.";
        }
        switch (parametre) {
            case "1":
            case "bdpm":
                if (MiseAJourBDPM.handler(logger)) {
                    codeHttp = HttpStatus.OK;
                    corps = "Mise à jour BDPM terminée.";
                }
                break;
            case "2":
            case "classes":
            case "classessubstances":
                if (MiseAJourClassesSubstances.handler(logger)) {
                    codeHttp = HttpStatus.OK;
                    corps = "Mise à jour des classes de substances terminée.";
                }
                break;
            case "3":
            case "interactions":
                if (MiseAJourInteractions.handler(logger)) {
                    codeHttp = HttpStatus.OK;
                    corps = "Mise à jour des interactions terminée.";
                }
                break;
            default:
                codeHttp = HttpStatus.BAD_REQUEST;
                corps = "Le paramètre maj de la requête n'est pas reconnu.";
        }
        if (codeHttp == null) { 
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR; 
            corps = "Un problème est survenu pendant la mise à jour. Voir les journaux.";
        }
		return request.createResponseBuilder(codeHttp)
            .body(corps 
                + " Durée totale : " 
                + String.valueOf(System.currentTimeMillis() - startTime) 
                + " ms")
			.build();
    }

    private class Identifiants {

        private String id;
        private String mdp;

        private Identifiants (String id, String mdp) {
            this.id = id;
            this.mdp = mdp;
        }
    }
}