package app.mesmedicaments.api.recherche;

import java.io.IOException;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.azure.recherche.ClientRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.ModeRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.NiveauRecherche;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.Utils;

public final class Recherche {

    @FunctionName("recherche")
    public HttpResponseMessage recherche(
        @HttpTrigger(
            name = "rechercheTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS,
            methods = {HttpMethod.GET, HttpMethod.POST},
            route = "recherche/{sousrequete=null}") 
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("sousrequete") String sousRequete,
        final ExecutionContext context
    ) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.OK;
        try {
            final ClientRecherche client = new ClientRecherche(logger);
            final Parameters params = parseRequest(request, sousRequete);
            if (params.request == Parameters.Request.NombreDocuments)
                corpsReponse.put("nombreDocuments", client.getDocumentCount());
            else {
                final String recherche = params.recherche;
                logger.info("Recherche de \"" + recherche + "\"");
                final JSONArray resultats = rechercher(recherche, client);
                corpsReponse
                    .put("resultats", resultats)
                    .put("nombre", resultats.length());
            }
        } catch (IllegalArgumentException | JSONException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONArray rechercher(String recherche, ClientRecherche client) throws IOException {
        assertion(recherche != null);
        final JSONArray resultats = new JSONArray();
        for (NiveauRecherche niveau : NiveauRecherche.ordonnes()) {
            if (!resultats.isEmpty()) break;
            JSONArrays.append(resultats,
                client.search(recherche)
                    .mode(ModeRecherche.TousLesMots)
                    .niveau(niveau)
                    .getResultats()
                    .getAll());
        }
        return resultats;
    }

    private Parameters parseRequest(HttpRequestMessage<Optional<String>> request, String sousRequete) {
        final Parameters.Request typeRequete = extraireTypeRequete(request, sousRequete);
        final String recherche = typeRequete == Parameters.Request.Recherche
            ? extraireRecherche(request, sousRequete)
            : null;
        return new Parameters(typeRequete, recherche);
    }

    private Parameters.Request extraireTypeRequete(HttpRequestMessage<Optional<String>> request, String sousRequete) {
        final int version = Commun.getCodeVersion(request);
        if (version < 41)
            return sousRequete.equals("nombredocuments")
                ? Parameters.Request.NombreDocuments
                : Parameters.Request.Recherche;
        final JSONObject body = new JSONObject(request.getBody().get());
        return body.getString("requete")
            .equalsIgnoreCase("nombredocuments")
                ? Parameters.Request.NombreDocuments
                : Parameters.Request.Recherche;
    }

    private String extraireRecherche(HttpRequestMessage<Optional<String>> request, String sousRequete) {
        final int version = Commun.getCodeVersion(request);
        if (version < 40) return sousRequete;
        return new JSONObject(request.getBody().get())
            .getString("recherche");
    }

    private void assertion(boolean condition) throws IllegalArgumentException {
        if (!condition)
            throw new IllegalArgumentException();
    }

    static private class Parameters {

        enum Request {
            NombreDocuments,
            Recherche
        }

        private final Request request;
        private final String recherche;

        public Parameters(Request request, String recherche) {
            this.request = request;
            this.recherche = recherche;
        }
    }
}
