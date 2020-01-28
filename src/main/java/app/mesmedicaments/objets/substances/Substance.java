package app.mesmedicaments.objets.substances;

import org.json.JSONObject;

import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.ObjetConcret;
import app.mesmedicaments.objets.Pays;

/**
 * @param <P> = Pays
 */
public
class Substance<P extends Pays>
extends ObjetConcret<P> {

    private final Noms noms;

    public Substance(P pays, int code, Noms noms) {
        super(pays, code);
        this.noms = new Noms(noms);
    }

    public Noms getNoms() {
        return new Noms(noms);
    }

	@Override
	public JSONObject toJSON() {
        return new JSONObject()
            .put("pays", pays.code)
            .put("code", code)
            .put("noms", noms);
	}
    
}