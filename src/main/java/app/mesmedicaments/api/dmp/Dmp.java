package app.mesmedicaments.api.dmp;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.json.JSONObject;
import app.mesmedicaments.api.Commun;
import app.mesmedicaments.api.Convertisseur;
import app.mesmedicaments.azure.tables.clients.ClientTableStatistiquesDmp;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.dmp.DMPHomePage;
import app.mesmedicaments.dmp.documents.DMPDocument;
import app.mesmedicaments.dmp.documents.DMPDocumentListPage;
import app.mesmedicaments.dmp.documents.readers.DMPDonnesRemboursementReader;
import app.mesmedicaments.dmp.documents.readers.DMPDonneesRemboursement.Ligne;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public final class Dmp {

    @FunctionName("dmp")
    public HttpResponseMessage dmp(@HttpTrigger(name = "dmpTrigger",
            authLevel = AuthorizationLevel.ANONYMOUS, methods = {HttpMethod.POST},
            route = "dmp/{categorie:alpha}") final HttpRequestMessage<Optional<String>> request,
            @BindingName("categorie") final String categorie, final ExecutionContext context) {
        final Logger logger = context.getLogger();
        final JSONObject corpsRequete = new JSONObject(request.getBody().get());
        final JSONObject corpsReponse = new JSONObject();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        try {
            if (!categorie.equals("medicaments"))
                return Commun.construireReponse(HttpStatus.BAD_REQUEST, request);
            final var donneesConnexion = corpsRequete.getJSONObject("donneesConnexion");
            final var homePage = new DMPHomePage(donneesConnexion);
            final var docsPage = DMPDocumentListPage.fromHomePage(homePage);
            final DMPDocument doc = docsPage.listDocuments().stream()
                    .filter(d -> d.getTitre().matches("Données de remboursement")).findFirst()
                    .orElseThrow();
            final var reader = new DMPDonnesRemboursementReader();
            final var meds = doc.read(reader::readDocument).getMedicaments();
            CompletableFuture
                    .runAsync(() -> saveLibellesToDb(meds.stream().map(Ligne::getLibelle), logger));
            final var dmpUtils = new DMPUtils(logger);
            final Multimap<LocalDate, MedicamentFrance> medsParDate =
                    meds.parallelStream().map(Unchecker.panic((Ligne m) -> {
                        final var date = m.getDateDelivrance();
                        final var result = dmpUtils.getMedicamentFromDb(m);
                        return result.map(r -> new AbstractMap.SimpleEntry<>(date, r));
                    })).filter(Optional::isPresent).map(Optional::get)
                            .collect(Multimaps.toMultimap(Map.Entry::getKey, Map.Entry::getValue,
                                    MultimapBuilder.hashKeys().hashSetValues()::build));
            corpsReponse.put("medicaments", medicamentsEnJson(medsParDate.asMap()));
            codeHttp = HttpStatus.OK;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONObject medicamentsEnJson(Map<LocalDate, Collection<MedicamentFrance>> medsParDate) {
        final JSONObject json = new JSONObject();
        medsParDate.forEach((d, s) -> json.put(d.toString(),
                s.stream().map(Convertisseur::toJSON).distinct().collect(Collectors.toSet())));
        return json;
    }

    private void saveLibellesToDb(Stream<String> libelles, Logger logger) {
        try {
            final var client = new ClientTableStatistiquesDmp();
            client.incrementSearchCounts(libelles.collect(Collectors.toSet()));
        } catch (ExceptionTable e) {
            Utils.logErreur(e, logger);
        }
    }
}
