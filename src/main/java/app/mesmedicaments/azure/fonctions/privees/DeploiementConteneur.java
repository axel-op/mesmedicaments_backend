package app.mesmedicaments.azure.fonctions.privees;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.utils.ClientHttp;
import app.mesmedicaments.utils.MultiMap;
import app.mesmedicaments.utils.Utils;

public
class DeploiementConteneur {

    @FunctionName("containerDeployment")
    public void containerDeployment(
        @TimerTrigger(
            name = "containerDeploymentTrigger", 
            schedule = "0 0 0 1,15 * *")
        final String timerInfo,
        final ExecutionContext context
    ) {
        final Logger logger = context.getLogger();
        try {
            final String containerName = "maintenance";
            final String accessToken = getAccessToken();
            final ClientHttp httpClient = new ClientHttp(logger);
            final MultiMap<String, String> requestProperties = new MultiMap<>();
            requestProperties.add("Authorization", "Bearer " + accessToken);

            // https://management.azure.com/subscriptions/{subscriptionId}/resourceGroups/
            // {resourceGroupName}/providers/Microsoft.ContainerInstance/containerGroups/
            // {containerGroupName}
            final String urlBase = "https://management.azure.com/subscriptions/"
                                    + Environnement.AZURE_SUBSCRIPTIONID + "/"
                                    + "resourceGroups/"
                                    + Environnement.AZURE_RESOURCEGROUP + "/"
                                    + "providers/Microsoft.ContainerInstance/containerGroups/"
                                    + containerName;

            // CREATE OR UPDATE
            // PUT {urlBase}?api-version=2018-10-01"
            final String urlCreate = urlBase + "?api-version=2018-10-01";

            final JSONArray envVariables = new JSONArray();
            for (Entry<String, String> e : System.getenv().entrySet()) {
                if (e.getKey().matches("[A-z_]+")) {
                    envVariables.put(new JSONObject()
                                    .put("name", e.getKey())
                                    .put("secureValue", e.getValue()));
                }
            }

            final JSONObject resources = new JSONObject()
                .put("requests", new JSONObject()
                    .put("memoryInGB", 1.5)
                    .put("cpu", 1));
            
            final JSONObject container = new JSONObject()
                .put("name", containerName)
                .put("properties", new JSONObject()
                    .put("image", Environnement.MAINTENANCE_IMAGE)
                    .put("environmentVariables", envVariables)
                    .put("resources", resources));

            final JSONArray containers = new JSONArray()
                .put(container);

            final JSONArray regCredentials = new JSONArray()
                .put(new JSONObject()
                    .put("server", "index.docker.io")
                    .put("username", Environnement.MAINTENANCE_DOCKERUSERNAME)
                    .put("password", Environnement.MAINTENANCE_DOCKERTOKEN));
            
            final JSONObject diagnostics = new JSONObject()
                .put("logAnalytics", new JSONObject()
                    .put("workspaceId", Environnement.MAINTENANCE_LOGANALYTICSID)
                    .put("workspaceKey", Environnement.MAINTENANCE_LOGANALYTICSKEY)
                    .put("logType", "ContainerInsights"));

            final JSONObject properties = new JSONObject()
                .put("containers", containers)
                .put("imageRegistryCredentials", regCredentials)
                .put("diagnostics", diagnostics)
                .put("osType", "Linux")
                .put("restartPolicy", "Never");

            final JSONObject requestBody = new JSONObject()
                .put("location", "westeurope")
                .put("properties", properties);

            final MultiMap<String, String> headersCreate = new MultiMap<>(requestProperties);
            headersCreate.add("Content-Type", "application/json");
            final InputStream isCreate = httpClient.send("PUT", urlCreate, headersCreate, requestBody.toString());
            logger.info(Utils.stringify(new InputStreamReader(isCreate)));

            // START
            // POST {urlBase}/start?api-version=2018-10-01
            final String urlStart = urlBase + "/start?api-version=2018-10-01";
            final MultiMap<String, String> headersStart = new MultiMap<>(requestProperties);
            final InputStream isStart = httpClient.post(urlStart, headersStart, null);
            logger.info(Utils.stringify(new InputStreamReader(isStart)));            
            
        } catch (IOException | URISyntaxException e) {
            Utils.logErreur(e, logger);
            throw new RuntimeException(e);
        }
    }

    private String getAccessToken() throws IOException, URISyntaxException {
        String url = System.getenv("MSI_ENDPOINT");
        final String secret = System.getenv("MSI_SECRET");
        if (url == null || secret == null)
            throw new RuntimeException("Cannot find environment variable "
                                        + url == null ? "MSI_ENDPOINT" : "MSI_SECRET");
        url += "?resource=https://management.azure.com&api-version=2017-09-01";
        final MultiMap<String, String> headers = new MultiMap<>();
        headers.add("Content-Type", "application/json");
        headers.add("Secret", secret);
        final ClientHttp client = new ClientHttp();
        final InputStream is = client.get(url, headers);
        final JSONObject response = new JSONObject(Utils.stringify(new InputStreamReader(is)));
        return response.getString("access_token");
    }
}