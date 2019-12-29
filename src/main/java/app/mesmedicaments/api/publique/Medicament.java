package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import org.json.JSONObject;

final class Medicament {

    private Medicament() {}

    // Maintenue uniquement pour compatibilité avec versions < 25
    @FunctionName("medicament")
    public HttpResponseMessage medicament(
            @HttpTrigger(
                            name = "medicamentTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.GET},
                            route = "medicament/{code}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("code") final String codeStr,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject reponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        // String[] parametres = request.getUri().getPath().split("/");
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            reponse.put(
                    "medicament",
                    Utils.medicamentFranceEnJsonDepreciee(
                            EntiteMedicamentFrance.obtenirEntite(Long.parseLong(codeStr)).get(),
                            logger));
            codeHttp = HttpStatus.OK;
        } catch (IllegalArgumentException | NoSuchElementException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, reponse, request);
    }
}
