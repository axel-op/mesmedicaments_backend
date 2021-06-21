package app.mesmedicaments.api.substances;

import org.json.JSONObject;
import lombok.experimental.UtilityClass;

@UtilityClass
class JSONConverter {

    static JSONObject toJSON(Substance substance) {
        return new JSONObject().put("id", substance.getId()).put("source", substance.getSource())
                .put("names", substance.getNames());
    }

}
