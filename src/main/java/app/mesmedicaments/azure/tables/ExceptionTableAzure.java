package app.mesmedicaments.azure.tables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

import app.mesmedicaments.basededonnees.ExceptionTable;

class ExceptionTableAzure extends ExceptionTable {

    private static final long serialVersionUID = -3024439532862840810L;

    public ExceptionTableAzure(Throwable cause) {
        super(cause);
    }

    public static <T> T tryCatch(ISupplierWithTableException<T> supplier) throws ExceptionTableAzure {
        try {
            return supplier.get();
        }
        catch (StorageException | URISyntaxException | InvalidKeyException e) {
            throw new ExceptionTableAzure(e);
        }
    }

}