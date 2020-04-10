package app.mesmedicaments.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

public class ClientHttp {

    final Logger logger;

    public ClientHttp() {
        this(null);
    }

    public ClientHttp(Logger logger) {
        this.logger = logger;
    }

    public InputStream get(String url) throws IOException {
        return get(url, null);
    }

    public InputStream get(
        String url,
        MultiMap<String, String> requestProperties
    )
        throws IOException
    {
        return send("GET", url, requestProperties, null);
    }

    public InputStream post(
        String url, 
        MultiMap<String, String> requestProperties, 
        String content
    )
        throws IOException
    {
        return send("POST", url, requestProperties, content);
    }

    public InputStream send(
        String method,
        String url,
        MultiMap<String, String> requestProperties,
        String content
    )
        throws IOException
    {
        return send(method, new URL(url), requestProperties, content);
    }

    public InputStream send(
        String method,
        URL url,
        MultiMap<String, String> requestProperties,
        String content
    )
        throws IOException
    {
        final URLConnection conn = url.openConnection();
        HttpURLConnection connection;
        if (conn instanceof HttpsURLConnection) connection = (HttpsURLConnection) conn;
        else connection = (HttpURLConnection) conn;
        connection.setRequestMethod(method);
        String logMessage = method + " " + url.toString();
        if (requestProperties != null) {
            logMessage += "\n\nHeaders:";
            for (Entry<String, String> e : requestProperties) {
                connection.addRequestProperty(e.getKey(), e.getValue());
                logMessage += "\n" + e.getKey() + ": " + e.getValue();
            }
        }
        if (content != null) {
            logMessage += "\n\nContent:\n" + content;
            connection.setDoOutput(true);
            final DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
            final byte[] encodedContent = content.getBytes(StandardCharsets.UTF_8);
            dos.write(encodedContent, 0, encodedContent.length);
            dos.flush();
            dos.close();
        }
        final int responseCode = connection.getResponseCode();
        if (logger != null) {
            logger.info(logMessage);
            logMessage = "Response code = " + responseCode + " " + connection.getResponseMessage();
            logger.log(
                (responseCode < 200 || responseCode > 299) ? Level.WARNING : Level.INFO,
                logMessage);
        }
        final InputStream errorStream = connection.getErrorStream();
        return errorStream == null
            ? connection.getInputStream()
            : errorStream;
    }
}
