package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.connexion.Authentificateur;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.entitestables.EntiteMedicamentBelgique;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Synchronisation {

    @FunctionName("synchronisation")
    public HttpResponseMessage synchronisation(
            @HttpTrigger(
                            name = "synchronisationTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.POST, HttpMethod.GET},
                            route = "synchronisation/{categorie:alpha}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("categorie") final String categorie,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            final String accessToken = request.getHeaders().get(Commun.HEADER_AUTHORIZATION);
            final String id = Authentificateur.getIdFromToken(accessToken);
            final EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
            if (categorie.equalsIgnoreCase("obtenir")) {
                final JSONObject medsPerso = new JSONObject();
                entiteU.getMedicamentsPersoMap()
                        .entrySet()
                        .parallelStream()
                        .forEach(
                                e -> {
                                    final Map<Pays, Set<Long>> parPays = e.getValue();
                                    final Set<AbstractEntiteMedicament<? extends Presentation>>
                                            entitesM = new HashSet<>();
                                    if (parPays.containsKey(Pays.France))
                                        entitesM.addAll(
                                                EntiteMedicamentFrance.obtenirEntites(
                                                        parPays.get(Pays.France), false, logger));
                                    if (parPays.containsKey(Pays.Belgique))
                                        entitesM.addAll(
                                                EntiteMedicamentBelgique.obtenirEntites(
                                                        parPays.get(Pays.Belgique), false, logger));
                                    medsPerso.put(
                                            e.getKey().toString(),
                                            entitesM.parallelStream()
                                                    .map(
                                                            entite ->
                                                                    Utils.medicamentEnJson(
                                                                            entite, logger))
                                                    .collect(Collectors.toSet()));
                                });
                final JSONObject corpsReponse = new JSONObject().put("medicamentsPerso", medsPerso);
                return Commun.construireReponse(HttpStatus.OK, corpsReponse, request);
            }
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            if (categorie.equalsIgnoreCase("ajouter")) {
                final JSONArray medicaments = corpsRequete.getJSONArray("medicaments");
                for (int i = 0; i < medicaments.length(); i++) {
                    final JSONObject medicament = medicaments.getJSONObject(i);
                    final Pays pays = Pays.obtenirPays(medicament.getString("pays"));
                    final long code = medicament.getLong("code");
                    entiteU.ajouterMedicamentPerso(pays, code);
                }
                entiteU.mettreAJourEntite();
                codeHttp = HttpStatus.OK;
            } else if (categorie.equalsIgnoreCase("retirer")) {
                final JSONObject medicament = corpsRequete.getJSONObject("medicament");
                final Pays pays = Pays.obtenirPays(medicament.getString("pays"));
                final long code = medicament.getLong("code");
                final LocalDate date =
                        LocalDate.parse(
                                medicament.getString("dateAchat"),
                                DateTimeFormatter.ISO_LOCAL_DATE);
                entiteU.retirerMedicamentPerso(pays, code, date);
                entiteU.mettreAJourEntite();
                codeHttp = HttpStatus.OK;
            } else throw new IllegalArgumentException("La catégorie de route n'existe pas");
        } catch (final JSONException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.UNAUTHORIZED;
        } catch (NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, request);
    }
}
