package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.presentations.Presentation;
import app.mesmedicaments.objets.substances.Substance;

public interface Updater<
    P extends Pays, 
    S extends Substance<P>, 
    Pr extends Presentation<P>, 
    M extends Medicament<P, ? extends Substance<P>, Pr>
> {

    public P getPays();

    public Set<Substance<P>> getNouvellesSubstances() throws IOException;

    public Set<MedicamentIncomplet<P, M>> getNouveauxMedicaments() throws IOException;

    /**
     * Classe contenant les médicaments incomplets, sans leurs effets indésirables.
     * Le médicament final ne peut être obtenu qu'une fois les effets fournis.
     * 
     * @param <P> Pays du médicament
     */
    static public class MedicamentIncomplet<
        P extends Pays, 
        M extends Medicament<P, ? extends Substance<P>, ? extends Presentation<P>>
    > {
        protected final Supplier<Optional<M>> getMedicament;

        public MedicamentIncomplet(Supplier<Optional<M>> getMedicament) {
            this.getMedicament = getMedicament;
        }

        /**
         * Peut prendre du temps.
         * @return
         */
        public Optional<M> getMedicamentComplet() {
            return getMedicament.get();
        }
    }

}
