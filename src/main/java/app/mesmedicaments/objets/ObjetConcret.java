package app.mesmedicaments.objets;

import app.mesmedicaments.IJSONSerializable;

public abstract
class ObjetConcret<P extends Pays>
implements IJSONSerializable {

    protected final int code;
    protected final P pays;

    protected ObjetConcret(P pays, int code) {
        this.pays = pays;
        this.code = code;
    }

    public final long getCode() {
        return code;
    }

    public final P getPays() {
        return pays;
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof ObjetConcret) {
            final ObjetConcret<?> autre = (ObjetConcret<?>) other;
            return autre.getCode() == getCode()
                && autre.getPays() == getPays();
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Long.hashCode(code) + pays.hashCode();
    }
}