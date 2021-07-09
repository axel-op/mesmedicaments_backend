package app.mesmedicaments.api.legal;

import java.time.LocalDate;
import java.util.Optional;

import com.microsoft.azure.storage.table.EntityProperty;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.database.azuretables.IDDocumentTableAzure;
import lombok.Data;

public class ClientTableDatesMaj extends DBClientTableAzure<ClientTableDatesMaj.DateMaj> {

    private static final String PARTITION = "dateMaj";
    private static final String ROWKEY_BDPM = "bdpm";
    private static final String ROWKEY_INTERACTIONS = "interactions";

    public ClientTableDatesMaj() throws DBExceptionTableAzure {
        super(Environnement.TABLE_LEGAL, Environnement.AZUREWEBJOBSSTORAGE, new Adaptor());
    }

    public void setDateMajBDPM(LocalDate date) throws DBException {
        super.set(new DateMaj(ROWKEY_BDPM, date));
    }

    public void setDateMajInteractions(LocalDate date) throws DBException {
        super.set(new DateMaj(ROWKEY_INTERACTIONS, date));
    }

    public Optional<LocalDate> getDateMajBDPM() throws DBException {
        return getObjet(PARTITION, ROWKEY_BDPM);
    }

    public Optional<LocalDate> getDateMajInteractions() throws DBException {
        return getObjet(PARTITION, ROWKEY_INTERACTIONS);
    }

    private Optional<LocalDate> getObjet(String partitionKey, String rowKey) throws DBException {
        return super.get(new IDDocumentTableAzure(partitionKey, rowKey)).map(DateMaj::getDate);
    }

    @Data
    protected static class DateMaj {
        final String rowKey;
        final LocalDate date;
    }

    static private class Adaptor extends DBAdaptor<DBDocumentTableAzure, DateMaj> {

        @Override
        public DateMaj fromDocumentToObject(DBDocumentTableAzure doc) {
            return new ClientTableDatesMaj.DateMaj(doc.getRowKey(),
                    LocalDate.parse(doc.getProperties().get("Date").getValueAsString()));
        }

        @Override
        public DBDocumentTableAzure fromObjectToDocument(DateMaj d) {
            final var entite = new DBDocumentTableAzure(PARTITION, d.rowKey);
            entite.getProperties().put("Date", new EntityProperty(d.date.toString()));
            return entite;
        }

    }

}
