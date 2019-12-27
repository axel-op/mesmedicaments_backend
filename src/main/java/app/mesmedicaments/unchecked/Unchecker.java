package app.mesmedicaments.unchecked;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import app.mesmedicaments.Utils;

public class Unchecker {

    private Unchecker() {
    }

    public static <T, R, E extends Exception> Function<T, R> wrap(Logger logger,
            FunctionWithException<T, R, E> function) {
        return a -> {
            try {
                return function.apply(a);
            } catch (Exception e) {
                Utils.logErreur(e, logger);
                throw new RuntimeException();
            }
        };
    }

    public static <T, E extends Exception> Consumer<T> wrap(Logger logger, ConsumerWithException<T, E> consumer) {
        return a -> {
            try {
                consumer.accept(a);
            } catch (Exception e) {
                Utils.logErreur(e, logger);
                throw new RuntimeException();
            }
        };
    }

    public static <T, E extends Exception> Supplier<T> wrap(Logger logger, SupplierWithException<T, E> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                Utils.logErreur(e, logger);
                throw new RuntimeException();
            }
        };
    }
}