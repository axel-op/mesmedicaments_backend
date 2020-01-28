package app.mesmedicaments;

import org.json.JSONObject;
import org.json.JSONString;

public
interface IJSONSerializable
extends JSONString {
    public JSONObject toJSON();
    default String toJSONString() {
        return toJSON().toString();
    }
}