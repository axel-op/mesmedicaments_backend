package app.mesmedicaments.objets;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.objets.substances.Substance;

public
class Interaction implements IJSONSerializable {

    public final int risque;
    public final String descriptif;
    public final String conduite;
    private final Set<Substance<? extends Pays>> substances;

    public Interaction(
        int risque, 
        String descriptif, 
        String conduite, 
        Collection<Substance<? extends Pays>> substances
    ) {
        this.risque = risque;
        this.descriptif = descriptif;
        this.conduite = conduite;
        this.substances = new HashSet<>(substances);
    }

    public Set<Substance<? extends Pays>> getSubstances() {
        return new HashSet<>(substances);
    }

	@Override
	public JSONObject toJSON() {
        return new JSONObject()
            .put("risque", risque)
            .put("descriptif", descriptif)
            .put("conduite", conduite)
            .put("substances", substances);
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Interaction) {
            final Interaction oi = (Interaction) other;
            return substances.equals(oi.substances);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return substances.hashCode();
    }
    
}