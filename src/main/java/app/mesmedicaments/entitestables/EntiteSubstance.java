package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

public class EntiteSubstance extends AbstractEntiteProduit {

	public static EntiteSubstance obtenirEntite (long codeSubstance)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirEntite("substance", codeSubstance, EntiteSubstance.class);
	}

	public static Iterable<EntiteSubstance> obtenirToutesLesEntites ()
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return AbstractEntiteProduit.obtenirToutesLesEntites("substance", EntiteSubstance.class);
	}

	String noms;

	public EntiteSubstance (long codeSubstance) 
		throws StorageException, InvalidKeyException, URISyntaxException 
	{
		super("substance", codeSubstance);
	}

	public EntiteSubstance () throws StorageException, InvalidKeyException, URISyntaxException {}

	/* Getters */

	public String getNoms () { return noms; }

	public JSONArray obtenirNomsJArray () {
        return new JSONArray(noms);
    }

	/* Setters */

	public void setNoms (String noms) { this.noms = noms; }

	public void definirNomsJArray (JSONArray noms) {
        this.noms = noms.toString();
    }

    public void ajouterNom (String nom) {
        JSONArray noms = new JSONArray(this.noms);
        noms.put(nom);
        this.noms = noms.toString();
    }
}