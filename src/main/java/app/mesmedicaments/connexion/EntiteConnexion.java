package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

import org.json.JSONObject;

public class EntiteConnexion extends TableServiceEntity {

	private static final CloudTable TABLE_UTILISATEURS;
	private static final String CLE_PARTITION_CONNEXIONS;

	static { 
		CLE_PARTITION_CONNEXIONS = "connexion"; // a modifier 
		TABLE_UTILISATEURS = obtenirCloudTable();
	}

	private static CloudTable obtenirCloudTable () {
		try {
			return CloudStorageAccount
				.parse(System.getenv("AzureWebJobsStorage"))
				.createCloudTableClient()
				.getTableReference(System.getenv("tableazure_connexions"));
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			return null;
		}
	} 

	protected static void definirEntite (EntiteConnexion entite) 
		throws StorageException
	{
		TableOperation operation = TableOperation.insertOrReplace(entite);
		TABLE_UTILISATEURS.execute(operation);
	}
	
	protected static void mettreAJourEntite (EntiteConnexion entite)
		throws StorageException
	{
		TableOperation operation = TableOperation.merge(entite);
		TABLE_UTILISATEURS.execute(operation);
	}

	protected static EntiteConnexion obtenirEntite (String id) 
		throws StorageException
	{
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION_CONNEXIONS, 
			id, 
			EntiteConnexion.class);
		return TABLE_UTILISATEURS
			.execute(operation)
			.getResultAsType();
	}

	String sid;
	String tformdata;
	String cookies;
	//boolean inscriptionRequise;

	// Le type HashMap n'est pas supporté et doit être String dès en entrant pour les cookies
	public EntiteConnexion (String id) {
		this.partitionKey = CLE_PARTITION_CONNEXIONS;
		this.rowKey = id;
	}

	public EntiteConnexion () {}

	// Getters
	public String getSid () { return sid; }
	public String getTformdata () { return tformdata; }
	public String getCookies () { return cookies; }
	public HashMap<String, String> obtenirCookiesMap () {
		HashMap<String, String> map = new HashMap<>();
		HashMap<String, Object> cookiesMap = new HashMap<>(new JSONObject(cookies).toMap());
		for (String cookie : cookiesMap.keySet()) {
			map.put(cookie, cookiesMap.get(cookie).toString());
		}
		return map;
	}
	//public boolean getInscriptionRequise () { return inscriptionRequise; }

	// Setters
	public void setSid (String sid) { this.sid = sid; }
	public void setTformdata (String tformdata) { this.tformdata = tformdata; }
	public void setCookies (String cookies) { this.cookies = cookies; }
	public void definirCookiesMap (HashMap<String,String> cookies) {
		this.cookies = new JSONObject(cookies).toString();
	}
	//public void setInscriptionRequise (boolean inscriptionRequise) { this.inscriptionRequise = inscriptionRequise; }

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