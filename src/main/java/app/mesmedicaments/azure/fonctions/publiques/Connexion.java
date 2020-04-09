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
import app.mesmedicaments.dmp.DonneesConnexion;
import app.mesmedicaments.dmp.Authentificateur.ReponseConnexion;
import app.mesmedicaments.dmp.Authentificateur.ReponseConnexion1;
import app.mesmedicaments.dmp.Authentificateur.ReponseConnexion2;
import app.mesmedicaments.utils.Utils;

public final class Connexion {

    @FunctionName("connexion")
    public HttpResponseMessage connexion(
        @HttpTrigger(name = "connexionTrigger", 
            authLevel = AuthorizationLevel.ANONYMOUS, 
            methods = { HttpMethod.POST }, 
            dataType = "string", 
            route = "connexion/{etape:int}")
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("etape")
        final int etape,
        final ExecutionContext context
    ) {
        final JSONObject corpsReponse = new JSONObject();
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            final String id = corpsRequete.getString("id");
            final Authentificateur auth = new Authentificateur(logger, id);
            switch (etape) {
                case 1:
                    final String mdp = corpsRequete.getString("mdp");
                    final ReponseConnexion1 resultat = auth.connexionDMPPremiereEtape(mdp);
                    corpsReponse.put("donneesConnexion", resultat.donneesConnexion);
                    codeHttp = obtenirCodeHttp(resultat);
                    if (codeHttp == HttpStatus.OK) {
                        corpsReponse.put("envoiCode", resultat.modeEnvoiCode);
                    }
                    break;
                case 2:
                    final String code = String.valueOf(corpsRequete.getInt("code"));
                    final DonneesConnexion donneesConnexion = new DonneesConnexion(corpsRequete.getJSONObject("donneesConnexion"));
                    final ReponseConnexion2 resultat2 = auth.connexionDMPDeuxiemeEtape(code, donneesConnexion);
                    corpsReponse.put("donneesConnexion", resultat2.donneesConnexion);
                    codeHttp = obtenirCodeHttp(resultat2);
                    if (codeHttp == HttpStatus.OK) {
                        corpsReponse.put("urlRemboursements", resultat2.urlRemboursements);
                        if (resultat2.genre != null) corpsReponse.put("genre", resultat2.genre);
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (JSONException | NullPointerException | NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private HttpStatus obtenirCodeHttp(ReponseConnexion resultat) {
        switch (resultat.codeReponse) {
            case ok: return HttpStatus.OK;
            case erreurIds: return HttpStatus.CONFLICT;
            default: return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}

class IncorrectIdsException extends Exception {
    private static final long serialVersionUID = 1L;
}
