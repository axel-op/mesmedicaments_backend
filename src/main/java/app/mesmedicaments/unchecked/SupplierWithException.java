package app.mesmedicaments.unchecked;

@FunctionalInterface
public interface SupplierWithException<T, E extends Exception> {
    T get() throws E;
}