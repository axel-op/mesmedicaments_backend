package app.mesmedicaments.objets;

import java.util.HashSet;
import java.util.Set;

import app.mesmedicaments.objets.substances.Substance;

public
class ClasseSubstances {

    public final String nom;
    private final Set<Substance<?>> substances;

    public ClasseSubstances(String nom, Set<Substance<?>> substances) {
        this.nom = nom;
        this.substances = new HashSet<>(substances);
    }

    public Set<Substance<?>> getSubstances() {
        return new HashSet<>(substances);
    }

    @Override
    public String toString() {
        return "Classe \""
            + nom 
            + "\" (" 
            + substances.size()
            + " substances)";
    }
    
}