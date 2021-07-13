package app.mesmedicaments.api;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Langue;
import app.mesmedicaments.objets.medicaments.Medicament;
import lombok.SneakyThrows;

public class ConvertisseurJSONMedicament extends ConvertisseurJSON<Medicament<?, ?, ?>> {

    private final ClientTableExpressionsCles clientExpr;

    @SneakyThrows(DBExceptionTableAzure.class)
    public ConvertisseurJSONMedicament() {
        this.clientExpr = new ClientTableExpressionsCles();
    }

    @Override
    // TODO: à revoir
    @SneakyThrows({JSONException.class, DBException.class, IOException.class})
    public JSONObject toJSON(Medicament<?, ?, ?> medicament) {
        final var substances = medicament.getSubstances().stream()
                .map(s -> new Substance("bdpm", String.valueOf(s.getCode()),
                        s.getNoms().get(Langue.Francais)))
                .map(IdentifieurSubstance::new).collect(Collectors.toSet());
        return medicament.toJSON().put("substances", substances)
                .put("codeMedicament", medicament.getCode())
                .put("expressionsCles", getExpressionsCles(medicament));

    }

    private Set<String> getExpressionsCles(Medicament<?, ?, ?> medicament)
            throws DBException, IOException {
        return clientExpr.get(medicament.getPays(), medicament.getCode(),
                medicament.getEffetsIndesirables());
    }

}
