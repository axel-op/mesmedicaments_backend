package app.mesmedicaments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import org.json.JSONObject;

public class LetsEncrypt {    

    @FunctionName("letsEncrypt")
    public void letsEncrypt(
        @TimerTrigger(
            name = "letsEncryptTrigger", 
            schedule = "0 0 0 1 * *"
        ) final String timerInfo,
        final ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        final String functionAppName = "mesmedicaments";
        final String userName = "$" + functionAppName;
        final String userPwd = System.getenv("letsencrypt_userpwd");
        final String pfxPassword = System.getenv("letsencrypt_pfxpwd");
        final String clientSecret = System.getenv("letsencrypt_clientsecret");
        final Config configBody = new Config(
            new AzureEnvironment(
                "mesmedicaments", 
                "8f13bfe0-6910-4e00-b8a6-ceec2cb7102b", 
                clientSecret,
                "mesmedicaments", 
                "cc55b04e-512c-49db-a5e6-aaed2abf0fcc", 
                "mesmedicaments.app"
            ),
            new AcmeConfig("contact@mesmedicaments.app", "", new String[] {}, 2048, pfxPassword, true),
            new CertificateSettings(false), 
            new AuthorizationChallengeProviderConfig(false)
        );
        try {
            HttpsURLConnection connClient = (HttpsURLConnection) new URL("https://" + functionAppName + ".scm.azurewebsites.net/letsencrypt/api/certificates/challengeprovider/http/kudu/certificateinstall/azurewebapp?api-version=2017-09-01")
                .openConnection();
            connClient.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((userName + ":" + userPwd).getBytes("UTF-8")));
            configBody.acmeConfig.host = "api.mesmedicaments.app";
            createCertificate(configBody, functionAppName, connClient, logger);
            /*configBody.acmeConfig.host = "legal.mesmedicaments.app";
            createCertificate(configBody, functionAppName, connClient, logger);*/
        }
        catch (IOException e) {
            Utils.logErreur(e, logger);
        }
        
    }

    private void createCertificate (Config config, String functionAppName, HttpsURLConnection connection, Logger logger) 
        throws IOException
    {
        logger.info("createCertificate starts");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        OutputStreamWriter ows = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
        String corps = new JSONObject(config).toString();
        ows.write(corps);
        logger.info("Corps = " + corps);
        ows.flush();
        ows.close();
        connection.connect();
        String reponse = new BufferedReader(new InputStreamReader(connection.getInputStream()))
            .lines()
            .collect(Collectors.joining(Utils.NEWLINE));
        logger.info("Réponse = " + reponse);
    }

    private class Config {
        private AzureEnvironment azureEnvironment;
        private AcmeConfig acmeConfig;
        private CertificateSettings certificateSettings;
        private AuthorizationChallengeProviderConfig authorizationChallengeProviderConfig;
        Config (
            AzureEnvironment azureEnvironment,
            AcmeConfig acmeConfig,
            CertificateSettings certificateSettings,
            AuthorizationChallengeProviderConfig authorizationChallengeProviderConfig
        ) {
            this.azureEnvironment = azureEnvironment;
            this.acmeConfig = acmeConfig;
            this.certificateSettings = certificateSettings;
            this.authorizationChallengeProviderConfig = authorizationChallengeProviderConfig;
        }
        public AzureEnvironment getAzureEnvironment () { return azureEnvironment; }
        public AcmeConfig getAcmeConfig () { return acmeConfig; }
        public CertificateSettings getCertificateSettings () { return certificateSettings; }
        public AuthorizationChallengeProviderConfig getAuthorizationChallengeProviderConfig () {
            return authorizationChallengeProviderConfig;
        }
    }

    private class AzureEnvironment {
        private String webAppName;
        private String clientId;
        private String clientSecret;
        private String resourceGroupName;
        private String subscriptionId;
        private String tenant;
        AzureEnvironment (
            String webAppName,
            String clientId,
            String clientSecret,
            String resourceGroupname,
            String subscriptionId,
            String tenant
        ) {
            this.webAppName = webAppName;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.resourceGroupName = resourceGroupname;
            this.subscriptionId = subscriptionId;
            this.tenant = tenant;
        }
        public String getWebAppName () { return webAppName; }
        public String getClientId () { return clientId; }
        public String getClientSecret () { return clientSecret; }
        public String getResourceGroupName () { return resourceGroupName; }
        public String getSubscriptionId () { return subscriptionId; }
        public String getTenant () { return tenant; }
    }

    private class AcmeConfig {
        private String registrationEmail;
        private String host;
        private String[] alternateNames;
        private int RSAKeyLength;
        private String PFXPassword;
        private boolean useProduction;
        AcmeConfig (
            String registrationEmail,
            String host,
            String[] alternateNames,
            int RSAKeyLength,
            String PFXPassword,
            boolean useProduction
        ) {
            this.registrationEmail = registrationEmail;
            this.host = host;
            this.alternateNames = alternateNames;
            this.RSAKeyLength = RSAKeyLength;
            this.PFXPassword = PFXPassword;
            this.useProduction = useProduction;
        }
        public String getRegistrationEmail () { return registrationEmail; }
        public String getHost () { return host; }
        public String[] getAlternateNames () { return alternateNames; }
        public int getRSAKeyLength () { return RSAKeyLength; }
        public String getPFXPassword () { return PFXPassword; }
        public boolean getUseProduction () { return useProduction; }
    }

    private class CertificateSettings {
        private boolean useIPBasedSSL;
        CertificateSettings (boolean useIPBasedSSL) {
            this.useIPBasedSSL = useIPBasedSSL;
        }
        public boolean getUseIPBasedSSL () { return useIPBasedSSL; }
    }

    private class AuthorizationChallengeProviderConfig {
        private boolean disableWebConfigUpdate;
        AuthorizationChallengeProviderConfig (boolean disableWebConfigUpdate) {
            this.disableWebConfigUpdate = disableWebConfigUpdate;
        }
        public boolean getDisableWebConfigUpdate () { return disableWebConfigUpdate; }
    }
}