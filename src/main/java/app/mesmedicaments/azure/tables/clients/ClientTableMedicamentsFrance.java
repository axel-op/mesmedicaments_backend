package app.mesmedicaments.azure.tables.clients;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.adapteurs.AdapteurMedicamentFrance;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;

public
class ClientTableMedicamentsFrance
extends ClientTableAzure<MedicamentFrance> {

    public ClientTableMedicamentsFrance() {
        super(
            Environnement.TABLE_MEDICAMENTS,
            new AdapteurMedicamentFrance()
        );
    }

    public Optional<MedicamentFrance> get(int code) throws ExceptionTable {
        return super.get(Pays.France.instance.code, String.valueOf(code));
    }

    public void set(Collection<MedicamentFrance> medicaments) throws ExceptionTable {
        super.set(medicaments.stream()
            .collect(Collectors.toMap(
                m -> m, 
                m -> new String[]{m.getPays().code, String.valueOf(m.getCode())}
            ))
        );
    }
}