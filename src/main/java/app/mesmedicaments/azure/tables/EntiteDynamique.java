package app.mesmedicaments.azure.tables;

import com.microsoft.azure.storage.table.DynamicTableEntity;

public class EntiteDynamique extends DynamicTableEntity {

    private static String supprimerCaracteresInterdits(String s) {
        s = s.replaceAll("\\\\|/|#|\\?", " ");
        return s;
    }

    private static String formatKey(String key) {
        return supprimerCaracteresInterdits(key).replaceAll("  ", " ").trim();
    }

    /**
     * Nullary constructor. Ne pas utiliser.
     */
    public EntiteDynamique() {
        super();
    }

    public EntiteDynamique(String partitionKey, String rowKey) {
        super(partitionKey, rowKey);
    }

    @Override
    public void setPartitionKey(String key) {
        super.setPartitionKey(formatKey(key));
    }

    @Override
    public void setRowKey(String key) {
        super.setRowKey(formatKey(key));
    }

    @Override
    public String toString() {
        return "\tClasse = " + this.getClass().toGenericString() + "\tPartitionKey = " + this.getPartitionKey()
                + "\tRowKey = " + this.getRowKey();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EntiteDynamique) {
            EntiteDynamique other = (EntiteDynamique) o;
            return (other.getPartitionKey().equals(this.getPartitionKey()))
                    && (other.getRowKey().equals(this.getRowKey()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getPartitionKey().hashCode();
    }
}