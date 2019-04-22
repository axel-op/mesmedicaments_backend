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
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.misesajour.MiseAJourBDPM;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

public final class Triggers {

    private static final String CLE_CAUSE;
    private static final String CLE_HEURE;
    private static final String CLE_ERREUR_AUTH;
    private static final String CLE_ENVOI_CODE;
    private static final String CLE_PRENOM;
    private static final String CLE_EMAIL;
    private static final String CLE_GENRE;
    private static final String CLE_INSCRIPTION_REQUISE;
    private static final String ERR_INTERNE;
    private static final String HEADER_AUTHORIZATION;

    static {
        CLE_CAUSE = "cause";
        CLE_HEURE = "heure";
        CLE_ERREUR_AUTH = Authentification.CLE_ERREUR;
        CLE_ENVOI_CODE = Authentification.CLE_ENVOI_CODE;
        CLE_PRENOM = Authentification.CLE_PRENOM;
        CLE_GENRE = Authentification.CLE_GENRE;
        CLE_EMAIL = Authentification.CLE_EMAIL;
        CLE_INSCRIPTION_REQUISE = Authentification.CLE_INSCRIPTION_REQUISE;
        ERR_INTERNE = Authentification.ERR_INTERNE;
        HEADER_AUTHORIZATION = "jwt";
    }

    private Logger logger;
    //private JSONObject corpsRequete;

    // mettre une doc
    @FunctionName("dmp")
    public HttpResponseMessage dmp (
        @HttpTrigger(
            name = "dmpTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS,
            methods = {HttpMethod.GET})
        final HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        String accessToken;
        String id;
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        JSONObject corpsReponse = new JSONObject();
        try {
            accessToken = request.getHeaders().get(HEADER_AUTHORIZATION);
            id = Authentification.getIdFromToken(accessToken);
            // terminer
        }
        catch (JwtException e) {
            codeHttp = HttpStatus.UNAUTHORIZED;
        }
        return request.createResponseBuilder(codeHttp)
            .body(corpsReponse)
            .build();
    }

    /*
    @FunctionName("medicaments")
    public HttpResponseMessage medicaments (
        @HttpTrigger(
            name = "medicamentsTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS,
            methods = {HttpMethod.GET},
            route = "medicaments/{codecis:int?}") // à tester
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("codecis")
        final ExecutionContext context
    ) {
        // FAIRE QUELQUE CHOSE
        // Si codecis spécifié : fichier JSON sur le medicament
        // Sinon : liste des codecis associés à leur noms
        return null;
    }
    */

    @FunctionName("connexion")
    public HttpResponseMessage connexion (
        @HttpTrigger(
            name = "connexionTrigger",
            authLevel = AuthorizationLevel.FUNCTION,
            methods = {HttpMethod.POST},
            dataType = "string",
            route = "connexion/{etape:int}")
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("etape") int etape,
        final ExecutionContext context
    ) {
        final String id;
        final String mdp;
        String jwt = null;
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        JSONObject corpsReponse = new JSONObject();
        JSONObject resultat = new JSONObject();
        logger = context.getLogger();
        Authentification auth;
        try {
            if (!verifierHeure(request.getHeaders().get(CLE_HEURE), 2)) { 
                throw new IllegalArgumentException(); }
            JSONObject corpsRequete = new JSONObject(request.getBody().get());
            if (etape == 0) { // Renouvellement du token d'accès
                jwt = request.getHeaders().get(HEADER_AUTHORIZATION);
                if (Authentification.checkRefreshToken(jwt)) { 
                    id = Authentification.getIdFromToken(jwt);
                    auth = new Authentification(logger, id);
                    corpsReponse.put("accessToken", auth.createAccessToken()); 
                    codeHttp = HttpStatus.OK;
                }
                else { throw new JwtException("Le token de rafraîchissement n'est pas valide"); }
            }
            else if (etape == 1) { // Première étape de la connexion
                id = corpsRequete.getString("id");
                mdp = corpsRequete.getString("mdp");
                auth = new Authentification(logger, id);
                resultat = auth.connexionDMP(mdp);
                if (!resultat.isNull(CLE_ERREUR_AUTH)) {
                    codeHttp = HttpStatus.CONFLICT;
                    corpsReponse.put(CLE_CAUSE, resultat.get(CLE_ERREUR_AUTH));
                } else {
                    codeHttp = HttpStatus.OK;
                    corpsReponse.put(CLE_ENVOI_CODE, resultat.getString(CLE_ENVOI_CODE));
                }
            }
            else if (etape == 2) { // Deuxième étape de la connexion
                id = corpsRequete.getString("id");
                auth = new Authentification(logger, id);
                String code = String.valueOf(corpsRequete.getInt("code"));
                resultat = auth.doubleAuthentification(code);
                if (!resultat.isNull(CLE_ERREUR_AUTH)) {
                    codeHttp = HttpStatus.CONFLICT;
                    corpsReponse.put(CLE_CAUSE, resultat.get(CLE_ERREUR_AUTH));
                } else {
                    codeHttp = HttpStatus.OK;
                    corpsReponse.put(CLE_PRENOM, resultat.get(CLE_PRENOM));
                    corpsReponse.put(CLE_EMAIL, resultat.get(CLE_EMAIL));
                    corpsReponse.put(CLE_GENRE, resultat.get(CLE_GENRE));
                    corpsReponse.put(CLE_INSCRIPTION_REQUISE, resultat.get(CLE_INSCRIPTION_REQUISE));
                    corpsReponse.put("accessToken", auth.createAccessToken());
                    corpsReponse.put("refreshToken", auth.createRefreshToken());
                }
            } else { throw new IllegalArgumentException(); }
        }
        catch (JSONException 
            | NullPointerException 
            | NoSuchElementException
            | IllegalArgumentException e) 
        {
            codeHttp = HttpStatus.BAD_REQUEST;
            corpsReponse = new JSONObjectUneCle(CLE_CAUSE, "Mauvais format du corps de la requête");
        }
        catch (ExpiredJwtException e) {
            codeHttp = HttpStatus.UNAUTHORIZED;
            corpsReponse = new JSONObjectUneCle(CLE_CAUSE, "Jeton expiré");
        }
        catch (JwtException e) 
        {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.UNAUTHORIZED;
        }
        catch (Exception e) {
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
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        JSONObject corpsReponse = new JSONObject();
        //JSONObject retour = new JSONObject();
        logger = context.getLogger();
        Authentification auth;
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
            auth = new Authentification(logger, id);
            auth.inscription(prenom, email, genre);
            codeHttp = HttpStatus.OK;
        }
        catch (NullPointerException 
            | IllegalArgumentException e) {
            codeHttp = HttpStatus.BAD_REQUEST;
        }
        catch (JSONException
            | NoSuchElementException e) {
            codeHttp = HttpStatus.UNAUTHORIZED;
        }
        catch (Exception e) {
            Utils.logErreur(e, logger);
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
            methods = {HttpMethod.GET},
            route = "mettreAJourBases/{etape:int}")
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("etape") int etape,
        final ExecutionContext context
    ) {
		long startTime = System.currentTimeMillis();
		HttpStatus codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
		String corps = "";
        logger = context.getLogger();
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
}