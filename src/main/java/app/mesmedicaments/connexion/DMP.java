package app.mesmedicaments.connexion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.collect.Sets;
import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.*;

public class DMP {

	private static ConcurrentMap<String, Set<Long>> nomsMedicamentsNormalisesMin = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, Set<Long>> cacheRecherche = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, Optional<Long>> cacheCorrespondances = new ConcurrentHashMap<>();
	private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static Map<String, Set<Long>> importerNomsMedicamentsNormalisesMin () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Map<String, Set<Long>> nomsMed = new HashMap<>();
		for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
			entite.obtenirNomsJArray().forEach(
				nom -> nomsMed
					.computeIfAbsent(
						Utils.normaliser(nom.toString())
						.replaceAll("  ", " ")
						.toLowerCase()
						.trim(), 
						cle -> new HashSet<>()
					)
					.add(entite.obtenirCodeCis())
			);
		}
		return nomsMed;
	}

	private static final BiFunction<String, Boolean, String> obtenirRegex = (mot, precisematch) -> {
		if (precisematch) { return "(?i:.*\\b" + mot + "\\b.*)"; }
		else { return "(?i:.*\\b" + mot + ".*)"; }
	};

	private static Set<Long> rechercherMedicament (String recherche) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return cacheRecherche.computeIfAbsent(recherche, exp -> {
			final String expNorm = Utils.normaliser(exp)
				.replaceAll("  ", " ")
				.toLowerCase()
				.trim();
			final String[] mots = expNorm.split(" ");
			Set<Long> pmTrue = Sets.newConcurrentHashSet();
			Set<Long> pmFalse = Sets.newConcurrentHashSet();
			nomsMedicamentsNormalisesMin.keySet().stream().parallel()
				.forEach(nom -> {
					for (String mot : mots) {
						if (nom.matches(obtenirRegex.apply(mot, true))) {
							pmTrue.addAll(nomsMedicamentsNormalisesMin.get(nom));
						}
						else if (pmTrue.isEmpty() && nom.matches(obtenirRegex.apply(mot, false))) {
							pmFalse.addAll(nomsMedicamentsNormalisesMin.get(nom));
						}
					}
				});
			if (pmTrue.isEmpty()) return pmFalse;
			return pmTrue;
		});
	}

	private final Logger LOGGER;
	//private final String ID;
	private final String URL_FICHIER_REMBOURSEMENTS;
	private Map<String, String> cookies;

	public DMP (String urlFichierRemboursements, Map<String, String> cookies, Logger logger) {
		this.LOGGER = logger;
		this.URL_FICHIER_REMBOURSEMENTS = urlFichierRemboursements;
		this.cookies = cookies;
	}

	public JSONObject obtenirMedicaments (Logger logger) 
		throws IOException, StorageException, URISyntaxException, InvalidKeyException
	{
		PDDocument fichierRemboursements = obtenirFichierRemboursements().get(); // TODO : gérer le cas où Optional est vide
		PDFTextStripper stripper = new PDFTextStripper();
		BufferedReader br = new BufferedReader(
			new StringReader(
				new String(
					stripper.getText(fichierRemboursements).getBytes(),
					Charset.forName("ISO-8859-1")
				)
			)
		);
		Map<LocalDate, Set<String>> aChercher = new HashMap<>();
		String ligne;
		boolean balise = false;
		boolean alerte = true; // Si pas de section Pharmacie trouvée
		while ((ligne = br.readLine()) != null) {
			if (ligne.contains("Hospitalisation")) { balise = false; }
			if (balise) {
				if (ligne.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}.*")) {
					LocalDate date = parserDate(ligne.substring(0, 10));
					if (!ligne.substring(0, 10).matches(" *")) {
						aChercher.computeIfAbsent(date, k -> new HashSet<>())
							.add(ligne.substring(10));
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
		if (nomsMedicamentsNormalisesMin.isEmpty()) {
			nomsMedicamentsNormalisesMin.putAll(importerNomsMedicamentsNormalisesMin());
		}
		ConcurrentMap<LocalDate, Set<Long>> resultats = new ConcurrentHashMap<>();
		aChercher.entrySet().stream().parallel()
			.forEach((entree) -> {
				Set<Long> corr = entree.getValue().stream().parallel()
					.map((terme) -> {
						try { return trouverCorrespondanceMedicament(terme); }
						catch (StorageException | URISyntaxException | InvalidKeyException e) {
							Utils.logErreur(e, logger);
							throw new RuntimeException();
						}
					})
					.filter((opt) -> opt.isPresent())
					.map((opt) -> opt.get())
					.collect(Collectors.toSet());
				resultats.put(entree.getKey(), corr);
			});
		JSONObject medParDate = new JSONObject();
		resultats.forEach((date, codes) -> medParDate.put(date.toString(), new JSONArray(codes)));
		if (alerte) {} // TODO : alerter
		return medParDate;
	}

	private Optional<Long> trouverCorrespondanceMedicament (String recherche) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Optional<Long> cache = cacheCorrespondances.get(recherche);
		if (cache != null) return cache;
		if (recherche.matches(" *")) { return Optional.empty(); }
		HashMap<Long, Double> classement = new HashMap<>();
		boolean devraitTrouver = true;
		for (String mot : recherche.split(" ")) {
			mot = mot.toLowerCase();
			if (mot.equals("-") // ? ne devrait pas aller avec les deux lignes du dessous
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
				Set<Long> resultats = rechercherMedicament(mot);
				//if (resultats.isEmpty()) { resultats = rechercherMedicament(mot, false); }
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
			cacheCorrespondances.put(recherche, Optional.empty());
			return Optional.empty(); 
		}
		final double scoremax = classement.values().stream()
			.max(Double::compare)
			.get();
		Optional<Long> resultat = classement.entrySet().stream()
			.filter(entree -> entree.getValue() == scoremax)
			.map(Entry::getKey)
			.findFirst();
		cacheCorrespondances.put(recherche, resultat);
		return resultat;
	}

	private Optional<PDDocument> obtenirFichierRemboursements () {
		try {
			HttpsURLConnection connPDF = (HttpsURLConnection) new URL(URL_FICHIER_REMBOURSEMENTS).openConnection();
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

	private LocalDate parserDate (String date) 
		throws DateTimeParseException
	{
		return LocalDate.parse(date, dateFormatter);
	}
}