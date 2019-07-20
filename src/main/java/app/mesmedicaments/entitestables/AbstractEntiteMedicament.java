package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class AbstractEntiteMedicament<Presentation extends Object> extends AbstractEntiteProduit {

    public void supprimerEntite() throws StorageException, URISyntaxException, InvalidKeyException {
        obtenirCloudTable(TABLE).execute(TableOperation.delete(this));
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
    String effetsIndesirables;

    // Constructeurs

    public AbstractEntiteMedicament (String partition, long code) 
        throws StorageException, InvalidKeyException, URISyntaxException 
    {
        super(partition, code);
    }
    
    /**
     * NE PAS UTILISER
     * @throws StorageException
     * @throws InvalidKeyException
     * @throws URISyntaxException
     */
    public AbstractEntiteMedicament () throws StorageException, InvalidKeyException, URISyntaxException {}

    /* Getters */

    public abstract Set<Presentation> obtenirPresentations();

    public String getNoms () { return noms; }
    public String getForme () { return forme; }
    public String getAutorisation () { return autorisation; }
    public String getMarque () { return marque; }
    public String getEffetsIndesirables () { return effetsIndesirables; }
    public String getPresentations () { return presentations; }
    public String getSubstancesActives () { return substancesActives; }

    public JSONArray obtenirNomsJArray () { 
        if (noms == null) { return new JSONArray(); }
        return new JSONArray(noms); 
    }

    public Set<SubstanceActive> obtenirSubstancesActives () {
        if (substancesActives == null) { return new HashSet<>(); }
        try {
            JSONObject json = new JSONObject(substancesActives);
            return json.keySet().stream()
                .map(codeStr -> {
                    JSONObject jsonSub = json.getJSONObject(codeStr);
                    return new SubstanceActive(
                        Long.parseLong(codeStr), 
                        jsonSub.getString("dosage"), 
                        jsonSub.getString("referenceDosage")
                    );
                })
                .collect(Collectors.toSet());
        }
        catch (JSONException e) {
            return StreamSupport.stream(new JSONArray(substancesActives).spliterator(), false)
                .map((code) -> new SubstanceActive((Long) code, null, null))
                .collect(Collectors.toSet());
        }
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
    public void setEffetsIndesirables (String effets) { this.effetsIndesirables = effets; }

    public abstract void definirPresentations (Iterable<Presentation> presentations);

    public void definirNomsJArray (JSONArray noms) {
        this.noms = noms.toString();
    }

    public void setSubstancesActives (String substancesActives) { this.substancesActives = substancesActives; }

    public void definirSubstancesActives (Iterable<SubstanceActive> substances) {
        JSONObject json = new JSONObject();
        for (SubstanceActive substance : substances) {
            json.put(substance.codeSubstance.toString(), new JSONObject()
                .put("dosage", substance.dosage)
                .put("referenceDosage", substance.referenceDosage)
            );
        }
        this.substancesActives = json.toString();
    }


    public static class SubstanceActive {
        public final Long codeSubstance;
        public final String dosage;
        public final String referenceDosage;

        public SubstanceActive (long code, String dosage, String referenceDosage) {
            this.codeSubstance = code;
            this.dosage = dosage != null ? dosage : "";
            this.referenceDosage = referenceDosage != null ? referenceDosage : "";
        }
    }

}