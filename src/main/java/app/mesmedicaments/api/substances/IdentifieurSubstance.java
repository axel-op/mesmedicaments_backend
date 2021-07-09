package app.mesmedicaments.api.substances;

import org.json.JSONObject;
import app.mesmedicaments.api.IdentifieurGenerique;
import lombok.experimental.PackagePrivate;

@PackagePrivate
class IdentifieurSubstance extends IdentifieurGenerique {

    public IdentifieurSubstance(JSONObject json) {
        super(json);
    }

}
