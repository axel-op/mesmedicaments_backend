package app.mesmedicaments.api;

import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;
import app.mesmedicaments.utils.JSONArrays;

public interface IRequeteAvecIdentifieurs<T extends IObjetIdentifiable, ID extends IdentifieurGenerique<T>> {

    ID deserializeIdentifier(JSONObject jsonObject);

    default JSONObject serializeIdentifier(ID identifier) {
        return identifier.toJSON();
    }

    default String getJSONKeyForIdentifiers() {
        return "identifiers";
    }

    default List<ID> parseIdentifiersFromRequestBody(String body) {
        final var identifiers = new JSONObject(body).getJSONArray(getJSONKeyForIdentifiers());
        return JSONArrays.toStreamJSONObject(identifiers).map(this::deserializeIdentifier)
                .collect(Collectors.toList());
    }
}
