package app.mesmedicaments.entitestables;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

import org.json.JSONArray;

import app.mesmedicaments.Utils;

public class EntiteCacheRecherche extends AbstractEntite {

	//private static final String TABLE = "cacheRecherche";
	private static final String TABLE = "cacheRechercheSimplifie";

	public static JSONArray obtenirResultatsCache (String terme) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		String resultats = "";
		for (EntiteCacheRecherche entite : obtenirEntites(terme)) {
			resultats += entite.getResultats();
			/*if (ligne == 1) {
				entite.setNombreRequetes(entite.getNombreRequetes() + 1);
				entite.mettreAJourEntite();
			}*/
		}
		if (resultats.equals("")) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, 1);
			entite.setResultats(new JSONArray().toString());
			//entite.setNombreRequetes(1);
			entite.creerEntite();
			resultats = entite.getResultats();
		}
		return new JSONArray(resultats);
	}

	public static void mettreEnCache (String terme, JSONArray resultats) 
		throws UnsupportedEncodingException, StorageException, URISyntaxException, InvalidKeyException
	{
		mettreEnCache(terme, resultats.toString());
	}
	
	public static void mettreEnCache (String terme, String resultats) 
		throws UnsupportedEncodingException, StorageException, URISyntaxException, InvalidKeyException
	{
		final int nbrLignes = resultats.getBytes("UTF-16").length / 64000 + 1;
		CloudTable cloudTable = obtenirCloudTable(TABLE);
		TableBatchOperation batchOp = new TableBatchOperation();
		String[] decoupes = Utils.decouperTexte(resultats, nbrLignes);
		for (int i = 0; i < decoupes.length; i++) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, i + 1);
			entite.setResultats(decoupes[i]);
			//entite.mettreAJourEntite(); // TODO !!!!!!!!!!!
			batchOp.insertOrMerge(entite);
			if ((i + 1) % 100 == 0 || (i + 1) == decoupes.length) {
				cloudTable.execute(batchOp);
				if ((i + 1) != decoupes.length) batchOp.clear();
			}
		}
	}

	private static Iterable<EntiteCacheRecherche> obtenirEntites (String terme)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			terme
		);
		Iterable<EntiteCacheRecherche> resNonTries = 
			obtenirCloudTable(TABLE)
				.execute(new TableQuery<>(EntiteCacheRecherche.class)
					.where(filtrePK));
		return (Iterable<EntiteCacheRecherche>) () -> 
			StreamSupport.stream(resNonTries.spliterator(), false)
				.sorted((e1, e2) -> Integer.valueOf(e1.getRowKey()).compareTo(Integer.valueOf(e2.getRowKey())))
				.map(EntiteCacheRecherche.class::cast)
				.iterator();
	}

    private static Optional<EntiteCacheRecherche> obtenirEntite (String terme, int ligne)
		throws URISyntaxException, InvalidKeyException
	{
		try {
			TableOperation operation = TableOperation.retrieve(
				terme, 
				String.valueOf(ligne), 
				EntiteCacheRecherche.class);
			return Optional.ofNullable(
				obtenirCloudTable(TABLE)
				.execute(operation)
				.getResultAsType()
			);
		}
		catch (StorageException e) {
			return Optional.empty();
		}
	}
	
	String resultats;
	int nombreRequetes;
    
    public EntiteCacheRecherche (String terme, int ligne) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(
			TABLE,
			terme, 
			String.valueOf(ligne)
		);
	}

	/**
	 * NE PAS UTILISER
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	public EntiteCacheRecherche () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		super(TABLE);
	}

	// Setters

	public void setResultats (String resultats) { this.resultats = resultats; }
	public void setNombreRequetes (int nombreRequetes) { this.nombreRequetes = nombreRequetes; }

	// Getters

	public String getResultats () { return this.resultats; }
	public int getNombreRequetes () { return this.nombreRequetes; }

}