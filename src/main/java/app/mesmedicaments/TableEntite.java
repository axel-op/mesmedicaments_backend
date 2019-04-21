package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

public abstract class TableEntite extends TableServiceEntity {

	
	protected static CloudTable obtenirCloudTable (String table)
		throws URISyntaxException,
		StorageException,
		InvalidKeyException
	{
		return CloudStorageAccount
			.parse(System.getenv("AzureWebJobsStorage"))
			.createCloudTableClient()
			.getTableReference(table);
	} 
	
	private final CloudTable TABLE;

	public TableEntite (String table, String partitionKey, String rowKey) 
		throws URISyntaxException, StorageException, InvalidKeyException
	{ 
		this.partitionKey = partitionKey;
		this.rowKey = rowKey;
		TABLE = obtenirCloudTable(table); 
	}

	public TableEntite (String table)
		throws StorageException, URISyntaxException, InvalidKeyException 
	{
		this(table, null, null);
	}

	/**
	 * NE PAS UTILISER
	 * Je dois le mettre uniquement à cause de l'incompétence des "ingénieurs" de Microsoft
	 */
	/*
	public Entite () {
		TABLE = null;
	}
	*/

	public void creerEntite () throws StorageException {
		TableOperation operation = TableOperation.insertOrReplace(this);
		TABLE.execute(operation);
	}
	
	public void mettreAJourEntite () throws StorageException {
		TableOperation operation = TableOperation.insertOrMerge(this);
		TABLE.execute(operation);
	}

}