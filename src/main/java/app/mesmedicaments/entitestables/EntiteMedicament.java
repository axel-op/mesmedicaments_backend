package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    String effetsIndesirables;

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

    public Set<Presentation> obtenirPresentations () {
        if (presentations == null) return new HashSet<>();
        JSONObject json = new JSONObject(presentations);
        return json.keySet().stream()
            .map(nom -> {
                JSONObject jsonPres = json.getJSONObject(nom);
                return new Presentation(
                    nom, 
                    jsonPres.getDouble("prix"), 
                    jsonPres.getInt("tauxRemboursement"), 
                    jsonPres.getDouble("honorairesDispensation"), 
                    jsonPres.getString("conditionsRemboursement")
                );
            })
            .collect(Collectors.toSet());
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

    public void definirPresentations (Iterable<Presentation> presentations) {
        if (presentations == null) this.presentations = new JSONObject().toString();
        else {
            JSONObject json = new JSONObject();
            for (Presentation presentation : presentations) {
                json.put(presentation.nom, new JSONObject()
                    .put("prix", presentation.prix)
                    .put("tauxRemboursement", presentation.tauxRemboursement)
                    .put("honorairesDispensation", presentation.honorairesDispensation)
                    .put("conditionsRemboursement", presentation.conditionsRemboursement)
                );
            }
            this.presentations = json.toString();
        }
    }

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

    public static class Presentation {
        public final String nom;
        public final Double prix;
        public final Integer tauxRemboursement;
        public final Double honorairesDispensation;
        public final String conditionsRemboursement;

        public Presentation (String nom, double prix, int tauxRemboursement, double honorairesDispensation, String conditionsRemboursement) {
            if (nom == null) throw new IllegalArgumentException();
            this.nom = nom;
            this.prix = prix;
            this.tauxRemboursement = tauxRemboursement;
            this.honorairesDispensation = honorairesDispensation;
            this.conditionsRemboursement = conditionsRemboursement != null ? conditionsRemboursement : "";
        }
    }

}