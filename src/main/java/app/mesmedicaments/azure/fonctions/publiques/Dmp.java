package app.mesmedicaments.azure.fonctions.publiques;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONObject;

import app.mesmedicaments.azure.fonctions.Convertisseur;
import app.mesmedicaments.dmp.DMP;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.Utils;

public final class Dmp {

    @FunctionName("dmp")
    public HttpResponseMessage dmp(
            @HttpTrigger(name = "dmpTrigger", authLevel = AuthorizationLevel.ANONYMOUS, methods = {
                    HttpMethod.POST }, route = "dmp/{categorie:alpha}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("categorie") final String categorie, final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsRequete = new JSONObject(request.getBody().get());
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            if (!categorie.equals("medicaments"))
                return Commun.construireReponse(HttpStatus.BAD_REQUEST, request);
            final JSONObject donneesConnexion = corpsRequete.getJSONObject("donneesConnexion");
            verifierDate(donneesConnexion);
            final DMP dmp = new DMP(corpsRequete.getString("urlRemboursements"), obtenirCookies(donneesConnexion),
                    logger);
            final Map<LocalDate, Set<MedicamentFrance>> medsParDate = dmp.obtenirMedicaments();
            corpsReponse.put("medicaments", medicamentsEnJson(medsParDate));
            codeHttp = HttpStatus.OK;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONObject medicamentsEnJson(Map<LocalDate, Set<MedicamentFrance>> medsParDate) {
        final JSONObject json = new JSONObject();
        medsParDate.forEach(
                (d, s) -> json.put(d.toString(), s.stream().map(Convertisseur::toJSON).collect(Collectors.toSet())));
        return json;
    }

    private Map<String, String> obtenirCookies(JSONObject donneesConnexion) {
        final JSONObject cookiesJson = donneesConnexion.getJSONObject("cookies");
        return cookiesJson.keySet().stream().collect(Collectors.toMap(k -> k, k -> cookiesJson.getString(k)));
    }

    private void verifierDate(JSONObject donneesConnexion) throws IllegalArgumentException {
        if (LocalDateTime.parse(donneesConnexion.getString("date"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .isBefore(LocalDateTime.now().minusMinutes(30)))
            throw new IllegalArgumentException("Plus de 30 minutes se sont écoulées depuis la connexion");
    }
}
