package app.mesmedicaments.api.substances;

import lombok.NonNull;

class SubstanceNotFoundException extends Exception {

    SubstanceNotFoundException(@NonNull SubstanceIdentifier identifier) {
        super("Substance non trouvée : " + identifier.toString());
    }

}
