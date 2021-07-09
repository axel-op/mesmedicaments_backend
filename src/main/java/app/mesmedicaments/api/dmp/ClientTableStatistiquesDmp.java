package app.mesmedicaments.api.dmp;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.database.azuretables.IDDocumentTableAzure;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

public class ClientTableStatistiquesDmp
        extends DBClientTableAzure<ClientTableStatistiquesDmp.SearchCount> {

    static private final String table = Environnement.TABLE_STATS_DMP;
    static private final String partition = "count";

    static private String getSearchRowKey(String search) {
        return String.valueOf(search.hashCode());
    }

    public ClientTableStatistiquesDmp() throws DBExceptionTableAzure {
        super(table, Environnement.AZUREWEBJOBSSTORAGE, new Adaptor());
    }

    public void incrementSearchCounts(Set<String> searches) throws DBException {
        set(searches.parallelStream().map(this::getIncrementedSearchCount)
                .collect(Collectors.toSet()));
    }

    @SneakyThrows(DBException.class)
    private SearchCount getIncrementedSearchCount(String search) {
        final var rowKey = getSearchRowKey(search);
        final var sc = get(new IDDocumentTableAzure(partition, rowKey))
                .orElseGet(() -> new SearchCount(search, 0));
        sc.setCount(sc.getCount() + 1);
        return sc;
    }

    @Data
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static protected class SearchCount {
        private final String search;
        private int count;
    }

    static protected class Adaptor extends DBAdaptor<DBDocumentTableAzure, SearchCount> {

        @Override
        public SearchCount fromDocumentToObject(DBDocumentTableAzure doc) {
            return new SearchCount((String) doc.getProperty("search"),
                    (Integer) doc.getProperty("count"));
        }

        @Override
        public DBDocumentTableAzure fromObjectToDocument(SearchCount sc) {
            final var e = new DBDocumentTableAzure(partition, getSearchRowKey(sc.getSearch()));
            e.setProperties(Map.of("search", sc.getSearch(), "count", sc.getCount()));
            return e;
        }
    }

}
