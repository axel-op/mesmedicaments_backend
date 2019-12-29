package app.mesmedicaments.api.publique;

import app.mesmedicaments.JSONArrays;
import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteMedicamentBelgique;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.unchecked.Unchecker;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Interactions {

    private Interactions() {}

    @FunctionName("interactions")
    public HttpResponseMessage interactions(
            @HttpTrigger(
                            name = "interactionsTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.GET, HttpMethod.POST},
                            route = "interactions")
                    final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final Map<Pays, Set<Long>> codesParPays = obtenirCodesParPaysDepuisRequete(request);
            final Set<? extends AbstractEntiteMedicament<? extends Presentation>>
                    entitesMedicament = obtenirEntitesMedicament(codesParPays, logger);
            final Set<JSONObject> interactions =
                    EntiteInteraction.obtenirInteractions(logger, entitesMedicament).stream()
                            .map(
                                    Unchecker.wrap(
                                            logger,
                                            (final EntiteInteraction e) ->
                                                    Commun.utiliserDepreciees(request)
                                                            ? Utils.interactionEnJsonDepreciee(
                                                                    e, logger)
                                                            : Utils.interactionEnJson(e, logger)))
                            .collect(Collectors.toSet());
            corpsReponse.put("interactions", interactions);
            codeHttp = HttpStatus.OK;
        } catch (IllegalArgumentException | JSONException | NoSuchElementException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private Map<Pays, Set<Long>> obtenirCodesParPaysDepuisRequete(
            HttpRequestMessage<Optional<String>> requete) {
        final JSONObject corpsRequete = new JSONObject(requete.getBody().get());
        final Map<Pays, Set<Long>> codesParPays = new HashMap<>();
        if (Commun.utiliserDepreciees(requete)) {
            codesParPays.put(
                    Pays.France, JSONArrays.toSetLong(corpsRequete.getJSONArray("medicaments")));
        } else {
            final JSONArray medicaments = corpsRequete.getJSONArray("medicaments");
            for (int i = 0; i < medicaments.length(); i++) {
                final JSONObject details = medicaments.getJSONObject(i);
                codesParPays
                        .computeIfAbsent(
                                Pays.obtenirPays(details.getString("pays")), k -> new HashSet<>())
                        .add(details.getLong("code"));
            }
        }
        return codesParPays;
    }

    private Set<? extends AbstractEntiteMedicament<? extends Presentation>>
            obtenirEntitesMedicament(Map<Pays, Set<Long>> codesParPays, Logger logger) {
        return codesParPays
                .entrySet()
                .parallelStream()
                .flatMap(
                        e -> {
                            final Pays pays = e.getKey();
                            if (pays == Pays.France)
                                return EntiteMedicamentFrance.obtenirEntites(
                                        e.getValue(), true, logger)
                                        .stream();
                            else if (pays == Pays.Belgique)
                                return EntiteMedicamentBelgique.obtenirEntites(
                                        e.getValue(), true, logger)
                                        .stream();
                            else
                                throw new NotImplementedException(
                                        "Il n'est pas encore possible de détecter les interactions avec les médicaments de ce pays");
                        })
                .collect(Collectors.toSet());
    }
}
