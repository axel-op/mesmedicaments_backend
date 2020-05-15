package app.mesmedicaments.azure.tables.clients;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONArray;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.ClientAnalyseTexte;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.azure.tables.adapteurs.GenericAdapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.utils.JSONArrays;

public
class ClientTableExpressionsCles
extends ClientTableAzure<ClientTableExpressionsCles.ObjetExpressionCles> {

    public ClientTableExpressionsCles() {
        super(
            Environnement.TABLE_EXPRESSIONSCLES,
            new GenericAdapteur<>(
                e -> new ObjetExpressionCles(
                    e.getPartitionKey(), 
                    e.getRowKey(), 
                    new JSONArray(e.getProperties()
                        .get("EffetsIndesirables")
                        .getValueAsString())
                ), 
                o -> {
                    final EntiteDynamique entite = new EntiteDynamique(o.partition, o.row);
                    entite.getProperties().put("EffetsIndesirables", new EntityProperty(o.expressions.toString()));
                    return entite;
                }
            )
        );
    }

    /**
     * Fera appel à {@link ClientAnalyseTexte} et met la table à jour automatiquement
     * si les effets n'ont pas encore été analysés pour ce médicament
     * 
     * @param medicament
     * @return
     * @throws ExceptionTable
     * @throws IOException
     */
    public Set<String> get(Pays pays, long code, String effets) throws ExceptionTable, IOException {
        if (effets == null || effets.matches(" *")) return new HashSet<>();
        final String partition = pays.code;
        final String row = String.valueOf(code);
        final Optional<ObjetExpressionCles> o = super.get(partition, row);
        if (!o.isPresent()) {
            final Set<String> nouvellesExprCles = ClientAnalyseTexte.getExpressionsCles(effets);
            super.put(
                ClientTableAzure.getKeysEntite(partition, row),
                new ObjetExpressionCles(partition, row, new JSONArray(nouvellesExprCles))
            );
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
}