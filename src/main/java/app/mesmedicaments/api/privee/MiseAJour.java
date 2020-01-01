package app.mesmedicaments.api.privee;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import app.mesmedicaments.entitestables.EntiteDateMaj;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.misesajour.Updater;
import app.mesmedicaments.misesajour.updaters.UpdaterFrance;
import app.mesmedicaments.recherche.Indexeur;

public class MiseAJour {

    private static final Logger logger = Logger.getGlobal();

    public static void main(String[] args) throws Exception {
        final Updater<EntiteMedicamentFrance> updater = new UpdaterFrance(logger);
        logger.info("Mise à jour des substances...");
        final Set<EntiteSubstance> nouvellesSubstances = updater.getNouvellesSubstances();
        logger.info(String.valueOf(nouvellesSubstances.size()) + " substances récupérées vont être mises à jour...");
        EntiteSubstance.mettreAJourEntitesBatch(nouvellesSubstances);
        logger.info("Mise à jour des médicaments...");
        final Set<EntiteMedicamentFrance> nouveauxMedicaments = updater.getNouveauxMedicaments();
        logger.info(String.valueOf(nouveauxMedicaments.size()) + " médicaments récupérés");
        logger.info("Récupération de leurs effets indésirables...");
        nouveauxMedicaments.parallelStream().forEach((EntiteMedicamentFrance m) -> {
            try {
                m.setEffetsIndesirables(updater.getEffetsIndesirables(m));
            } catch (IOException e) {
                logger.info(e.toString());
            }
        });
        logger.info("Ces médicaments vont maintenant être mis à jour...");
        EntiteMedicamentFrance.mettreAJourEntitesBatch(
                nouveauxMedicaments.stream().filter(m -> m.conditionsARemplir()).collect(Collectors.toSet()));
        logger.info("Ces médicaments vont maintenant être indexés...");
        new Indexeur(nouveauxMedicaments, logger).indexer();
        EntiteDateMaj.definirDateMajFrance();
    }

    /*
     * @FunctionName("miseAJourAutomatique") public static void
     * miseAJourAutomatique(
     * 
     * @TimerTrigger(name = "miseAJourAutomatiqueTrigger", schedule =
     * "0 0 0 15 * *") final String timerInfo, final ExecutionContext context ) {
     * 
     * }
     */
}