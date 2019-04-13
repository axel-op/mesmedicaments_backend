package app.mesmedicaments;

import java.sql.SQLException;
import java.util.logging.Logger;

import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public final class BaseDeDonnees {

	public static final Integer TAILLE_BATCH;
	private static final String DB_HOSTNAME;
	private static final String DB_NAME;
	private static final String DB_PORT;

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
	}

	private BaseDeDonnees () {}

	public static SQLServerConnection nouvelleConnexion (String idMsi, Logger logger) {
		logger.info("(Classe BaseDeDonnees) Tentative de connexion à la base de données SQL Azure");
		try {
			SQLServerDataSource ds = new SQLServerDataSource();
			ds.setEncrypt(true);
			ds.setHostNameInCertificate("*.database.windows.net");
			ds.setServerName(DB_HOSTNAME);
			ds.setPortNumber(Integer.parseInt(DB_PORT));
			ds.setDatabaseName(DB_NAME);
			ds.setAuthentication("ActiveDirectoryMSI");
			ds.setMSIClientId(idMsi);
			SQLServerConnection connexion = (SQLServerConnection) ds.getConnection();
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