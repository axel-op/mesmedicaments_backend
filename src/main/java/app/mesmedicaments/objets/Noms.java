package app.mesmedicaments.objets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.utils.JSONArrays;

public class Noms implements IJSONSerializable {

    private final Map<Langue, Set<String>> nomsParLangue;

    public Noms(Map<Langue, Set<String>> nomsParLangue) {
        this.nomsParLangue = new HashMap<>(nomsParLangue);
    }

    public Noms(Noms noms) {
        this(noms.nomsParLangue);
    }

    public Noms(JSONObject json) {
        nomsParLangue = new HashMap<>();
        for (String key : json.keySet()) {
            nomsParLangue.put(
                Langue.getLangue(key), 
                JSONArrays.toSetString(json.getJSONArray(key))
            );
        }
    }

    /**
     * Renvoie tous les noms appartenant à une même langue.
     * 
     * @param langue
     * @return {@link Set} des noms de cette langue, vide s'il n'y en a pas.
     */
    public Set<String> get(Langue langue) {
        return new HashSet<>(nomsParLangue.computeIfAbsent(langue, l -> new HashSet<>()));
    }

    /**
     * Ajoute plusieurs noms d'une même langue.
     * 
     * @param langue
     * @param noms
     */
    public void ajouter(Langue langue, Set<String> noms) {
        nomsParLangue.computeIfAbsent(langue, l -> new HashSet<>()).addAll(noms);
    }

    /**
     * Ajoute un nom d'une même langue.
     */
    public void ajouter(Langue langue, String nom) {
        nomsParLangue.computeIfAbsent(langue, l -> new HashSet<>()).add(nom);
    }

    /**
     * Renvoie un {@link JSONObject} sérialisé en String.
     */
    @Override
    public String toString() {
        return new JSONObject(nomsParLangue).toString();
    }

	@Override
	public JSONObject toJSON() {
		return new JSONObject(nomsParLangue);
	}
}