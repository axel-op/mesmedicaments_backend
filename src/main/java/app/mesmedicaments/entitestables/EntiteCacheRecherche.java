package app.mesmedicaments.entitestables;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
//import java.util.Optional;
import java.util.stream.StreamSupport;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;

import org.json.JSONArray;

import app.mesmedicaments.Utils;

public class EntiteCacheRecherche extends AbstractEntite {

	private static final String TABLE_NOUV = "cacheRecherche";
	private static final String TABLE_DEPR = "cacheRechercheSimplifie";

	public static JSONArray obtenirResultatsCache(String terme, boolean depreciee)
			throws StorageException, InvalidKeyException, URISyntaxException {
		String resultats = "";
		for (EntiteCacheRecherche entite : obtenirEntites(terme, depreciee)) {
			resultats += entite.getResultats();
			/*
			 * if (ligne == 1) { entite.setNombreRequetes(entite.getNombreRequetes() + 1);
			 * entite.mettreAJourEntite(); }
			 */
		}
		if (resultats.equals("")) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, 1, depreciee);
			entite.setResultats(new JSONArray().toString());
			// entite.setNombreRequetes(1);
			entite.creerEntite();
			resultats = entite.getResultats();
		}
		return new JSONArray(resultats);
	}

	public static void mettreEnCache(String terme, JSONArray resultats, boolean depreciee)
			throws UnsupportedEncodingException, StorageException, URISyntaxException, InvalidKeyException {
		mettreEnCache(terme, resultats.toString(), depreciee);
	}

	public static void mettreEnCache(String terme, String resultats, boolean depreciee)
			throws UnsupportedEncodingException, StorageException, URISyntaxException, InvalidKeyException {
		final int nbrLignes = resultats.getBytes(StandardCharsets.UTF_16).length / 64000 + 1;
		CloudTable cloudTable = obtenirCloudTable(depreciee ? TABLE_DEPR : TABLE_NOUV);
		TableBatchOperation batchOp = new TableBatchOperation();
		String[] decoupes = Utils.decouperTexte(resultats, nbrLignes);
		for (int i = 0; i < decoupes.length; i++) {
			EntiteCacheRecherche entite = new EntiteCacheRecherche(terme, i + 1, depreciee);
			entite.setResultats(decoupes[i]);
			entite.checkConditions();
			batchOp.insertOrMerge(entite);
			if ((i + 1) % 100 == 0 || (i + 1) == decoupes.length) {
				cloudTable.execute(batchOp);
				if ((i + 1) != decoupes.length)
					batchOp.clear();
			}
		}
	}

	private static Iterable<EntiteCacheRecherche> obtenirEntites(String terme, boolean depreciee)
			throws StorageException, URISyntaxException, InvalidKeyException {
		Iterable<EntiteCacheRecherche> resNonTries = obtenirToutesLesEntites(depreciee ? TABLE_DEPR : TABLE_NOUV, terme,
				EntiteCacheRecherche.class);
		return (Iterable<EntiteCacheRecherche>) () -> StreamSupport.stream(resNonTries.spliterator(), false)
				.sorted((e1, e2) -> Integer.valueOf(e1.getRowKey()).compareTo(Integer.valueOf(e2.getRowKey())))
				.map(EntiteCacheRecherche.class::cast).iterator();
	}

	/*
	 * private static Optional<EntiteCacheRecherche> obtenirEntite (String terme,
	 * int ligne) throws URISyntaxException, InvalidKeyException, StorageException {
	 * return obtenirEntite(TABLE, terme, String.valueOf(ligne),
	 * EntiteCacheRecherche.class); }
	 */

	String resultats;
	int nombreRequetes;

	public EntiteCacheRecherche(String terme, int ligne, boolean depreciee) {
		super(depreciee ? TABLE_DEPR : TABLE_NOUV, terme, String.valueOf(ligne));
	}

	/**
	 * NE PAS UTILISER
	 */
	public EntiteCacheRecherche() {
		super(TABLE_NOUV);
	}

	// Setters

	public void setResultats(String resultats) {
		this.resultats = resultats;
	}

	public void setNombreRequetes(int nombreRequetes) {
		this.nombreRequetes = nombreRequetes;
	}

	// Getters

	public String getResultats() {
		return this.resultats;
	}

	public int getNombreRequetes() {
		return this.nombreRequetes;
	}

	@Override
	public boolean conditionsARemplir() {
		return !getResultats().equals("");
	}

}