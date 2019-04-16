package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
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
import com.microsoft.azure.storage.StorageException;

import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;

public final class Triggers {

    private static final String CLE_CAUSE;
    private static final String CLE_HEURE;
    private static final String CLE_ERREUR_AUTH;
    private static final String CLE_DA;
    private static final String CLE_ENVOI_CODE;
    private static final String CLE_PRENOM;
    private static final String CLE_EMAIL;
    private static final String CLE_GENRE;
    //private static final String CLE_SID;
    //private static final String CLE_COOKIES;
    //private static final String CLE_TFORMDATA;
    //private static final String CLE_EXISTENCE;
    private static final String CLE_INSCRIPTION_REQUISE;
    private static final String ERR_INTERNE;

    static {
        CLE_CAUSE = "cause";
        CLE_HEURE = "heure";
        CLE_DA = "da";
        CLE_ERREUR_AUTH = Authentification.CLE_ERREUR;
        CLE_ENVOI_CODE = Authentification.CLE_ENVOI_CODE;
        CLE_PRENOM = Authentification.CLE_PRENOM;
        CLE_GENRE = Authentification.CLE_GENRE;
        CLE_EMAIL = Authentification.CLE_EMAIL;
        //CLE_SID = Authentification.CLE_SID;
        //CLE_COOKIES = Authentification.CLE_COOKIES;
        //CLE_TFORMDATA = Authentification.CLE_TFORMDATA;
        //CLE_EXISTENCE = Authentification.CLE_EXISTENCE_DB;
        CLE_INSCRIPTION_REQUISE = Authentification.CLE_INSCRIPTION_REQUISE;
        ERR_INTERNE = Authentification.ERR_INTERNE;
    }

    private Logger logger;
    private HttpStatus codeHttp;
    private Boolean DA;
    private JSONObject corpsReponse;
    private JSONObject retour;
    //private JSONObject corpsRequete;

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
        corpsReponse = new JSONObject();
        retour = new JSONObject();
        logger = context.getLogger();
        try {
            if (!verifierHeure(request.getHeaders().get(CLE_HEURE), 2)
                || !verifierEnTeteDA(request.getHeaders().get(CLE_DA))
            ) { throw new IllegalArgumentException(); }
            JSONObject corpsRequete = new JSONObject(request.getBody().get());
            id = corpsRequete.getString("id");
            try { mdp = corpsRequete.getString("mdp"); }
            catch (JSONException e) { mdp = ""; }
            if (id.length() != 8
                || mdp.length() > 128) { throw new IllegalArgumentException(); }
            if (!DA) { // Première étape de la connexion
                retour = new Authentification(logger).connexionDMP(id, mdp);
                if (!retour.isNull(CLE_ERREUR_AUTH)) {
                    codeHttp = HttpStatus.CONFLICT;
                    corpsReponse.put(CLE_CAUSE, retour.get(CLE_ERREUR_AUTH));
                } else {
                    codeHttp = HttpStatus.OK;
                    corpsReponse.put(CLE_ENVOI_CODE, retour.getString(CLE_ENVOI_CODE));
                    //corpsReponse.put(CLE_EXISTENCE, retour.getBoolean(CLE_EXISTENCE));
                }
            }
            else { // Deuxième étape de la connexion
                String code = String.valueOf(corpsRequete.getInt("code"));
                if (code.length() >= 10) { throw new IllegalArgumentException(); }
                retour = new Authentification(logger).doubleAuthentification(id, code);
                if (!retour.isNull(CLE_ERREUR_AUTH)) {
                    codeHttp = HttpStatus.CONFLICT;
                    corpsReponse.put(CLE_CAUSE, retour.get(CLE_ERREUR_AUTH));
                } else {
                    codeHttp = HttpStatus.OK;
                    corpsReponse.put(CLE_PRENOM, retour.get(CLE_PRENOM));
                    corpsReponse.put(CLE_EMAIL, retour.get(CLE_EMAIL));
                    corpsReponse.put(CLE_GENRE, retour.get(CLE_GENRE));
                    corpsReponse.put(CLE_INSCRIPTION_REQUISE, retour.get(CLE_INSCRIPTION_REQUISE));
                    // générer et envoyer un jeton
                }
            }
        }
        catch (JSONException 
            | NullPointerException 
            | NoSuchElementException
            | IllegalArgumentException e
        ) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
            corpsReponse = new JSONObjectUneCle(CLE_CAUSE, "Mauvais format du corps de la requête");
        }
        catch (InvalidKeyException
            | URISyntaxException
            | StorageException e
        ) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
            corpsReponse = new JSONObjectUneCle(CLE_CAUSE, ERR_INTERNE);
        }
        corpsReponse.put(CLE_HEURE, obtenirHeure().toString());
        return request.createResponseBuilder(codeHttp)
            .body(corpsReponse)
            .build();
    }

    @FunctionName("inscription")
    public HttpResponseMessage inscription (
        @HttpTrigger(
            name = "inscriptionTrigger",
            dataType = "string",
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.POST})
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        String id;
        String prenom;
        String email;
        String genre;
        codeHttp = HttpStatus.OK;
        corpsReponse = new JSONObject();
        retour = new JSONObject();
        logger = context.getLogger();
        try {
            // vérifier le jeton
            if (!verifierHeure(request.getHeaders().get(CLE_HEURE), 2)) {
                throw new IllegalArgumentException();
            }
            JSONObject corpsRequete = new JSONObject(request.getBody().get());
            id = corpsRequete.getString("id");
            prenom = corpsRequete.getString("prenom");
            email = corpsRequete.getString("email");
            genre = corpsRequete.getString("genre");
            if (id.length() != 8
                || prenom.length() > 30
                || email.length() > 128
                || genre.length() != 1
            ) { throw new IllegalArgumentException(); }
            new Authentification(logger).inscription(id, prenom, email, genre);
        }
        catch (NullPointerException 
            | IllegalArgumentException e) {
            codeHttp = HttpStatus.BAD_REQUEST;
        }
        catch (JSONException
            | NoSuchElementException e) {
            codeHttp = HttpStatus.UNAUTHORIZED;
        }
        catch (InvalidKeyException
            | URISyntaxException
            | StorageException e
        ) {
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
            corpsReponse = new JSONObjectUneCle(CLE_CAUSE, ERR_INTERNE);
        }
        corpsReponse.put(CLE_HEURE, obtenirHeure().toString());
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

    @FunctionName("ping")
    public HttpResponseMessage ping (
        @HttpTrigger(
            name = "pingTrigger",
            dataType = "string",
            authLevel = AuthorizationLevel.ANONYMOUS,
            methods = {HttpMethod.GET}
        )
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        return request.createResponseBuilder(HttpStatus.OK)
            .body("Pong").build();
    }

    private LocalDateTime obtenirHeure () {
        return LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
    }

	private boolean verifierHeure (String heure, long intervalle) {
		LocalDateTime heureObtenue;
        LocalDateTime maintenant = obtenirHeure();
        if (heure != null && heure.length() >= 19 && heure.length() <= 26) {
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