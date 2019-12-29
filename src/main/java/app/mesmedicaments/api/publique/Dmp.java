package app.mesmedicaments.api.publique;

import app.mesmedicaments.Utils;
import app.mesmedicaments.connexion.Authentification;
import app.mesmedicaments.connexion.DMP;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteUtilisateur;
import app.mesmedicaments.unchecked.Unchecker;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import io.jsonwebtoken.JwtException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.JSONObject;

final class Dmp {

    private Dmp() {}

    // mettre une doc
    @FunctionName("dmp")
    public HttpResponseMessage dmp(
            @HttpTrigger(
                            name = "dmpTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.GET},
                            route = "dmp/{categorie:alpha}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("categorie") final String categorie,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final String accessToken = request.getHeaders().get(Commun.HEADER_AUTHORIZATION);
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            // verifierHeure(request.getHeaders().get(CLE_HEURE), 2);
            final String id = Authentification.getIdFromToken(accessToken);
            if (categorie.equalsIgnoreCase("medicaments")) {
                final Optional<EntiteConnexion> optEntiteC = EntiteConnexion.obtenirEntite(id);
                if (!optEntiteC.isPresent())
                    throw new IllegalArgumentException("Pas d'entité Connexion trouvée");
                final EntiteConnexion entiteC = optEntiteC.get();
                if (Utils.dateToLocalDateTime(entiteC.getTimestamp())
                        .isBefore(LocalDateTime.now().minusMinutes(30)))
                    throw new IllegalArgumentException(
                            "Plus de 30 minutes se sont écoulées depuis la connexion");
                final DMP dmp =
                        new DMP(
                                entiteC.getUrlFichierRemboursements(),
                                entiteC.obtenirCookiesMap(),
                                logger);
                final Map<LocalDate, Set<Long>> medsParDate = dmp.obtenirMedicaments(logger);
                final EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id).get();
                entiteU.ajouterMedicamentsDMP(medsParDate);
                entiteU.mettreAJourEntite();
                final JSONObject medsEnJson = new JSONObject();
                medsParDate
                        .entrySet()
                        .parallelStream()
                        .forEach(
                                e ->
                                        medsEnJson.put(
                                                e.getKey().toString(),
                                                EntiteMedicamentFrance.obtenirEntites(
                                                                e.getValue(), false, logger)
                                                        .stream()
                                                        .map(
                                                                Unchecker.wrap(
                                                                        logger,
                                                                        entite ->
                                                                                Commun
                                                                                                .utiliserDepreciees(
                                                                                                        request)
                                                                                        ? Utils
                                                                                                .medicamentFranceEnJsonDepreciee(
                                                                                                        entite,
                                                                                                        logger)
                                                                                        : Utils
                                                                                                .medicamentEnJson(
                                                                                                        entite,
                                                                                                        logger)))
                                                        .collect(Collectors.toSet())));
                corpsReponse.put("medicaments", medsEnJson);
                codeHttp = HttpStatus.OK;
            }
        } catch (JwtException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.UNAUTHORIZED;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }
}
