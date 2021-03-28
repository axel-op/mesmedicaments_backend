package app.mesmedicaments.azure.tables.clients;

import java.util.Set;
import java.util.stream.Collectors;
import com.microsoft.azure.storage.table.EntityProperty;
import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.azure.tables.adapteurs.GenericAdapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

public class ClientTableStatistiquesDmp
        extends ClientTableAzure<ClientTableStatistiquesDmp.SearchCount> {

    static private final String table = Environnement.TABLE_STATS_DMP;
    static private final String partition = "count";

    static private String getSearchRowKey(String search) {
        return String.valueOf(search.hashCode());
    }

    static private GenericAdapteur<SearchCount, EntiteDynamique> getAdapteur() {
        return new GenericAdapteur<>(
                e -> new SearchCount(e.getProperties().get("search").getValueAsString(),
                        e.getProperties().get("count").getValueAsInteger()),
                sc -> {
                    final var e = new EntiteDynamique(partition, getSearchRowKey(sc.getSearch()));
                    e.getProperties().put("search", new EntityProperty(sc.getSearch()));
                    e.getProperties().put("count", new EntityProperty(sc.getCount()));
                    return e;
                });
    }

    public ClientTableStatistiquesDmp() {
        super(table, getAdapteur());
    }

    public void incrementSearchCounts(Set<String> searches) throws ExceptionTable {
        set(searches.parallelStream()
                .collect(Collectors.toConcurrentMap(this::getIncrementedSearchCount,
                        s -> new String[] {partition, getSearchRowKey(s)}, (v1, v2) -> v1)));
    }

    @SneakyThrows(ExceptionTable.class)
    private SearchCount getIncrementedSearchCount(String search) {
        final var rowKey = getSearchRowKey(search);
        final var sc = get(partition, rowKey).orElseGet(() -> new SearchCount(search, 0));
        sc.setCount(sc.getCount() + 1);
        return sc;
    }

    @Data
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static protected class SearchCount {
        private final String search;
        private int count;
    }

}
