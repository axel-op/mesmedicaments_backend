package app.mesmedicaments.api.medicaments;

import org.json.JSONObject;
import app.mesmedicaments.api.IdentifieurGenerique;
import lombok.experimental.PackagePrivate;

@PackagePrivate class IdentifieurMedicament extends IdentifieurGenerique {

    public IdentifieurMedicament(JSONObject json) {
        super(json);
    }
    
}
