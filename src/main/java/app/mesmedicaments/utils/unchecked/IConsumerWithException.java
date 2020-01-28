package app.mesmedicaments.utils.unchecked;

@FunctionalInterface
public interface IConsumerWithException<T, E extends Exception> {
    void accept(T t) throws E;
}
