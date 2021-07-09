package app.mesmedicaments.api;

import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;
import app.mesmedicaments.utils.JSONArrays;

public interface IRequeteAvecIdentifieurs<ID extends IdentifieurGenerique>
        extends IConvertisseurIdentifieur<ID, JSONObject> {

    @Override
    default JSONObject serializeIdentifier(ID identifier) {
        return identifier.toJSON();
    }

    default String getJSONKeyForIdentifiers() {
        return "identifiers";
    }

    default List<ID> parseIdentifiersFromRequestBody(String body) {
        final var identifiers = new JSONObject(body).getJSONArray(getJSONKeyForIdentifiers());
        return JSONArrays.toStreamJSONObject(identifiers).map(jo -> deserializeIdentifier(jo))
                .collect(Collectors.toList());
    }
}
