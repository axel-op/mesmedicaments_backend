package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Date;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;
import org.json.JSONObject;

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

    int idDmp;
    Date dateInscription;
    Date derniereConnexion;
    String medicaments;
    //String medicamentsRecentsPerso

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
    }

    // Getters

    public int getIdDmp () { return idDmp; }
    public Date getDateInscription () { return dateInscription; }
    public Date getDerniereConnexion () { return derniereConnexion; }

    /**
     * @return {@link JSONObject} converti en {@link String}
     */
    public String getMedicaments () { return medicaments; }

    /**
     * L'objet JSON associe une date (non formatée) à une liste de médicaments
     */
    public Optional<JSONObject> obtenirMedicamentsJObject () { 
        if (medicaments == null || medicaments.equals("")) { return Optional.empty(); }
        return Optional.of(new JSONObject(medicaments)); 
    }

    // Setters

    public void setIdDmp (int idDmp) { this.idDmp = idDmp; }
    public void setDateInscription (Date dateInscription) { this.dateInscription = dateInscription; }
    public void setDerniereConnexion (Date date) { derniereConnexion = date; }

    /**
     * Doit être sous forme de {@link JSONObject} converti en {@link String}
     */
    public void setMedicaments (String medicaments) {
        if (medicaments != null) {
            this.medicaments = new JSONObject(medicaments).toString();
        }
        else { this.medicaments = null; }
    }

    /**
     * Ecrase les médicaments déjà présents par les nouveaux
     */
    public void definirMedicamentsJObject (JSONObject medicaments) {
        this.medicaments = medicaments.toString();
    }

    /**
     * Ajoute les médicaments à ceux existant déjà
     * @param nouveaux
     */
    public void ajouterMedicamentsJObject (JSONObject nouveaux) {
        if (this.medicaments != null) {
            JSONObject actuels = new JSONObject(this.medicaments);
            nouveaux.keySet().forEach((cleDate) -> {
                JSONArray jarray = nouveaux.getJSONArray(cleDate);
                for (int i = 0; i < jarray.length(); i++) {
                    int codeCis = jarray.getInt(i);
                    if (!actuels.has(cleDate)
                        || (actuels.has(cleDate) && !actuels.getJSONArray(cleDate).toList().contains(codeCis))
                    ) {
                        actuels.append(cleDate, codeCis);
                    }
                }
            });
            definirMedicamentsJObject(actuels);
        }
        else { definirMedicamentsJObject(nouveaux); }
    }

}