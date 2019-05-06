package app.mesmedicaments.connexion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.*;

public class DMP {

	private static Map<String, Set<Long>> nomsMedicamentsNormalisesMin = Collections.emptyMap();
	private static Map<String, Set<Long>> cacheRecherche = new HashMap<>();

	private static Map<String, Set<Long>> importerNomsMedicamentsNormalisesMin () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Map<String, Set<Long>> nomsMed = new HashMap<>();
		for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
			entite.obtenirNomsJArray().forEach(
				nom -> nomsMed
					.computeIfAbsent(Utils.normaliser(nom.toString()).toLowerCase(), cle -> new TreeSet<>())
					.add(Long.parseLong(entite.getRowKey()))
			);
		}
		return nomsMed;
	}

	private static final BiFunction<String, Boolean, String> obtenirRegex = (mot, precisematch) -> {
		if (precisematch) { return "(?i:.*\\b" + mot + "\\b.*)"; }
		else { return "(?i:.*\\b" + mot + ".*)"; }
	};

	private static Set<Long> rechercherMedicament (String recherche, boolean precisematch) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (nomsMedicamentsNormalisesMin.isEmpty()) { 
			nomsMedicamentsNormalisesMin = importerNomsMedicamentsNormalisesMin(); 
		}
		return cacheRecherche.computeIfAbsent(recherche, exp -> {
			final String expNorm = Utils.normaliser(exp).toLowerCase();
			final String[] mots = expNorm.split(" ");
			return nomsMedicamentsNormalisesMin.keySet().stream()
				.filter(nom -> {
					for (String mot : mots) {
						if (nom.matches(obtenirRegex.apply(mot, precisematch))) { 
							return true; 
						}
					}
					return false;
				})
				.flatMap(nom -> nomsMedicamentsNormalisesMin.get(nom).stream())
				.collect(Collectors.toSet());
		});
	}

	private final Logger LOGGER;
	private final String ID;

	public DMP (String id, Logger logger) {
		this.ID = id;
		this.LOGGER = logger;
	}

	public void mettreAJourUtilisateur () throws StorageException, URISyntaxException, InvalidKeyException {
		EntiteConnexion entiteC = EntiteConnexion.obtenirEntiteAboutie(ID).get();
		String urlFichier = entiteC.getUrlFichierRemboursements();
		Map<String, String> cookies = entiteC.obtenirCookiesMap();
		String id = entiteC.getRowKey();
		Optional<PDDocument> fichier = obtenirFichierRemboursements(urlFichier, cookies);
		if (fichier.isPresent()) {
			try {
				JSONObject medicaments = obtenirMedicaments(fichier.get());
				EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(id);
				// TODO : vérifier jusqu'à quand la connexion tient et aviser
				if (!medicaments.isEmpty() || entiteU.getMedicaments() == null) {
					entiteU.definirMedicamentsJObject(medicaments);
				}
				entiteU.mettreAJourEntite();
				entiteC.setTentatives(0);
			}
			catch (IOException | StorageException | URISyntaxException | InvalidKeyException e) {
				entiteC.setTentatives(entiteC.getTentatives() + 1);
				if (entiteC.getTentatives() >= 5) {
					entiteC.marquerCommeEchouee();
				}
				else {
					int nouvellePartition = Integer.parseInt(entiteC.getPartitionKey()) + 10;
					entiteC.setPartitionKey(String.valueOf(nouvellePartition));
				}
			}
			entiteC.mettreAJourEntite();
		}
		else { 
			/* TODO : grave erreur, notifier */ 
			entiteC.marquerCommeEchouee();
		}
	}

	private JSONObject obtenirMedicaments (PDDocument fichierRemboursements) 
		throws IOException, StorageException, URISyntaxException, InvalidKeyException
	{
		JSONObject medParDate = new JSONObject();
		PDFTextStripper stripper = new PDFTextStripper();
		BufferedReader br = new BufferedReader(
			new StringReader(
				new String(
					stripper.getText(fichierRemboursements).getBytes(),
					Charset.forName("ISO-8859-1")
				)
			)
		);
		String ligne;
		boolean balise = false;
		boolean alerte = true; // Si pas de section Pharmacie trouvée
		while ((ligne = br.readLine()) != null) {
			LOGGER.info("Debug : ligne = " + ligne);
			if (ligne.contains("Hospitalisation")) { balise = false; }
			if (balise) {
				if (ligne.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}.*")) {
					String date = ligne.substring(0, 10);
					String recherche = ligne.substring(0, 10);
					if (!recherche.matches(" *")) {
						Optional<Long> resultat = trouverCorrespondanceMedicament(ligne.substring(10));
						if (resultat.isPresent()) {
							if (!medParDate.has(date)) { medParDate.put(date, new JSONArray()); }
							medParDate.getJSONArray(date).put(resultat.get());
						}
					}
				}
			}
			if (ligne.contains("Pharmacie / fournitures")) {
				alerte = false;
				balise = true;
			}
		}
		fichierRemboursements.close();
		br.close();
		if (alerte) {} // TODO : alerter
		LOGGER.info("Debug : taille medParDate = " + medParDate.length());
		return medParDate;
	}

	private Optional<Long> trouverCorrespondanceMedicament (String recherche) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (recherche.matches(" *")) { return Optional.empty(); }
		HashMap<Long, Double> classement = new HashMap<>();
		boolean devraitTrouver = true;
		for (String mot : recherche.split(" ")) {
			mot = mot.toLowerCase();
			if (mot.equals("-")
				|| mot.equals("verre")
				|| mot.equals("monture"))
			{
				devraitTrouver = false;
				break; 
			}
			if (mot.matches("[0-9,].*")) { mot = mot.split("[^0-9,]")[0]; }
			if (mot.matches("[^0-9]+[0-9].*")) { mot = mot.split("[0-9]")[0]; }
			if (mot.equals("mg")) { mot = ""; }
			switch (mot) {
				case "myl": mot = "mylan";
							break;
				case "sdz": mot = "sandoz";
							break;
				case "bga": mot = "biogaran";
							break;
				case "tvc": mot = "teva";
							break;
				case "sol": mot = "solution";
							break;
				case "solbu": 
							mot = "solution buvable";
							break;
				case "cpr": mot = "comprimé";
							break;
				case "eff": mot = "effervescent";
							break;
				case "inj": mot = "injectable";
							break;
				case "ser": mot = "seringue";
							break;
			}
			if (!mot.equals("")) {
				Set<Long> resultats = rechercherMedicament(mot, true);
				if (resultats.isEmpty()) { resultats = rechercherMedicament(mot, false); }
				if (classement.isEmpty()) { 
					resultats.forEach(resultat -> classement.put(resultat, 1.0)); 
				}
				else { 
					resultats.stream()
						.filter(resultat -> classement.containsKey(resultat))
						.forEach(resultat -> classement.put(resultat, classement.get(resultat) + 1.0));
				}
			}
		}
		if (classement.isEmpty()) { 
			if (devraitTrouver) {
				LOGGER.info("Pas de médicament trouvé pour : " + recherche);
			}
			return Optional.empty(); 
		}
		final double scoremax = classement.values().stream()
			.max(Double::compare)
			.get();
		return classement.entrySet().stream()
			.filter(entree -> entree.getValue() == scoremax)
			.map(Entry::getKey)
			.findFirst();
	}

	private Optional<PDDocument> obtenirFichierRemboursements (String urlFichier, Map<String, String> cookies) {
		try {
			//EntiteConnexion entite = EntiteConnexion.obtenirEntite(ID);
			//HashMap<String, String> cookies = entite.obtenirCookiesMap();
			//String urlFichier = entite.getUrlFichierRemboursements();
			HttpsURLConnection connPDF = (HttpsURLConnection) new URL(urlFichier).openConnection();
			connPDF.setRequestMethod("GET");
			for (String cookie : cookies.keySet()) { 
				connPDF.addRequestProperty("Cookie", cookie + "=" + cookies.get(cookie) + "; "); 
			}
			PDDocument document = PDDocument.load(connPDF.getInputStream());
			return Optional.of(document);
		}
		catch (IOException e) {
			LOGGER.warning("Problème de connexion au fichier des remboursements");
			Utils.logErreur(e, LOGGER);
		}
		return Optional.empty();
	}
}