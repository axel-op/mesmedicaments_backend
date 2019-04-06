package app.mesmedicaments;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public class Triggers {

    private static final String CLE_CAUSE;
    private static Logger logger;

    static {
        CLE_CAUSE = "cause";
    }

    //private Triggers () {}

    @FunctionName("connexion")
    public HttpResponseMessage connexion (
        @HttpTrigger(
            name = "connexionTrigger",
            dataType = "string",
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.POST})
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        String id;
        String mdp;
        JSONObject retour = new JSONObject();
        HttpStatus codeHttp = null;
        JSONObject corpsReponse = new JSONObject();
        logger = context.getLogger();
        /*** Ajouter des contrôles ici ***/
        try {
            JSONObject corpsRequete = new JSONObject(request.getBody().get());
            String heureRequete = request.getHeaders().get("heure");
            if (!Utils.verifierHeure(heureRequete, 10)) {
                codeHttp = HttpStatus.BAD_REQUEST;
                corpsReponse.put(CLE_CAUSE, "L'heure ne correspond pas");
            }
            else {
                Boolean doubleAuthentification = null;
                String DA = request.getHeaders().get("da");
                if (DA == null) { 
                    codeHttp = HttpStatus.BAD_REQUEST; 
                    corpsReponse.put(CLE_CAUSE, "Pas d'en-tête spécifié pour la DA");
                }
                else {
                    DA = DA.toLowerCase();
                    if (DA.equals("true")) { doubleAuthentification = true; }
                    else if (DA.equals("false")) { doubleAuthentification = false; }
                    if (doubleAuthentification == null) { 
                        codeHttp = HttpStatus.BAD_REQUEST;
                        corpsReponse.put(CLE_CAUSE, "En-tête DA incorrect");
                    }
                    else if (!doubleAuthentification) { // Première étape de la connexion
                        id = corpsRequete.getString("id");
                        mdp = corpsRequete.getString("mdp");
                        retour = Authentification.connexionDMP(logger, id, mdp);
                        if (!retour.isNull("erreur")) {
                            codeHttp = HttpStatus.CONFLICT;
                            corpsReponse.put(CLE_CAUSE, retour.get("erreur"));
                        } else {
                            codeHttp = HttpStatus.OK;
                            corpsReponse.put("envoiCode", retour.getString("envoiCode"));
                            corpsReponse.put("sid", Utils.XOREncrypt(retour.getString("sid")));
                            corpsReponse.put("tformdata", Utils.XOREncrypt(retour.getString("tformdata")));
                            corpsReponse.put("cookies", Utils.XOREncrypt(retour.getJSONObject("cookies").toString()));
                        }
                    }
                    else { // Deuxième étape de la connexion
                        String code = String.valueOf(corpsRequete.getInt("code"));
                        JSONArray sidChiffre = corpsRequete.getJSONArray("sid");
                        JSONArray tformdataChiffre = corpsRequete.getJSONArray("tformdata");
                        JSONArray cookiesChiffres = corpsRequete.getJSONArray("cookies");
                        String sid = Utils.XORDecrypt(
                            Utils.JSONArrayToIntArray(sidChiffre));
                        String tformdata = Utils.XORDecrypt(
                            Utils.JSONArrayToIntArray(tformdataChiffre));
                        JSONObject cookies = new JSONObject(Utils.XORDecrypt(
                            Utils.JSONArrayToIntArray(cookiesChiffres)));
                        retour = Authentification.doubleAuthentification(
                            logger,
                            code,
                            sid, 
                            tformdata, 
                            cookies);
                        if (!retour.isNull("erreur")) {
                            codeHttp = HttpStatus.CONFLICT;
                            corpsReponse.put(CLE_CAUSE, retour.get("erreur"));
                        } else {
                            codeHttp = HttpStatus.OK;
                            corpsReponse.put("ici", "tout est ok");
                        }
                    }
                }
            }
        }
        catch (JSONException | NullPointerException | NoSuchElementException e) {
            codeHttp = HttpStatus.BAD_REQUEST;
            corpsReponse = new JSONObject();
            corpsReponse.put(CLE_CAUSE, "Mauvais format du corps de la requête");
        }
        corpsReponse.put("heure", Utils.obtenirHeure().toString());
        return request.createResponseBuilder(codeHttp)
            .body(corpsReponse)
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

/*
    private class Requete {
        private String id;
        private String mdp;
        private Integer code;
        private String sid;
        private String tformdata;
        private String cookies;
        private String heure;
        private Requete (String id, String mdp) {
            this.id = id;
            this.mdp = mdp;
        }
        private Requete (Integer code, Integer[] sid, Integer[] tformdata, Integer[] cookies, String heure) {
            this.code = code;
            this.sid = sid;
            this.tformdata = tformdata;
            this.cookies = cookies;
            this.heure = heure;
        }
    }*/
}