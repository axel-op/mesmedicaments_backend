package app.mesmedicaments.api.substances;

import org.json.JSONObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.PackagePrivate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
@PackagePrivate
class SubstanceIdentifier {
    final private String source;
    final private String id;

    static SubstanceIdentifier fromJSON(JSONObject jsonObject) {
        final var id = jsonObject.getString("id");
        final var source = jsonObject.getString("source");
        return new SubstanceIdentifier(source, id);
    }
}
