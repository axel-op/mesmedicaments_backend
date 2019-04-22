package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONException;
import org.json.JSONObject;

public class EntiteConnexion extends AbstractEntite {

	//private static final CloudTable TABLE_UTILISATEURS;
	private static final String CLE_PARTITION;

	static { 
		CLE_PARTITION = "connexion"; // a modifier 
	}

	public static EntiteConnexion obtenirEntite (String id) 
		throws URISyntaxException, InvalidKeyException
	{
		try {
			TableOperation operation = TableOperation.retrieve(
				CLE_PARTITION, 
				id, 
				EntiteConnexion.class);
			return obtenirCloudTable(System.getenv("tableazure_connexions"))
				.execute(operation)
				.getResultAsType();
		}
		catch (StorageException e ) {
			return null;
		}
	}

	String sid;
	String tformdata;
	String cookies;
	//boolean inscriptionRequise;
	String urlFichierRemboursements;
	String motDePasse;

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

	/*** Getters ***/

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
	public String getMotDePasse () { return motDePasse; }

	/*** Setters ***/

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
	public void setMotDePasse (String mdp) { motDePasse = mdp; }

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