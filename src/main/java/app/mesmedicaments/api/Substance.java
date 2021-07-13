package app.mesmedicaments.api;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class Substance implements IObjetIdentifiable {

    final private String source;
    final private String id;
    final private Set<String> names;

}
