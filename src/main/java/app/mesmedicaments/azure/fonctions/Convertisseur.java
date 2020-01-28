package app.mesmedicaments.azure.fonctions;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.azure.tables.clients.ClientTableExpressionsCles;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.objets.substances.Substance;

/**
 * Convertit les produits dans le format JSON spécifié par la documentation de l'API.
 * 
 * Les méthodes toJSON des objets Medicament et Substance ne sont pas exactement
 * compatibles avec le format d'anciennes versions de l'application. Cette
 * classe rajoute des clés à leur objet JSON pour résoudre cela.
 */
public class Convertisseur {

    private Convertisseur() {}

    static public JSONObject toJSON(
        Interaction interaction, Medicament<?, ?, ?> medicament1, Medicament<?, ?, ?> medicament2
    ) {
        final Set<JSONObject> substances = interaction.getSubstances()
            .stream()
            .map(Convertisseur::toJSON)
            .collect(Collectors.toSet());
        return interaction.toJSON()
            .put("substances", substances)
            .put("medicaments", new JSONArray()
                .put(Convertisseur.toJSON(medicament1))
                .put(Convertisseur.toJSON(medicament2)));
    }

    static public JSONObject toJSON(Medicament<?, ?, ?> medicament) {
        final Set<JSONObject> substances = medicament.getSubstances()
            .stream()
            .map(Convertisseur::toJSON)
            .collect(Collectors.toSet());
        return medicament.toJSON()
            .put("substances", substances)
            .put("codeMedicament", medicament.getCode())
            .put("expressionsCles", getExpressionsCles(medicament));

    }

    static public JSONObject toJSON(Substance<?> substance) {
        return substance.toJSON()
            .put("codeSubstance", substance.getCode());
    }

    static private final ClientTableExpressionsCles clientExpr = new ClientTableExpressionsCles();

    static private Set<String> getExpressionsCles(Medicament<?, ?, ?> medicament) {
        try {
            return clientExpr
                .get(
                    medicament.getPays(), 
                    medicament.getCode(), 
                    medicament.getEffetsIndesirables());
        } catch (ExceptionTable | IOException e) {
            throw new RuntimeException(e);
        }
    }

    
}