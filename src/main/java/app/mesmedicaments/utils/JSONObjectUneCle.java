package app.mesmedicaments.utils;

import org.json.JSONObject;

public class JSONObjectUneCle extends JSONObject {

    public JSONObjectUneCle(String cle, Object valeur) {
        super();
        super.put(cle, valeur);
    }
}
