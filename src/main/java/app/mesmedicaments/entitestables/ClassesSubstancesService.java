package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.HashSet;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableQuery;

public class ClassesSubstancesService {

	private static final String TABLE = System.getenv("tableazure_classes");
	private static CloudTable cloudTable;

	public static Collection<String> obtenirToutesLesClasses () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		HashSet<String> nomsClasses = new HashSet<>();
		AbstractEntite.obtenirCloudTable(TABLE)
			.execute(new TableQuery<>(AbstractEntite.class))
			.forEach(entite -> nomsClasses.add(entite.getPartitionKey()));
		return nomsClasses;
	}

	public static void mettreAJourClasseBatch (String classe, Iterable<Long> codesSubstances) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (cloudTable == null) { cloudTable = AbstractEntite.obtenirCloudTable(TABLE); }
		TableBatchOperation batchOperation = new TableBatchOperation();
		for (long code : codesSubstances) {
			batchOperation.insertOrMerge(
				new EntiteClasse(classe, code)
			);
			if (batchOperation.size() >= 100) {
				cloudTable.execute(batchOperation);
				batchOperation.clear();
			}
		}
		if (!batchOperation.isEmpty()) {
			cloudTable.execute(batchOperation);
		}
	}

	private ClassesSubstancesService () {}

	private static class EntiteClasse extends AbstractEntite {

		public EntiteClasse (String nomClasse, long codeSubstance) 
			throws StorageException, InvalidKeyException, URISyntaxException
		{
			super(TABLE, nomClasse, String.valueOf(codeSubstance));
		}

		/**
		 * NE PAS UTILISER
		 * @throws StorageException
		 * @throws URISyntaxException
		 * @throws InvalidKeyException
		 */
		public EntiteClasse () 
			throws StorageException, URISyntaxException, InvalidKeyException
		{
			super(TABLE);
		}
	}

}