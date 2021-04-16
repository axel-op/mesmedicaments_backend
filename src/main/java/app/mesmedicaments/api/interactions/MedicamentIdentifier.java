package app.mesmedicaments.api.interactions;

import org.json.JSONObject;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.PackagePrivate;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Getter
@PackagePrivate
class MedicamentIdentifier {

    private final MedicamentSource source;
    private final String id;

    static @PackagePrivate MedicamentIdentifier parse(JSONObject json) {
        var id = json.optString("id");
        if (id.equals(""))
            id = String.valueOf(json.getInt("code"));
        if (!json.optString("source").toUpperCase().equals("BDPM")
                && !json.optString("pays").equals("france"))
            throw new IllegalArgumentException("Source médicament incorrecte");
        final var source = MedicamentSource.BDPM;
        return new MedicamentIdentifier(source, id);
    }

}
