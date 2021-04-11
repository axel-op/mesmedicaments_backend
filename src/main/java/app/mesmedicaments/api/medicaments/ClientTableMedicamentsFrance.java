package app.mesmedicaments.api.medicaments;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;

public
class ClientTableMedicamentsFrance
extends DBClientTableAzure<MedicamentFrance> {

    public ClientTableMedicamentsFrance() throws DBExceptionTableAzure {
        super(
                Environnement.TABLE_MEDICAMENTS,
            Environnement.AZUREWEBJOBSSTORAGE,
            new AdapteurMedicamentFrance()
        );
    }

    public Optional<MedicamentFrance> get(int code) throws DBException {
        return super.get(new String[] {Pays.France.instance.code, String.valueOf(code)});
    }

    public void set(Collection<MedicamentFrance> medicaments) throws DBException {
        super.set(new HashSet<>(medicaments));
    }
}