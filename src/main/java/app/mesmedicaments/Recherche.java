package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.entitestables.EntiteMedicament;

class Recherche {

    private Recherche () {}

    protected static JSONArray rechercher (String terme, Logger logger) 
        throws StorageException, URISyntaxException, InvalidKeyException  
    {
        Set<String> iterable = new HashSet<>();
        iterable.add(terme);
        return rechercher(iterable, logger).get(terme);
    }

    protected static Map<String, JSONArray> rechercher (Iterable<String> termes, Logger logger) 
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        Map<String, JSONArray> resultats = new HashMap<>();
        int compteur = 0;
        for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
            compteur += 1;
            String entiteStr = Utils.normaliser(
                entite.getNoms() + " " + 
                entite.getForme() + " " + 
                entite.getAutorisation())
                .toLowerCase();
            Set<String> trouves = new HashSet<>();
            for (String terme : termes) {
                if (entiteStr.contains(terme)) {  trouves.add(terme); }
            }
            if (trouves.size() > 0) {
                JSONObject medJson = Utils.medicamentEnJson(entite, logger);
                for (String trouve : trouves) {
                    resultats.computeIfAbsent(trouve, k -> new JSONArray())
                        .put(medJson);
                }
            }
        }
        if (compteur == 0) { 
            throw new RuntimeException("Erreur lors de la récupération des entités Medicament");
        }
        return resultats;
    }
}