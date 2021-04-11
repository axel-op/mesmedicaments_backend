package app.mesmedicaments.api.interactions;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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

import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.api.Convertisseur;
import app.mesmedicaments.api.medicaments.ClientTableMedicamentsFrance;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.Sets;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;
import lombok.SneakyThrows;

public final class Interactions {

    @FunctionName("interactions")
    public HttpResponseMessage interactions(
            @HttpTrigger(
                            name = "interactionsTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.POST},
                            route = "interactions")
                    final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            final Map<Pays, Set<Integer>> codesParPays = obtenirCodesParPaysDepuisRequete(corpsRequete);
            final Set<Medicament<?, ?, ?>> medicaments = obtenirMedicaments(codesParPays, logger);
            final Set<InteractionAvecMedicaments> interactions = obtenirInteractions(medicaments);
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

    /**
     * Le corps de la requête doit avoir la structure suivante :
     * {
     *   "medicaments": [
     *     {
     *       "pays": ...,
     *       "code": ...,
     *     }
     *   ]
     * }
     * @param requete
     * @return
     */
    private Map<Pays, Set<Integer>> obtenirCodesParPaysDepuisRequete(JSONObject corpsRequete) {
        final Map<Pays, Set<Integer>> codesParPays = new HashMap<>();
        final JSONArray medicaments = corpsRequete.getJSONArray("medicaments");
        for (int i = 0; i < medicaments.length(); i++) {
            final JSONObject details = medicaments.getJSONObject(i);
            final String codePays = details.getString("pays");
            final int codeMed = details.getInt("code");
            codesParPays
                .computeIfAbsent(Pays.fromCode(codePays), k -> new HashSet<>())
                .add(codeMed);
        }
        return codesParPays;
    }

    private Set<Medicament<?, ?, ?>> obtenirMedicaments(Map<Pays, Set<Integer>> codesParPays, Logger logger) throws DBExceptionTableAzure {
        final var client = new ClientTableMedicamentsFrance();
        return codesParPays
            .entrySet()
            .stream()
            .flatMap(e -> {
                final Pays pays = e.getKey();
                if (!pays.equals(Pays.France.instance)) {
                    throw new NotImplementedException(
                        "Il n'est pas encore possible de détecter les interactions avec les médicaments de ce pays");
                }
                return e.getValue().stream();
            })
            .parallel()
            .map(Unchecker.panic((Integer code) -> client.get(code)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Set<InteractionAvecMedicaments> obtenirInteractions(Set<Medicament<?, ?, ?>> medicaments) {
        if (medicaments.size() < 2) return new HashSet<>();
        return Sets.combinations(medicaments)
            .parallelStream()
                .map(Unchecker
                        .panic((List<Medicament<?, ?, ?>> comb) -> obtenirInteractions(comb.get(0),
                                comb.get(1))))
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    }

    private Set<InteractionAvecMedicaments> obtenirInteractions(Medicament<?, ?, ?> medicament1, Medicament<?, ?, ?> medicament2) throws DBException {
        final var client = new ClientTableInteractions();
        Set<List<Substance<?>>> combinaisons = Sets.cartesianProduct(medicament1.getSubstances(), medicament2.getSubstances());
        return combinaisons
            .parallelStream()
            .map(Unchecker.panic((List<Substance<?>> comb) -> client.get(comb.get(0), comb.get(1))))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(i -> new InteractionAvecMedicaments(i, medicament1, medicament2))
            .collect(Collectors.toSet());
    }

    static protected
    class InteractionAvecMedicaments
    implements IJSONSerializable
    {
        final Interaction interaction;
        final Medicament<?, ?, ?> medicament1;
        final Medicament<?, ?, ?> medicament2;
        final Convertisseur convertisseur;

        @SneakyThrows(DBException.class)
        private InteractionAvecMedicaments(
            Interaction interaction,
            Medicament<?, ?, ?> medicament1,
            Medicament<?, ?, ?> medicament2
        ) {
            this.interaction = interaction;
            this.medicament1 = medicament1;
            this.medicament2 = medicament2;
            this.convertisseur = new Convertisseur();
        }

        @Override
        @SneakyThrows({DBException.class, IOException.class})
        public JSONObject toJSON() {
            return convertisseur.toJSON(interaction, medicament1, medicament2);
        }
    }
}
