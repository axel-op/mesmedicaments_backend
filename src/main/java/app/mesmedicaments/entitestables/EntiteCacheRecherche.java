package app.mesmedicaments.entitestables;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;

public class EntiteCacheRecherche extends AbstractEntite {

	private static final String TABLE = "cacheRecherche";
	private static CloudTable cloudTable;

	public static JSONArray obtenirResultatsCache (String terme) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		String resultats = "";
		Optional<EntiteCacheRecherche> optEntite;
		int ligne = 1;
		while ((optEntite = obtenirEntite(terme, ligne)).isPresent()) {
			resultats += optEntite.get().getResultats();
			if (ligne == 1) {
				EntiteCacheRecherche entite = optEntite.get();
				entite.setNombreRequetes(entite.getNombreRequetes() + 1);
				entite.mettreAJourEntite();
			}
			ligne += 1;
		}
		if (resultats.equals("")) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, 1);
			entite.setResultats(new JSONArray().toString());
			entite.setNombreRequetes(1);
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
		int nbrLignes = resultats.getBytes("UTF-16").length / 64000 + 1;
		int longRes = resultats.length();
		if (cloudTable == null) { cloudTable = obtenirCloudTable(TABLE); }
		TableBatchOperation batchOp = new TableBatchOperation();
		for (int i = 1; i <= nbrLignes; i++) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, i);
			entite.setResultats(resultats.substring(
					longRes / nbrLignes * (i - 1),
					i == nbrLignes ? longRes : longRes / nbrLignes * i
				));
			entite.mettreAJourEntite();
			batchOp.insertOrMerge(entite);
			if (i % 100 == 0 || i == nbrLignes) {
				cloudTable.execute(batchOp);
				if (i != nbrLignes) { batchOp.clear(); }
			}
		}
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