package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONObject;

public final class EntiteMedicamentFrance extends AbstractEntiteMedicament<EntiteMedicamentFrance.Presentation> {

    private static final String PARTITION = "medicament-france";

    public static Optional<EntiteMedicamentFrance> obtenirEntite(long codeCIS)
        throws StorageException, URISyntaxException, InvalidKeyException 
    {
        return AbstractEntiteProduit.obtenirEntite(
            PARTITION, 
            codeCIS, 
            EntiteMedicamentFrance.class
        );
    }

    public static Iterable<EntiteMedicamentFrance> obtenirToutesLesEntites()
        throws StorageException, URISyntaxException, InvalidKeyException 
    {
        return AbstractEntiteProduit.obtenirToutesLesEntites(PARTITION, EntiteMedicamentFrance.class);
    }

    // Constructeurs

    public EntiteMedicamentFrance (long codeCis)
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        super(PARTITION, codeCis);
    }

    /**
     * NE PAS UTILISER
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InvalidKeyException
     */
    public EntiteMedicamentFrance () throws StorageException, URISyntaxException, InvalidKeyException
    {
        super();
    }

    // Getters

    @Override
    public Set<Presentation> obtenirPresentations () {
        if (presentations == null) return new HashSet<>();
        JSONObject json = new JSONObject(presentations);
        return json.keySet().stream().map(nom -> {
            JSONObject jsonPres = json.getJSONObject(nom);
            return new Presentation(
                nom, 
                jsonPres.getDouble("prix"), 
                jsonPres.getInt("tauxRemboursement"),
                jsonPres.getDouble("honorairesDispensation"), 
                jsonPres.getString("conditionsRemboursement")
            );
        }).collect(Collectors.toSet());
    }

    // Setters

    @Override
    public void definirPresentations (Iterable<Presentation> presentations) {
        if (presentations == null) this.presentations = new JSONObject().toString();
        else {
            JSONObject json = new JSONObject();
            for (Presentation presentation : presentations) {
                json.put(presentation.nom,
                new JSONObject()
                    .put("prix", presentation.prix)
                    .put("tauxRemboursement", presentation.tauxRemboursement)
                    .put("honorairesDispensation", presentation.honorairesDispensation)
                    .put("conditionsRemboursement", presentation.conditionsRemboursement));
            }
            this.presentations = json.toString();
        }
    }
    

    public static class Presentation {
        public final String nom;
        public final Double prix;
        public final Integer tauxRemboursement;
        public final Double honorairesDispensation;
        public final String conditionsRemboursement;

        public Presentation(
            String nom, 
            double prix, 
            int tauxRemboursement, 
            double honorairesDispensation,
            String conditionsRemboursement
        ) {
            if (nom == null) throw new IllegalArgumentException();
            this.nom = nom;
            this.prix = prix;
            this.tauxRemboursement = tauxRemboursement;
            this.honorairesDispensation = honorairesDispensation;
            this.conditionsRemboursement = conditionsRemboursement != null ? conditionsRemboursement : "";
        }
    }
}