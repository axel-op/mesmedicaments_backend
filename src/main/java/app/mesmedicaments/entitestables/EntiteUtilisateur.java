package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Date;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONObject;

public class EntiteUtilisateur extends AbstractEntite {

    //private static final CloudTable TABLE_UTILISATEURS;
    private static final String CLE_PARTITION;

    static {
        CLE_PARTITION = "utilisateur";
        //TABLE_UTILISATEURS = obtenirCloudTable();
    }

    public static EntiteUtilisateur obtenirEntite (String id) 
        throws StorageException,
        URISyntaxException,
        InvalidKeyException
    {
        TableOperation operation = TableOperation.retrieve(CLE_PARTITION, id, EntiteUtilisateur.class);
        return obtenirCloudTable(System.getenv("tableazure_utilisateurs")).execute(operation).getResultAsType();
    }

    String prenom;
    String genre;
    String email;
    //String motDePasse;
    Date dateInscription;
    String deviceId;
    byte[] jwtSalt;
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

    public String getPrenom () { return prenom; }
    public String getGenre () { return genre; }
    public String getEmail () { return email; }
    public Date getDateInscription () { return dateInscription; }
    public String getDeviceId () { return deviceId; }
    public byte[] getJwtSalt () { return jwtSalt; }
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

    public void setPrenom (String prenom) { this.prenom = prenom; }

    public void setGenre (String genre) {
        char g = genre.charAt(0);
        if (g == 'M' || g == 'm') { this.genre = "M"; }
        else if (g == 'F' || g == 'f') { this.genre = "F"; }
        else { throw new IllegalArgumentException(); }
    }

    public void setEmail (String email) {
        // checker avec regex
        this.email = email;
    }

    public void setDateInscription (Date dateInscription) { this.dateInscription = dateInscription; }
    public void setDeviceId (String deviceId) { this.deviceId = deviceId; }
    public void setJwtSalt (byte[] salt) { jwtSalt = salt; }
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

    public void definirMedicamentsJObject (JSONObject medicamentsRecents) {
        this.medicaments = medicamentsRecents.toString();
    }

}