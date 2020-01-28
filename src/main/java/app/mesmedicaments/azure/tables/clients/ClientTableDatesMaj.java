package app.mesmedicaments.azure.tables.clients;

import java.time.LocalDate;
import java.util.Optional;

import com.microsoft.azure.storage.table.EntityProperty;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.azure.tables.adapteurs.GenericAdapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;

public
class ClientTableDatesMaj
extends ClientTableAzure<ClientTableDatesMaj.DateMaj> {

    private static final String PARTITION = "dateMaj";
    private static final String ROWKEY_BDPM = "bdpm";
    private static final String ROWKEY_INTERACTIONS = "interactions";

    public ClientTableDatesMaj() {
        super(
            Environnement.TABLE_LEGAL,
            new GenericAdapteur<>(
                e -> new ClientTableDatesMaj.DateMaj(
                    e.getRowKey(), 
                    LocalDate.parse(e.getProperties().get("Date").getValueAsString())
                ), 
                d -> {
                    final EntiteDynamique entite = new EntiteDynamique(PARTITION, d.rowKey);
                    entite.getProperties().put("Date", new EntityProperty(d.date.toString()));
                    return entite;
                }
            )
        );
    }

    public void setDateMajBDPM(LocalDate date) throws ExceptionTable {
        super.set(new DateMaj(ROWKEY_BDPM, date), PARTITION, ROWKEY_BDPM);
    }

    public void setDateMajInteractions(LocalDate date) throws ExceptionTable {
        super.set(new DateMaj(ROWKEY_INTERACTIONS, date), PARTITION, ROWKEY_INTERACTIONS);
    }

    public Optional<LocalDate> getDateMajBDPM() throws ExceptionTable {
        return getObjet(PARTITION, ROWKEY_BDPM);
    }

    public Optional<LocalDate> getDateMajInteractions() throws ExceptionTable {
        return getObjet(PARTITION, ROWKEY_INTERACTIONS);
    }

    private Optional<LocalDate> getObjet(String partitionKey, String rowKey) throws ExceptionTable {
        final Optional<DateMaj> d = super.get(partitionKey, rowKey);
        if (!d.isPresent()) return Optional.empty();
        return Optional.of(d.get().date);
    }

    protected static class DateMaj {
        final LocalDate date;
        final String rowKey;

        private DateMaj(String rowKey, LocalDate date) {
            this.date = date;
            this.rowKey = rowKey;
        }
    }

}