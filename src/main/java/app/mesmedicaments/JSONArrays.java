package app.mesmedicaments;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

/** Classe contenant des méthodes pour effectuer des opérations utiles sur les {@link JSONArray} */
public class JSONArrays {

    private JSONArrays() {}

    /**
     * Ajoute tous les éléments du {@link JSONArray} comme des nombres de type {@link Long} à la
     * {@link Collection}
     *
     * @param collection La {@link Collection} à laquelle ajouter les {@link Long}
     * @param jsonArray
     * @throws JSONException Si l'un des éléments du {@link JSONArray} ne peut être converti en
     *     {@link Long}
     */
    public static void addAsLongs(Collection<Long> collection, JSONArray jsonArray)
            throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) collection.add(jsonArray.getLong(i));
    }

    /**
     * Ajoute tous les éléments du {@link JSONArray} comme des objets de type {@link String} à la
     * {@link Collection}
     *
     * @param collection La {@link Collection} à laquelle ajouter les {@link String}
     * @param jsonArray
     * @throws JSONException Si l'un des éléments du {@link JSONArray} ne peut être converti en
     *     {@link String}
     */
    public static void addAsStrings(Collection<String> collection, JSONArray jsonArray)
            throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) collection.add(jsonArray.getString(i));
    }

    /**
     * Convertit tous les éléments du {@link JSONArray} en objet {@link Long} et les place dans un
     * {@link Set} (donc supprime les doublons)
     *
     * @param jsonArray
     * @return Set<Long>
     * @throws JSONException Si un élément du {@link JSONArray} ne peut être converti en Long
     */
    public static Set<Long> toSetLong(JSONArray jsonArray) throws JSONException {
        Set<Long> set = new HashSet<>();
        addAsLongs(set, jsonArray);
        return set;
    }

    /**
     * Convertit tous les éléments du {@link JSONArray} en objet {@link String} et les place dans un
     * {@link Set} (donc supprime les doublons)
     *
     * @param jsonArray
     * @return Set<String>
     * @throws JSONException Si un élément du {@link JSONArray} ne peut être converti en {@link
     *     String}
     */
    public static Set<String> toSetString(JSONArray jsonArray) throws JSONException {
        Set<String> set = new HashSet<>();
        addAsStrings(set, jsonArray);
        return set;
    }
}
