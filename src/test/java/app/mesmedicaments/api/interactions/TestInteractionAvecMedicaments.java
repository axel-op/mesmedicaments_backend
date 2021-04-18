package app.mesmedicaments.api.interactions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import app.mesmedicaments.terminologies.substances.ConceptSubstanceBDPM;
import app.mesmedicaments.terminologies.substances.TerminologySubstances;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesBDPM;

public class TestInteractionAvecMedicaments {

    static private final TerminologySubstances<ConceptSubstanceBDPM> termino =
            new TerminologySubstancesBDPM();

    // acétate d'abyratérone, métoprolol
    static private final List<ConceptSubstance> substances =
            List.of(37304, 933, 933).stream().map(String::valueOf).map(termino::getConcept)
                    .map(Optional::get).collect(Collectors.toList());

    static private final List<MockMedicament> mockMedicaments =
            List.of(new MockMedicament(Set.of(substances.get(0))),
                    new MockMedicament(Set.of(substances.get(0), substances.get(1))),
                    new MockMedicament(Set.of(substances.get(1))));

    static private final List<MedicamentAvecSubstances<MockMedicament>> mockMedicamentsAvecSubstances =
            mockMedicaments.stream()
                    .map(mock -> new MedicamentAvecSubstances<>(mock, mock.getSubstances()))
                    .collect(Collectors.toList());

    @Test
    public void testJointureSubstancesInteragissantesAuxMedicaments() {
        final var mockInteractionSubstances = new MockInteractionSubstances(substances);
        final var mockInteractionMedicaments = new InteractionAvecMedicaments<>(
                mockInteractionSubstances, mockMedicamentsAvecSubstances);
        final var jointure = assertDoesNotThrow(
                mockInteractionMedicaments::joindreSubstancesInteragissantesAuxMedicaments);
        assertTrue(
                mockInteractionMedicaments.getInteractionSubstances().getSubstances().stream()
                        .allMatch(s -> jointure.stream().anyMatch(j -> j.getSubstance().equals(s))),
                "Toutes les substances de l'interaction n'ont pas été récupérées lors de la jointure");
        assertTrue(
                jointure.stream().map(SubstanceAvecMedicament::getMedicament)
                        .collect(Collectors.toSet())
                        .size() == mockInteractionSubstances.getSubstances().size(),
                "Le même médicament a été associé à deux substances différentes");
        for (var j : jointure) {
            assertTrue(j.getMedicament().getSubstances().contains(j.getSubstance()));
        }
    }

}
