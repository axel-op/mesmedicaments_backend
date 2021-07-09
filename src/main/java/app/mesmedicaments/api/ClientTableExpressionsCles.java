package app.mesmedicaments.api;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.ClientAnalyseTexte;
import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.database.azuretables.IDDocumentTableAzure;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.utils.JSONArrays;
import lombok.experimental.PackagePrivate;

@PackagePrivate
class ClientTableExpressionsCles
        extends DBClientTableAzure<ClientTableExpressionsCles.ObjetExpressionCles> {

    public ClientTableExpressionsCles() throws DBExceptionTableAzure {
        super(Environnement.TABLE_EXPRESSIONSCLES, Environnement.AZUREWEBJOBSSTORAGE,
                new Adaptor());
    }

    /**
     * Fera appel à {@link ClientAnalyseTexte} et met la table à jour automatiquement si les effets
     * n'ont pas encore été analysés pour ce médicament
     * 
     * @param medicament
     * @return
     * @throws ExceptionTable
     * @throws IOException
     */
    public Set<String> get(Pays pays, long code, String effets) throws DBException, IOException {
        if (effets == null || effets.matches(" *"))
            return new HashSet<>();
        final String partition = pays.code;
        final String row = String.valueOf(code);
        final Optional<ObjetExpressionCles> o = super.get(new IDDocumentTableAzure(partition, row));
        if (!o.isPresent()) {
            final Set<String> nouvellesExprCles = ClientAnalyseTexte.getExpressionsCles(effets);
            super.set(new ObjetExpressionCles(partition, row, new JSONArray(nouvellesExprCles)));
            return nouvellesExprCles;
        }
        return new HashSet<>(o.get().expressions);
    }

    static protected class ObjetExpressionCles {
        final String partition;
        final String row;
        final Set<String> expressions;

        private ObjetExpressionCles(String partition, String row, JSONArray expressions) {
            this.partition = partition;
            this.row = row;
            this.expressions = JSONArrays.toSetString(expressions);
        }
    }

    static private class Adaptor extends DBAdaptor<DBDocumentTableAzure, ObjetExpressionCles> {

        @Override
        public ObjetExpressionCles fromDocumentToObject(DBDocumentTableAzure doc) {
            return new ObjetExpressionCles(doc.getPartitionKey(), doc.getRowKey(),
                    new JSONArray((String) doc.getProperty("EffetsIndesirables")));
        }

        @Override
        public DBDocumentTableAzure fromObjectToDocument(ObjetExpressionCles o) {
            final var entite = new DBDocumentTableAzure(o.partition, o.row);
            entite.setProperties(Map.of("EffetsIndesirables", o.expressions.toString()));
            return entite;
        }
    }
}
