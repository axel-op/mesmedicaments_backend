package app.mesmedicaments.api.interactions;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
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
import app.mesmedicaments.api.IdentifieurMedicament;
import app.mesmedicaments.interactions.service.InteractionSubstancesDTO;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public final class Interactions {

    @FunctionName("interactions")
    public HttpResponseMessage interactions(
            @HttpTrigger(name = "interactionsTrigger", authLevel = AuthorizationLevel.ANONYMOUS,
                    methods = {HttpMethod.POST},
                    route = "interactions") final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final var corpsRequete = new JSONObject(request.getBody().get());
            final var medicamentsIds = Streams.stream(corpsRequete.getJSONArray("medicaments"))
                    .map(JSONObject.class::cast).map(IdentifieurMedicament::new)
                    .collect(Collectors.toList());
            final var medicamentsSubstances =
                    MedicamentClientProxy.getMedicamentsAvecSubstances(medicamentsIds);
            // ici le lien entre médicaments et substances est rompu
            final Set<List<ConceptSubstance>> combinaisons = Sets.cartesianProduct(
                    medicamentsSubstances.stream().map(MedicamentAvecSubstances::getSubstances)
                            .collect(Collectors.toList()));
            final var interactionService = InteractionServiceProxy.getInteractionService();
            final var interactionsSubstances = combinaisons.parallelStream()
                    .map(Unchecker.panic(interactionService::findInteractions)).flatMap(Set::stream)
                    .collect(Collectors.toSet());
            final var interactionsMedicaments = joindreMedicamentsAuxInteractions(
                    medicamentsSubstances, interactionsSubstances);
            corpsReponse.put("interactions", new JSONConverter().toJSON(interactionsMedicaments));
            codeHttp = HttpStatus.OK;
        } catch (IllegalArgumentException | JSONException | NoSuchElementException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private <M> List<InteractionAvecMedicaments<M>> joindreMedicamentsAuxInteractions(
            List<MedicamentAvecSubstances<M>> medicamentsAvecSubstances,
            Set<InteractionSubstancesDTO> interactionsTrouvees) {
        final var result = new LinkedList<InteractionAvecMedicaments<M>>();
        for (var interaction : interactionsTrouvees) {
            // les ConceptSubstance retournés sont les mêmes que ceux en entrée
            final var combs = interaction.getSubstances().stream()
                    .map(s -> medicamentsAvecSubstances.stream()
                            .filter(ms -> ms.getSubstances().contains(s))
                            .collect(Collectors.toSet()))
                    .collect(Collectors.toList());
            result.addAll(Sets.cartesianProduct(combs).stream()
                    .map(c -> new InteractionAvecMedicaments<>(interaction, c))
                    .collect(Collectors.toList()));
        }
        return result;
    }
}
