package app.mesmedicaments.api;

import org.json.JSONObject;

public class IdentifieurSubstance extends IdentifieurGenerique<Substance> {

    public IdentifieurSubstance(JSONObject json) {
        super(json);
    }

    public IdentifieurSubstance(Substance substance) {
        super(substance);
    }

}
