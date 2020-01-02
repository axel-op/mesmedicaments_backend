package app.mesmedicaments.recherche;

import app.mesmedicaments.HttpClient;
import app.mesmedicaments.JSONArrays;

import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SearchClient {

    private final Logger logger;
    // TODO ajouter aux var env
    private final String baseUrl = "https://mesmedicaments.search.windows.net";
    private final String adminKey = "4432617016088405A0A4BC2498A9846B";
    private final String queryKey = "0C518540A4EA1EB55AE166A5C6979E59";
    private final String indexName = "index-medicaments";
    private final String apiVersion = "2019-05-06";

    protected SearchClient(Logger logger) {
        this.logger = logger;
    }

    protected JSONArray queryDocuments(JSONObject query) throws IOException {
        final String url = baseUrl + "/indexes/" + indexName + "/docs/search?api-version=" + apiVersion;
        final JSONArray results = new JSONArray();
        String content = query.toString();
        boolean toContinue = true;
        while (toContinue) {
            final JSONObject response = new JSONObject(post(url, queryKey, content));
            JSONArrays.append(results, response.getJSONArray("value"));
            final String keyNextPage = "@search.nextPageParameters";
            toContinue = response.has(keyNextPage);
            if (toContinue) {
                content = response.getJSONObject(keyNextPage).toString();
            }
        }
        return results;
    }



    protected String uploadDocuments(String documents) throws IOException {
        logger.info("Envoi des documents pour indexation...");
        final String url =
                baseUrl + "/indexes/" + indexName + "/docs/index?api-version=" + apiVersion;
        final String rep = post(url, adminKey, documents);
        logger.info(rep);
        return rep;
    }


    private String post(String url, String key, String contents) throws IOException {
        contents = contents == null ? "" : contents;
        final Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("api-key", key);
        requestProperties.put("content-type", "application/json");
        final InputStream responseStream = new HttpClient().post(url, requestProperties, contents);
        final String corpsRep =
                CharStreams.toString(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
        return corpsRep;
    }
}
