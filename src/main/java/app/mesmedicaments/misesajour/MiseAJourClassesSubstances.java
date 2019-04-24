package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableBatchOperation;
import com.microsoft.azure.storage.table.TableServiceEntity;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite;
import app.mesmedicaments.entitestables.EntiteSubstance;

public final class MiseAJourClassesSubstances {

	private static final String URL_CLASSES;
	private static final String TABLE;

	private static Logger logger;
	private static HashMap<String, HashSet<Long>> classes = new HashMap<>();
	private static HashMap<String, HashSet<Long>> nomsSubstances = new HashMap<>();
	private static HashMap<String, HashSet<Long>> cacheRecherche = new HashMap<>();

	static {
		URL_CLASSES = System.getenv("url_classes");
		TABLE = System.getenv("tableazure_classes"); /// A METTRE
	}
		
	private MiseAJourClassesSubstances () {}

	public static boolean handler (Logger logger) {
		MiseAJourClassesSubstances.logger = logger;
		logger.info("Début de la mise à jour des classes de substances");
		nomsSubstances = importerSubstances(logger);
		if (nomsSubstances.isEmpty()) { return false; }
		if (!importerClasses()) { return false; }
		if (!exporterClasses()) { return false; }
		return true;
	}

	private static boolean importerClasses () {
		Integer nbrClassesTrouvees = 0;
		try {
			logger.info("Récupération du fichier des classes de substances (url = " + URL_CLASSES + ")");
			HttpsURLConnection connexion = (HttpsURLConnection) new URL(URL_CLASSES).openConnection();
			connexion.setRequestMethod("GET");
			PDDocument document = PDDocument.load(connexion.getInputStream());
			logger.info("Fichier récupéré");
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(2);
			stripper.setParagraphStart("/t");
			String classeencours = "";
			HashSet<Long> substancesencours = new HashSet<>();
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
						if (classes.get(classeencours) == null) {
							classes.put(classeencours, new HashSet<>());
						}
						for (long codesubstance : substancesencours) {
							classes.get(classeencours).add(codesubstance);
						}
						nbrClassesTrouvees += 1;
					}
					substancesencours = new HashSet<>();
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
								for (long code : rechercherSubstances(substance)) { 
									substancesencours.add(code); 
								} 
							}
						}
					}
				}
			}
			logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
			document.close();
		} catch (IOException e) { 
			Utils.logErreur(e, logger);
			return false;
		}
		logger.info("Nombre total de classes de substances trouvées : " + nbrClassesTrouvees);
		return true;
	}

	private static boolean exporterClasses () {
		logger.info("Mise à jour de la base de données en cours...");
		try {
			CloudTable cloudTable = AbstractEntite.obtenirCloudTable(TABLE);
			long startTime = System.currentTimeMillis();
			for (Entry<String, HashSet<Long>> entree : classes.entrySet()) {
				TableBatchOperation batchOperation = new TableBatchOperation();
				for (Long codeSubstance : entree.getValue()) {
					batchOperation.insertOrMerge(
						new TableServiceEntity(
							AbstractEntite.supprimerCaracteresInterdits(entree.getKey()), 
							AbstractEntite.supprimerCaracteresInterdits(String.valueOf(codeSubstance))
						)
					);
					if (batchOperation.size() >= 100) {
						cloudTable.execute(batchOperation);
						batchOperation.clear();
					}
				}
				if (!batchOperation.isEmpty()) {
					cloudTable.execute(batchOperation);
				}
			}
			logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms");
		} 
		catch (StorageException
			| InvalidKeyException
			| URISyntaxException e)
		{
			Utils.logErreur(e, logger);
			return false;
		}
		return true;
	}

	private static HashSet<Long> rechercherSubstances (String recherche) {
		final String rechercheNorm = Utils.normaliser(recherche).toLowerCase();
		HashSet<Long> resultats = Optional
			.ofNullable(cacheRecherche.get(recherche))
			.orElseGet(HashSet::new);
		nomsSubstances.keySet().stream()
			.map(Utils::normaliser)
			.map(String::toLowerCase)
			.filter(nom -> nom.matches("(?i:.*" + rechercheNorm + ".*)"))
			.forEach(nom -> resultats.addAll(nomsSubstances.get(nom)));
		cacheRecherche.put(recherche, resultats);
		logger.fine(resultats.size() + " substances trouvées à la recherche : " + recherche);
		return resultats;
	}
	
	protected static HashMap<String, HashSet<Long>> importerSubstances (Logger logger) {
		HashMap<String, HashSet<Long>> resultats = new HashMap<>();
		try {
			for (EntiteSubstance entite : EntiteSubstance.obtenirToutesLesEntites()) {
				for (String nom : (Iterable<String>) () -> 
					entite.obtenirNomsJsonArray().toList()
						.stream()
						.map(String::valueOf)
						.iterator()
				) {
					if (!resultats.containsKey(nom)) {
						resultats.put(nom, new HashSet<>());
					}
					long codeSubstance = Long.parseLong(entite.getRowKey());
					resultats.get(nom).add(codeSubstance);
				}
			}
		}
		catch (StorageException
			| URISyntaxException
			| InvalidKeyException e) 
		{
			Utils.logErreur(e, logger);
			return new HashMap<>();
		}
		logger.info(resultats.size() + " noms de substances importés");
		return resultats;
	}
}