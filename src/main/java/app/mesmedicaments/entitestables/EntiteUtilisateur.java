package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Utils;

public class EntiteUtilisateur extends AbstractEntite {

    //private static final CloudTable TABLE_UTILISATEURS;
    private static final String CLE_PARTITION = "utilisateur";

    public static Optional<EntiteUtilisateur> obtenirEntite (String id) 
        throws StorageException,
        URISyntaxException,
        InvalidKeyException
    {
        TableOperation operation = TableOperation.retrieve(CLE_PARTITION, id, EntiteUtilisateur.class);
        return Optional.ofNullable(
            obtenirCloudTable(System.getenv("tableazure_utilisateurs"))
            .execute(operation)
            .getResultAsType()
        );
    }

    Date dateInscription;
    String medicamentsDMP;
    String medicamentsPerso;
    String idAnalytics;

    /**
     * NE PAS UTILISER
     * @throws StorageException
     * @throws InvalidKeyException
     * @throws URISyntaxException
     */
    public EntiteUtilisateur ()
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        super(System.getenv("tableazure_utilisateurs"));
    }

    public EntiteUtilisateur (String id)
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        super(System.getenv("tableazure_utilisateurs"), CLE_PARTITION, id);
        dateInscription = Date.from(LocalDateTime.now().atZone(Utils.TIMEZONE).toInstant());
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        idAnalytics = String.valueOf(id.hashCode()) + new String(salt);
    }

    // Getters

    public Date getDateInscription () { return dateInscription; }
    public String getIdAnalytics () { return idAnalytics; }

    /**
     * @return {@link JSONObject} converti en {@link String}
     */
    public String getMedicamentsDMP () { return medicamentsDMP; }

    /**
     * L'objet JSON associe une date (non formatée) à une liste de médicaments
     */
    public JSONObject obtenirMedicamentsDMPJObject () { 
        if (medicamentsDMP == null || medicamentsDMP.equals("")) { return new JSONObject(); }
        return new JSONObject(medicamentsDMP); 
    }

    public String getMedicamentsPerso () { return medicamentsPerso; }
    public JSONObject obtenirMedicamentsPersoJObject () {
        if (medicamentsPerso == null || medicamentsPerso.equals("")) { return new JSONObject(); }
        return new JSONObject(medicamentsPerso);
    }

    // Setters

    /**
     * NE PAS UTILISER
     * @param dateInscription
     */
    public void setDateInscription (Date dateInscription) { this.dateInscription = dateInscription; }
    /**
     * NE PAS UTILISER
     */
    public void setIdAnalytics (String idAnalytics) { this.idAnalytics = idAnalytics; }

    public void setMedicamentsDMP (String medicaments) {
        medicamentsDMP = medicaments;
    }

    /**
     * Ecrase les médicaments déjà présents par les nouveaux
     */
    public void definirMedicamentsDMPJObject (JSONObject medicaments) {
        this.medicamentsDMP = medicaments.toString();
    }

    public void setMedicamentsPerso (String medicaments) {
        medicamentsPerso = medicaments;
    }

    public void definirMedicamentsPersoJObject (JSONObject medicaments) {
        medicamentsPerso = medicaments.toString();
    }

    /**
     * Ajoute les médicaments à ceux existant déjà
     * @param nouveaux
     */
    public void ajouterMedicamentsDMPJObject (JSONObject nouveaux, DateTimeFormatter formatter) 
        throws DateTimeParseException
    {
        JSONObject medicaments = obtenirMedicamentsDMPJObject();
        for (String cle : nouveaux.keySet()) {
            String date = LocalDate.parse(cle, formatter).toString();
            Set<Long> codes = new HashSet<>();
            Utils.ajouterTousLong(
                codes, 
                Optional.ofNullable(medicaments.optJSONArray(cle))
                    .orElseGet(() -> new JSONArray())
            );
            Utils.ajouterTousLong(codes, nouveaux.getJSONArray(cle));
            medicaments.put(date, new JSONArray(codes));
        }
        definirMedicamentsDMPJObject(medicaments);
    }

    /**
     * La date d'achat est définie implicitement sur la date actuelle
     * @param codeCis
     */
    public void ajouterMedicamentPerso (Long codeCis) {
        LocalDate dateAchat = LocalDate.now();
        String dateStr = dateAchat.toString();
        JSONObject medicaments = obtenirMedicamentsPersoJObject();
        Optional<JSONArray> optActuels = Optional.ofNullable(medicaments.optJSONArray(dateStr));
        if (optActuels.isPresent()) {
            JSONArray actuels = optActuels.get();
            Set<Long> codes = new HashSet<>();
            Utils.ajouterTousLong(codes, actuels);
            codes.add(codeCis);
            medicaments.put(dateStr, codes);
            definirMedicamentsPersoJObject(medicaments);
        }
        else {
            medicaments.put(dateStr, new JSONArray().put(codeCis));
            definirMedicamentsPersoJObject(medicaments);
        }
    }

    /**
     * Ne fait rien si le médicament n'est pas trouvé
     * @param codeCis
     * @param dateAchat
     */
    public void retirerMedicamentPerso (Long codeCis, LocalDate dateAchat) {
        String dateStr = dateAchat.toString();
        JSONObject medicaments = obtenirMedicamentsPersoJObject();
        Optional<JSONArray> optActuels = Optional.ofNullable(medicaments.optJSONArray(dateStr));
        if (optActuels.isPresent()) {
            JSONArray actuels = optActuels.get();
            Integer indexASuppr = null;
            for (int i = 0; i < actuels.length(); i++) {
                if (codeCis.equals(Long.valueOf(actuels.getLong(i)))) {
                    indexASuppr = i;
                }
            }
            if (indexASuppr != null) {
                actuels.remove(indexASuppr);
                medicaments.put(dateStr, actuels);
                definirMedicamentsPersoJObject(medicaments);
            }
        }
    }

}