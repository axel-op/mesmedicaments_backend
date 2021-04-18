package app.mesmedicaments.api.interactions;

import java.util.List;
import app.mesmedicaments.interactions.service.InteractionSubstancesDTO;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import lombok.NonNull;

public class MockInteractionSubstances extends InteractionSubstancesDTO {

    MockInteractionSubstances(@NonNull List<ConceptSubstance> substances) {
        super(substances, "Ceci est une fausse interaction", 0, "MockSource");
    }
    
}
