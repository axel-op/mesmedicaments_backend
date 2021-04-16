package app.mesmedicaments.api.interactions;

import java.util.Collection;
import app.mesmedicaments.interactions.service.InteractionSubstancesDTO;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.PackagePrivate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@PackagePrivate
class InteractionAvecMedicaments<M> {
    private final InteractionSubstancesDTO interactionSubstances;
    private final Collection<MedicamentAvecSubstances<M>> medicaments;
}
