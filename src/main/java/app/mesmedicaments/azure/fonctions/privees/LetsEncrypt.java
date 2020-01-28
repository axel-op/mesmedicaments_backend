package app.mesmedicaments.azure.fonctions.privees;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.utils.JSONObjectUneCle;
import app.mesmedicaments.utils.Utils;

public class LetsEncrypt {

    @FunctionName("letsEncryptChallenge")
    public HttpResponseMessage letsEncryptChallenge(
            @HttpTrigger(
                            name = "letsEncryptChallengeTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.GET},
                            route = ".well-known/acme-challenge/{code}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("code") String code,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        // String functionDirectory =
        // System.getenv("EXECUTION_CONTEXT_FUNCTIONDIRECTORY");
        // logger.info("function directory = " + functionDirectory);
        logger.info("code = " + code);
        logger.info("current relative path = " + Paths.get("").toAbsolutePath().toString());
        String file = "D:\\home\\site\\wwwroot\\.well-known\\acme-challenge\\" + code;
        logger.info("file path = " + file);
        Path path = Paths.get(file);
        logger.info("path used = " + path.toAbsolutePath().toString());
        try {
            String contenu = new String(Files.readAllBytes(path));
            logger.info("contenu = " + contenu);
            return request.createResponseBuilder(HttpStatus.OK).body(contenu).build();
        } catch (IOException e) {
            Utils.logErreur(e, logger);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @FunctionName("letsEncrypt")
    public void letsEncrypt(
            @TimerTrigger(name = "letsEncryptTrigger", schedule = "0 0 0 1 * *")
                    final String timerInfo,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        final String functionAppName = "mesmedicaments";
        final String userName = "$" + functionAppName;
        final String userPwd = Environnement.LETSENCRYPT_USERPASSWORD;
        final String pfxPassword = Environnement.LETSENCRYPT_PFXPASSWORD;
        final String clientId = Environnement.LETSENCRYPT_CLIENTID;
        final String clientSecret = Environnement.LETSENCRYPT_CLIENTSECRET;
        final String tenant = Environnement.LETSENCRYPT_TENANT;
        final String resourceGroupName = Environnement.LETSENCRYPT_RESOURCEGROUPNAME;
        final String subscriptionId = Environnement.LETSENCRYPT_SUBSCRIPTIONID;
        final Config configBody =
                new Config(
                        new AzureEnvironment(
                                "mesmedicaments",
                                clientId,
                                clientSecret,
                                resourceGroupName,
                                subscriptionId,
                                tenant),
                        new AcmeConfig(
                                "contact@mesmedicaments.app",
                                "",
                                new String[] {},
                                2048,
                                pfxPassword,
                                true),
                        new CertificateSettings(false),
                        new AuthorizationChallengeProviderConfig(false));
        try {
            HttpsURLConnection connClient =
                    (HttpsURLConnection)
                            new URL(
                                            "https://"
                                                    + functionAppName
                                                    + ".scm.azurewebsites.net/letsencrypt/api/certificates/challengeprovider/http/kudu/certificateinstall/azurewebapp"
                                                    + "?api-version=2017-09-01")
                                    .openConnection();
            connClient.setRequestProperty(
                    "Authorization",
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString((userName + ":" + userPwd).getBytes("UTF-8")));
            configBody.acmeConfig.host = "api.mesmedicaments.app";
            createCertificate(configBody, functionAppName, connClient, logger);
            /*
             * configBody.acmeConfig.host = "legal.mesmedicaments.app";
             * createCertificate(configBody, functionAppName, connClient, logger);
             */
        } catch (IOException e) {
            Utils.logErreur(e, logger);
        }
    }

    private void createCertificate(
            Config config, String functionAppName, HttpsURLConnection connection, Logger logger)
            throws IOException {
        logger.info("createCertificate starts");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        OutputStreamWriter ows = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
        // String corps = new JSONObject(config).toString();
        String corps = config.toJson().toString();
        ows.write(corps);
        logger.info("Corps de la requête = " + corps);
        ows.close();
        connection.connect();
        logger.info("response code = " + connection.getResponseCode());
        logger.info("response message = " + connection.getResponseMessage());
        String reponse =
                new BufferedReader(new InputStreamReader(connection.getInputStream()))
                        .lines()
                        .collect(Collectors.joining(Utils.NEWLINE));
        logger.info("Réponse = " + reponse);
    }

    private class Config {
        private AzureEnvironment azureEnvironment;
        private AcmeConfig acmeConfig;
        private CertificateSettings certificateSettings;
        private AuthorizationChallengeProviderConfig authorizationChallengeProviderConfig;

        Config(
                AzureEnvironment azureEnvironment,
                AcmeConfig acmeConfig,
                CertificateSettings certificateSettings,
                AuthorizationChallengeProviderConfig authorizationChallengeProviderConfig) {
            this.azureEnvironment = azureEnvironment;
            this.acmeConfig = acmeConfig;
            this.certificateSettings = certificateSettings;
            this.authorizationChallengeProviderConfig = authorizationChallengeProviderConfig;
        }

        public JSONObject toJson() {
            return new JSONObject()
                    .put("AzureEnvironment", azureEnvironment.toJson())
                    .put("AcmeConfig", acmeConfig.toJson())
                    .put("CertificateSettings", certificateSettings.toJson())
                    .put(
                            "AuthorizationChallengeProviderConfig",
                            authorizationChallengeProviderConfig.toJson());
        }
    }

    private class AzureEnvironment {
        private String webAppName;
        private String clientId;
        private String clientSecret;
        private String resourceGroupName;
        private String subscriptionId;
        private String tenant;

        AzureEnvironment(
                String webAppName,
                String clientId,
                String clientSecret,
                String resourceGroupname,
                String subscriptionId,
                String tenant) {
            this.webAppName = webAppName;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.resourceGroupName = resourceGroupname;
            this.subscriptionId = subscriptionId;
            this.tenant = tenant;
        }

        public JSONObject toJson() {
            return new JSONObject()
                    .put("WebAppName", webAppName)
                    .put("ClientId", clientId)
                    .put("ClientSecret", clientSecret)
                    .put("ResourceGroupName", resourceGroupName)
                    .put("SubscriptionId", subscriptionId)
                    .put("Tenant", tenant);
        }
    }

    private class AcmeConfig {
        private String registrationEmail;
        private String host;
        private String[] alternateNames;
        private int RSAKeyLength;
        private String PFXPassword;
        private boolean useProduction;

        AcmeConfig(
                String registrationEmail,
                String host,
                String[] alternateNames,
                int RSAKeyLength,
                String PFXPassword,
                boolean useProduction) {
            this.registrationEmail = registrationEmail;
            this.host = host;
            this.alternateNames = alternateNames;
            this.RSAKeyLength = RSAKeyLength;
            this.PFXPassword = PFXPassword;
            this.useProduction = useProduction;
        }

        public JSONObject toJson() {
            return new JSONObject()
                    .put("RegistrationEmail", registrationEmail)
                    .put("Host", host)
                    .put("AlternateNames", alternateNames)
                    .put("RSAKeyLength", RSAKeyLength)
                    .put("PFXPassword", PFXPassword)
                    .put("UseProduction", useProduction);
        }
    }

    private class CertificateSettings {
        private boolean useIPBasedSSL;

        CertificateSettings(boolean useIPBasedSSL) {
            this.useIPBasedSSL = useIPBasedSSL;
        }

        public JSONObject toJson() {
            return new JSONObjectUneCle("UseIPBasedSSL", useIPBasedSSL);
        }
    }

    private class AuthorizationChallengeProviderConfig {
        private boolean disableWebConfigUpdate;

        AuthorizationChallengeProviderConfig(boolean disableWebConfigUpdate) {
            this.disableWebConfigUpdate = disableWebConfigUpdate;
        }

        public JSONObject toJson() {
            return new JSONObjectUneCle("DisableWebConfigUpdate", disableWebConfigUpdate);
        }
    }
}
