package app.mesmedicaments.recherche;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.io.CharStreams;

import app.mesmedicaments.HttpClient;

public class SearchClient {

    private final Logger logger;
    // TODO ajouter aux var env
    private final String url = "https://mesmedicaments.search.windows.net";
    private final String adminKey = "4432617016088405A0A4BC2498A9846B";
    private final String indexName = "index-medicaments";
    private final String apiVersion = "2019-05-06";

    protected SearchClient(Logger logger) {
        this.logger = logger;
    }

    protected String uploadDocuments(String documents) throws IOException {
        logger.info("Envoi des documents pour indexation...");
        final String endpoint = url + "/indexes/" + indexName + "/docs/index?api-version=" + apiVersion;
        final String rep = post(endpoint, adminKey, documents);
        logger.info(rep);
        return rep;
    }

    private String post(String url, String key, String contents) throws IOException {
        contents = contents == null ? "" : contents;
        final Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("api-key", key);
        requestProperties.put("content-type", "application/json");
        final InputStream responseStream = new HttpClient().post(url, requestProperties, contents);
        final String corpsRep = CharStreams.toString(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
        return corpsRep;
    }
}
