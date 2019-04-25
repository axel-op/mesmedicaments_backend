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
import org.json.JSONObject;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteConnexion;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteUtilisateur;

public class DMP {

	private static Map<String, Set<Long>> nomsMedicamentsNormalises = Collections.emptyMap();
	private static Map<String, Set<Long>> cacheRecherche = new HashMap<>();

	private static Map<String, Set<Long>> importerNomsMedicamentsNormalises () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		long startTime = System.currentTimeMillis();
		Map<String, Set<Long>> nomsMed = new HashMap<>();
		for (EntiteMedicament entite : EntiteMedicament.obtenirToutesLesEntites()) {
			entite.obtenirNomsJArray().forEach(
				nom -> nomsMed
					.computeIfAbsent(Utils.normaliser(nom.toString()), cle -> new TreeSet<>())
					.add(Long.parseLong(entite.getRowKey()))
			);
		}
		return nomsMed;
	}

	private final Logger LOGGER;
	private final String ID;

	public DMP (String id, Logger logger) {
		this.ID = id;
		this.LOGGER = logger;
	}

	public JSONObject obtenirMedicamentsRecents () 
		throws IOException, StorageException, URISyntaxException, InvalidKeyException
	{
		JSONObject medParDate = new JSONObject();
		Optional<PDDocument> optional = obtenirFichierRemboursements();
		if (optional.isPresent()) {
			PDDocument document = optional.get();
			PDFTextStripper stripper = new PDFTextStripper();
			BufferedReader br = new BufferedReader(
				new StringReader(
					new String(
						stripper.getText(document).getBytes(),
						Charset.forName("ISO-8859-1")
					)
				)
			);
			String ligne;
			boolean balise = false;
			boolean alerte = true; // Si pas de section Pharmacie trouvée
			while ((ligne = br.readLine()) != null) {
				if (ligne.contains("Hospitalisation")) { balise = false; }
				if (balise) {
					if (ligne.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}.*")) {
						String date = ligne.substring(0, 10);
						String recherche = ligne.substring(0, 10);
						if (!recherche.matches(" *")) {
							Optional<Long> resultat = trouverCorrespondanceMedicament(ligne.substring(10));
							if (resultat.isPresent()) {
								medParDate.append(date, resultat.get());
							}
						}
					}
				}
				if (ligne.contains("Pharmacie / fournitures")) {
					alerte = false;
					balise = true;
				}
			}
			document.close();
			br.close();
			if (alerte) {} // faire quelque chose
		}
		else { LOGGER.info("Impossible de récupérer le fichier des remboursements"); }
		try {
			EntiteUtilisateur entiteU = EntiteUtilisateur.obtenirEntite(ID);
			entiteU.definirMedicamentsRecentsJObject(medParDate);
			entiteU.mettreAJourEntite();
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e)
		{
			Utils.logErreur(e, LOGGER);
			LOGGER.warning("Impossible de mettre à jour les médicaments récents pour l'utilisateur " + ID);
		}
		return medParDate;
	}

	private Optional<Long> trouverCorrespondanceMedicament (String recherche) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
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

	private Optional<PDDocument> obtenirFichierRemboursements () {
		try {
			EntiteConnexion entite = EntiteConnexion.obtenirEntite(ID);
			HashMap<String, String> cookies = entite.obtenirCookiesMap();
			String urlFichier = entite.getUrlFichierRemboursements();
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
		catch (URISyntaxException | InvalidKeyException e) {
			LOGGER.warning("Impossible de récupérer l'entité Connexion");
			Utils.logErreur(e, LOGGER);
		}
		return Optional.empty();
	}

	private static final BiFunction<String, Boolean, String> obtenirRegex = (mot, precisematch) -> {
		if (precisematch) { return "(?i:.*\\b" + mot + "\\b.*)"; }
		else { return "(?i:.*\\b" + mot + ".*)"; }
	};

	private static Set<Long> rechercherMedicament (String recherche, boolean precisematch) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		if (nomsMedicamentsNormalises.isEmpty()) { 
			nomsMedicamentsNormalises = importerNomsMedicamentsNormalises(); 
		}
		return cacheRecherche.computeIfAbsent(recherche, exp -> {
			final String expNorm = Utils.normaliser(exp).toLowerCase();
			return nomsMedicamentsNormalises.keySet().stream()
				.filter(nom -> {
					for (String mot : expNorm.split(" ")) {
						if (nom
							.toLowerCase()
							.matches(obtenirRegex.apply(mot, precisematch))
						) { return true; }
					}
					return false;
				})
				.flatMap(nom -> nomsMedicamentsNormalises.get(nom).stream())
				.collect(Collectors.toSet());
		});
	}
}