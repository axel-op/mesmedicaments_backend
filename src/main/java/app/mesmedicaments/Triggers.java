package app.mesmedicaments;

import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.connexion.*;
import app.mesmedicaments.misesajour.*;

public class Triggers {

    private static Logger logger;

    //private Triggers () {}

    @FunctionName("connexion")
    public HttpResponseMessage connexion (
        @HttpTrigger(
            name = "connexionTrigger",
            dataType = "",
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.POST})
        final HttpRequestMessage<Optional<Identifiants>> request,
        final ExecutionContext context
    ) {
        String id;
        String mdp;
        JSONObject retour = new JSONObject();
        HttpStatus codeHttp = null;
        JSONObject corps = new JSONObject();
        logger = context.getLogger();
        /*** Ajouter des contrôles ici ***/
        //codeHttp = HttpStatus.OK;
        if (!request.getBody().isPresent()) {
            codeHttp = HttpStatus.BAD_REQUEST;
            corps.put("cause", "Bad request");
        }
        else {
            Boolean doubleAuthentification = null;
            String parametre = request
                .getQueryParameters()
                .get("double");
            if (parametre == null) { 
                codeHttp = HttpStatus.BAD_REQUEST; 
                corps.put("cause", "Pas de paramètre spécifié pour la DA");
            }
            else {
                parametre = parametre.toLowerCase();
                if (parametre.equals("true")) { doubleAuthentification = true; }
                else if (parametre.equals("false")) { doubleAuthentification = false; }
                if (doubleAuthentification == null) { 
                    codeHttp = HttpStatus.BAD_REQUEST;
                    corps.put("cause", "Paramètre DA incorrect");
                }
                else {
                    Identifiants identifiants = request.getBody().get();
                    id = identifiants.id;
                    mdp = identifiants.mdp;
                    if (id == null || mdp == null) {
                        codeHttp = HttpStatus.BAD_REQUEST;
                        corps.put("cause", "Mauvais format des identifiants");
                    }
                    else {
                        System.out.println("ID : " + id);
                        System.out.println("CODE : " + mdp);
                        if (!doubleAuthentification) { 
                            retour = Authentification.connexionDMP(logger, id, mdp);
                            if (!retour.isNull("erreur")) {
                                codeHttp = HttpStatus.CONFLICT;
                                corps.put("cause", retour.get("erreur"));
                            } else {
                                codeHttp = HttpStatus.OK;
                                corps.put("heure", retour.get("heure"));
                                corps.put("sid", Utils.XOREncrypt(retour.getString("sid")));
                                corps.put("tformdata", Utils.XOREncrypt(retour.getString("tformdata")));
                                corps.put("baseUri", Utils.XOREncrypt(retour.getString("baseUri")));
                                corps.put("cookies", Utils.XOREncrypt(retour.getJSONObject("cookies").toString()));
                            }
                        }
                        else { 
                            String code = request.getQueryParameters().get("code");
                            logger.info("code = " + code);
                            String baseUri = request.getQueryParameters().get("baseUri");
                            logger.info("baseUri = " + baseUri);
                            String sid = request.getQueryParameters().get("sid");
                            logger.info("sid = " + sid);
                            String tformdata = request.getQueryParameters().get("tformdata");
                            logger.info("tformdata = " + tformdata);
                            logger.info("cookies = " + request.getQueryParameters().get("cookies"));
                            String cookiesChiffres = request.getQueryParameters().get("cookies");
                            if (code == null
                                || baseUri == null
                                || sid == null
                                || tformdata == null
                                || cookiesChiffres == null)
                            { 
                                codeHttp = HttpStatus.BAD_REQUEST;
                                corps.put("cause", "Mauvais format pour la DA");
                            }
                            else { 
                                try {
                                    sid = Utils.XORDecrypt(
                                        Utils.JSONArrayToIntArray(new JSONArray(sid)));
                                    tformdata = Utils.XORDecrypt(
                                        Utils.JSONArrayToIntArray(new JSONArray(tformdata)));
                                    baseUri = Utils.XORDecrypt(
                                        Utils.JSONArrayToIntArray(new JSONArray(baseUri)));
                                    JSONObject cookies = new JSONObject(Utils.XORDecrypt(
                                        Utils.JSONArrayToIntArray(new JSONArray(cookiesChiffres))));
                                    retour = Authentification.doubleAuthentification(
                                        logger,
                                        code,
                                        sid, 
                                        tformdata, 
                                        baseUri,
                                        cookies);
                                    if (!retour.isNull("erreur")) {
                                        codeHttp = HttpStatus.CONFLICT;
                                        corps.put("cause", retour.get("erreur"));
                                    } else {
                                        codeHttp = HttpStatus.OK;
                                        corps.put("ici", "tout est ok");
                                    }
                                }
                                catch (NumberFormatException e) {
                                    codeHttp = HttpStatus.BAD_REQUEST;
                                    corps.put("cause", "Mauvais format du corps JSON");
                                }
                            }
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