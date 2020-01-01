package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.Optional;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Recherche {

    @FunctionName("recherche")
    public HttpResponseMessage recherche(
            @HttpTrigger(
                            name = "rechercheTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.GET},
                            route = "recherche/{recherche}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("recherche") String recherche,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.OK;
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            if (recherche.length() > 100) throw new IllegalArgumentException();
            recherche = Utils.normaliser(recherche).toLowerCase();
            logger.info("Recherche de \"" + recherche + "\"");
            final JSONArray resultats =
                    EntiteCacheRecherche.obtenirResultatsCache(
                            recherche, Commun.utiliserDepreciees(request));
            corpsReponse.put("resultats", resultats);
            logger.info(resultats.length() + " résultats trouvés");
        } catch (IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }
}
