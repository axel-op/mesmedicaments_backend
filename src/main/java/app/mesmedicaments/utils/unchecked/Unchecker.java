package app.mesmedicaments.utils.unchecked;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Unchecker {

    private Unchecker() {}

    public static <T, R, E extends Exception> Function<T, R> panic(
        IFunctionWithException<T, R, E> function
    ) {
        return a -> {
            try {
                return function.apply(a);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T, E extends Exception> Consumer<T> panic(
        IConsumerWithException<T, E> consumer
    ) {
        return a -> {
            try {
                consumer.accept(a);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <T, E extends Exception> Supplier<T> panic(
        ISupplierWithException<T, E> supplier
    ) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
