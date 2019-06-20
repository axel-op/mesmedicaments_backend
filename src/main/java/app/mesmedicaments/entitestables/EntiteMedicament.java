package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EntiteMedicament extends AbstractEntiteProduit {

    public static Optional<EntiteMedicament> obtenirEntite (long codeCIS)
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
    String substancesActives;
    String presentations;

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
    public JSONArray obtenirNomsJArray () { 
        if (noms == null) { return new JSONArray(); }
        return new JSONArray(noms); 
    }
    public String getSubstancesActives () { return substancesActives; }
    public JSONObject obtenirSubstancesActivesJObject () {
        if (substancesActives == null) { return new JSONObject(); }
        try {
            return new JSONObject(substancesActives);
        }
        catch (JSONException e) {
            JSONObject jObject = new JSONObject();
            new JSONArray(substancesActives)
                .forEach((code) -> jObject.put(String.valueOf(code), new JSONObject()
                    .put("dosage", "")
                    .put("referenceDosage", "")
                ));
            return jObject;
        }
    }
    public String getPresentations () { return presentations; }
    public JSONObject obtenirPresentationsJObject () {
        if (presentations == null) return new JSONObject();
        return new JSONObject(presentations);
    }

    public Long obtenirCodeCis () {
        return Long.parseLong(getRowKey());
    }

    /* Setters */

    public void setNoms (String noms) { this.noms = noms; }
    public void setForme (String forme) { this.forme = forme; }
    public void setAutorisation (String autorisation) { this.autorisation = autorisation; }
    public void setMarque (String marque) { this.marque = marque; }
    public void setPresentations (String presentations) { this.presentations = presentations; }

    public void definirPresentationsJObject (JSONObject presentations) {
        if (presentations == null) this.presentations = new JSONObject().toString();
        else this.presentations = presentations.toString();
    }

    public void definirNomsJArray (JSONArray noms) {
        this.noms = noms.toString();
    }

    public void ajouterNom (String nom) {
        JSONArray noms = new JSONArray(this.noms);
        noms.put(nom);
        this.noms = noms.toString();
    }

    public void setSubstancesActives (String substancesActives) { this.substancesActives = substancesActives; }

    public void definirSubstancesActivesJObject (JSONObject jObject) {
        this.substancesActives = jObject.toString();
    }

}