package app.mesmedicaments.utils.unchecked;

@FunctionalInterface
public interface ISupplierWithException<T, E extends Exception> {
    T get() throws E;
}
