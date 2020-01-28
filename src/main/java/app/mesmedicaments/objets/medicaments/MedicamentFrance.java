package app.mesmedicaments.objets.medicaments;

import java.util.Set;

import org.json.JSONObject;

import app.mesmedicaments.objets.Noms;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.presentations.PresentationFrance;
import app.mesmedicaments.objets.substances.SubstanceActiveFrance;

public class MedicamentFrance extends Medicament<Pays.France, SubstanceActiveFrance, PresentationFrance> {

    private static final String formaterEffets(String effets) {
        return effets.replaceFirst(
            "Comme tous les médicaments, ce médicament peut provoquer des effets indésirables, mais ils ne surviennent pas systématiquement chez tout le monde\\.",
            "")
            .replaceAll("\\?dème", "œdème")
            .replaceAll("c\\?ur", "cœur"); // TODO Ajouter ce que j'ai mis dans
        // l'appli
    }

    private final String forme;

    public MedicamentFrance(
        int code, 
        Noms noms, 
        Set<SubstanceActiveFrance> substances, 
        String marque,
        String effetsIndesirables, 
        Set<PresentationFrance> presentations,
        String forme
    ) {
        super(
            Pays.France.instance, 
            code, 
            noms, 
            substances, 
            marque, 
            formaterEffets(effetsIndesirables), 
            presentations
        );
        this.forme = forme;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("forme", forme);            
    }

    public String getForme() {
        return forme;
    }
    
}