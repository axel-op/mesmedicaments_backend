package app.mesmedicaments.azure.tables.clients;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.adapteurs.AdapteurSubstance;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;

public
class ClientTableSubstances
extends ClientTableAzure<Substance<?>> {

	public ClientTableSubstances() {
		super(
            Environnement.TABLE_SUBSTANCES,
            new AdapteurSubstance()
        );
    }
    
    public Optional<Substance<?>> get(Pays pays, long code) throws ExceptionTable {
        return super.get(pays.code, String.valueOf(code));
    }

    /**
     * Retourne TOUTES les substances de la base.
     * Pour l'instant, ne retourne que celles de la partition France.
     * @return Set<Substance>>
     * @throws ExceptionTable
     */
    public Set<Substance<?>> getAll() throws ExceptionTable {
        return super.getAll(Pays.France.instance.code);
    }

    public void set(Collection<? extends Substance<?>> substances) throws ExceptionTable {
        super.put(substances
            .stream()
            .collect(Collectors.toMap(
                s -> ClientTableAzure.getKeysEntite(s.getPays().code, String.valueOf(s.getCode())),
                s -> s
            )));
    }

}