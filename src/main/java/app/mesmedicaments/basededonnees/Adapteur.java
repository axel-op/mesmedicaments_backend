package app.mesmedicaments.basededonnees;

/**
 * 
 * @param <O> Type de l'objet en lequel est converti le document
 * @param <D> Type du document
 */
public
abstract class Adapteur<O, D> {

    public Adapteur() {}

    /**
     * Ecrase les champs de l'entité avec ceux de l'objet
     * @param object
     * @return
     */
    abstract public D fromObject(O object);
    abstract public O toObject(D document);

}