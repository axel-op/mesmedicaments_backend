package app.mesmedicaments.objets.substances;

import org.json.JSONObject;

import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;

public class SubstanceActiveFrance extends Substance<Pays.France> {

    private final String dosage;
    private final String referenceDosage;

    public SubstanceActiveFrance(int code, Noms noms, String dosage, String referenceDosage) {
        super(Pays.France.instance, code, noms);
        this.dosage = dosage;
        this.referenceDosage = referenceDosage;
    }

    public SubstanceActiveFrance(JSONObject json) {
        this(
            json.getInt("code"),
            new Noms(json.getJSONObject("noms")),
            json.getString("dosage"),
            json.getString("referenceDosage"));
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("dosage", dosage)
            .put("referenceDosage", referenceDosage);
    }
}