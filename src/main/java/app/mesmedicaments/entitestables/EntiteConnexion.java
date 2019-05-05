package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableQuery;
import com.microsoft.azure.storage.table.TableQuery.QueryComparisons;

import org.json.JSONException;
import org.json.JSONObject;

public class EntiteConnexion extends AbstractEntite {

	//private static final CloudTable TABLE_UTILISATEURS;
	//private static final String CLE_PARTITION;
	private static final String TABLE = System.getenv("tableazure_connexions");
	private static final String CLEPARTITION_NONABOUTIE = "non aboutie";

	public static Iterable<EntiteConnexion> obtenirEntitesPartition (String partition) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			partition);
		return obtenirCloudTable(TABLE)
			.execute(new TableQuery<>(EntiteConnexion.class)
				.where(filtrePK));
	} 

	private static EntiteConnexion obtenirEntite (String partition, String id)
		throws URISyntaxException, InvalidKeyException
	{
		try {
			TableOperation operation = TableOperation.retrieve(
				partition, 
				id, 
				EntiteConnexion.class);
			return obtenirCloudTable(TABLE)
				.execute(operation)
				.getResultAsType();
		}
		catch (StorageException e) {
			return null;
		}
	}

	public static Optional<EntiteConnexion> obtenirEntiteAboutie (String id) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		EntiteConnexion entite = null;
		for (int i = 0; i < 60; i += 5) {
			String partition = String.valueOf(i);
			if (partition.length() == 1) { partition = "0" + partition; }
			entite = obtenirEntite(partition, id);
			if (entite != null) { return Optional.of(entite); }
		}
		return Optional.empty();
	}

	/**
	 * Ne renvoie que les connexions non abouties
	 * @param id
	 * @return
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	public static EntiteConnexion obtenirEntiteNonAboutie (String id) 
		throws URISyntaxException, InvalidKeyException
	{
		return obtenirEntite(CLEPARTITION_NONABOUTIE, id);
	}

	String sid;
	String tformdata;
	String cookies;
	//boolean inscriptionRequise;
	String urlFichierRemboursements;
	String motDePasse;
	Integer tentatives;

	public EntiteConnexion (String id) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(TABLE, CLEPARTITION_NONABOUTIE, id);
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

	public void marquerCommeEchouee ()
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		setPartitionKey("échouée");
		supprimerAutresOccurrences();
		mettreAJourEntite();
	}

	/**
	 * Supprime toutes les occurrences ABOUTIES ayant le même id sur une partition différente de la table connexions
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	private void supprimerAutresOccurrences () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		CloudTable cloudTable = obtenirCloudTable(TABLE);
		for (int i = 0; i < 60; i += 5) {
			String partition = String.valueOf(i);
			if (partition.length() == 1) { partition = "0" + partition; }
			if (!partition.equals(getPartitionKey())) {
				TableOperation operation = TableOperation.retrieve(partition, getRowKey(), EntiteConnexion.class);
				EntiteConnexion entite = cloudTable.execute(operation).getResultAsType();
				if (entite != null) {
					operation = TableOperation.delete(entite);
					cloudTable.execute(operation);
				}
			}
		}
	}

	@Override
	public void mettreAJourEntite () throws StorageException {
		if (tentatives == null) { tentatives = 0; }
		if (this.partitionKey.equals(CLEPARTITION_NONABOUTIE)
			&& !Optional.ofNullable(urlFichierRemboursements).orElse("").equals(""))
		{
			int minute = LocalDateTime.now().getMinute();
			String partition = String.valueOf(minute - (minute % 5));
			if (partition.length() == 1) { partition = "0" + partition; }
			this.partitionKey = partition;
		}
		try { supprimerAutresOccurrences(); }
		catch (URISyntaxException | InvalidKeyException e) {}
		super.mettreAJourEntite();
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
	public String getMotDePasse () { return motDePasse; }

	public Integer getTentatives () { 
		return Optional.ofNullable(tentatives).orElse(0);
	}

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
	public void setMotDePasse (String mdp) { motDePasse = mdp; }
	public void setTentatives (int tentatives) { this.tentatives = tentatives; }

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