package app.mesmedicaments.azure.fonctions.publiques;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

import app.mesmedicaments.azure.fonctions.Convertisseur;
import app.mesmedicaments.dmp.DMPUtils;
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
            final var dmpUtils = new DMPUtils(logger);
            final Map<LocalDate, Set<MedicamentFrance>> medsParDate =
                    meds.parallelStream().map(Unchecker.panic((Ligne m) -> {
                        final var date = m.getDateDelivrance();
                        final var result = dmpUtils.getMedicamentFromDb(m);
                        return result.map(r -> new AbstractMap.SimpleEntry<>(date, r));
                    })).filter(Optional::isPresent).map(Optional::get)
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                                final Set<MedicamentFrance> set = new HashSet<>();
                                set.add(e.getValue());
                                return set;
                            }, (s1, s2) -> {
                                s1.addAll(s2);
                                return s1;
                            }));
            corpsReponse.put("medicaments", medicamentsEnJson(medsParDate));
            codeHttp = HttpStatus.OK;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONObject medicamentsEnJson(Map<LocalDate, Set<MedicamentFrance>> medsParDate) {
        final JSONObject json = new JSONObject();
        medsParDate.forEach((d, s) -> json.put(d.toString(),
                s.stream().map(Convertisseur::toJSON).collect(Collectors.toSet())));
        return json;
    }
}
