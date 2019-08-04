package app.mesmedicaments.connexion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.*;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.unchecked.Unchecker;

public class DMP {

	private static ConcurrentMap<String, Set<Long>> nomsMedicamentsNormalisesMin = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, String> cacheNormalisation = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, Set<Long>> cacheRecherche = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, Optional<Long>> cacheCorrespondances = new ConcurrentHashMap<>();
	private static ConcurrentMap<String, String> cacheTransformationMot = new ConcurrentHashMap<>();
	private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final Function<String, String> normaliser = nom -> cacheNormalisation
		.computeIfAbsent(nom, k -> Utils.normaliser(nom)
			.replaceAll("  ", " ")
			.toLowerCase()
			.trim());

	private static Map<String, Set<Long>> importerNomsMedicamentsNormalisesMin () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		ConcurrentMap<String, Set<Long>> nomsMed = new ConcurrentHashMap<>();
		for (EntiteMedicamentFrance entite : EntiteMedicamentFrance.obtenirToutesLesEntites()) {
			//StreamSupport.stream(entite.obtenirNomsJArray().spliterator(), true)
			entite.getNomsLangue(Langue.Francais).stream()
				.map(nom -> normaliser.apply(nom.toString()))
				.forEach(nom -> nomsMed.computeIfAbsent(nom, k -> new HashSet<>())
					.add(entite.getCodeMedicament())
				);
		}
		return nomsMed;
	}

	private static final BiFunction<String, Boolean, String> obtenirRegex = (mot, precisematch) -> {
		if (precisematch) { return "(?i:.*\\b" + mot + "\\b.*)"; }
		else { return "(?i:.*\\b" + mot + ".*)"; }
	};

	private static Set<Long> rechercherMedicament (String mot) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		return cacheRecherche.computeIfAbsent(mot, exp -> {
			final String expNorm = normaliser.apply(exp);
			Set<Long> pmTrue = Sets.newConcurrentHashSet();
			Set<Long> pmFalse = Sets.newConcurrentHashSet();
			nomsMedicamentsNormalisesMin.keySet().stream().parallel()
				.forEach(nomMed -> {	
					if (nomMed.matches(obtenirRegex.apply(expNorm, true))) {
						pmTrue.addAll(nomsMedicamentsNormalisesMin.get(nomMed));
					}
					else if (pmTrue.isEmpty() && nomMed.matches(obtenirRegex.apply(expNorm, false))) {
						pmFalse.addAll(nomsMedicamentsNormalisesMin.get(nomMed));
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

	public Map<LocalDate, Set<Long>> obtenirMedicaments (Logger logger) 
		throws IOException, StorageException, URISyntaxException, InvalidKeyException
	{
		PDDocument fichierRemboursements = obtenirFichierRemboursements().get(); // TODO : gérer le cas où Optional est vide
		PDFTextStripper stripper = new PDFTextStripper();
		BufferedReader br = new BufferedReader(
			new StringReader(
				new String(
					stripper.getText(fichierRemboursements).getBytes(),
					StandardCharsets.ISO_8859_1
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
		Map<LocalDate, Set<Long>> resultats = aChercher.entrySet()
			.parallelStream()
			.collect(Collectors.toMap(
				entree -> entree.getKey(),
				entree -> entree.getValue().parallelStream()
					// TODO tester
					.flatMap((terme) -> trouverCorrespondanceMedicament(terme, logger)
						.map(Stream::of)
						.orElseGet(Stream::empty))
					//.filter((opt) -> opt.isPresent())
					//.map((opt) -> opt.get())
					.collect(Collectors.toSet())
			));
		if (alerte) {} // TODO : alerter
		return resultats;
	}

	private static final Function<String, String> transformerMot = leMot -> cacheTransformationMot
		.computeIfAbsent(leMot, mot -> {
			if (mot.matches("[0-9,].*")) return mot.split("[^0-9,]")[0];
			if (mot.matches("[^0-9]+[0-9].*")) return mot.split("[0-9]")[0];
			if (mot.equals("mg") || mot.equals("-")) return "";
			switch (mot) {
				case "myl": return "mylan";
				case "sdz": return "sandoz";
				case "bga": return "biogaran";
				case "tvc": return "teva";
				case "sol": return "solution";
				case "solbu": return "solution buvable";
				case "cpr": return "comprimé";
				case "eff": return "effervescent";
				case "inj": return "injectable";
				case "ser": return "seringue";
			}
			return mot;
		});

	private Optional<Long> trouverCorrespondanceMedicament (String recherche, Logger logger) {
		Optional<Long> cache = cacheCorrespondances.get(recherche);
		if (cache != null) return cache;
		if (recherche.matches(" *")) { return Optional.empty(); }
		ConcurrentMap<Long, Double> classement = new ConcurrentHashMap<>();
		AtomicBoolean devraitTrouver = new AtomicBoolean(true);
		Streams.stream(Arrays.asList(recherche.split(" ")).iterator())
			.parallel()
			.forEach(Unchecker.wrap(logger, mot -> {
				mot = mot.toLowerCase();
				if (mot.equals("verre")
					|| mot.equals("monture"))
				{ devraitTrouver.set(false); }
				if (devraitTrouver.get()) {
					mot = transformerMot.apply(mot);
					if (!mot.equals("")) {
						Set<Long> resultats = rechercherMedicament(mot);
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
			}));
		if (classement.isEmpty()) { 
			if (devraitTrouver.get()) {
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