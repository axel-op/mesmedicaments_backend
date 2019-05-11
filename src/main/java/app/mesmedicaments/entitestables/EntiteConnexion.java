package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

import org.json.JSONException;
import org.json.JSONObject;

public class EntiteConnexion extends AbstractEntite {

	//private static final CloudTable TABLE_UTILISATEURS;
	private static final String CLE_PARTITION = "connexion";
	private static final String TABLE = System.getenv("tableazure_connexions");

	public static Iterable<EntiteConnexion> obtenirToutesLesEntites () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			CLE_PARTITION
		);
		return obtenirCloudTable(TABLE)
			.execute(new TableQuery<>(EntiteConnexion.class)
				.where(filtrePK));
	}

	public static Optional<EntiteConnexion> obtenirEntite (String id)
		throws URISyntaxException, InvalidKeyException
	{
		try {
			TableOperation operation = TableOperation.retrieve(
				CLE_PARTITION, 
				id, 
				EntiteConnexion.class);
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

	String sid;
	String tformdata;
	String cookies;
	String urlFichierRemboursements;

	public EntiteConnexion (String id) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(TABLE, CLE_PARTITION, id);
	}

	/**
	 * NE PAS UTILISER
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	public EntiteConnexion () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		super(TABLE);
	}

	public void supprimerEntite () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		obtenirCloudTable(TABLE)
			.execute(TableOperation.delete(this));
	}

	/* Getters */

	public String getSid () { return sid; }
	public String getTformdata () { return tformdata; }

	/**
	 * @return Cookies sous forme d'objet JSON transformé en texte
	 */
	public String getCookies () { return cookies; }

	/**
	 * Méthode alternative pour obtenir les cookies
	 * @return Cookies sous forme d'objet Map
	 */
	public HashMap<String, String> obtenirCookiesMap () {
		HashMap<String, String> map = new HashMap<>();
		HashMap<String, Object> cookiesMap = new HashMap<>(new JSONObject(cookies).toMap());
		for (String cookie : cookiesMap.keySet()) {
			map.put(cookie, cookiesMap.get(cookie).toString());
		}
		return map;
	}

	//public boolean getInscriptionRequise () { return inscriptionRequise; }
	public String getUrlFichierRemboursements () { return urlFichierRemboursements; }

	/* Setters */

	public void setSid (String sid) { this.sid = sid; }

	public void setTformdata (String tformdata) { this.tformdata = tformdata; }

	/**
	 * @param cookies Doit pouvoir être désérialisé en JSON
	 * @throws JSONException Si la chaîne de caractères ne représente pas un objet JSON
	 */
	public void setCookies (String cookies) throws JSONException { 
		new JSONObject(cookies);
		this.cookies = cookies; 
	}

	/**
	 * Méthode alternative pour définir les cookies, directement depuis un objet Map.
	 * L'objet Map est converti en objet JSON puis en texte.
	 * @param cookies
	 */
	public void definirCookiesMap (HashMap<String,String> cookies) {
		this.cookies = new JSONObject(cookies).toString();
	}

	//public void setInscriptionRequise (boolean inscriptionRequise) { this.inscriptionRequise = inscriptionRequise; }
	public void setUrlFichierRemboursements (String url) { urlFichierRemboursements = url; }

	// Affichage
	public String toString () {
		String s = "***EntiteConnexion***\nid = " + rowKey 
			+ "\nsid = " + sid 
			+ "\ntformdata = " + tformdata 
			+ "\ntimestamp = " + getTimestamp().toString()
			+ "\ncookies = " + cookies
			+ "\n******************";
		return s;
	}

}