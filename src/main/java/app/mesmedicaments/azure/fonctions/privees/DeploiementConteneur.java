package app.mesmedicaments.azure.fonctions.privees;

import java.util.stream.Collectors;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.AppServiceMSICredentials;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerinstance.ContainerGroupRestartPolicy;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;

import app.mesmedicaments.Environnement;

public
class DeploiementConteneur {

    @FunctionName("containerDeployment")
    public void containerDeployment(
        @TimerTrigger(name = "containerDeploymentTrigger", schedule = "0 0 0 1,15 * *")
        final String timerInfo,
        final ExecutionContext context
    ) {
        final AppServiceMSICredentials credentials = new AppServiceMSICredentials(AzureEnvironment.AZURE);
        final Azure azure = Azure.authenticate(credentials)
            .withSubscription(Environnement.AZURE_SUBSCRIPTIONID);
        final String aciName = "maintenance";
        azure.containerGroups()
            .define(aciName)
            .withRegion(Region.EUROPE_WEST)
            .withExistingResourceGroup(Environnement.AZURE_RESOURCEGROUP)
            .withLinux()
            .withPrivateImageRegistry(
                "index.docker.io", 
                Environnement.MAINTENANCE_DOCKERUSERNAME, 
                Environnement.MAINTENANCE_DOCKERTOKEN)
            .withoutVolume()
            .defineContainerInstance(aciName)
                .withImage(Environnement.MAINTENANCE_IMAGE)
                .withoutPorts()
                .withEnvironmentVariableWithSecuredValue(System.getenv()
                                                                .entrySet()
                                                                .stream()
                                                                .filter(e -> e.getKey()
                                                                                .matches("[A-z_]+"))
                                                                .collect(Collectors.toMap(
                                                                    e -> e.getKey(),
                                                                    e -> e.getValue())))
            .attach()
            .withRestartPolicy(ContainerGroupRestartPolicy.NEVER)
            .withLogAnalytics(
                Environnement.MAINTENANCE_LOGANALYTICSID,
                Environnement.MAINTENANCE_LOGANALYTICSKEY)
            .create();
    }
}