package app.mesmedicaments.api.dmp;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import app.mesmedicaments.api.medicaments.ClientTableMedicamentsFrance;
import app.mesmedicaments.azure.recherche.ClientRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.ModeRecherche;
import app.mesmedicaments.azure.recherche.ClientRecherche.NiveauRecherche;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.dmp.documents.readers.DMPDonneesRemboursement;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.Sets;

final class DMPUtils {

    static private final ConcurrentMap<String, String> cacheTransformationMot =
            new ConcurrentHashMap<>();

    private final ClientRecherche clientRecherche;
    private final ClientTableMedicamentsFrance clientTable;

    public DMPUtils(Logger logger) throws DBExceptionTableAzure {
        this.clientRecherche = new ClientRecherche(logger);
        this.clientTable = new ClientTableMedicamentsFrance();
    }

    public Optional<MedicamentFrance> getMedicamentFromDb(DMPDonneesRemboursement.Ligne med)
            throws JSONException, DBException, IOException {
        final var libelle = med.getLibelle();
        if (libelle.matches(" *"))
            return Optional.empty();
        final var tokens =
                Sets.fromArray(libelle.split(" ")).stream().map(DMPUtils::transformerToken)
                        .filter(m -> !m.equals("")).collect(Collectors.toList());
        if (tokens.isEmpty() || tokens.contains("verre") || tokens.contains("monture"))
            return Optional.empty();
        return makeRequest(tokens);
    }

    private Optional<MedicamentFrance> makeRequest(Collection<String> tokens)
            throws JSONException, DBException, IOException {
        final var request = String.join(" ", tokens);
        Optional<JSONObject> resultat = Optional.empty();
        for (ModeRecherche mode : ModeRecherche.ordonnees()) {
            if (resultat.isPresent())
                break;
            resultat = clientRecherche.search(request).niveau(NiveauRecherche.Exacte).mode(mode)
                    .avecNombreMaxResultats(1).getResultats().getBest();
        }
        if (!resultat.isPresent())
            return Optional.empty();
        return clientTable.get(String.valueOf(resultat.get().getInt("code")));
    }

    static private String transformerToken(String motATransformer) {
        return cacheTransformationMot.computeIfAbsent(motATransformer, mot -> {
            mot = mot.toLowerCase();
            if (mot.matches("[^a-z0-9,]+"))
                return "";
            if (mot.matches("[0-9,].*"))
                mot = mot.split("[^0-9,]")[0];
            if (mot.matches("[^0-9]+[0-9].*"))
                mot = mot.split("[0-9]")[0];
            if (mot.equals("mg") || mot.equals("-"))
                return "";
            switch (mot) {
                case "myl":
                    return "mylan";
                case "sdz":
                    return "sandoz";
                case "bga":
                    return "biogaran";
                case "tvc":
                    return "teva";
                case "sol":
                    return "solution";
                case "solbu":
                    return "solution buvable";
                case "cpr":
                    return "comprimé";
                case "eff":
                    return "effervescent";
                case "inj":
                    return "injectable";
                case "ser":
                    return "seringue";
                case "fl":
                    return "flacon";
                case "susp":
                    return "suspension";
            }
            return mot;
        });
    }

}
