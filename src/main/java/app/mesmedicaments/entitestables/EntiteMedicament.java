package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

public class EntiteMedicament extends AbstractEntiteProduit {

    public static EntiteMedicament obtenirEntite (long codeCIS)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntite("medicament", codeCIS, EntiteMedicament.class);
	}

	public static Iterable<EntiteMedicament> obtenirToutesLesEntites ()
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirToutesLesEntites("medicament", EntiteMedicament.class);
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

    /* Getters */

    public String getNoms () { return noms; }
    public String getForme () { return forme; }
    public String getAutorisation () { return autorisation; }
    public String getMarque () { return marque; }

    public JSONArray obtenirNomsJsonArray () {
        return new JSONArray(noms);
    }

    /* Setters */

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