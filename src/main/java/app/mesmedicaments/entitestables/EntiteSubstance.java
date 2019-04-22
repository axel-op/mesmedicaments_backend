package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

public class EntiteSubstance extends AbstractEntiteProduit {

	/*
	private static final String TABLE = System.getenv("tableazure_substances"); /// A METTRE

	public static EntiteSubstance obtenirEntite (long codesubstance, String nom)
		throws URISyntaxException, InvalidKeyException, StorageException
	{
		TableOperation operation = TableOperation.retrieve(
			String.valueOf(codesubstance), 
			nom, 
			EntiteSubstance.class);
		return obtenirCloudTable(TABLE) /// A METTRE
				.execute(operation)
				.getResultAsType();
	}
	

	public static Iterable<EntiteSubstance> obtenirEntites (long codesubstance)
		throws URISyntaxException, InvalidKeyException, StorageException
	{
		String filtrePK = TableQuery.generateFilterCondition(
			"PartitionKey", 
			QueryComparisons.EQUAL, 
			String.valueOf(codesubstance));
		return obtenirCloudTable(TABLE)
			.execute(new TableQuery<>(EntiteSubstance.class)
			.where(filtrePK));
	}
	*/

	public static EntiteSubstance obtenirEntite (long codeSubstance)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntite("substance", codeSubstance, EntiteSubstance.class);
	}

	public static Iterable<EntiteSubstance> obtenirToutesLesEntites ()
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntites("substance", EntiteSubstance.class);
	}

	String noms;

	public EntiteSubstance (long codeSubstance) 
		throws StorageException, InvalidKeyException, URISyntaxException 
	{
		super("substance", codeSubstance);
	}

	public EntiteSubstance () throws StorageException, InvalidKeyException, URISyntaxException {}

	/*** Getters ***/

	public String getNoms () { return noms; }

	public JSONArray obtenirNomsJsonArray () {
        return new JSONArray(noms);
    }

	/*** Setters ***/

	public void setNoms (String noms) { this.noms = noms; }

	public void definirNomsJsonArray (JSONArray noms) {
        this.noms = noms.toString();
    }

    public void ajouterNom (String nom) {
        JSONArray noms = new JSONArray(this.noms);
        noms.put(nom);
        this.noms = noms.toString();
    }
}