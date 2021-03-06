package app.mesmedicaments.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.Streams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import lombok.experimental.UtilityClass;

@UtilityClass
/** Classe contenant des méthodes pour effectuer des opérations utiles sur les {@link JSONArray} */
public class JSONArrays {

    /**
     * Ajoute tous les éléments dans array2 à la fin d'array1.
     *
     * @param array1
     * @param array2
     */
    public static void append(JSONArray array1, JSONArray array2) {
        int nextIndex = array1.length();
        for (int i = 0; i < array2.length(); i++) {
            array1.put(nextIndex, array2.get(i));
            nextIndex += 1;
        }
    }

    /**
     * Ajoute tous les éléments du {@link JSONArray} comme des nombres de type {@link Integer} à la
     * {@link Collection}
     *
     * @param collection La {@link Collection} à laquelle ajouter les {@link Integer}
     * @param jsonArray
     * @throws JSONException Si l'un des éléments du {@link JSONArray} ne peut être converti en
     *     {@link Integer}
     */
    public static void addAsInts(Collection<Integer> collection, JSONArray jsonArray)
            throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) collection.add(jsonArray.getInt(i));
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
     * Convertit tous les éléments du {@link JSONArray} en objet {@link Integer} et les place dans un
     * {@link Set} (donc supprime les doublons)
     *
     * @param jsonArray
     * @return Set<Integer>
     * @throws JSONException Si un élément du {@link JSONArray} ne peut être converti en Integer
     */
    public static Set<Integer> toSetInt(JSONArray jsonArray) throws JSONException {
        Set<Integer> set = new HashSet<>();
        addAsInts(set, jsonArray);
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

    /**
     * Convertit tous les éléments du {@link JSONArray} en objet {@link JSONObject} et
     * les place dans un {@link Set}
     *
     * @param jsonArray
     * @return Set<JSONObject>
     * @throws JSONException Si un élément du {@link JSONArray} ne peut être
     *                       converti en {@link JSONObject}
     */
    public static Set<JSONObject> toSetJSONObject(JSONArray jsonArray) throws JSONException {
        return toStreamJSONObject(jsonArray).collect(Collectors.toSet());
    }
    
    public static Stream<Object> toStream(JSONArray jsonArray) {
        return Streams.stream(jsonArray.iterator());
    }

    public static Stream<JSONObject> toStreamJSONObject(JSONArray jsonArray) {
        return toStream(jsonArray).map(o -> (JSONObject) o);
    }
}
