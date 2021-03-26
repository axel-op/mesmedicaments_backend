package app.mesmedicaments.azure.fonctions.publiques;

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
import app.mesmedicaments.dmp.DMPTimeoutException;
import app.mesmedicaments.dmp.authentication.DMPAuthenticationException;
import app.mesmedicaments.dmp.authentication.DMPFirstAuthenticationPage;
import app.mesmedicaments.dmp.authentication.DMPThirdAuthenticationPage;
import app.mesmedicaments.utils.Utils;

public final class Connexion {

    @FunctionName("connexion")
    public HttpResponseMessage connexion(@HttpTrigger(name = "connexionTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS, methods = {HttpMethod.POST},
            dataType = "string",
            route = "connexion/{etape:int}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("etape") final int etape, final ExecutionContext context) {
        final JSONObject corpsReponse = new JSONObject();
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.OK;
        try {
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            final String id = corpsRequete.getString("id");
            switch (etape) {
                case 1:
                    final String mdp = corpsRequete.getString("mdp");
                    final var page = DMPFirstAuthenticationPage.create().provideCredentials(id, mdp)
                            .sendOTPCode();
                    corpsReponse.put("donneesConnexion", page.toJSONObject());
                    corpsReponse.put("envoiCode", "SMS ou email");
                    break;
                case 2:
                    final var code = corpsRequete.getInt("code");
                    final var pageJson = corpsRequete.getJSONObject("donneesConnexion");
                    final var homePage =
                            new DMPThirdAuthenticationPage(pageJson).provideOTPCode(code);
                    corpsReponse.put("donneesConnexion", homePage.toJSONObject());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (DMPAuthenticationException e) {
            codeHttp = HttpStatus.CONFLICT;
        } catch (JSONException | DMPTimeoutException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }
}


class IncorrectIdsException extends Exception {
    private static final long serialVersionUID = 1L;
}
