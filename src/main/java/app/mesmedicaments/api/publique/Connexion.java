package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import io.jsonwebtoken.JwtException;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

final class Connexion {

    private Connexion() {}

    @FunctionName("connexion")
    public static HttpResponseMessage connexion(
            @HttpTrigger(
                            name = "connexionTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.POST},
                            dataType = "string",
                            route = "connexion/{etape:int}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("etape") final int etape,
            final ExecutionContext context) {
        final JSONObject corpsReponse = new JSONObject();
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            if (etape == 1) { // Première étape de la connexion
                final String id = corpsRequete.getString("id");
                final String mdp = corpsRequete.getString("mdp");
                final JSONObject resultat = new Authentification(logger, id).connexionDMP(mdp);
                if (!resultat.isNull(Authentification.CLE_ERREUR)) {
                    codeHttp =
                            resultat.get(Authentification.CLE_ERREUR)
                                            .equals(Authentification.ERR_INTERNE)
                                    ? HttpStatus.INTERNAL_SERVER_ERROR
                                    : HttpStatus.CONFLICT;
                } else {
                    codeHttp = HttpStatus.OK;
                    corpsReponse.put(
                            Authentification.CLE_ENVOI_CODE,
                            resultat.getString(Authentification.CLE_ENVOI_CODE));
                }
            } else if (etape == 2) { // Deuxième étape de la connexion
                final String id = corpsRequete.getString("id");
                final Authentification auth = new Authentification(logger, id);
                final String code = String.valueOf(corpsRequete.getInt("code"));
                final JSONObject resultat = auth.doubleAuthentification(code);
                if (!resultat.isNull(Authentification.CLE_ERREUR)) codeHttp = HttpStatus.CONFLICT;
                else {
                    final EntiteUtilisateur entiteU =
                            EntiteUtilisateur.obtenirEntiteOuCreer(id, logger);
                    corpsReponse.put("idAnalytics", entiteU.getIdAnalytics());
                    corpsReponse.put("accessToken", auth.createAccessToken());
                    if (resultat.has("genre")) corpsReponse.put("genre", resultat.get("genre"));
                    codeHttp = HttpStatus.OK;
                }
            } else {
                throw new IllegalArgumentException();
            }
        } catch (JSONException
                | NullPointerException
                | NoSuchElementException
                | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final JwtException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.UNAUTHORIZED;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }
}
