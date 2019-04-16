package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

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

	private static CloudTable obtenirCloudTable () {
		try {
			return CloudStorageAccount
				.parse(System.getenv("AzureWebJobsStorage"))
				.createCloudTableClient()
				.getTableReference(System.getenv("tableazure_utilisateurs"));
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			return null;
		}
	} 

	protected static void definirEntite (EntiteUtilisateur entite) 
		throws StorageException
	{
		TableOperation operation = TableOperation.insertOrReplace(entite);
		TABLE_UTILISATEURS.execute(operation);
	}
	
	protected static void mettreAJourEntite (EntiteUtilisateur entite)
		throws StorageException
	{
		TableOperation operation = TableOperation.merge(entite);
		TABLE_UTILISATEURS.execute(operation);
	}

	protected static EntiteUtilisateur obtenirEntite (String id) 
		throws StorageException
	{
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION_UTILISATEURS, 
			id, 
			EntiteUtilisateur.class);
		return TABLE_UTILISATEURS
			.execute(operation)
			.getResultAsType();
	}

    String prenom;
    char genre;
    String email;

    public EntiteUtilisateur () {}

    public EntiteUtilisateur (String id) {
        this.partitionKey = CLE_PARTITION_UTILISATEURS;
        this.rowKey = id;
    }

    // Getters
    public String getPrenom () { return prenom; }
    public char getGenre () { return genre; }
    public String getEmail () { return email; }

    // Setters
    public void setPrenom (String prenom) { this.prenom = prenom; }
    public void setGenre (char genre) {
        if (genre == 'M' || genre == 'm') { this.genre = 'M'; }
        if (genre == 'F' || genre == 'f') { this.genre = 'F'; }
        throw new IllegalArgumentException();
    }
    public void setEmail (String email) {
        // checker avec regex
        this.email = email;
    }

    // Affichage
	public String toString () {
		String s = "***EntiteUtilisateur***\nid = " + rowKey 
			+ "\nprenom = " + prenom 
			+ "\ngenre = " + genre 
			+ "\nemail = " + email
			+ "\ntimestamp = " + getTimestamp().toString()
			+ "\n******************";
		return s;
	}
}