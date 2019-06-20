package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.ClassesSubstancesService;
import app.mesmedicaments.entitestables.EntiteSubstance;

public final class MiseAJourClassesSubstances {

	private static final String URL_CLASSES;

	private static Logger logger;
	private static Map<String, Set<Long>> classes = new HashMap<>();
	private static Map<String, Set<Long>> nomsSubstancesNormalisesMin = new HashMap<>();
	private static Map<String, Set<Long>> cacheRecherche = new HashMap<>();

	static {
		URL_CLASSES = System.getenv("url_classes");
	}
		
	private MiseAJourClassesSubstances () {}

	public static boolean handler (Logger logger) {
		MiseAJourClassesSubstances.logger = logger;
		logger.info("Début de la mise à jour des classes de substances");
		nomsSubstancesNormalisesMin = importerSubstances(logger);
		if (nomsSubstancesNormalisesMin.isEmpty()) { return false; }
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
			HashSet<Long> substancesEnCours = new HashSet<>();
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
						for (long codesubstance : substancesEnCours) {
							classes.get(classeencours).add(codesubstance);
						}
						nbrClassesTrouvees += 1;
					}
					substancesEnCours = new HashSet<>();
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
								rechercherSubstances(substance)
									.forEach(substancesEnCours::add);
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
			long startTime = System.currentTimeMillis();
			for (Entry<String, Set<Long>> entree : classes.entrySet()) {
				ClassesSubstancesService.mettreAJourClasseBatch(
					entree.getKey(),
					entree.getValue()
				);				
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

	private static Set<Long> rechercherSubstances (String recherche) {
		final String rechercheNorm = Utils.normaliser(recherche)
			.replaceAll("  ", " ")
			.toLowerCase()
			.trim();
		Set<Long> resultats = Optional
			.ofNullable(cacheRecherche.get(recherche))
			.orElseGet(HashSet::new);
		nomsSubstancesNormalisesMin.keySet().stream()
			.filter(nom -> nom.matches("(?i:.*" + rechercheNorm + ".*)"))
			.forEach(nom -> resultats.addAll(nomsSubstancesNormalisesMin.get(nom)));
		cacheRecherche.put(recherche, resultats);
		logger.fine(resultats.size() + " substances trouvées à la recherche : " + recherche);
		return resultats;
	}
	
	protected static HashMap<String, Set<Long>> importerSubstances (Logger logger) {
		HashMap<String, Set<Long>> resultats = new HashMap<>();
		try {
			for (EntiteSubstance entite : EntiteSubstance.obtenirToutesLesEntites()) {
				for (String nom : (Iterable<String>) () -> 
					entite.obtenirNomsJArray().toList()
						.stream()
						.map(String::valueOf)
						.map(Utils::normaliser)
						.map(String::toLowerCase)
						.iterator()
				) {
					if (!resultats.containsKey(nom)) {
						resultats.put(nom, new HashSet<>());
					}
					long codeSubstance = entite.obtenirCodeSubstance();
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