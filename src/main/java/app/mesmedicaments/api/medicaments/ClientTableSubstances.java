package app.mesmedicaments.api.medicaments;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;

public
class ClientTableSubstances
extends DBClientTableAzure<Substance<?>> {

	public ClientTableSubstances() throws DBExceptionTableAzure {
		super(
                Environnement.TABLE_SUBSTANCES,
            Environnement.AZUREWEBJOBSSTORAGE,
            new AdapteurSubstance()
        );
    }
    
    public Optional<Substance<?>> get(Pays pays, long code) throws DBException {
        return super.get(new String[] {pays.code, String.valueOf(code)});
    }

    /**
     * Retourne TOUTES les substances de la base.
     * Pour l'instant, ne retourne que celles de la partition France.
     * @return Set<Substance>>
     * @throws ExceptionTable
     */
    public Set<Substance<?>> getAll() {
        return super.getEntirePartition(Pays.France.instance.code);
    }

    public void set(Collection<? extends Substance<?>> substances) throws DBException  {
        super.set(new HashSet<>(substances));
    }

}