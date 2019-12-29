package app.mesmedicaments.entitestables;

import app.mesmedicaments.JSONArrays;
import app.mesmedicaments.Utils;
import app.mesmedicaments.unchecked.Unchecker;
import com.google.common.collect.Sets;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.Ignore;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class EntiteUtilisateur extends AbstractEntite {

    // private static final CloudTable TABLE_UTILISATEURS;
    private static final String TABLE = System.getenv("tableazure_utilisateurs");
    private static final String CLE_PARTITION = "utilisateur";

    public static Optional<EntiteUtilisateur> obtenirEntite(String id)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirEntite(TABLE, CLE_PARTITION, id, EntiteUtilisateur.class);
    }

    public static EntiteUtilisateur obtenirEntiteOuCreer(String id, Logger logger)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirEntite(id)
                .orElseGet(
                        Unchecker.wrap(
                                logger,
                                () -> {
                                    EntiteUtilisateur entite = new EntiteUtilisateur(id);
                                    entite.creerEntite();
                                    return entite;
                                }));
    }

    Date dateInscription;
    String medicamentsDMP;
    String medicamentsPerso;
    String idAnalytics;

    private final Map<LocalDate, Map<Pays, Set<Long>>> medicamentsDMPParDate = new HashMap<>();
    private final Map<LocalDate, Map<Pays, Set<Long>>> medicamentsPersoParDate = new HashMap<>();

    /** NE PAS UTILISER */
    public EntiteUtilisateur() {
        super(TABLE);
    }

    public EntiteUtilisateur(String id) {
        super(TABLE, CLE_PARTITION, id);
        dateInscription = Date.from(LocalDateTime.now().atZone(Utils.TIMEZONE).toInstant());
        idAnalytics = String.valueOf(id.hashCode());
        Random rd = new Random();
        for (int i = 0; i < 32; i++) { // longueur choisie au hasard
            idAnalytics += String.valueOf(rd.nextInt(10));
        }
    }

    @Override
    public boolean conditionsARemplir() {
        return getIdAnalytics() != null && !getIdAnalytics().equals("") && dateInscription != null;
    }

    /* Getters */

    public Date getDateInscription() {
        return dateInscription;
    }

    public String getIdAnalytics() {
        return idAnalytics;
    }

    public String getMedicamentsDMP() {
        return medicamentsParDateToJson(medicamentsDMPParDate).toString();
    }

    public String getMedicamentsPerso() {
        return medicamentsParDateToJson(medicamentsPersoParDate).toString();
    }

    @Ignore
    public Map<LocalDate, Map<Pays, Set<Long>>> getMedicamentsDMPMap() {
        return new HashMap<>(medicamentsDMPParDate);
    }

    @Ignore
    public Map<LocalDate, Map<Pays, Set<Long>>> getMedicamentsPersoMap() {
        return new HashMap<>(medicamentsPersoParDate);
    }

    /* Setters */

    /**
     * NE PAS UTILISER
     *
     * @param dateInscription
     */
    public void setDateInscription(Date dateInscription) {
        this.dateInscription = dateInscription;
    }

    /** NE PAS UTILISER */
    public void setIdAnalytics(String idAnalytics) {
        this.idAnalytics = idAnalytics;
    }

    public void setMedicamentsDMP(String medicamentsDMP) {
        this.medicamentsDMP = medicamentsDMP;
        medicamentsDMPParDate.clear();
        if (medicamentsDMP != null) {
            medicamentsDMPParDate.putAll(
                    medicamentsParDateFromJson(new JSONObject(medicamentsDMP)));
        }
    }

    public void setMedicamentsPerso(String medicamentsPerso) {
        this.medicamentsPerso = medicamentsPerso;
        medicamentsPersoParDate.clear();
        if (medicamentsPerso != null) {
            medicamentsPersoParDate.putAll(
                    medicamentsParDateFromJson(new JSONObject(medicamentsPerso)));
        }
    }

    @Ignore
    private Map<LocalDate, Map<Pays, Set<Long>>> medicamentsParDateFromJson(JSONObject json) {
        Map<LocalDate, Map<Pays, Set<Long>>> medsParDate = new HashMap<>();
        json.keySet()
                .forEach(
                        cleDate -> {
                            LocalDate date = LocalDate.parse(cleDate);
                            try {
                                JSONObject codesParPays = json.getJSONObject(cleDate);
                                codesParPays
                                        .keySet()
                                        .forEach(
                                                codePays -> {
                                                    Pays pays = Pays.obtenirPays(codePays);
                                                    Set<Long> codesMeds =
                                                            JSONArrays.toSetLong(
                                                                    codesParPays.getJSONArray(
                                                                            codePays));
                                                    medsParDate
                                                            .computeIfAbsent(
                                                                    date, k -> new HashMap<>())
                                                            .computeIfAbsent(
                                                                    pays, k -> new HashSet<>())
                                                            .addAll(codesMeds);
                                                });
                            } catch (JSONException e) {
                                Set<Long> codesMeds =
                                        JSONArrays.toSetLong(json.getJSONArray(cleDate));
                                medsParDate
                                        .computeIfAbsent(date, k -> new HashMap<>())
                                        .computeIfAbsent(Pays.France, k -> new HashSet<>())
                                        .addAll(codesMeds);
                            }
                        });
        return medsParDate;
    }

    private JSONObject medicamentsParDateToJson(Map<LocalDate, Map<Pays, Set<Long>>> medsParDate) {
        JSONObject json = new JSONObject();
        medsParDate
                .entrySet()
                .forEach(
                        e -> {
                            LocalDate date = e.getKey();
                            Map<Pays, Set<Long>> medsParPays = e.getValue();
                            JSONObject jsonDate = new JSONObject();
                            medsParPays
                                    .entrySet()
                                    .forEach(e2 -> jsonDate.put(e2.getKey().code, e2.getValue()));
                            json.put(date.toString(), jsonDate);
                        });
        return json;
    }

    /**
     * Ajoute les médicaments à ceux existant déjà
     *
     * @param nouveaux
     */
    public void ajouterMedicamentsDMP(Map<LocalDate, Set<Long>> medsParDate) {
        for (Entry<LocalDate, Set<Long>> entree : medsParDate.entrySet()) {
            medicamentsDMPParDate
                    .computeIfAbsent(entree.getKey(), k -> new HashMap<>())
                    .computeIfAbsent(Pays.France, k -> new HashSet<>())
                    .addAll(entree.getValue());
        }
    }

    /**
     * La date d'achat est définie implicitement sur la date actuelle
     *
     * @param codeCis
     */
    public void ajouterMedicamentPerso(Pays pays, long code) {
        LocalDate dateAchat = LocalDate.now();
        medicamentsPersoParDate
                .computeIfAbsent(dateAchat, k -> new HashMap<>())
                .computeIfAbsent(pays, k -> new HashSet<>())
                .add(code);
    }

    /**
     * La date d'achat est définie implicitement sur la date actuelle
     *
     * @param codesCis
     */
    public void ajouterMedicamentsPerso(Pays pays, Iterable<Long> codes) {
        LocalDate dateAchat = LocalDate.now();
        medicamentsPersoParDate
                .computeIfAbsent(dateAchat, k -> new HashMap<>())
                .computeIfAbsent(pays, k -> new HashSet<>())
                .addAll(Sets.newHashSet(codes));
    }

    /**
     * Ne fait rien si le médicament n'est pas trouvé
     *
     * @param codeCis
     * @param dateAchat
     */
    public void retirerMedicamentPerso(Pays pays, long code, LocalDate dateAchat) {
        Map<Pays, Set<Long>> codesParPays = medicamentsPersoParDate.get(dateAchat);
        if (codesParPays != null) {
            Set<Long> codes = codesParPays.get(pays);
            if (codes != null) codes.remove(code);
        }
    }
}
