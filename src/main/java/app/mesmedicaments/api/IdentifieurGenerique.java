package app.mesmedicaments.api;

import java.util.Map;
import org.json.JSONObject;
import app.mesmedicaments.IJSONSerializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
public class IdentifieurGenerique implements IJSONSerializable {

    private final String source;
    private final String id;

    public IdentifieurGenerique(JSONObject json) {
        this.source = json.getString("source");
        this.id = json.getString("id");
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject(Map.of("source", source, "id", id));
    }

}
