package app.mesmedicaments.api;

import org.json.JSONObject;

public class ConvertisseurJSONSubstance extends ConvertisseurJSON<Substance> {

    @Override
    public JSONObject toJSON(Substance substance) {
        return new JSONObject().put("id", substance.getId()).put("source", substance.getSource())
                .put("names", substance.getNames());
    }

}
