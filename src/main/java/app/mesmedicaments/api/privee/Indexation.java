package app.mesmedicaments.api.privee;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.collect.Sets;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.recherche.Indexeur;

public class Indexation {

    private Indexation() {
    }

    @FunctionName("indexation")
    public static HttpResponseMessage indexation(
            @HttpTrigger(name = "indexTrigger", dataType = "string", authLevel = AuthorizationLevel.FUNCTION, methods = {
                    HttpMethod.GET }, route = "indexation") final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        HttpStatus codeHttp = HttpStatus.OK;
        final long startTime = System.currentTimeMillis();
        final Logger logger = context.getLogger();
        try {
            final Set<AbstractEntiteMedicament<?>> meds = Sets
                    .newHashSet(EntiteMedicamentFrance.obtenirToutesLesEntites());
            logger.info("Médicaments récupérés");
            new Indexeur(meds, logger).indexer();
        } catch (Exception e) {
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String corps = "";
        return request.createResponseBuilder(codeHttp)
                .body(corps + " Durée totale : " + String.valueOf(System.currentTimeMillis() - startTime) + " ms")
                .build();
    }
}