package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableOperation;

public class EntiteInteraction extends AbstractEntite {

    private static final String TABLE = System.getenv("tableazure_interactions");
    private static CloudTable cloudTable;

    public static Optional<EntiteInteraction> obtenirEntite (long codeSubstance1, long codeSubstance2) 
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        TableOperation operation = TableOperation.retrieve(
            String.valueOf(Math.min(codeSubstance1, codeSubstance2)), 
            String.valueOf(Math.max(codeSubstance1, codeSubstance2)), 
            EntiteInteraction.class);
        return Optional.ofNullable(obtenirCloudTable(TABLE)
            .execute(operation)
            .getResultAsType());
    }

    /**
     * 
     * @param entites Les entités à mettre à jour, appartenant à une seule et même partition
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InvalidKeyException
     */
    public static void mettreAJourEntitesBatch (Collection<EntiteInteraction> entites) 
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        final String clePartition = entites.iterator().next().getPartitionKey();
        if (cloudTable == null) { cloudTable = obtenirCloudTable(TABLE); }
        TableBatchOperation batchOperation = new TableBatchOperation();
        for (EntiteInteraction entite : entites) { 
            if (!entite.getPartitionKey().equals(clePartition)) {
                throw new IllegalArgumentException("Toutes les entités doivent appartenir à la même partition");
            }
            batchOperation.insertOrMerge(entite); 
            if (batchOperation.size() >= 100) {
                cloudTable.execute(batchOperation);
                batchOperation.clear();
            }
        }
        if (!batchOperation.isEmpty()) { cloudTable.execute(batchOperation); }
    }
    
    int risque;
    String descriptif;
    String conduite;

    public EntiteInteraction (long codeSubstance1, long codeSubstance2) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(
            TABLE, 
            String.valueOf(Math.min(codeSubstance1, codeSubstance2)), 
			String.valueOf(Math.max(codeSubstance1, codeSubstance2))
        );
	}

    /**
	 * NE PAS UTILISER
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	public EntiteInteraction () 
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        super(TABLE);
    }

    // Getters

    public int getRisque () { return risque; }
    public String getDescriptif () { return descriptif; }
    public String getConduite () { return conduite; }

    public Long obtenirCodeSubstance1 () {
        return Long.parseLong(getPartitionKey());
    }

    public Long obtenirCodeSubstance2 () {
        return Long.parseLong(getRowKey());
    }

    // Setters

    public void setRisque (int risque) {
        if (risque < 1 || risque > 4) { throw new IllegalArgumentException(); }
        this.risque = risque; 
    }

    public void setDescriptif (String descriptif) { this.descriptif = descriptif; }
    public void setConduite (String conduite) { this.conduite = conduite; }
}