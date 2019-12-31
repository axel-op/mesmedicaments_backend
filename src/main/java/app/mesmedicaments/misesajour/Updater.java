package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.util.Set;

import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;

public interface Updater<T extends AbstractEntiteMedicament<?>> {

    public Pays getPays();

    public Set<EntiteSubstance> getNouvellesSubstances() throws IOException;

    public Set<T> getNouveauxMedicaments() throws IOException;

    public String getEffetsIndesirables(T medicament) throws IOException;
}