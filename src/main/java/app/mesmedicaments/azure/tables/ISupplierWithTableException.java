package app.mesmedicaments.azure.tables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import com.microsoft.azure.storage.StorageException;

@FunctionalInterface
interface ISupplierWithTableException<T> {
    T get() throws StorageException, URISyntaxException, InvalidKeyException, ExceptionTableAzure;
}