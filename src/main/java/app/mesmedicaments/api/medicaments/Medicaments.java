package app.mesmedicaments.api.medicaments;

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
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.api.Convertisseur;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.Utils;

public final class Medicaments {

    @FunctionName("medicaments")
    public HttpResponseMessage medicaments(
        @HttpTrigger(
            name = "medicamentsTrigger", 
            authLevel = AuthorizationLevel.ANONYMOUS, 
            methods = {HttpMethod.POST, HttpMethod.GET}, 
            route = "medicaments/{pays=null}/{code=0}") 
        final HttpRequestMessage<Optional<String>> request,
        @BindingName("pays") String pays, 
        @BindingName("code") int code, 
        final ExecutionContext context
    ) {
        final Logger logger = context.getLogger();
        final JSONObject reponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            final Parameters params = parseRequest(request, pays, code);
            final Optional<MedicamentFrance> optMed = getMedicament(params);
            if (optMed.isPresent()) {
                reponse.put("medicament", new Convertisseur().toJSON(optMed.get()));
                codeHttp = HttpStatus.OK;
            } else
                codeHttp = HttpStatus.NOT_FOUND;
        } catch (JSONException | NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, reponse, request);
    }

    private Optional<MedicamentFrance> getMedicament(Parameters params) throws DBExceptionTableAzure, DBException {
        final Pays pays = Pays.fromCode(params.pays);
        assertion(pays.equals(Pays.France.instance));
        return new ClientTableMedicamentsFrance().get(params.code);
    }

    private Parameters parseRequest(HttpRequestMessage<Optional<String>> request, String pays, int code) {
        final int version = Commun.getCodeVersion(request);
        final HttpMethod method = request.getHttpMethod();
        if (version > 40) {
            assertion(method.equals(HttpMethod.GET));
            return new Parameters(pays, code);
        }
        assertion(method.equals(HttpMethod.POST));
        final JSONObject body = new JSONObject(request.getBody().get());
        final JSONObject params = body.getJSONObject("medicament");
        return new Parameters(params.getString("pays"), params.getInt("code"));
    }

    private void assertion(boolean condition) throws IllegalArgumentException {
        if (!condition)
            throw new IllegalArgumentException();
    }

    static private class Parameters {
        private final String pays;
        private final int code;

        private Parameters(String pays, int code) {
            this.pays = pays;
            this.code = code;
        }
    }
}
