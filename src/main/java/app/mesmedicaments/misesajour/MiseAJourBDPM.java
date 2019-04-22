package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.Map.Entry;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntiteProduit;
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
		try {
			logger.info("Parsing en cours...");
			String ligne;
			long startTime = System.currentTimeMillis();
			while ((ligne = listeSubstances.readLine()) != null) {
				String[] elements = ligne.split("\t");
				Long codecis = Long.parseLong(elements[0]);
				Long codesubstance = Long.parseLong(elements[2]);
				String nom = elements[3].trim();
				if (substances.get(codesubstance) == null) {
					substances.put(codesubstance, new TreeSet<>());
				}
				substances.get(codesubstance).add(nom);
			}
			double total = substances.size();
			logger.info("Parsing terminé en " + tempsDepuis(startTime) + " ms. " 
				+ ((int) total) + " substances trouvées."
			);
			logger.info("Création des entités en cours...");
			TreeSet<EntiteSubstance> entites = new TreeSet<>(getComparatorEntites());
			startTime = System.currentTimeMillis();
			for (Entry<Long, TreeSet<String>> entree : substances.entrySet()) {
				long codesubstance = entree.getKey();
				EntiteSubstance entite = new EntiteSubstance(codesubstance);
				entite.definirNomsJsonArray(new JSONArray(entree.getValue()));
				entites.add(entite);
			}
			logger.info(entites.size() + " entités créées en " + tempsDepuis(startTime) + " ms. "
			);
			logger.info("Mise à jour de la base de données en cours...");
			startTime = System.currentTimeMillis();
			EntiteSubstance.mettreAJourEntitesBatch(entites);
			logger.info("Base mise à jour en " + tempsDepuis(startTime) + " ms"
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
			logger.info("Parsing en cours...");
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
			logger.info("Parsing terminé en " + tempsDepuis(startTime) + " ms. " 
				+ ((int) total) + " médicaments trouvés."
			);
			logger.info("Création des entités en cours...");
			TreeSet<EntiteMedicament> entites = new TreeSet<>(getComparatorEntites());
			startTime = System.currentTimeMillis();
			for (Entry<Long, TreeSet<String>> entree : nomsMed.entrySet()) {
				long codecis = entree.getKey();
				EntiteMedicament entite = new EntiteMedicament(codecis);
				entite.definirNomsJsonArray(new JSONArray(entree.getValue()));
				entite.setForme(caracMed.get(codecis)[0]);
				entite.setAutorisation(caracMed.get(codecis)[1]);
				entite.setMarque(caracMed.get(codecis)[2]);
				entites.add(entite);
			}
			logger.info(entites.size() + " entités créées en " + tempsDepuis(startTime) + " ms. "
			);
			logger.info("Mise à jour de la base de données en cours...");
			startTime = System.currentTimeMillis();
			EntiteMedicament.mettreAJourEntitesBatch(entites);
			logger.info("Base mise à jour en " + tempsDepuis(startTime) + " ms. "
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

	private static Comparator<AbstractEntiteProduit> getComparatorEntites () {
		return new Comparator<AbstractEntiteProduit> () {
			@Override
			public int compare(AbstractEntiteProduit o1, AbstractEntiteProduit o2) {
				return Integer.valueOf(o1.getRowKey())
					.compareTo(Integer.valueOf(o2.getRowKey())
				);
			}
		};
	}

	private static long tempsDepuis (long startTime) {
		return System.currentTimeMillis() - startTime;
	}
}
