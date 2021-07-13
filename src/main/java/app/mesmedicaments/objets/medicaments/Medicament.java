package app.mesmedicaments.objets.medicaments;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;
import app.mesmedicaments.api.IObjetIdentifiable;
import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.ObjetConcret;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.presentations.Presentation;
import app.mesmedicaments.objets.substances.Substance;

/**
 * Un Medicament peut avoir plusieurs {@link Presentation}s.
 * 
 * @param <P> est le {@link Pays} du médicament.
 * @param <S> est le type de ses objets {@link Substance}s.
 * @param <Pr> est le type des {@link Présentation}s.
 */
public abstract
class Medicament<P extends Pays, S extends Substance<P>, Pr extends Presentation<P>>
extends ObjetConcret<P>
implements IObjetIdentifiable
{

    private final Noms noms;
    private final Set<S> substances;
    private final String marque;
    private final String effetsIndesirables;
    private final Set<Pr> presentations;

    public Medicament(
        P pays, 
        int code,
        Noms noms, 
        Set<S> substances, 
        String marque,
        String effetsIndesirables,
        Set<Pr> presentations
    ) {
        super(pays, code);
        this.noms = new Noms(noms);
        this.substances = new HashSet<>(substances);
        this.marque = marque;
        this.effetsIndesirables = effetsIndesirables;
        this.presentations = new HashSet<>(presentations);
    }

    public String getMarque() {
        return marque;
    }

    public Noms getNoms() {
        return new Noms(noms);
    }

    public String getEffetsIndesirables() {
        return effetsIndesirables;
    }

    public Set<S> getSubstances() {
        return new HashSet<>(substances);
    }

    public Set<Pr> getPresentations() {
        return new HashSet<>(presentations);
    }

    @Override
    public JSONObject toJSON() {
        return new JSONObject()
            .put("pays", pays.code)
            .put("code", code)
            .put("noms", noms.toJSON())
            .put("substances", substances)
            .put("marque", marque)
            .put("effetsIndesirables", effetsIndesirables)
            .put("presentations", presentations);
    }

    @Override
    public String getId() {
        return String.valueOf(this.getCode());
    }
}