package app.mesmedicaments.azure.tables.adapteurs;

import java.util.function.Function;

import app.mesmedicaments.basededonnees.Adapteur;

public
class GenericAdapteur<O, D>
extends Adapteur<O, D> {

    private final Function<D, O> toO;
    private final Function<O, D> fromO;

    public GenericAdapteur(Function<D, O> toObject, Function<O, D> fromObject) {
        this.toO = toObject;
        this.fromO = fromObject;
    }

    @Override
    public D fromObject(O object) {
        return fromO.apply(object);
    }

    @Override
    public O toObject(D document) {
        return toO.apply(document);
    }
}