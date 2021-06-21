package app.mesmedicaments.api.substances;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.json.JSONException;
import org.json.JSONObject;
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.Utils;

public class SubstancesEndpoint {

    @FunctionName("substances")
    public HttpResponseMessage substances(
            @HttpTrigger(name = "substancesTrigger", authLevel = AuthorizationLevel.ANONYMOUS,
                    methods = {HttpMethod.POST},
                    route = "substances") final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final var logger = context.getLogger();
        var httpCode = HttpStatus.OK;
        List<Substance> substances = new ArrayList<>();
        try {
            final var identifiers =
                    new JSONObject(request.getBody().get()).getJSONArray("identifiers");
            logger.info("" + identifiers.length());
            substances = JSONArrays.toSetJSONObject(identifiers).stream()
                    .map(SubstanceIdentifier::fromJSON).flatMap(identifier -> {
                        try {
                            final var s = Substance.fromIdentifier(identifier);
                            logger.info(s.toString());
                            return Stream.of(s);
                        } catch (SubstanceNotFoundException e) {
                            logger.info(e.toString());
                            Utils.logErreur(e, logger);
                            return Stream.empty();
                        }
                    }).collect(Collectors.toList());
            logger.info(" " + substances.size());
        } catch (JSONException e) {
            httpCode = HttpStatus.BAD_REQUEST;
        }
        return Commun.construireReponse(httpCode, new JSONObject().put("substances",
                substances.stream().map(JSONConverter::toJSON).collect(Collectors.toList())),
                request);
    }

}
