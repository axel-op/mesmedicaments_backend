package app.mesmedicaments.api.substances;

import app.mesmedicaments.api.IdentifieurSubstance;
import lombok.NonNull;

class SubstanceNotFoundException extends Exception {

    SubstanceNotFoundException(@NonNull IdentifieurSubstance identifier) {
        super("Substance non trouvée : " + identifier.toString());
    }

}
