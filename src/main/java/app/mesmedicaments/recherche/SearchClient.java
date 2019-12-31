package app.mesmedicaments.recherche;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.io.CharStreams;

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
        final HttpsURLConnection connexion = (HttpsURLConnection) new URL(url).openConnection();
        connexion.setRequestMethod("POST");
        connexion.setRequestProperty("api-key", key);
        connexion.setRequestProperty("content-type", "application/json");
        connexion.setDoOutput(true);
        final DataOutputStream dos = new DataOutputStream(connexion.getOutputStream());
        byte[] encodedText = contents.getBytes(StandardCharsets.UTF_8);
        dos.write(encodedText, 0, encodedText.length);
        dos.flush();
        dos.close();
        if (connexion.getResponseCode() != 200)
            throw new IOException(connexion.getResponseMessage());
        final String corpsRep = CharStreams.toString(
            new InputStreamReader(connexion.getInputStream(), StandardCharsets.UTF_8));
        return corpsRep;
    }
}
