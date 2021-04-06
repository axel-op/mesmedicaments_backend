package app.mesmedicaments.azure.fonctions.publiques;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import org.json.JSONObject;

import app.mesmedicaments.utils.JSONObjectUneCle;

final public class Commun {

    protected static final String CLE_VERSION = "versionapplication";
    public static final String HEADER_AUTHORIZATION = "jwt";

    private Commun() {}

    public static HttpResponseMessage construireReponse(
            HttpStatus codeHttp,
            String corpsReponse,
            HttpRequestMessage<Optional<String>> request
    ) {
        return request.createResponseBuilder(codeHttp)
                .header("Content-Type", "text/plain")
                .header(
                        "Content-Length",
                        String.valueOf(corpsReponse.getBytes(StandardCharsets.UTF_8).length))
                .body(corpsReponse)
                .build();
    }

    public static HttpResponseMessage construireReponse(
            HttpStatus codeHttp,
            JSONObject corpsReponse,
            HttpRequestMessage<Optional<String>> request
    ) {
        if (corpsReponse == null) return construireReponse(codeHttp, request);
        corpsReponse.put("heure", obtenirHeure().toString());
        return request.createResponseBuilder(codeHttp)
                .header("Content-Type", "application/json")
                .header(
                        "Content-Length",
                        String.valueOf(
                                corpsReponse.toString().getBytes(StandardCharsets.UTF_8).length))
                .body(corpsReponse.toString())
                .build();
    }

    public static HttpResponseMessage construireReponse(
            HttpStatus codeHttp, HttpRequestMessage<Optional<String>> request) {
        return construireReponse(
                codeHttp, new JSONObjectUneCle("heure", obtenirHeure().toString()), request);
    }

    public static int getCodeVersion(HttpRequestMessage<Optional<String>> request) {
        return Integer.parseInt(
            Optional
                .ofNullable(request.getHeaders().get(CLE_VERSION))
                .orElse("0")
        );
    }

    private static LocalDateTime obtenirHeure() {
        return LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
    }
}
