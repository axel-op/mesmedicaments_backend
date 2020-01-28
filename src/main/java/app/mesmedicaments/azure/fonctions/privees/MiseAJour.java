package app.mesmedicaments.azure.fonctions.privees;

import java.io.IOException;
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
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import app.mesmedicaments.objets.presentations.PresentationFrance;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.unchecked.Unchecker;

public class MiseAJour {

    private static final Logger logger = Logger.getGlobal();

    static public void main(String[] args) throws Exception {
        logger.setLevel(Level.ALL);
        final ClientTableDatesMaj clientDates = new ClientTableDatesMaj();
        final Set<Substance<Pays.France>> nouvellesSubstances = updateBDPM();
        clientDates.setDateMajBDPM(LocalDate.now());
        // ignoré car inutile pour le moment
        // updateClassesSubstances(nouvellesSubstances);
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
    }

    /**
     * Renvoie les nouvelles substances car il y en aura besoin pour la suite.
     * @return
     * @throws ExceptionTable
     * @throws IOException
     */
    static private Set<Substance<Pays.France>> updateBDPM() throws ExceptionTable, IOException {
        final Updater<Pays.France, Substance<Pays.France>, PresentationFrance, MedicamentFrance> updater = new UpdaterFrance(logger);
        logger.info("Mise à jour des substances...");
        final Set<Substance<Pays.France>> nouvellesSubstances = updater.getNouvellesSubstances();
        logger.info(
                String.valueOf(nouvellesSubstances.size())
                        + " substances récupérées vont être mises à jour...");
        new ClientTableSubstances().set(nouvellesSubstances);
        logger.info("Mise à jour des médicaments...");
        final Set<MedicamentIncomplet<Pays.France, MedicamentFrance>> medicamentsIncomplets = updater.getNouveauxMedicaments();
        logger.info(String.valueOf(medicamentsIncomplets.size()) + " médicaments récupérés");
        logger.info("Récupération de leurs effets indésirables...");
        final Set<MedicamentFrance> nouveauxMedicaments = medicamentsIncomplets
                .parallelStream()
                .map(MedicamentIncomplet::getMedicamentComplet)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        logger.info("Ces médicaments vont maintenant être mis à jour...");
        new ClientTableMedicamentsFrance().set(nouveauxMedicaments);
        logger.info("Ces médicaments vont maintenant être indexés...");
        new ClientRecherche(logger).index(nouveauxMedicaments);
        return nouvellesSubstances;
    }

    static private void updateClassesSubstances(Set<? extends Substance<?>> nouvellesSubstances)
        throws ExceptionTable, IOException 
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
        throws ExceptionTable, IOException 
    {
        final ClientTableSubstances client = new ClientTableSubstances();
        final Set<Substance<?>> toutesSubstances = client.getAll();
        return new MiseAJourInteractions(logger, toutesSubstances).getInteractions();
    }

}
