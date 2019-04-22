package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

public class EntiteMedicament extends AbstractEntiteProduit {

    /*
    //private static final String CLE_PARTITION = "medicament";
    private static final String TABLE = System.getenv("tableazure_medicaments"); /// A METTRE

    public static EntiteMedicament obtenirEntite (long codecis, String nom)
        throws URISyntaxException, InvalidKeyException, StorageException
    {
        TableOperation operation = TableOperation.retrieve(
            String.valueOf(codecis), 
            nom, 
            EntiteMedicament.class);
        return obtenirCloudTable(TABLE) /// A METTRE
                .execute(operation)
                .getResultAsType();
    }

    public static Iterable<EntiteMedicament> obtenirEntites (long codecis)
        throws URISyntaxException, InvalidKeyException, StorageException
    {
        String filtrePK = TableQuery.generateFilterCondition(
            "PartitionKey", 
            QueryComparisons.EQUAL, 
            String.valueOf(codecis));
        return obtenirCloudTable(TABLE)
            .execute(new TableQuery<>(EntiteMedicament.class)
            .where(filtrePK));
    }
    */

    public static EntiteMedicament obtenirEntite (long codeCIS)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntite("medicament", codeCIS, EntiteMedicament.class);
	}

	public static Iterable<EntiteMedicament> obtenirToutesLesEntites ()
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntites("medicament", EntiteMedicament.class);
    }
    
    /**
     * Les différents noms du médicament, sous forme de JSONArray transformé en chaîne de caractères
     */
    String noms;
    String forme;
    String autorisation;
    String marque;

    public EntiteMedicament (long codeCIS) 
        throws StorageException, InvalidKeyException, URISyntaxException 
    {
        super("medicament", codeCIS);
    }

    public EntiteMedicament () throws StorageException, InvalidKeyException, URISyntaxException {}

    /*** Getters ***/

    public String getNoms () { return noms; }
    public String getForme () { return forme; }
    public String getAutorisation () { return autorisation; }
    public String getMarque () { return marque; }

    public JSONArray obtenirNomsJsonArray () {
        return new JSONArray(noms);
    }

    /*** Setters ***/

    public void setNoms (String noms) { this.noms = noms; }
    public void setForme (String forme) { this.forme = forme; }
    public void setAutorisation (String autorisation) { this.autorisation = autorisation; }
    public void setMarque (String marque) { this.marque = marque; }

    public void definirNomsJsonArray (JSONArray noms) {
        this.noms = noms.toString();
    }

    public void ajouterNom (String nom) {
        JSONArray noms = new JSONArray(this.noms);
        noms.put(nom);
        this.noms = noms.toString();
    }


}