package app.mesmedicaments.utils;

import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final
public class StreamCollectors {

    private StreamCollectors() {
    }

    static public <T, K, U>
    Collector<T, ?, HashMap<K, U>> toHashMap(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper
    ) {
        return Collectors.toMap(
            keyMapper, 
            valueMapper,
            (k1, k2) -> { throw new IllegalStateException(String.format("Duplicate key %s", k1)); },
            HashMap::new
        );
    }

}