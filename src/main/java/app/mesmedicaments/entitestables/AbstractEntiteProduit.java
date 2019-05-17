package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

public abstract class AbstractEntiteProduit extends AbstractEntite implements Comparable<AbstractEntiteProduit> {

	private static final String TABLE = System.getenv("tableazure_produits"); 
	private static CloudTable cloudTable;

	protected static <E extends AbstractEntiteProduit> Optional<E> obtenirEntite (String typeProduit, long codeProduit, Class<E> clazzType)
		throws URISyntaxException, InvalidKeyException, StorageException
	{
		TableOperation operation = TableOperation.retrieve(
			typeProduit,
			String.valueOf(codeProduit), 
			clazzType);
		return Optional.ofNullable(
			obtenirCloudTable(TABLE)
			.execute(operation)
			.getResultAsType()
		);
	}
	

	protected static <E extends AbstractEntiteProduit> Iterable<E> obtenirToutesLesEntites (String typeProduit, Class<E> clazzType)
		throws URISyntaxException, InvalidKeyException, StorageException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			typeProduit);
		return obtenirCloudTable(TABLE)
			.execute(new TableQuery<>(clazzType)
				.where(filtrePK));
	}

	public static <E extends AbstractEntiteProduit> void mettreAJourEntitesBatch (Iterable<E> entites) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (cloudTable == null) { cloudTable = obtenirCloudTable(TABLE); }
		TableBatchOperation batchOperation = new TableBatchOperation();
		for (E entite : entites) { 
			batchOperation.insertOrMerge(entite); 
			if (batchOperation.size() >= 100) {
				cloudTable.execute(batchOperation);
				batchOperation.clear();
			}
		}
		if (!batchOperation.isEmpty()) { cloudTable.execute(batchOperation); }
	}

	public AbstractEntiteProduit (String typeProduit, long codeProduit)
		throws StorageException, URISyntaxException, InvalidKeyException
	{ super(TABLE, typeProduit, String.valueOf(codeProduit)); }

	public AbstractEntiteProduit () throws StorageException, URISyntaxException, InvalidKeyException 
	{ super(TABLE); }

	@Override
	public int compareTo (AbstractEntiteProduit other) {
		return Long.valueOf(this.getRowKey())
			.compareTo(Long.valueOf(other.getRowKey())
		);
	}

	@Override
	public boolean equals (Object other) {
		if (other instanceof AbstractEntiteProduit) {
			AbstractEntiteProduit autre = (AbstractEntiteProduit) other;
			return autre.getPartitionKey().equals(getPartitionKey())
				&& autre.getRowKey().equals(getRowKey());
		}
		return false;
	}
}