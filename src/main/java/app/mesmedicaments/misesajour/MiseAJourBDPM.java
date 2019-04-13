package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;

import app.mesmedicaments.BaseDeDonnees;
import app.mesmedicaments.Utils;

/**
 * Met à jour la base de données à partir des informations récupérées sur la base publique des médicaments.
 */
public final class MiseAJourBDPM {

	private static final String TABLE_SUBSTANCES;
	private static final String TABLE_MEDICAMENTS;
	private static final String TABLE_NOMS_SUBSTANCES;
	private static final String TABLE_NOMS_MEDICAMENTS;
	private static final String URL_FICHIER_BDPM;
	private static final String URL_FICHIER_COMPO;
	private static final Integer TAILLE_BATCH; 
	private static final String ID_MSI;

	private static Logger logger;
	private static SQLServerConnection conn;
	
	static {
		TABLE_SUBSTANCES = System.getenv("table_substances");
		TABLE_MEDICAMENTS = System.getenv("table_medicaments");
		TABLE_NOMS_SUBSTANCES = System.getenv("table_nomssubstances");
		TABLE_NOMS_MEDICAMENTS = System.getenv("table_nomsmedicaments");
		URL_FICHIER_BDPM = System.getenv("url_cis_bdpm");
		URL_FICHIER_COMPO = System.getenv("url_cis_compo_bdpm");
		ID_MSI = System.getenv("msi_maj");
		TAILLE_BATCH = BaseDeDonnees.TAILLE_BATCH;
	}

	private MiseAJourBDPM () {}

	public static boolean handler (Logger logger) {
		MiseAJourBDPM.logger = logger;
		logger.info("Début de la mise à jour BDPM");
		conn = BaseDeDonnees.obtenirConnexion(ID_MSI, logger);
		if (conn == null) { return false; }
		if (!majSubstances()) { return false; }
		if (!majMedicaments()) { return false; }
		return true; 
	}

    private static boolean majSubstances () {
		SQLServerCallableStatement cs = null;
		String requete = "{call projetdmp.ajouterSubstance(?, ?, ?)}";
		logger.info("Début de la mise à jour des substances");
		try {
			String tailleAvant1 = "Taille de la table substances avant la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_SUBSTANCES, logger);
			String tailleAvant2 = "Taille de la table noms_substances avant la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_NOMS_SUBSTANCES, logger);
			long startTime = System.currentTimeMillis();
			conn.setAutoCommit(false);
			cs = (SQLServerCallableStatement) conn.prepareCall(requete);
			BufferedReader liste_substances = importerFichier(URL_FICHIER_COMPO);
			if (liste_substances == null) { return false; }
			String ligne;
			int c = 0;
			long dureeMoyenne = 0;
			while ((ligne = liste_substances.readLine()) != null) {
				String[] elements = ligne.split("\t");
				Long codecis = Long.parseLong(elements[0]);
				Integer codesubstance = Integer.parseInt(elements[2]);
				String nom = elements[3].trim();
				cs.setLong(1, codecis);
				cs.setInt(2, codesubstance);
				cs.setString(3, nom);
				cs.addBatch();
				c++;
				if (c % TAILLE_BATCH == 0) {
					logger.info("Execution batch "
						+ "de " + TAILLE_BATCH + " requêtes (" 
						+ String.valueOf(c / TAILLE_BATCH) + ")" );
					long start = System.currentTimeMillis();
					cs.executeBatch();
					dureeMoyenne += System.currentTimeMillis() - start;
				}
			}
			logger.info("Execution batch de " + String.valueOf(c % TAILLE_BATCH) + " requêtes");
			cs.executeBatch();
			conn.commit();
			long endTime = System.currentTimeMillis();
			logger.info(tailleAvant1);
			logger.info(tailleAvant2);
			logger.info("Taille de la table substances après la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_SUBSTANCES, logger));
			logger.info("Taille de la table noms_substances après la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_NOMS_SUBSTANCES, logger));
			logger.info("Fin de la mise à jour des substances en " 
				+ String.valueOf(endTime - startTime) + " ms");
			try {
				logger.info("Durée moyenne de l'exécution d'un batch de "
					+ TAILLE_BATCH + " requêtes : "
					+ String.valueOf(dureeMoyenne / (c / TAILLE_BATCH)) 
					+ " ms");
			} catch (ArithmeticException e) {}
		} 
        catch (IOException | SQLException e) { 
			Utils.logErreur(e, logger);
			return false;
        }
		finally {
			BaseDeDonnees.fermer(cs);
		}
		return true;
    }

    private static boolean majMedicaments () {
		SQLServerCallableStatement cs = null;
		String requete = "{call projetdmp.ajouterMedicament(?, ?, ?, ?, ?)}";
		int max = 0;
		logger.info("Début de la mise à jour des médicaments");
		try {
			String tailleAvant1 = "Taille de la table medicaments avant la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_MEDICAMENTS, logger);
			String tailleAvant2 = "Taille de la table noms_medicaments avant la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_NOMS_MEDICAMENTS, logger);
			long startTime = System.currentTimeMillis();
			conn.setAutoCommit(false);
			cs = (SQLServerCallableStatement) conn.prepareCall(requete);
			BufferedReader liste_medicaments = importerFichier(URL_FICHIER_BDPM);
			if (liste_medicaments == null) { return false; }
			String ligne;
			int c = 0;
			long dureeMoyenne = 0;
			while ((ligne = liste_medicaments.readLine()) != null) {
				String[] elements = ligne.split("\t");
				Integer codecis = Integer.parseInt(elements[0]);
				String nom = elements[1];
				if (nom.length() > max) { max = nom.length(); }
				String forme = elements[2];
				String autorisation = elements[4];
				String marque = elements[10];
				cs.setInt(1, codecis);
				cs.setString(2, nom);
				cs.setString(3, forme);
				cs.setString(4, autorisation);
				cs.setString(5, marque);
				cs.addBatch();
				c++;
				if (c % TAILLE_BATCH == 0) {
					logger.info("Execution batch "
						+ "de " + TAILLE_BATCH + " requêtes (" 
						+ String.valueOf(c / TAILLE_BATCH) + ")" );
					long start = System.currentTimeMillis();
					cs.executeBatch();
					dureeMoyenne += System.currentTimeMillis() - start;
				}
			}
			logger.info("Execution batch de " + String.valueOf(c % TAILLE_BATCH) + " requêtes");
			cs.executeBatch();
			conn.commit();
			long endTime = System.currentTimeMillis();
			logger.info(tailleAvant1);
			logger.info(tailleAvant2);
			logger.info("Taille de la table medicaments après la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_MEDICAMENTS, logger));
			logger.info("Taille de la table noms_medicaments après la mise à jour : " 
				+ BaseDeDonnees.obtenirTailleTable(TABLE_NOMS_MEDICAMENTS, logger));
			logger.info("Fin de la mise à jour des médicaments en " 
				+ String.valueOf(endTime - startTime) + " ms");
			try {
				logger.info("Durée moyenne de l'exécution d'un batch de "
					+ TAILLE_BATCH + " requêtes : "
					+ String.valueOf(dureeMoyenne / (c / TAILLE_BATCH)) 
					+ " ms");
			} catch (ArithmeticException e) {}
		}
		catch (IOException | SQLException e) { 
            Utils.logErreur(e, logger);;
			return false;
        }
		finally {
			BaseDeDonnees.fermer(cs);
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
