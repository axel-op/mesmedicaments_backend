package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

import app.mesmedicaments.entitestables.EntiteMedicament;

class Recherche {

    private Recherche () {}

    protected static JSONArray rechercher (String recherche, Logger logger) 
        throws StorageException, URISyntaxException, InvalidKeyException    
    {
        JSONArray resultats = new JSONArray();
        Set<EntiteMedicament> trouvees = new HashSet<>();
        for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
            if (trouvees.size() >= 10) { break; }
            if (Utils.normaliser(entite.getNoms() + " " + entite.getForme())
                .toLowerCase()
                .contains(recherche)
            ) { trouvees.add(entite); }
        }
        trouvees.stream()
            .forEach((entite) -> {
                try { resultats.put(Utils.medicamentEnJson(entite, logger)); }
                catch (StorageException | URISyntaxException | InvalidKeyException e) {
                    Utils.logErreur(e, logger);
                    throw new RuntimeException();
                }
            });
        return resultats;
    }
}