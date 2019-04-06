package app.mesmedicaments;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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

public final class Triggers {

    private static final String CLE_CAUSE;
    private static Logger logger;
    private Boolean DA = null;

    static {
        CLE_CAUSE = "cause";
    }

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
        HttpStatus codeHttp = null;
        JSONObject corpsReponse = new JSONObject();
        JSONObject retour = new JSONObject();
        logger = context.getLogger();
        /*** Ajouter des contrôles ici ***/
        try {
            JSONObject corpsRequete = new JSONObject(request.getBody().get());
            if (verifierHeure(request.getHeaders().get("heure"), 10)
                && verifierEnTeteDA(request.getHeaders().get("da"))
            ) {
                if (!DA) { // Première étape de la connexion
                    id = corpsRequete.getString("id");
                    if (id.length() != 8) { throw new IllegalArgumentException(); }
                    mdp = corpsRequete.getString("mdp");
                    retour = Authentification.connexionDMP(logger, id, mdp);
                    if (!retour.isNull("erreur")) {
                        codeHttp = HttpStatus.CONFLICT;
                        corpsReponse.put(CLE_CAUSE, retour.get("erreur"));
                    } else {
                        codeHttp = HttpStatus.OK;
                        corpsReponse.put("envoiCode", retour.getString("envoiCode"));
                        corpsReponse.put("sid", Utils.XOREncrypt(retour.getString("sid")));
                        logger.info("Longueur du tableau chiffré sid = " + Utils.XOREncrypt(retour.getString("sid")).length);
                        corpsReponse.put("tformdata", Utils.XOREncrypt(retour.getString("tformdata")));
                        logger.info("Longueur du tableau chiffré tformdata = " + Utils.XOREncrypt(retour.getString("tformdata")).length);
                        corpsReponse.put("cookies", Utils.XOREncrypt(retour.getJSONObject("cookies").toString()));
                        logger.info("Longueur du tableau chiffré cookies = " + Utils.XOREncrypt(retour.getJSONObject("cookies").toString()).length);
                    }
                }
                else { // Deuxième étape de la connexion
                    String code = String.valueOf(corpsRequete.getInt("code"));
                    if (code.length() >= 10) { throw new IllegalArgumentException(); }
                    JSONArray sidChiffre = corpsRequete.getJSONArray("sid");
                    JSONArray tformdataChiffre = corpsRequete.getJSONArray("tformdata");
                    JSONArray cookiesChiffres = corpsRequete.getJSONArray("cookies");
                    if (sidChiffre.length() > 15
                        || tformdataChiffre.length() < 270
                        || tformdataChiffre.length() > 300
                        || cookiesChiffres.length() < 200
                        || cookiesChiffres.length() > 250
                    ) { throw new IllegalArgumentException(); }
                    retour = Authentification.doubleAuthentification(
                        logger,
                        code,
                        Utils.XORDecrypt(Utils.JSONArrayToIntArray(sidChiffre)), 
                        Utils.XORDecrypt(Utils.JSONArrayToIntArray(tformdataChiffre)), 
                        new JSONObject(Utils.XORDecrypt(Utils.JSONArrayToIntArray(cookiesChiffres))));
                    if (!retour.isNull("erreur")) {
                        codeHttp = HttpStatus.CONFLICT;
                        corpsReponse.put(CLE_CAUSE, retour.get("erreur"));
                    } else {
                        codeHttp = HttpStatus.OK;
                        corpsReponse.put("ici", "tout est ok");
                    }
                }
            }
            else { throw new IllegalArgumentException(); }
        }
        catch (JSONException 
            | NullPointerException 
            | NoSuchElementException
            | IllegalArgumentException e
        ) {
            codeHttp = HttpStatus.BAD_REQUEST;
            corpsReponse = new JSONObject();
            corpsReponse.put(CLE_CAUSE, "Mauvais format du corps de la requête");
        }
        corpsReponse.put("heure", obtenirHeure().toString());
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

    private LocalDateTime obtenirHeure () {
        return LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
    }

	private boolean verifierHeure (String heure, long intervalle) {
		LocalDateTime heureObtenue;
        LocalDateTime maintenant = obtenirHeure();
        if (heure != null && heure.length() >= 19 && heure.length() <= 23) {
            try {
                heureObtenue = LocalDateTime.parse(heure);
                if (heureObtenue.isAfter(maintenant.minusMinutes(intervalle))
                    && heureObtenue.isBefore(maintenant.plusMinutes(2))
                ) { return true; }
            }
            catch (DateTimeParseException e) {}
        }
        return false;
    }
    
    private boolean verifierEnTeteDA (String entete) {
        if (entete != null && (entete.length() == 4 || entete.length() == 5)) {
            if (entete.matches("(?i:true)")) { 
                DA = true; 
                return true;
            }
            else if (entete.matches("(?i:false)")) { 
                DA = false; 
                return true;
            }
        }
        return false;
    }
}