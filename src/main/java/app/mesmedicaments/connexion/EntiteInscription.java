package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

public class EntiteInscription extends TableServiceEntity {

    private static final CloudTable TABLE_UTILISATEURS;
	private static final String CLE_PARTITION_INSCRIPTIONS;

	static { 
		CLE_PARTITION_INSCRIPTIONS = System.getenv("clepartition_inscriptions");
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

	protected static void definirEntite (EntiteInscription entite) 
		throws StorageException
	{
		TableOperation operation = TableOperation.insertOrReplace(entite);
		TABLE_UTILISATEURS.execute(operation);
	}
	
	protected static void mettreAJourEntite (EntiteInscription entite)
		throws StorageException
	{
		TableOperation operation = TableOperation.merge(entite);
		TABLE_UTILISATEURS.execute(operation);
	}

	protected static EntiteInscription obtenirEntite (String id) 
		throws StorageException
	{
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION_INSCRIPTIONS, 
			id, 
			EntiteInscription.class);
		return TABLE_UTILISATEURS
			.execute(operation)
			.getResultAsType();
    }

    String motDePasse;
    
    public EntiteInscription () {}

    public EntiteInscription (String id) {
        this.partitionKey = CLE_PARTITION_INSCRIPTIONS;
        this.rowKey = id; 
    }

    // Getters
    public String getMotDePasse () { return "[HIDDEN]"; }
    
    // Setters
    public void setMotDePasse (String motDePasse) { this.motDePasse = motDePasse; }

}