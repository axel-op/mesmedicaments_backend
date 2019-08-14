package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.Ignore;

import org.json.JSONObject;

import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;

public final class EntiteMedicamentBelgique extends AbstractEntiteMedicament<EntiteMedicamentBelgique.PresentationBelgique> {

    private static final Pays PAYS = Pays.Belgique;

    public static Optional<EntiteMedicamentBelgique> obtenirEntite (Long code) 
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        return obtenirEntite(PAYS, code, EntiteMedicamentBelgique.class);
    }

    /**
     * Les codes non trouvés lèvent une exception
     * @param codes
     * @return
     */
    public static Set<EntiteMedicamentBelgique> obtenirEntites (Set<Long> codes, boolean ignorerNonTrouves, Logger logger) {
        return obtenirEntites(PAYS, codes, EntiteMedicamentBelgique.class, logger, ignorerNonTrouves);
    }

    public EntiteMedicamentBelgique (long codeAMP) {
        super(PAYS, codeAMP); 
    }

    /**
     * NE PAS UTILISER
     */
    public EntiteMedicamentBelgique () { super(); }

    @Ignore
    @Override
    public void setPresentationsJson (Set<JSONObject> presJson) {
        this.presentationsSet.clear();
        this.presentationsSet.addAll(presJson.stream()
            .map(j -> new PresentationBelgique(j))
            .collect(Collectors.toSet())
        );
    }

    @Override
    public boolean conditionsARemplir() {
        return forme != null
            && !getNomsParLangue().isEmpty()
            && !getMarque().equals("");
    }

    public static class PresentationBelgique extends Presentation {
        private String nom;
        private double prix;
        private int codeCNK;

        public PresentationBelgique (
            String nom,
            Double prix,
            int codeCNK
        ) {
            if (nom == null) throw new IllegalArgumentException("Le nom de la présentation ne peut pas être null");
            this.nom = nom;
            this.prix = prix != null ? prix : 0;
            this.codeCNK = codeCNK;
        }

        protected PresentationBelgique (JSONObject json) {
            super(json);
        }

        @Override
        public JSONObject toJson() {
            return new JSONObject()
                .put("nom", nom)
                .put("prix", prix)
                .put("codeCNK", codeCNK);
        }

        @Override
        protected void fromJson(JSONObject json) {
            this.nom = json.getString("nom");
            this.prix = json.getDouble("prix");
            this.codeCNK = json.getInt("codeCNK");
        }
        
        @Ignore
        public String getNom () { return nom; }
        @Ignore
        public double getPrix () { return prix; }

    }
}