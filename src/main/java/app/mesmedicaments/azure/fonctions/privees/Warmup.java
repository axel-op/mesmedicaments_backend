package app.mesmedicaments.azure.fonctions.privees;

import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

public final class Warmup {

    @FunctionName("warmup")
    public void warmup(
        @TimerTrigger(
            name = "warmupTrigger", 
            schedule = "0 */15 * * * *")
        final String timerInfo,
        final ExecutionContext context
    )
        throws InterruptedException
    {
        final Logger logger = context.getLogger();
        logger.fine("Fonction préchauffée 🔥");
    }
}
