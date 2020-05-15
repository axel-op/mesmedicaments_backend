package app.mesmedicaments.azure.fonctions.privees;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import app.mesmedicaments.azure.recherche.ClientRecherche;
import app.mesmedicaments.azure.tables.clients.ClientTableClasseSubstances;
import app.mesmedicaments.azure.tables.clients.ClientTableDatesMaj;
import app.mesmedicaments.azure.tables.clients.ClientTableInteractions;
import app.mesmedicaments.azure.tables.clients.ClientTableMedicamentsFrance;
import app.mesmedicaments.azure.tables.clients.ClientTableSubstances;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.misesajour.MiseAJourClassesSubstances;
import app.mesmedicaments.misesajour.MiseAJourInteractions;
import app.mesmedicaments.misesajour.Updater;
import app.mesmedicaments.misesajour.Updater.MedicamentIncomplet;
import app.mesmedicaments.misesajour.updaters.UpdaterFrance;
import app.mesmedicaments.objets.ClasseSubstances;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.Pays.France;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.objets.presentations.PresentationFrance;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.unchecked.Unchecker;

public class MiseAJour {

    private static final Logger logger = Logger.getGlobal();

    static public void main(String[] args) throws Exception {
        logger.setLevel(Level.ALL);
        final ClientTableDatesMaj clientDates = new ClientTableDatesMaj();
        final Updater<France, Substance<France>, PresentationFrance, MedicamentFrance> updater = new UpdaterFrance(logger);
        final Set<Substance<France>> substances = updateAndGetSubstances(updater);
        final Set<MedicamentFrance> medicaments = updateAndGetMedicaments(updater);
        indexMedicaments(medicaments);
        clientDates.setDateMajBDPM(LocalDate.now());

        // ignoré car inutile pour le moment
        // updateClassesSubstances(nouvellesSubstances);

        // ignoré car il y a un bug
        /*
        final Set<Interaction> nouvellesInteractions = getNouvellesInteractions();
        logger.info(nouvellesInteractions.size() + " interactions créées");
        new ClientTableInteractions()
            .set(nouvellesInteractions
                .stream()
                .filter(i -> {
                    for (Substance<?> s : i.getSubstances()) {
                        if (nouvellesSubstances.contains(s))
                            return true;
                    }
                    return false;
                })
                .collect(Collectors.toSet())
            );
        clientDates.setDateMajInteractions(LocalDate.now());
        */
    }

    static private Set<Substance<France>> updateAndGetSubstances(
        Updater<France, Substance<France>, PresentationFrance, MedicamentFrance> updater
    ) throws ExceptionTable, IOException {
        logger.info("Mise à jour des substances...");
        final Set<Substance<Pays.France>> substances = updater.getNouvellesSubstances();
        logger.info(substances.size() + " substances récupérées vont être mises à jour...");
        new ClientTableSubstances().set(substances);
        return substances;
    }

    static private Set<MedicamentFrance> updateAndGetMedicaments(
        Updater<France, Substance<France>, PresentationFrance, MedicamentFrance> updater
    ) throws ExceptionTable, IOException {
        logger.info("Mise à jour des médicaments...");
        final Set<MedicamentIncomplet<France, MedicamentFrance>> medsIncomplets = updater.getNouveauxMedicaments();
        logger.info(medsIncomplets.size() + " médicaments récupérés");
        logger.info("Récupération de leurs effets indésirables...");
        final Set<MedicamentFrance> medicaments = medsIncomplets
                .parallelStream()
                .map(MedicamentIncomplet::getMedicamentComplet)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        logger.info("Ces médicaments vont maintenant être mis à jour...");
        new ClientTableMedicamentsFrance().set(medicaments);
        return medicaments;
    }

    static private void indexMedicaments(Set<MedicamentFrance> medicaments) throws IOException {
        logger.info("Ces médicaments vont maintenant être indexés...");
        new ClientRecherche(logger).index(medicaments);
    }

    static private void updateClassesSubstances(Set<? extends Substance<?>> nouvellesSubstances)
        throws ExceptionTable, IOException, URISyntaxException
    {
        final Set<ClasseSubstances> nouvellesClasses =
            new MiseAJourClassesSubstances(logger, nouvellesSubstances)
                .getNouvellesClasses();
        final ClientTableClasseSubstances client = new ClientTableClasseSubstances(logger);
        nouvellesClasses.parallelStream()
            .forEach(Unchecker.panic(c -> {
                logger.fine("Nouvelle classe : " + c.toString());
                client.update(c);
            }));
    }

    static private Set<Interaction> getNouvellesInteractions()
        throws ExceptionTable, IOException, URISyntaxException
    {
        final ClientTableSubstances client = new ClientTableSubstances();
        final Set<Substance<?>> toutesSubstances = client.getAll();
        return new MiseAJourInteractions(logger, toutesSubstances).getInteractions();
    }

}
