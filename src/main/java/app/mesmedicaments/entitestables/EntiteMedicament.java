package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Map;
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
    String prix;

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
    public String getPrix () { return prix; }
    public JSONObject obtenirPrixJObject () {
        if (prix == null) return new JSONObject();
        return new JSONObject(prix);
    }

    /* Setters */

    public void setNoms (String noms) { this.noms = noms; }
    public void setForme (String forme) { this.forme = forme; }
    public void setAutorisation (String autorisation) { this.autorisation = autorisation; }
    public void setMarque (String marque) { this.marque = marque; }
    public void setPrix (String prix) { this.prix = prix; }

    public void definirPrixMap (Map<String, Double> map) {
        prix = (map == null)
            ? new JSONObject().toString()
            : new JSONObject(map).toString();
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