package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.entitestables.EntiteMedicamentBelgique;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

final class Medicaments {

    private Medicaments() {}

    @FunctionName("medicaments")
    public static HttpResponseMessage medicaments(
            @HttpTrigger(
                            name = "medicamentsTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.POST},
                            route = "medicaments")
                    final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject reponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            final JSONObject med =
                    new JSONObject(request.getBody().get()).getJSONObject("medicament");
            final Pays pays = Pays.obtenirPays(med.getString("pays"));
            final Long code = med.getLong("code");
            final AbstractEntiteMedicament<? extends Presentation> entiteM =
                    pays == Pays.France
                            ? EntiteMedicamentFrance.obtenirEntite(code).get()
                            : EntiteMedicamentBelgique.obtenirEntite(code).get();
            reponse.put("medicament", Utils.medicamentEnJson(entiteM, logger));
            codeHttp = HttpStatus.OK;
        } catch (JSONException | NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, reponse, request);
    }
}
