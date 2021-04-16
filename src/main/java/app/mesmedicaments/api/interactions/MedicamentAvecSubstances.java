package app.mesmedicaments.api.interactions;

import java.util.Set;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.PackagePrivate;

@RequiredArgsConstructor
@Getter
@PackagePrivate
class MedicamentAvecSubstances<M> {

    private final M medicament;
    private final Set<? extends ConceptSubstance> substances;

}
