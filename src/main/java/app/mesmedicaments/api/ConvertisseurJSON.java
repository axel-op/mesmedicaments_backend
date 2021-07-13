package app.mesmedicaments.api;

import org.json.JSONObject;

public abstract class ConvertisseurJSON<T> {

    abstract public JSONObject toJSON(T object);
    
}
