package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

public abstract class AbstractEntite extends TableServiceEntity {

	
	public static CloudTable obtenirCloudTable (String table)
		throws URISyntaxException,
		StorageException,
		InvalidKeyException
	{
		return CloudStorageAccount
			.parse(System.getenv("AzureWebJobsStorage"))
			.createCloudTableClient()
			.getTableReference(table);
	}

	public static String supprimerCaracteresInterdits (String s) {
		s = s.replaceAll("\\\\|/|#|\\?", " ");
		return s;
	}
	
	private final CloudTable TABLE;

	public AbstractEntite (String table, String partitionKey, String rowKey) 
		throws URISyntaxException, StorageException, InvalidKeyException
	{ 
		if (partitionKey != null) { 
			partitionKey = supprimerCaracteresInterdits(partitionKey); 
			partitionKey = partitionKey.replaceAll("  ", " ");
			partitionKey = partitionKey.trim();
		}
		if (rowKey != null) { 
			rowKey = supprimerCaracteresInterdits(rowKey); 
			rowKey = rowKey.replaceAll("  ", " ");
			rowKey = rowKey.trim();
		}
		this.partitionKey = partitionKey;
		this.rowKey = rowKey;
		TABLE = obtenirCloudTable(table); 
	}

	public AbstractEntite (String table)
		throws StorageException, URISyntaxException, InvalidKeyException 
	{
		this(table, null, null);
	}

	public void creerEntite () throws StorageException {
		TableOperation operation = TableOperation.insertOrReplace(this);
		TABLE.execute(operation);
	}
	
	public void mettreAJourEntite () throws StorageException {
		TableOperation operation = TableOperation.insertOrMerge(this);
		TABLE.execute(operation);
	}
}