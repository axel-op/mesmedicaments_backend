package app.mesmedicaments.api.privee;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteConnexion;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.azure.storage.StorageException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.util.logging.Logger;

public final class NettoyageConnexions {

    @FunctionName("nettoyageConnexions")
    public void nettoyageConnexions(
            @TimerTrigger(name = "nettoyageConnexionsTrigger", schedule = "0 */15 * * * *") final String timerInfo,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        LocalDateTime maintenant = LocalDateTime.now();
        try {
            for (EntiteConnexion entiteC : EntiteConnexion.obtenirToutesLesEntites()) {
                LocalDateTime heureEntite = LocalDateTime.ofInstant(entiteC.getTimestamp().toInstant(), Utils.TIMEZONE);
                if (heureEntite.isBefore(maintenant.minusHours(1))) {
                    logger.info("EntiteConnexion supprimée : " + entiteC.getRowKey() + " (heure associée : "
                            + heureEntite.toString() + ")");
                    entiteC.supprimerEntite();
                }
            }
        } catch (StorageException | URISyntaxException | InvalidKeyException e) {
            Utils.logErreur(e, logger);
        }
    }
}
