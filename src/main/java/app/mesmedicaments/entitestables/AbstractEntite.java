package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableServiceEntity;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

public abstract class AbstractEntite extends TableServiceEntity {

	public static enum Pays {
		France ("france"),
		Belgique ("belgique");
		public final String code;
		private Pays (String code) { this.code = code; }
		public static Pays obtenirPays (String code) {
			for (Pays pays : Pays.values()) {
				if (pays.code.equals(code)) return pays;
			}
			throw new IllegalArgumentException("Le pays " + code + " n'existe pas");
		}
	}

	public static enum Langue {
		Francais ("francais"),
		Latin ("latin");
		public final String code;
		private Langue (String code) { this.code = code; }
		public static Langue obtenirLangue (String code) {
			for (Langue langue : Langue.values()) {
				if (langue.code.equals(code)) return langue;
			}
			throw new IllegalArgumentException("Le code de langue " + code + " n'existe pas");
		}
	}

	protected static <E extends AbstractEntite> Optional<E> obtenirEntite 
        (String table, String partition, String rowKey, Class<E> clazzType)
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        TableOperation tableOperation = TableOperation.retrieve(
            partition, 
            rowKey, 
            clazzType
        );
        return Optional.ofNullable(
            obtenirCloudTable(table)
                .execute(tableOperation)
                .getResultAsType()
        );
	}
	
	protected static <E extends AbstractEntite> Iterable<E> obtenirToutesLesEntites 
		(String table, String partition, Class<E> clazzType)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			partition
		);
		return obtenirCloudTable(table)
			.execute(new TableQuery<>(clazzType)
				.where(filtrePK));
	}

	
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

	public static String supprimerCaracteresInterdits (String s) {
		s = s.replaceAll("\\\\|/|#|\\?", " ");
		return s;
	}
	
	private final String table;

	public AbstractEntite (String table, String partitionKey, String rowKey) { 
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
		this.table = table;
	}

	/**
	 * NE PAS UTILISER
	 * @param table
	 */
	public AbstractEntite (String table) {
		this(table, null, null);
	}

	public void creerEntite () 
		throws StorageException, URISyntaxException, InvalidKeyException, RuntimeException
	{
		checkConditions();
		obtenirCloudTable(table)
			.execute(TableOperation.insertOrReplace(this));
	}
	
	public void mettreAJourEntite () 
		throws StorageException, URISyntaxException, InvalidKeyException, RuntimeException
	{
		checkConditions();
		obtenirCloudTable(table)
			.execute(TableOperation.insertOrMerge(this));
	}

	public void supprimerEntite () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		obtenirCloudTable(table)
			.execute(TableOperation.delete(this));
	}

	/**
	 * Doit renvoyer false s'il y a un problème avec l'entité.
	 * Une exception sera alors levée avant la mise à jour serveur, sauf en cas de suppression.
	 * @return booléen
	 */
	public abstract boolean conditionsARemplir ();

	protected void checkConditions () throws RuntimeException {
		if (!conditionsARemplir())
			throw new RuntimeException(
				"Toutes les conditions ne sont pas remplies pour l'entité suivante : \n" 
				+ toString()
			);
	}

	@Override
	public String toString () {
		return "\tClasse = " + this.getClass().toGenericString()
			+ "\tTable = " + this.table
			+ "\tPartitionKey = " + this.getPartitionKey()
			+ "\tRowKey = " + this.getRowKey();
	}

	@Override
	public boolean equals (Object o) {
		if (!(o instanceof AbstractEntite)) return false;
		AbstractEntite other = (AbstractEntite) o;
		return (other.getPartitionKey().equals(this.getPartitionKey()))
			&& (other.getRowKey().equals(this.getRowKey()));
	}

	@Override
	public int hashCode () {
		return getPartitionKey().hashCode();
	}
}