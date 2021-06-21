package app.mesmedicaments.api.substances;

import java.util.Map;
import java.util.Set;
import app.mesmedicaments.terminologies.substances.TerminologySubstances;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesBDPM;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesRxNorm;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
class Substance {

    static final private Map<String, TerminologySubstances<?>> terminos = Map.of("bdpm",
            new TerminologySubstancesBDPM(), "rxnorm", new TerminologySubstancesRxNorm());

    final private String source;
    final private String id;
    final private Set<String> names;

    static Substance fromIdentifier(SubstanceIdentifier identifier)
            throws SubstanceNotFoundException {
        final var source = identifier.getSource().toLowerCase();
        final var id = identifier.getId();
        if (!terminos.containsKey(source)) {
            throw new SubstanceNotFoundException(identifier);
        }
        final var concept = terminos.get(source).getConcept(id)
                .orElseThrow(() -> new SubstanceNotFoundException(identifier));
        return new Substance(source, concept.getId(), concept.getNames());
    }
}
