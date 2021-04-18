package app.mesmedicaments.api.interactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.google.common.collect.Sets;
import app.mesmedicaments.interactions.service.InteractionSubstancesDTO;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.PackagePrivate;

@Getter
@PackagePrivate
class InteractionAvecMedicaments<M> {
    private final InteractionSubstancesDTO interactionSubstances;
    private final Collection<MedicamentAvecSubstances<M>> medicaments;

    @PackagePrivate
    InteractionAvecMedicaments(InteractionSubstancesDTO interactionSubstances,
            Collection<MedicamentAvecSubstances<M>> medicaments) {
        assert interactionSubstances.getSubstances().size() == medicaments.size();
        this.interactionSubstances = interactionSubstances;
        this.medicaments = medicaments;
    }

    /**
     * Associe les substances de l'interaction aux médicaments.
     * 
     * @return
     */
    @PackagePrivate
    List<SubstanceAvecMedicament<M>> joindreSubstancesInteragissantesAuxMedicaments() {
        return joindreSubstancesInteragissantesAuxMedicaments(
                new ArrayList<>(interactionSubstances.getSubstances()),
                new ArrayList<>(medicaments));
    }

    private List<SubstanceAvecMedicament<M>> joindreSubstancesInteragissantesAuxMedicaments(
            List<ConceptSubstance> substances, List<MedicamentAvecSubstances<M>> medicaments) {
        if (substances.isEmpty())
            return new LinkedList<>();
        // Mappe chaque substance à l'ensemble des médicaments qui la contiennent
        final var possibilites = substances.stream()
                .collect(Collectors.toMap(s -> s,
                        s -> medicaments.stream().filter(m -> m.getSubstances().contains(s))
                                .collect(Collectors.toSet()),
                        (meds1, meds2) -> Sets.union(meds1, meds2)));
        /*
         * System.out.println("---"); for (var e : possibilites.entrySet()) {
         * System.out.println(e.getKey().getId()); e.getValue().stream().map(s -> "\t" +
         * s.getMedicament().toString()).forEach(System.out::println); }
         */
        // On associe prioritairement la substance ayant le moins de médicaments associés
        final var prioritaire = possibilites.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().size())).findFirst().get();
        final var substance = prioritaire.getKey();
        if (prioritaire.getValue().isEmpty())
            throw new IllegalArgumentException(
                    "Aucun médicament ne peut être associé à la substance "
                            + substance.getId().toString());
        // final var medicamentAvecSubstances = prioritaire.getValue().stream().findAny().get();
        for (var medAvecSubs : prioritaire.getValue()) {
            try {
                final var subs = withoutFirst(substances, substance);
                final var meds = withoutFirst(medicaments, medAvecSubs);
                final var list = joindreSubstancesInteragissantesAuxMedicaments(subs, meds);
                list.add(new SubstanceAvecMedicament<>(substance, medAvecSubs.getMedicament()));
                return list;
            } catch (IllegalArgumentException e) {
                continue;
            }
        }
        throw new IllegalArgumentException("Aucune combinaison trouvée");
    }

    /**
     * Renvoie une copie de la liste, sans la première occurrence de {@code object}.
     * 
     * @param <T>
     * @param list
     * @param object
     * @return
     */
    private <T> List<T> withoutFirst(List<T> list, T object) {
        if (list.isEmpty())
            return list;
        final var newList = new ArrayList<T>(list.size());
        var removed = false;
        for (T el : list) {
            if (!removed && Objects.equals(el, object)) {
                removed = true;
            } else {
                newList.add(el);
            }
        }
        return newList;
    }
}


@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
class SubstanceAvecMedicament<M> {
    private final ConceptSubstance substance;
    private final M medicament;
}
