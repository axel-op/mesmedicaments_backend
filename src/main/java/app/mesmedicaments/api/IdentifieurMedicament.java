package app.mesmedicaments.api;

import org.json.JSONObject;
import app.mesmedicaments.objets.medicaments.Medicament;

public class IdentifieurMedicament extends IdentifieurGenerique<Medicament<?, ?, ?>> {

    public IdentifieurMedicament(JSONObject json) {
        super(json);
    }

    public IdentifieurMedicament(Medicament<?, ?, ?> medicament) {
        super(medicament);
    }

}
