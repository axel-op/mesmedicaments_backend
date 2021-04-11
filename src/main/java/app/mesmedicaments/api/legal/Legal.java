package app.mesmedicaments.api.legal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONObject;
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.utils.Utils;

public final class Legal {

    @FunctionName("legal")
    public HttpResponseMessage legal(
        @HttpTrigger(
            name = "legalTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS,
            methods = {HttpMethod.GET},
            route = "legal/{fichier}")
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("fichier") String fichier,
        final ExecutionContext context
    ) {
        try {
            String ressource;
            switch (fichier) {
                case "confidentialite":
                    ressource = "/PolitiqueConfidentialite.txt";
                    break;
                case "mentions":
                    ressource = "/MentionsLegales.txt";
                    break;
                case "datesmaj":
                    final JSONObject corpsReponse =
                            new JSONObject()
                                    .put("heure", LocalDateTime.now().toString())
                                    .put("dernieresMaj", obtenirDatesMaj());
                    return Commun.construireReponse(HttpStatus.OK, corpsReponse, request);
                default:
                    return Commun.construireReponse(HttpStatus.NOT_FOUND, request);
            }
            final String corpsReponse = obtenirTexte(ressource);
            return Commun.construireReponse(HttpStatus.OK, corpsReponse, request);
        } catch (Exception e) {
            Utils.logErreur(e, context.getLogger());
            return Commun.construireReponse(HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
    }

    private String obtenirTexte(String ressource) {
        return new BufferedReader(new InputStreamReader(Legal.class.getResourceAsStream(ressource), StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining(Utils.NEWLINE));
    }

    private JSONObject obtenirDatesMaj() throws DBException {
        final ClientTableDatesMaj client = new ClientTableDatesMaj();
        final LocalDate majBDPM = client.getDateMajBDPM().get();
        final LocalDate majInteractions = client.getDateMajInteractions().get();
        return new JSONObject()
                .put("bdpm", majBDPM.toString())
                .put("interactions", majInteractions.toString());
    }
}
