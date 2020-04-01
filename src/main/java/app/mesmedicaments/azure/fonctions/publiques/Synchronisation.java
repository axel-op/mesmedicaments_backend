package app.mesmedicaments.azure.fonctions.publiques;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.azure.fonctions.Convertisseur;
import app.mesmedicaments.azure.tables.clients.ClientTableMedicamentsFrance;
import app.mesmedicaments.azure.tables.clients.ClientTableUtilisateur;
import app.mesmedicaments.dmp.Authentificateur;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.Utilisateur;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.utils.ConcurrentHashSet;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.JSONObjectUneCle;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public final class Synchronisation {

    @FunctionName("synchronisation")
    public HttpResponseMessage synchronisation(
            @HttpTrigger(
                            name = "synchronisationTrigger",
                            authLevel = AuthorizationLevel.ANONYMOUS,
                            methods = {HttpMethod.POST, HttpMethod.GET},
                            route = "synchronisation/{categorie:alpha}")
                    final HttpRequestMessage<Optional<String>> request,
            @BindingName("categorie") final String categorie,
            final ExecutionContext context) {
        final Logger logger = context.getLogger();
        HttpStatus codeHttp = HttpStatus.NOT_IMPLEMENTED;
        JSONObject corpsReponse = null;
        try {
            final JSONObject corpsRequete = new JSONObject(request.getBody().get());
            final String accessToken = request.getHeaders().get(Commun.HEADER_AUTHORIZATION);
            final String id = Authentificateur.getIdFromToken(accessToken);
            final ClientTableUtilisateur client = new ClientTableUtilisateur();
            final Utilisateur utilisateur = client.get(id).get();
            switch (categorie) {
                case "obtenir":
                    corpsReponse = reponseObtenir(utilisateur, corpsRequete);
                    break;
                case "ajouter":
                    ajouter(utilisateur, corpsRequete);
                    client.set(utilisateur);
                    break;
                case "retirer":
                    retirer(utilisateur, Commun.getCodeVersion(request) < 41
                        ? transformerRequeteAncienFormat(corpsRequete)
                        : corpsRequete);
                    client.set(utilisateur);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Cette route est incorrecte : " + categorie);
            }
        } catch (final JSONException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.UNAUTHORIZED;
        } catch (NoSuchElementException | IllegalArgumentException e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.BAD_REQUEST;
        } catch (final Exception e) {
            Utils.logErreur(e, logger);
            codeHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Commun.construireReponse(codeHttp, corpsReponse, request);
    }

    private JSONObject transformerRequeteAncienFormat(JSONObject corpsRequete) {
        final JSONObject medicament = corpsRequete.getJSONObject("medicament");
        return new JSONObjectUneCle("medicaments", new JSONArray().put(0, medicament));
    }

    /**
     * Renvoie les médicaments ajoutés par l'utilisateur uniquement.
     * Les médicaments du DMP ont déjà été envoyés lors de la connexion.
     * @param utilisateur
     * @param corpsRequete
     * @return
     */
    private JSONObject reponseObtenir(Utilisateur utilisateur, JSONObject corpsRequete) {
        final Map<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsPerso =
            utilisateur.getMedicamentsPerso();
        final JSONObject meds = new JSONObject();
        medicamentsPerso.forEach((date, set) -> meds.put(
            date.toString(),
            set.stream()
                .map(Convertisseur::toJSON)
                .collect(Collectors.toSet())
        ));
        return new JSONObjectUneCle("medicamentsPerso", meds);
    }

    /**
     * Les médicaments non trouvés seront ignorés
     * @param utilisateur
     * @param corpsRequete
     */
    private void ajouter(Utilisateur utilisateur, JSONObject corpsRequete) {
        final JSONArray aAjouter = corpsRequete.getJSONArray("medicaments");
        getMedicamentsParDate(InfosMedicament.fromJSONArray(aAjouter))
            .forEach((date, set) -> utilisateur.ajouterMedicamentsPerso(date, set));
    }

    private void retirer(Utilisateur utilisateur, JSONObject corpsRequete) {
        final JSONArray aRetirer = corpsRequete.getJSONArray("medicaments");
        getMedicamentsParDate(InfosMedicament.fromJSONArray(aRetirer))
            .forEach((date, set) -> utilisateur.supprimerMedicamentsPerso(date, set));
    }

    private Map<LocalDate, Set<Medicament<?, ?, ?>>> getMedicamentsParDate(Set<InfosMedicament> infos) {
        final Map<LocalDate, Set<Medicament<?, ?, ?>>> map = new ConcurrentHashMap<>();
        final ClientTableMedicamentsFrance client = new ClientTableMedicamentsFrance();
        infos
            .parallelStream()
            .filter(i -> i.pays.equals(Pays.France.instance))
            .forEach(Unchecker.panic(i -> {
                final Optional<MedicamentFrance> optMed = client.get(i.code);
                if (optMed.isPresent()) {
                    map.computeIfAbsent(i.date, k -> new ConcurrentHashSet<>())
                        .add(optMed.get());
                }
            }));
        return map;
    }

    /**
     * Seules trois infos sont transmises par l'application pour désigner un médicament :
     * * le pays
     * * le code
     * * la date d'achat
     * 
     * Ces informations sont contenues dans un fichier JSON.
     * Cette classe sert à représenter ce fichier JSON.
     */
    static private class InfosMedicament {

        static private Set<InfosMedicament> fromJSONArray(JSONArray array) {
            return JSONArrays.toSetJSONObject(array)
                .stream()
                .map(InfosMedicament::new)
                .collect(Collectors.toSet());
        }

        static private LocalDate parserDate(String date) {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        }

        private final Pays pays;
        private final int code;
        private final LocalDate date;

        private InfosMedicament(JSONObject json) {
            pays = Pays.fromCode(json.getString("pays"));
            code = json.getInt("code");
            date = parserDate(
                Optional.ofNullable(json.optString("date"))
                        .orElseGet(() -> json.getString("dateAchat"))
            );
        }

    }
}
