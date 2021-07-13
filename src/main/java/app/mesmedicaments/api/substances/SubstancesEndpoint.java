package app.mesmedicaments.api.substances;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import app.mesmedicaments.api.ConvertisseurJSON;
import app.mesmedicaments.api.ConvertisseurJSONSubstance;
import app.mesmedicaments.api.IRequeteAvecIdentifieurs;
import app.mesmedicaments.api.IdentifieurSubstance;
import app.mesmedicaments.api.Substance;
import app.mesmedicaments.terminologies.substances.TerminologySubstances;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesBDPM;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesRxNorm;
import app.mesmedicaments.utils.Utils;

public class SubstancesEndpoint implements IRequeteAvecIdentifieurs<Substance, IdentifieurSubstance> {

    static final private Map<String, TerminologySubstances<?>> terminos = Map.of("bdpm",
            new TerminologySubstancesBDPM(), "rxnorm", new TerminologySubstancesRxNorm());

    static final private ConvertisseurJSON<Substance> convertisseur =
            new ConvertisseurJSONSubstance();

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
            final var identifiers = parseIdentifiersFromRequestBody(request.getBody().get());
            substances = identifiers.stream().flatMap(identifier -> {
                try {
                    final var source = identifier.getSource();
                    final var id = identifier.getId();
                    if (!terminos.containsKey(source)) {
                        throw new SubstanceNotFoundException(identifier);
                    }
                    final var concept = terminos.get(source).getConcept(id)
                            .orElseThrow(() -> new SubstanceNotFoundException(identifier));
                    final var s = new Substance(source, concept.getId(), concept.getNames());
                    return Stream.of(s);
                } catch (SubstanceNotFoundException e) {
                    Utils.logErreur(e, logger);
                    return Stream.empty();
                }
            }).collect(Collectors.toList());
            logger.info(" " + substances.size());
        } catch (JSONException e) {
            httpCode = HttpStatus.BAD_REQUEST;
        }
        return Commun.construireReponse(httpCode, new JSONObject().put("substances",
                substances.stream().map(convertisseur::toJSON).collect(Collectors.toList())),
                request);
    }

    @Override
    public IdentifieurSubstance deserializeIdentifier(JSONObject serialized) {
        return new IdentifieurSubstance(serialized);
    }

}
