package app.mesmedicaments.api.interactions;

import java.util.Set;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.PackagePrivate;

@RequiredArgsConstructor
@Getter
@PackagePrivate
class MockMedicament {

    private final Set<ConceptSubstance> substances;

}
