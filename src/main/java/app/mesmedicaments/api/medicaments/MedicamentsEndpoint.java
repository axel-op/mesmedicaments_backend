package app.mesmedicaments.api.medicaments;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
import app.mesmedicaments.api.ConvertisseurJSONMedicament;
import app.mesmedicaments.api.IRequeteAvecIdentifieurs;
import app.mesmedicaments.api.IdentifieurMedicament;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.Utils;

public final class MedicamentsEndpoint
        implements IRequeteAvecIdentifieurs<Medicament<?, ?, ?>, IdentifieurMedicament> {

    static private final ConvertisseurJSON<Medicament<?, ?, ?>> convertisseur =
            new ConvertisseurJSONMedicament();

    @FunctionName("medicaments")
    public HttpResponseMessage medicaments(
            @HttpTrigger(name = "medicamentsTrigger", authLevel = AuthorizationLevel.ANONYMOUS,
                    methods = {HttpMethod.POST},
                    route = "medicaments") final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject reponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.OK;
        List<MedicamentFrance> medicaments = new ArrayList<>();
        try {
            final var identifiers = parseIdentifiersFromRequestBody(request.getBody().get());
            final var dbClient = new ClientTableMedicamentsFrance();
            medicaments = dbClient.get(identifiers.stream().map(IdentifieurMedicament::getId)
                    .collect(Collectors.toList()));
            final var convertis = new ArrayList<>(medicaments.size());
            for (var medicament : medicaments) {
                convertis.add(convertisseur.toJSON(medicament));
            }
            reponse.put("medicaments", convertis);
        } catch (JSONException | NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, reponse, request);
    }


    @Override
    public IdentifieurMedicament deserializeIdentifier(JSONObject serialized) {
        return new IdentifieurMedicament(serialized);
    }

}
