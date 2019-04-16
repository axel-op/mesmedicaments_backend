package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Date;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

public class EntiteUtilisateur extends TableServiceEntity {

    private static final CloudTable TABLE_UTILISATEURS;
    private static final String CLE_PARTITION_UTILISATEURS;

    static {
        CLE_PARTITION_UTILISATEURS = System.getenv("clepartition_utilisateurs");
        TABLE_UTILISATEURS = obtenirCloudTable();
    }

    private static CloudTable obtenirCloudTable() {
        try {
            return CloudStorageAccount.parse(System.getenv("AzureWebJobsStorage")).createCloudTableClient()
                    .getTableReference(System.getenv("tableazure_utilisateurs"));
        } catch (StorageException | URISyntaxException | InvalidKeyException e) {
            return null;
        }
    }

    protected static void definirEntite(EntiteUtilisateur entite) throws StorageException {
        TableOperation operation = TableOperation.insertOrReplace(entite);
        TABLE_UTILISATEURS.execute(operation);
    }

    protected static void mettreAJourEntite(EntiteUtilisateur entite) throws StorageException {
        TableOperation operation = TableOperation.merge(entite);
        TABLE_UTILISATEURS.execute(operation);
    }

    protected static EntiteUtilisateur obtenirEntite(String id) throws StorageException {
        TableOperation operation = TableOperation.retrieve(CLE_PARTITION_UTILISATEURS, id, EntiteUtilisateur.class);
        return TABLE_UTILISATEURS.execute(operation).getResultAsType();
    }

    String prenom;
    String genre;
    String email;
    String motDePasse;
    Date dateInscription;

    public EntiteUtilisateur () {}

    public EntiteUtilisateur (String id) {
        this.partitionKey = CLE_PARTITION_UTILISATEURS;
        this.rowKey = id;
    }

    // Getters
    public String getPrenom () { return prenom; }
    public String getGenre () { return genre; }
    public String getEmail () { return email; }
    public String getMotDePasse () { return motDePasse; }
    public Date getDateInscription () { return dateInscription; }

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
    public void setMotDePasse (String motDePasse) { this.motDePasse = motDePasse; }
    public void setDateInscription (Date dateInscription) { this.dateInscription = dateInscription; }

    // Affichage
	public String toString () {
		String s = "***EntiteUtilisateur***\nid = " + rowKey 
			+ "\nprenom = " + prenom 
			+ "\ngenre = " + genre 
			+ "\nemail = " + email
            + "\ndate inscription = " + dateInscription.toString()
            + "\ntimestamp = " + getTimestamp().toString()
			+ "\n******************";
		return s;
	}
}