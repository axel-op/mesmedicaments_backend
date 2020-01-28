package app.mesmedicaments.utils.unchecked;

@FunctionalInterface
public interface IFunctionWithException<T, R, E extends Exception> {
    R apply(T t) throws E;
}
