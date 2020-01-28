package app.mesmedicaments.azure.fonctions.privees;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

public final class Reveil {

    @FunctionName("reveil")
    public void reveil(
            @TimerTrigger(name = "reveilTrigger", schedule = "0 */15 * * * *")
                    final String timerInfo,
            final ExecutionContext context)
            throws InterruptedException {
        Thread.sleep(2500);
    }
}
