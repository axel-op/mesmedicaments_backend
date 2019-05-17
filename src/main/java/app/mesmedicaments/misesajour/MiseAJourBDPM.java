package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;

/**
 * Met à jour la base de données à partir des informations récupérées sur la base publique des médicaments.
 */
public final class MiseAJourBDPM {

	private static final String URL_FICHIER_BDPM;
	private static final String URL_FICHIER_COMPO;
	private static Logger logger;

	static {
		URL_FICHIER_BDPM = System.getenv("url_cis_bdpm");
		URL_FICHIER_COMPO = System.getenv("url_cis_compo_bdpm");
	}

	private MiseAJourBDPM () {}

    public static boolean majSubstances (Logger logger) {
		MiseAJourBDPM.logger = logger;
		logger.info("Début de la mise à jour des substances");
		BufferedReader listeSubstances = importerFichier(URL_FICHIER_COMPO);
		if (listeSubstances == null) { return false; }
		TreeMap<Long, TreeSet<String>> substances = new TreeMap<>();
		TreeMap<Long, TreeSet<Long>> medSubstances = new TreeMap<>();
		try {
			logger.info("Parsing en cours...");
			String ligne;
			long startTime = System.currentTimeMillis();
			while ((ligne = listeSubstances.readLine()) != null) {
				String[] elements = ligne.split("\t");
				long codecis = Long.parseLong(elements[0]);
				long codesubstance = Long.parseLong(elements[2]);
				String nom = elements[3].trim();
				substances.computeIfAbsent(codesubstance, cle -> new TreeSet<>())
					.add(nom);
				medSubstances.computeIfAbsent(codecis, cle -> new TreeSet<>())
					.add(codesubstance);
			}
			double total = substances.size();
			logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms. " 
				+ ((int) total) + " substances trouvées."
			);
			logger.info("Création des entités en cours...");
			TreeSet<EntiteSubstance> entitesSubstances = new TreeSet<>();
			TreeSet<EntiteMedicament> entitesMedicaments = new TreeSet<>();
			startTime = System.currentTimeMillis();
			for (Entry<Long, TreeSet<String>> entree : substances.entrySet()) {
				EntiteSubstance entite = new EntiteSubstance(entree.getKey());
				entite.definirNomsJArray(new JSONArray(entree.getValue()));
				entitesSubstances.add(entite);
			}
			for (Entry<Long, TreeSet<Long>> entree : medSubstances.entrySet()) {
				EntiteMedicament entite = new EntiteMedicament(entree.getKey());
				entite.definirSubstancesActivesJArray(new JSONArray(entree.getValue()));
				entitesMedicaments.add(entite);
			}
			logger.info(entitesSubstances.size() + " entités Substance"
				+ " et " + entitesMedicaments.size() + " entités Médicament"
				+ " créées en " + Utils.tempsDepuis(startTime) + " ms. ");
			logger.info("Mise à jour de la base de données en cours...");
			startTime = System.currentTimeMillis();
			EntiteSubstance.mettreAJourEntitesBatch(entitesSubstances);
			EntiteMedicament.mettreAJourEntitesBatch(entitesMedicaments);
			logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms"
			);
		}
		catch (IOException 
			| StorageException 
			| InvalidKeyException 
			| URISyntaxException e
		) {
			Utils.logErreur(e, logger);
			return false;
		}
		return true;
    }

    public static boolean majMedicaments (Logger logger) {
		MiseAJourBDPM.logger = logger;
		logger.info("Début de la mise à jour des médicaments");
		/**** A revoir 
		String tailleAvant1 = "Taille de la table medicaments avant la mise à jour : " 
			+ obtenirTailleTable(TABLE_MEDICAMENTS);
		String tailleAvant2 = "Taille de la table noms_medicaments avant la mise à jour : " 
			+ obtenirTailleTable(TABLE_NOMS_MEDICAMENTS); */
		BufferedReader listeMedicaments = importerFichier(URL_FICHIER_BDPM);
		if (listeMedicaments == null) { return false; }
		TreeMap<Long, TreeSet<String>> nomsMed = new TreeMap<>();
		TreeMap<Long, String[]> caracMed = new TreeMap<>();
		try {
			logger.info("Parsing en cours et ajout pour l'indexation...");
			String ligne;
			long startTime = System.currentTimeMillis();
			while ((ligne = listeMedicaments.readLine()) != null) {
				String[] elements = ligne.split("\t");
				long codecis = Long.parseLong(elements[0]);
				String nom = elements[1];
				String forme = elements[2];
				String autorisation = elements[4];
				String marque = elements[10];
				if (nomsMed.get(codecis) == null) { 
					nomsMed.put(codecis, new TreeSet<>()); 
				}
				nomsMed.get(codecis).add(nom);
				caracMed.put(codecis, new String[]{forme, autorisation, marque});
			}
			double total = nomsMed.size();
			logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms. " 
				+ ((int) total) + " médicaments trouvés."
			);
			logger.info("Création des entités en cours...");
			TreeSet<EntiteMedicament> entites = new TreeSet<>();
			startTime = System.currentTimeMillis();
			for (Entry<Long, TreeSet<String>> entree : nomsMed.entrySet()) {
				long codecis = entree.getKey();
				EntiteMedicament entite = new EntiteMedicament(codecis);
				entite.definirNomsJArray(new JSONArray(entree.getValue()));
				entite.setForme(caracMed.get(codecis)[0]);
				entite.setAutorisation(caracMed.get(codecis)[1]);
				entite.setMarque(caracMed.get(codecis)[2]);
				entites.add(entite);
			}
			logger.info(entites.size() + " entités créées en " + Utils.tempsDepuis(startTime) + " ms. "
			);
			logger.info("Mise à jour de la base de données en cours...");
			startTime = System.currentTimeMillis();
			EntiteMedicament.mettreAJourEntitesBatch(entites);
			logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms. "
			);
		}
		catch (StorageException 
			| InvalidKeyException 
			| URISyntaxException 
			| IOException e
		) {
			Utils.logErreur(e, logger);
			return false;
		}
		return true;
    }

    private static BufferedReader importerFichier (String url) {
        BufferedReader br = null;
		try {
			logger.info("Récupération du fichier (url = " + url + ")");
			HttpURLConnection connexion = (HttpURLConnection) new URL(url)
				.openConnection();
			connexion.setRequestMethod("GET");
			logger.info("Fichier de la base à télécharger atteint");
			InputStreamReader isr = new InputStreamReader(connexion.getInputStream());
			br = new BufferedReader(isr);
			//log("Encodage du fichier récupéré : " + isr.getEncoding());
		} catch (IOException e) {
            Utils.logErreur(e, logger);;
            return null;
        }
		return br;
	}
}
