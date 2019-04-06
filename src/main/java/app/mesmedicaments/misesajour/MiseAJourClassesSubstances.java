package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import app.mesmedicaments.BaseDeDonnees;
import app.mesmedicaments.Utils;

public final class MiseAJourClassesSubstances {

	private static final String URL_CLASSES;
	private static final Integer TAILLE_BATCH;
	private static Logger logger;
	private static SQLServerConnection conn;
	private static HashMap<String, HashSet<Integer>> noms = new HashMap<>();
	private static HashMap<Integer, String> codes = new HashMap<>();
	private static HashMap<Integer, Integer> majEnAttente = new HashMap<>();
	private static HashMap<Integer, String> classes = new HashMap<>();
	private static HashMap<String, HashSet<Integer>> cacheRecherche = new HashMap<>();

	static {
		URL_CLASSES = System.getenv("url_classes");
		TAILLE_BATCH = BaseDeDonnees.TAILLE_BATCH;
	}
		
	private MiseAJourClassesSubstances () {}

	public static boolean handler (Logger logger) {
		MiseAJourClassesSubstances.logger = logger;
		logger.info("Début de la mise à jour des classes de substances");
		conn = BaseDeDonnees.obtenirConnexion(logger);
		if (conn == null) { return false; }
		noms = BaseDeDonnees.obtenirNomsSubstances(logger);
		codes = BaseDeDonnees.obtenirCodesSubstances(logger);
		if (noms.isEmpty() || codes.isEmpty()) { return false; }
		if (!importerClasses()) { return false; }
		if (!mettreAJourClasses()) { return false; }
		return true;
	}

	private static boolean importerClasses () {
		Integer nbrClassesTrouvees = 0;
		try {
			logger.info("Tentative de récupération du fichier des classes de substances (url = " + URL_CLASSES + ")");
			HttpsURLConnection connexion = (HttpsURLConnection) new URL(URL_CLASSES).openConnection();
			connexion.setRequestMethod("GET");
			PDDocument document = PDDocument.load(connexion.getInputStream());
			logger.info("Fichier récupéré");
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(2);
			stripper.setParagraphStart("/t");
			String classeencours = "";
			HashSet<Integer> substancesencours = new HashSet<Integer>();
			String[] paragraphes = stripper.getText(document).split(stripper.getParagraphStart());
			int c = 0;
			long startTime = System.currentTimeMillis();
			for (String paragraphe : paragraphes) { 
				c++;
				logger.info("Parsing du paragraphe " + c + "/" + paragraphes.length + "...");
				BufferedReader br = new BufferedReader(new StringReader(paragraphe));
				String ligne = br.readLine();
				if (ligne != null && !(ligne.matches("(Page .*)|(Thésaurus .*)|(Index .*)|(ANSM .*)"))) {
					if (!classeencours.equals("")) { 
						for (Integer codesubstance : substancesencours) {
							Integer idclasse = obtenirIdClasse(classeencours);
							classes.put(idclasse, classeencours);
							if (idclasse != null) { majEnAttente.put(codesubstance, idclasse); }
							else {
								logger.severe("Impossible de mettre à jour "
									+ "la classe \"" + classeencours + "\", "
									+ "car son id n'a pas été obtenu (id = null)."
								);
							}
							
						}
						nbrClassesTrouvees += 1;
					}
					substancesencours = new HashSet<Integer>();
					classeencours = ligne.trim();
				}
				while ((ligne = br.readLine()) != null) {
					if (!(ligne.matches("(Page .*)|(Thésaurus .*)|(Index .*)|(ANSM .*)"))) {
						for (String substance : ligne.split(",")) {
							substance = substance.replaceAll("\\s", " ");
							substance = substance.replaceAll("[^- \\p{ASCII}\\p{IsLatin}]|\\(|\\)", "");
							substance = substance.replaceAll("(acide)|(virus)", "");
							substance = substance.replaceAll("\\brota\\b", "rotavirus");
							substance = substance.trim();
							if (substance.matches(".*[a-z].*")) {
								for (Integer code : rechercherSubstances(substance)) { 
									substancesencours.add(code); 
								} 
							}
						}
					}
				}
			}
			long endTime = System.currentTimeMillis();
			logger.info("Parsing terminé en " + String.valueOf(endTime - startTime) + " ms");
			document.close();
		} catch (IOException e) { 
			Utils.logErreur(e, logger);
			return false;
		}
		logger.info("Nombre total de classes de substances trouvées : " + nbrClassesTrouvees);
		return true;
	}

	private static Integer obtenirIdClasse (String classe) {
		SQLServerCallableStatement cs = null;
		Integer id = null;
		String requete = "{call obtenirIdClasse(?, ?)}";
		try {
			cs = (SQLServerCallableStatement) conn.prepareCall(requete);
			cs.setString(1, classe);
			cs.registerOutParameter(2, java.sql.Types.SMALLINT);
			cs.execute();
			id = cs.getInt(2);
		} catch (SQLException e) {
			Utils.logErreur(e, logger);
		} finally {
			BaseDeDonnees.fermer(cs);
			if (id == null || id == 0) {
				logger.warning("Impossible d'obtenir l'id de la classe " + classe
					+ ". La procédure SQL a retourné " + id
				);
			}
		}
		return id;
	}

	private static boolean mettreAJourClasses () {
		SQLServerCallableStatement cs = null;
		String requete = "{call mettreAJourClasse(?, ?, ?)}";
		int c = 0;
		long dureeMoyenne = 0;
		long startTime = System.currentTimeMillis();
		try {
			for (Integer codesubstance : majEnAttente.keySet()) {
				Integer idclasse = majEnAttente.get(codesubstance);
				cs = (SQLServerCallableStatement) conn.prepareCall(requete);
				cs.setInt(1, codesubstance);
				cs.setInt(2, idclasse);
				cs.setInt(3, 0);
				cs.addBatch();
				c++;
				if (c % TAILLE_BATCH == 0) {
					logger.info("Execution batch de "
						+ TAILLE_BATCH + " requêtes (" 
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
			logger.info("Fin de la mise à jour des classes en " 
				+ String.valueOf(endTime - startTime) 
				+ " ms");
			try {
				logger.info("Durée moyenne de l'exécution d'un batch de "
					+ TAILLE_BATCH + " requêtes : "
					+ String.valueOf(dureeMoyenne / (c / TAILLE_BATCH)) 
					+ " ms");
			} catch (ArithmeticException e) {}
		} catch (SQLException e) {
			Utils.logErreur(e, logger);
			return false;
		} finally {
			BaseDeDonnees.fermer(cs);
		}
		return true;
	}

	private static HashSet<Integer> rechercherSubstances (String recherche) {
		recherche = normaliser(recherche);
		HashSet<Integer> resultats = cacheRecherche.get(recherche);
		if (resultats == null) {
			resultats = new HashSet<Integer>();
			for (String nom : noms.keySet()) {
				if (normaliser(nom).matches("(?i:.*" + recherche + ".*)")) { 
					resultats.addAll(noms.get(nom)); 
				}
			}
			cacheRecherche.put(recherche, resultats);
			logger.fine(resultats.size() + " substances trouvées à la recherche : " + recherche);
		}
		return resultats;
	}
		
	private static String normaliser (String original) {
		original = Normalizer.normalize(original, Normalizer.Form.NFD)
			.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		original = original.toLowerCase();
			original = original.trim();
			original = original.replaceAll("  ", " ");
			return original;
	}
}