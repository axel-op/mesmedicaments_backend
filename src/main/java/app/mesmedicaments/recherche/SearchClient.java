package app.mesmedicaments.recherche;

import app.mesmedicaments.HttpClient;
import app.mesmedicaments.JSONArrays;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class SearchClient {

    private static String getEnv(String variable) {
        return System.getenv("search_" + variable);
    }

    private static final String baseUrl = getEnv("baseurl");
    private static final String adminKey = getEnv("adminkey");
    private static final String queryKey = getEnv("querykey");
    private static final String indexName = getEnv("indexname");
    private static final String apiVersion = getEnv("apiversion");

    private final Logger logger;

    public SearchClient(Logger logger) {
        this.logger = logger;
    }

    public int getDocumentCount() throws IOException {
        final String url = baseUrl + "/indexes/" + indexName + "/stats?api-version=" + apiVersion;
        final JSONObject response = new JSONObject(send("GET", url, adminKey, null));
        return response.getInt("documentCount");
    }

    protected JSONArray queryDocuments(JSONObject query) throws IOException {
        final String url =
                baseUrl + "/indexes/" + indexName + "/docs/search?api-version=" + apiVersion;
        final JSONArray results = new JSONArray();
        String content = query.toString();
        boolean toContinue = true;
        while (toContinue) {
            final JSONObject response = new JSONObject(send("POST", url, queryKey, content));
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
        final String rep = send("POST", url, adminKey, documents);
        logger.info(rep);
        return rep;
    }

    private String send(String method, String url, String key, String contents) throws IOException {
        contents = contents == null ? "" : contents;
        final Multimap<String, String> requestProperties = HashMultimap.create();
        requestProperties.put("api-key", key);
        requestProperties.put("content-type", "application/json");
        final HttpClient client = new HttpClient();
        final InputStream responseStream =
                method.equals("GET")
                        ? client.get(url, requestProperties)
                        : client.post(url, requestProperties, contents);
        final String corpsRep =
                CharStreams.toString(new InputStreamReader(responseStream, StandardCharsets.UTF_8));
        return corpsRep;
    }
}
