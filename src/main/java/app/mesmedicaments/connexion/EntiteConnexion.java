package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONObject;

import app.mesmedicaments.TableEntite;

public class EntiteConnexion extends TableEntite {

	//private static final CloudTable TABLE_UTILISATEURS;
	private static final String CLE_PARTITION;

	static { 
		CLE_PARTITION = "connexion"; // a modifier 
	}

	protected static EntiteConnexion obtenirEntite (String id) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION, 
			id, 
			EntiteConnexion.class);
		return obtenirCloudTable(System.getenv("tableazure_connexions"))
			.execute(operation)
			.getResultAsType();
	}

	String sid;
	String tformdata;
	String cookies;
	//boolean inscriptionRequise;
    String urlFichierRemboursements;

	// Le type HashMap n'est pas supporté et doit être String dès en entrant pour les cookies
	public EntiteConnexion (String id) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(System.getenv("tableazure_connexions"), CLE_PARTITION, id);
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
		super(System.getenv("tableazure_connexions"));
	}

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
    public String getUrlFichierRemboursements () { return urlFichierRemboursements; }

	// Setters
	public void setSid (String sid) { this.sid = sid; }
	public void setTformdata (String tformdata) { this.tformdata = tformdata; }
	public void setCookies (String cookies) { this.cookies = cookies; }
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