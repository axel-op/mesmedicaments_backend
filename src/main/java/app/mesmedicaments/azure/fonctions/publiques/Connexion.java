package app.mesmedicaments.azure.fonctions.publiques;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.dmp.Authentificateur;
import app.mesmedicaments.utils.Utils;

public final class Connexion {

    @FunctionName("connexion")
    public HttpResponseMessage connexion(
            @HttpTrigger(name = "connexionTrigger", authLevel = AuthorizationLevel.ANONYMOUS, methods = {
                    HttpMethod.POST }, dataType = "string", route = "connexion/{etape:int}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("etape") final int etape, final ExecutionContext context) {
        final JSONObject corpsReponse = new JSONObject();
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            final String id = corpsRequete.getString("id");
            final Authentificateur auth = new Authentificateur(logger, id);
            JSONObject resultat;
            switch (etape) {
                case 1:
                    final String mdp = corpsRequete.getString("mdp");
                    resultat = auth.connexionDMPPremiereEtape(mdp);
                    corpsReponse.put("donneesConnexion", resultat.getJSONObject("donneesConnexion"));
                    codeHttp = obtenirCodeHttp(resultat);
                    if (codeHttp == HttpStatus.OK) {
                        corpsReponse.put(Authentificateur.CLE_ENVOI_CODE,
                                resultat.getString(Authentificateur.CLE_ENVOI_CODE));
                    }
                    break;
                case 2:
                    final String code = String.valueOf(corpsRequete.getInt("code"));
                    final JSONObject donneesConnexion = corpsRequete.getJSONObject("donneesConnexion");
                    resultat = auth.connexionDMPDeuxiemeEtape(code, donneesConnexion);
                    codeHttp = obtenirCodeHttp(resultat);
                    if (codeHttp == HttpStatus.OK) {
                        corpsReponse.put("urlRemboursements", resultat.getString("urlRemboursements"));
                        if (resultat.has("genre"))
                            corpsReponse.put("genre", resultat.get("genre"));
                    } else {
                        corpsReponse.put("donneesConnexion", resultat.getJSONObject("donneesConnexion"));
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (JSONException | NullPointerException | NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private HttpStatus obtenirCodeHttp(JSONObject resultat) {
        if (resultat.isNull(Authentificateur.CLE_ERREUR))
            return HttpStatus.OK;
        return resultat.getString(Authentificateur.CLE_ERREUR).equals(Authentificateur.ERR_INTERNE)
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.CONFLICT;
    }
}
