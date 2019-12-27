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

public final class EntiteMedicamentFrance extends AbstractEntiteMedicament<EntiteMedicamentFrance.PresentationFrance> {

    private static final Pays PAYS = Pays.France;

    /**
     * Les codes CIS non trouvés lèvent une exception
     * 
     * @param codesCis
     * @return
     */
    public static Set<EntiteMedicamentFrance> obtenirEntites(Set<Long> codesCis, boolean ignorerNonTrouves,
            Logger logger) {
        return obtenirEntites(PAYS, codesCis, EntiteMedicamentFrance.class, logger, ignorerNonTrouves);
    }

    public static Optional<EntiteMedicamentFrance> obtenirEntite(long codeCis)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirEntite(PAYS, codeCis, EntiteMedicamentFrance.class);
    }

    public static Iterable<EntiteMedicamentFrance> obtenirToutesLesEntites()
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirToutesLesEntites(PAYS, EntiteMedicamentFrance.class);
    }

    // Constructeurs

    public EntiteMedicamentFrance(long codeCis) {
        super(PAYS, codeCis);
    }

    /**
     * NE PAS UTILISER
     */
    public EntiteMedicamentFrance() {
        super();
    }

    @Override
    public boolean conditionsARemplir() {
        return forme != null && !getNomsParLangue().isEmpty() && !getMarque().equals("");
    }

    @Ignore
    @Override
    public void setPresentationsJson(Set<JSONObject> presJson) {
        this.presentationsSet.clear();
        this.presentationsSet.addAll(presJson.stream().map(PresentationFrance::new).collect(Collectors.toSet()));
    }

    public static class PresentationFrance extends Presentation {
        private String nom;
        private double prix;
        private int tauxRemboursement;
        private double honorairesDispensation;
        private String conditionsRemboursement;

        public PresentationFrance(String nom, double prix, int tauxRemboursement, double honorairesDispensation,
                String conditionsRemboursement) {
            if (nom == null)
                throw new IllegalArgumentException();
            this.nom = nom;
            this.prix = prix;
            this.tauxRemboursement = tauxRemboursement;
            this.honorairesDispensation = honorairesDispensation;
            this.conditionsRemboursement = conditionsRemboursement != null ? conditionsRemboursement : "";
        }

        protected PresentationFrance(JSONObject json) {
            super(json);
        }

        @Override
        protected void fromJson(JSONObject json) {
            this.nom = json.getString("nom");
            this.prix = json.getDouble("prix");
            this.tauxRemboursement = json.getInt("tauxRemboursement");
            this.honorairesDispensation = json.getDouble("honorairesDispensation");
            this.conditionsRemboursement = json.getString("conditionsRemboursement");
        }

        @Override
        public JSONObject toJson() {
            return new JSONObject().put("nom", nom).put("prix", prix).put("tauxRemboursement", tauxRemboursement)
                    .put("honorairesDispensation", honorairesDispensation)
                    .put("conditionsRemboursement", conditionsRemboursement);
        }

        @Ignore
        public String getNom() {
            return nom;
        }

        @Ignore
        public double getPrix() {
            return prix;
        }

        @Ignore
        public int getTauxRemboursement() {
            return tauxRemboursement;
        }

        @Ignore
        public double getHonoraires() {
            return honorairesDispensation;
        }

        @Ignore
        public String getConditionsRemboursement() {
            return conditionsRemboursement;
        }
    }
}