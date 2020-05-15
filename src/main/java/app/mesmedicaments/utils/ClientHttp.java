package app.mesmedicaments.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHttp {

    final Logger logger;

    public ClientHttp() {
        this(null);
    }

    public ClientHttp(Logger logger) {
        this.logger = logger;
    }

    public InputStream get(String url) throws IOException, URISyntaxException {
        return get(url, null);
    }

    public InputStream get(
        String url,
        MultiMap<String, String> requestProperties
    )
        throws IOException, URISyntaxException
    {
        return send("GET", url, requestProperties, null);
    }

    public InputStream post(
        String url, 
        MultiMap<String, String> requestProperties, 
        String content
    )
        throws IOException, URISyntaxException
    {
        return send("POST", url, requestProperties, content);
    }

    public InputStream send(
        String method,
        String url,
        MultiMap<String, String> requestProperties,
        String content
    )
        throws IOException, URISyntaxException
    {
        return send(method, new URL(url), requestProperties, content);
    }

    public InputStream send(
        String method,
        URL url,
        MultiMap<String, String> requestProperties,
        String content
    )
        throws IOException, URISyntaxException
    {
        final var request = HttpRequest.newBuilder(url.toURI())
                .headers(buildHeaders(requestProperties))
                .method(method, content != null
                                ? BodyPublishers.ofString(content)
                                : BodyPublishers.noBody())
                .build();
        try {
            final var response = HttpClient.newHttpClient()
                    .send(request, BodyHandlers.ofInputStream());
            final int statusCode = response.statusCode();
            if (logger != null) {
                final var level = statusCode < 200 || statusCode > 299 ? Level.WARNING : Level.INFO;
                final var message = new StringBuilder();
                message.append("Request to " + url.toString());
                message.append("\nResponse code = " + String.valueOf(statusCode));
                logger.log(level, message.toString());
            }
            return response.body();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String[] buildHeaders(MultiMap<String, String> headersMap) {
        if (headersMap == null) return new String[0];
        final var headers = new String[headersMap.size()];
        int index = 0;
        for (var entry : headersMap.entrySet()) {
            headers[index] = entry.getKey();
            headers[index + 1] = entry.getValue();
            index += 2;
        }
        return headers;
    }
}
