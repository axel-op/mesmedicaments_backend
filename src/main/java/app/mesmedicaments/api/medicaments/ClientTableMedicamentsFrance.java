package app.mesmedicaments.api.medicaments;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.database.azuretables.IDDocumentTableAzure;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.medicaments.MedicamentFrance;
import lombok.NonNull;

public class ClientTableMedicamentsFrance extends DBClientTableAzure<MedicamentFrance> {

    public ClientTableMedicamentsFrance() throws DBExceptionTableAzure {
        super(Environnement.TABLE_MEDICAMENTS, Environnement.AZUREWEBJOBSSTORAGE,
                new AdapteurMedicamentFrance());
    }

    public List<MedicamentFrance> get(Collection<String> codes) throws DBException {
        return super.get(codes.stream().map(this::mapToDatabaseID).collect(Collectors.toList()));
    }

    public Optional<MedicamentFrance> get(@NonNull String code) throws DBException {
        return super.get(mapToDatabaseID(code));
    }

    public void set(Collection<MedicamentFrance> medicaments) throws DBException {
        super.set(new HashSet<>(medicaments));
    }

    private IDDocumentTableAzure mapToDatabaseID(@NonNull String code) {
        return new IDDocumentTableAzure(Pays.France.instance.code, code);
    }
}
