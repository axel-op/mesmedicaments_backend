package app.mesmedicaments.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class ClientHttp {

    public ClientHttp() {}

    public InputStream get(String URL) throws IOException {
        return get(URL, null);
    }

    public InputStream get(String URL, MultiMap<String, String> requestProperties)
            throws IOException {
        final HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();
        if (requestProperties != null) {
            requestProperties.forEach((k, v) -> connection.addRequestProperty(k, v));
        }
        return connection.getInputStream();
    }

    public InputStream post(String URL, MultiMap<String, String> requestProperties, String content)
            throws IOException {
        final HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();
        connection.setRequestMethod("POST");
        if (requestProperties != null) {
            requestProperties.forEach((k, v) -> connection.addRequestProperty(k, v));
        }
        connection.setDoOutput(true);
        final DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
        final byte[] encodedContent = content.getBytes(StandardCharsets.UTF_8);
        dos.write(encodedContent, 0, encodedContent.length);
        dos.flush();
        dos.close();
        return connection.getInputStream();
    }
}
