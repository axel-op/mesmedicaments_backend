package app.mesmedicaments.api.publique;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
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
import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.JSONArrays;
import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteCacheRecherche;
import app.mesmedicaments.recherche.Requeteur;
import app.mesmedicaments.recherche.SearchClient;

public final class Recherche {

    @FunctionName("recherche")
    public HttpResponseMessage recherche(
            @HttpTrigger(name = "rechercheTrigger", authLevel = AuthorizationLevel.ANONYMOUS, methods = {
                    HttpMethod.GET,
                    HttpMethod.POST }, route = "recherche/{recherche}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("recherche") String recherche, // inutilisé à partir de la version 40 de l'application
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.OK;
        try {
            final boolean ancienneVersion = utiliserAncienIndex(request);
            if (ancienneVersion) {
                corpsReponse.put("resultats", obtenirResultatAncienIndex(request, recherche, logger));
            } else if (recherche.equals("nombreDocuments")) {
                corpsReponse.put("nombreDocuments", new SearchClient(logger).getDocumentCount());
            } else {
                recherche = extraireRecherche(request);
                logger.info("Recherche de \"" + recherche + "\"");
                final Requeteur requeteur = new Requeteur(logger);
                final JSONArray resultats = requeteur.rechercher(recherche);
                int nombre = resultats.length();
                logger.info(String.valueOf(nombre) + " résultats trouvés");
                if (nombre == 0) {
                    JSONArrays.append(resultats, requeteur.rechercherApproximativement(recherche));
                    nombre = resultats.length();
                    logger.info(String.valueOf(nombre) + " résultats trouvés à la recherche approximative");
                }
                corpsReponse.put("resultats", resultats).put("nombre", nombre);
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

    private String extraireRecherche(HttpRequestMessage<Optional<String>> request) {
        return new JSONObject(request.getBody().get()).getString("recherche");
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
