package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.recherche.Requeteur;
import app.mesmedicaments.recherche.SearchClient;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.storage.StorageException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Recherche {

    @FunctionName("recherche")
    public HttpResponseMessage recherche(
            @HttpTrigger(name = "rechercheTrigger", authLevel = AuthorizationLevel.ANONYMOUS, methods = {
                    HttpMethod.GET,
                    HttpMethod.POST }, route = "recherche/{recherche}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("recherche") final String recherche, // inutilisé à partir de la version 40 de l'application
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.OK;
        try {
            final boolean ancienneVersion = utiliserAncienIndex(request);
            if (!ancienneVersion && recherche.equals("nombredocuments")) {
                corpsReponse.put("nombreDocuments", new SearchClient(logger).getDocumentCount());
            } else {
                final JSONArray resultats = utiliserAncienIndex(request)
                        ? obtenirResultatAncienIndex(request, recherche, logger)
                        : obtenirResultats(request, logger);
                logger.info(resultats.length() + " résultats trouvés");
                corpsReponse.put("resultats", resultats);
            }
        } catch (IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONArray obtenirResultats(HttpRequestMessage<Optional<String>> request, Logger logger) throws IOException {
        final String recherche = new JSONObject(request.getBody().get()).getString("recherche");
        logger.info("Recherche de \"" + recherche + "\"");
        return new Requeteur(logger).rechercher(recherche);
    }

    private JSONArray obtenirResultatAncienIndex(HttpRequestMessage<Optional<String>> request, String recherche,
            Logger logger) throws StorageException, URISyntaxException, InvalidKeyException {
        recherche = Utils.normaliser(recherche).toLowerCase();
        logger.info("Recherche de \"" + recherche + "\"");
        return EntiteCacheRecherche.obtenirResultatsCache(recherche, Commun.utiliserDepreciees(request));
    }

    private boolean utiliserAncienIndex(HttpRequestMessage<Optional<String>> request) {
        final int version = Integer.parseInt(request.getHeaders().getOrDefault(Commun.CLE_VERSION, "0"));
        return version < 40;
    }
}
