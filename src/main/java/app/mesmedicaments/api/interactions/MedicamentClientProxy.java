package app.mesmedicaments.api.interactions;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import app.mesmedicaments.api.medicaments.ClientTableMedicamentsFrance;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.presentations.Presentation;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.terminologies.substances.ConceptSubstance;
import app.mesmedicaments.terminologies.substances.ConceptSubstanceBDPM;
import app.mesmedicaments.terminologies.substances.TerminologySubstances;
import app.mesmedicaments.terminologies.substances.TerminologySubstancesBDPM;
import lombok.SneakyThrows;
import lombok.experimental.PackagePrivate;
import lombok.experimental.UtilityClass;

@UtilityClass
@PackagePrivate
class MedicamentClientProxy {

    static private ClientTableMedicamentsFrance client;
    static private TerminologySubstances<ConceptSubstanceBDPM> terminoBDPM =
            new TerminologySubstancesBDPM();

    static private ClientTableMedicamentsFrance getClient() throws DBException {
        if (client == null)
            client = new ClientTableMedicamentsFrance();
        return client;
    }

    static @PackagePrivate List<MedicamentAvecSubstances<Medicament<?, Substance<?>, ?>>> getMedicamentsAvecSubstances(
            List<MedicamentIdentifier> identifiers) {
        // pour l'instant tous viennent de la même source
        final var sourcesCompatibles = Set.of(MedicamentSource.BDPM);
        final var sourceInconnue = identifiers.stream()
                .filter(mi -> !sourcesCompatibles.contains(mi.getSource())).findAny();
        if (sourceInconnue.isPresent())
            throw new IllegalArgumentException("Source inconnue : " + sourceInconnue.get());
        return identifiers.parallelStream().map(MedicamentIdentifier::getId).map(Integer::parseInt)
                .map(MedicamentClientProxy::sneakyGet).filter(Optional::isPresent)
                .map(Optional::get)
                .<MedicamentAvecSubstances<Medicament<?, Substance<?>, ?>>>map(
                        m -> new MedicamentAvecSubstances<Medicament<?, Substance<?>, ?>>(
                                (Medicament<?, Substance<?>, ?>) m,
                                mapSubstances((Set<Substance<?>>) m.getSubstances(), terminoBDPM)))
                .collect(Collectors.toList());
    }

    static private <C extends ConceptSubstance> Set<C> mapSubstances(Set<Substance<?>> substances,
            TerminologySubstances<C> refTerminology) {
        return substances.stream().map(Substance::getCode).map(String::valueOf)
                // TODO: créer les concepts non trouvés
                .map(refTerminology::getConcept).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toSet());
    }

    @SneakyThrows(DBException.class)
    static private Optional<Medicament<?, ?, ?>> sneakyGet(int code) {
        return getClient().get(code).map(
                m -> (Medicament<Pays.France, ? extends Substance<Pays.France>, ? extends Presentation<Pays.France>>) m);
    }

}
