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
    public static Set<EntiteMedicamentBelgique> obtenirEntites (Set<Long> codes, Logger logger) {
        return obtenirEntites(PAYS, codes, EntiteMedicamentBelgique.class, logger);
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
    public Set<PresentationBelgique> getPresentationsSet() {
        // TODO
        return null;
    }

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
        // TODO
		return false;
    }

    public static class PresentationBelgique extends Presentation {

        protected PresentationBelgique (JSONObject json) {
            super(json);
        }

        @Override
        public JSONObject toJson() {
            // TODO
            return null;
        }

        @Override
        protected void fromJson(JSONObject json) {
			// TODO
		}

    }
}