package app.mesmedicaments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Optional;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;

import app.mesmedicaments.entitestables.EntiteConnexion;

public class DMP {

	private final Logger LOGGER;
	private final String ID;

	protected DMP (String id, Logger logger) {
		this.ID = id;
		this.LOGGER = logger;
	}

	protected JSONObject obtenirMedicamentsRecents () throws IOException {
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
						medParDate.append(
							date, 
							trouverCorrespondanceMedicament(ligne.substring(10))
						);
					}
				}
				if (ligne.contains("Pharmacie / fournitures")) {
					alerte = false;
					balise = true;
				}
			}
			br.close();
			document.close();
			if (alerte) {} // faire quelque chose
		}
		else { LOGGER.info("Impossible de récupérer le fichier des remboursements"); }
		return medParDate;
	}

	private Optional<Integer> trouverCorrespondanceMedicament (String recherche) {
		HashMap<Integer, Double> classement = new HashMap<>();
		for (String mot : recherche.split(" ")) {
			mot = mot.toLowerCase();
			if (mot.equals("-")
				|| mot.equals("verre")
				|| mot.equals("monture"))
			{ break; }
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
				TreeSet<Integer> resultats = rechercherMedicament(mot, true);
				if (resultats.isEmpty()) { resultats = rechercherMedicament(mot, false); }
				if (classement.isEmpty()) { for (Integer resultat : resultats) { classement.put(resultat, 1.0); } }
				else { 
					for (Integer resultat : resultats) { 
						if (classement.containsKey(resultat)) { 
							classement.put(resultat, classement.get(resultat) + 1.0); 
						} 
					} 
				}
			}
		}
		double scoremax = 0;
		Integer meilleur = null;
		for (Integer membre : classement.keySet()) {
			if (classement.get(membre) >= scoremax) {
				scoremax = classement.get(membre);
				meilleur = membre;
			}
		}
		return Optional.ofNullable(meilleur);
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
			return Optional.of(PDDocument.load(connPDF.getInputStream()));
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

	///****** J'ai recopié la fonction telle quelle : à vérifier ******///
	///****** Importer les noms des médicaments ******///
	private static TreeSet<Integer> rechercherMedicament (String recherche, boolean precisematch) {
		/*
		TreeSet<Integer> trouves = new TreeSet<>();
		//HashSet<String> noms_uniquement = new HashSet<String>(noms.keySet()); 
		for (String mot : recherche.split(" ")) {
			TreeSet<Integer> correspondances = new TreeSet<>();
			for (String nom : noms.keySet()) {
				boolean match = false;		
				if (precisematch) { 
					if (Texte.normaliser(nom).matches("(?i:.*\\b" + mot + "\\b.*)")) { 
						match = true; 
					} 
				}
				else { 
					if (Texte.normaliser(nom).matches("(?i:.*\\b" + mot + ".*)")) { 
						match = true; 
					} 
				}
				////////// à modifier (ou pas)
				if (match && nom != null) {	correspondances.addAll(noms.get(nom)); }
			}
			trouves.addAll(correspondances);
		}
		return trouves;
		*/
		return null;
	}
}