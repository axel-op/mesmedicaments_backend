package app.mesmedicaments;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerResultSet;
import com.microsoft.sqlserver.jdbc.SQLServerStatement;

public final class BaseDeDonnees {

	public static final Integer TAILLE_BATCH;

	private static final String TABLE_INTERACTIONS;
	private static String TABLE_NOMS_SUBSTANCES;
	private static final String DB_HOSTNAME;
	private static final String DB_NAME;
	private static final String DB_PORT;
	private static final String MSI_CLIENTID;

	private static SQLServerConnection connexion = null;
	private static HashMap<String, HashSet<Integer>> nomsSubstances = new HashMap<>();
	private static HashMap<Integer, String> codesSubstances = new HashMap<>();
	private static HashMap<Long, Integer> interactions = new HashMap<>();

	static {
		String taille = System.getenv("taille_batch");
		if (taille != null) { 
			TAILLE_BATCH = Integer.parseInt(taille);
		}
		else {
			TAILLE_BATCH = 1000;
		}
		DB_HOSTNAME = System.getenv("db_hostname");
		DB_NAME = System.getenv("db_name");
		DB_PORT = System.getenv("db_port");
		MSI_CLIENTID = System.getenv("MSI_ENDPOINT");
		TABLE_INTERACTIONS = System.getenv("table_interactions");
		TABLE_NOMS_SUBSTANCES = System.getenv("table_nomssubstances");
	}

	private BaseDeDonnees () {}

	public static SQLServerConnection obtenirConnexion (Logger logger) {
		if (connexion == null) { return nouvelleConnexion(logger); }
		try { if (connexion.isValid(10)) { return connexion; } }
		catch (SQLException e) {}
		return nouvelleConnexion(logger);
	}

	public static void fermer (AutoCloseable element) {
		if (element != null) {
			try {
				element.close();
			}
			catch (Exception e) {}
		}
	}
	
	public static Integer obtenirTailleTable (String table, Logger logger) {
		Integer taille = null;
		SQLServerStatement stmt = null;
		SQLServerResultSet rs = null;
		String requete = "SELECT COUNT(*) FROM " + table;
		try {
			stmt = (SQLServerStatement) obtenirConnexion(logger).createStatement();
			rs = (SQLServerResultSet) stmt.executeQuery(requete);
			rs.next();
			taille = rs.getInt(1);
		}
		catch (SQLException e) {
			logger.warning("(Classe BaseDeDonnees) Erreur lors de la requête tailleTable"
				+ " pour la table " + table);
			Utils.logErreur(e, logger);
		}
		finally {
			fermer(stmt);
			fermer(rs);
		}
		return taille;
	}

	public static HashMap<Long, Integer> obtenirInteractions (Logger logger) {
		if (interactions.isEmpty()) {
			importerInteractions(logger);
		}
		return interactions;
	}

	public static HashMap<String, HashSet<Integer>> obtenirNomsSubstances (Logger logger) {
		if (nomsSubstances.isEmpty()) {
			importerSubstances(logger);
		}
		return nomsSubstances;
	}

	public static HashMap<Integer, String> obtenirCodesSubstances (Logger logger) {
		if (codesSubstances.isEmpty()) {
			importerSubstances(logger);
		}
		return codesSubstances;
	}

	private static void importerSubstances (Logger logger) {
		String requete = "SELECT nom, codesubstance FROM " + TABLE_NOMS_SUBSTANCES;
		SQLServerStatement statement = null;
		SQLServerResultSet resultset = null;
		try {
			statement = (SQLServerStatement) obtenirConnexion(logger).createStatement();
			resultset = (SQLServerResultSet) statement.executeQuery(requete);
			while (resultset.next()) {
				String nom = resultset.getString(1);
				Integer code = resultset.getInt(2);
				if (nomsSubstances.containsKey(nom)) { nomsSubstances.get(nom).add(code); }
				else {
					HashSet<Integer> nouveauset = new HashSet<>();
					nouveauset.add(code);
					nomsSubstances.put(nom, nouveauset);
				}
				codesSubstances.put(code, nom);
			}
		} 
		catch (SQLException e) {
			logger.warning("(Classe BaseDeDonnees) Erreur lors de l'importation des substances");
			Utils.logErreur(e, logger);
		} 
		finally {
			BaseDeDonnees.fermer(resultset);
			BaseDeDonnees.fermer(statement);
		}
		logger.info("Substances importées : "
			+ Utils.NEWLINE + "taille du HashSet noms = " + nomsSubstances.size()
			+ Utils.NEWLINE + "taille du HashSet codes = " + codesSubstances.size());
	}

	private static void importerInteractions (Logger logger) {
		String requete = "SELECT id, risque FROM " + TABLE_INTERACTIONS;
		SQLServerStatement statement = null;
		SQLServerResultSet resultset = null;
		try {
			statement = (SQLServerStatement) obtenirConnexion(logger).createStatement();
			resultset = (SQLServerResultSet) statement.executeQuery(requete);
			while (resultset.next()) {
				Long id = resultset.getLong(1);
				Integer risque = resultset.getInt(2);
				interactions.put(id, risque);
			}
		}
		catch (SQLException e) {
			logger.warning("(Classe BaseDeDonnees) Erreur lors de l'importation des interactions");
			Utils.logErreur(e, logger);
		}
		finally {
			BaseDeDonnees.fermer(resultset);
			BaseDeDonnees.fermer(statement);
		}
		logger.info(interactions.size() + " interactions importées");
	}

	private static SQLServerConnection nouvelleConnexion (Logger logger) {
		logger.info("(Classe BaseDeDonnees) Tentative de connexion à la base de données SQL Azure");
		try {
			SQLServerDataSource ds = new SQLServerDataSource();
			ds.setEncrypt(true);
			ds.setHostNameInCertificate("*.database.windows.net");
			ds.setServerName(DB_HOSTNAME);
			ds.setPortNumber(Integer.parseInt(DB_PORT));
			ds.setDatabaseName(DB_NAME);
			ds.setAuthentication("ActiveDirectoryMSI");
			ds.setMSIClientId(MSI_CLIENTID);
			connexion = (SQLServerConnection) ds.getConnection();
			logger.info("(Classe BaseDeDonnees) Connexion à la base de données réussie");
			return connexion;
		}
		catch (SQLException e) {
			logger.severe("(Classe BaseDeDonnees) Erreur lors de la connexion à la base de données");
			Utils.logErreur(e, logger);
		}
		return null;
	}

}