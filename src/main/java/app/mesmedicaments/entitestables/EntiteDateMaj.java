package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.LocalDate;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

public class EntiteDateMaj extends AbstractEntite {

    private static final String TABLE = "legal";
    private static final String CLE_PARTITION = "dateMaj";
    private static final String ROWKEY_BDPM = "bdpm";
    private static final String ROWKEY_INTERACTIONS = "interactions";

    public static void definirDateMajInteractions() throws StorageException, URISyntaxException, InvalidKeyException {
        definirDateMaj(ROWKEY_INTERACTIONS, LocalDate.now());
    }

    public static void definirDateMajFrance() throws StorageException, URISyntaxException, InvalidKeyException {
        definirDateMaj(ROWKEY_BDPM, LocalDate.now());
    }

    private static void definirDateMaj(String rowKey, LocalDate date)
            throws StorageException, URISyntaxException, InvalidKeyException {
        EntiteDateMaj entite = new EntiteDateMaj(rowKey);
        entite.setDate(date.toString());
        entite.mettreAJourEntite();
    }

    public static Optional<LocalDate> obtenirDateMajInteractions()
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirDateMaj(ROWKEY_INTERACTIONS);
    }

    public static Optional<LocalDate> obtenirDateMajBDPM()
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirDateMaj(ROWKEY_BDPM);
    }

    private static Optional<LocalDate> obtenirDateMaj(String rowKey)
            throws StorageException, URISyntaxException, InvalidKeyException {
        TableOperation operation = TableOperation.retrieve(CLE_PARTITION, rowKey, EntiteDateMaj.class);
        EntiteDateMaj entite = obtenirCloudTable(TABLE).execute(operation).getResultAsType();
        if (entite == null)
            return Optional.empty();
        return Optional.of(LocalDate.parse(entite.getDate()));
    }

    String date;

    /**
     * NE PAS UTILISER
     */
    public EntiteDateMaj() {
        super(TABLE);
    }

    private EntiteDateMaj(String rowKey) {
        super(TABLE, CLE_PARTITION, rowKey);
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public boolean conditionsARemplir() {
        return true;
    }
}