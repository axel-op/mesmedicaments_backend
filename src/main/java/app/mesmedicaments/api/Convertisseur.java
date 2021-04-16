package app.mesmedicaments.api;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.substances.Substance;

/**
 * Convertit les produits dans le format JSON spécifié par la documentation de l'API.
 * 
 * Les méthodes toJSON des objets Medicament et Substance ne sont pas exactement compatibles avec le
 * format d'anciennes versions de l'application. Cette classe rajoute des clés à leur objet JSON
 * pour résoudre cela.
 */
public class Convertisseur {

    private final ClientTableExpressionsCles clientExpr;

    public Convertisseur() throws DBExceptionTableAzure {
        this.clientExpr = new ClientTableExpressionsCles();
    }

    public JSONObject toJSON(Medicament<?, ?, ?> medicament)
            throws JSONException, DBException, IOException {
        final Set<JSONObject> substances =
                medicament.getSubstances().stream().map(this::toJSON).collect(Collectors.toSet());
        return medicament.toJSON().put("substances", substances)
                .put("codeMedicament", medicament.getCode())
                .put("expressionsCles", getExpressionsCles(medicament));

    }

    public JSONObject toJSON(Substance<?> substance) {
        return substance.toJSON().put("codeSubstance", substance.getCode());
    }

    private Set<String> getExpressionsCles(Medicament<?, ?, ?> medicament)
            throws DBException, IOException {
        return clientExpr.get(medicament.getPays(), medicament.getCode(),
                medicament.getEffetsIndesirables());
    }

}
