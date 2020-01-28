package app.mesmedicaments.objets.presentations;

import org.json.JSONObject;

import app.mesmedicaments.objets.Pays;

public
class PresentationFrance
extends Presentation<Pays.France> {

    private String nom;
    private double prix;
    private int tauxRemboursement;
    private double honorairesDispensation;
    private String conditionsRemboursement;

    public PresentationFrance(
        String nom, 
        double prix, 
        int tauxRemboursement, 
        double honorairesDispensation, 
        String conditionsRemboursement
    ) {
        if (nom == null) throw new IllegalArgumentException("Le nom d'une présentation ne peut pas être null");
        this.nom = nom;
        this.prix = prix;
        this.tauxRemboursement = tauxRemboursement;
        this.honorairesDispensation = honorairesDispensation;
        this.conditionsRemboursement = conditionsRemboursement != null ? conditionsRemboursement : "";
    }

    public PresentationFrance(JSONObject json) {
        this(
            json.getString("nom"),
            json.getDouble("prix"),
            json.getInt("tauxRemboursement"),
            json.getDouble("honorairesDispensation"),
            json.getString("conditionsRemboursement")
        );
    }

	@Override
	public JSONObject toJSON() {
        return new JSONObject()
            .put("nom", nom)
            .put("prix", prix)
            .put("tauxRemboursement", tauxRemboursement)
            .put("honorairesDispensation", honorairesDispensation)
            .put("conditionsRemboursement", conditionsRemboursement);
	}

}