package app.mesmedicaments.misesajour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.common.base.Functions;
import com.microsoft.azure.storage.StorageException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteDateMaj;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.unchecked.Unchecker;

/**
 * Met à jour la base de données à partir des informations récupérées sur la
 * base publique des médicaments.
 */
public final class MiseAJourFrance {

	private static final String URL_FICHIER_BDPM;
	private static final String URL_FICHIER_COMPO;
	private static final String URL_FICHIER_PRESENTATIONS;
	private static Logger logger;
	private static Map<Long, EntiteMedicamentFrance> entitesMedsEnCours;

	static {
		URL_FICHIER_BDPM = System.getenv("url_cis_bdpm");
		URL_FICHIER_COMPO = System.getenv("url_cis_compo_bdpm");
		URL_FICHIER_PRESENTATIONS = System.getenv("url_cis_cip_bdpm");
	}

	private MiseAJourFrance() {
	}

	public static boolean handler(Logger logger) {
		MiseAJourFrance.logger = logger;
		if (!majSubstances())
			return false;
		if (!majMedicaments())
			return false;
		try {
			EntiteDateMaj.definirDateMajFrance();
		} catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
		}
		return true;
	}

	private static boolean majSubstances() {
		logger.info("Début de la mise à jour des substances");
		BufferedReader listeSubstances = importerFichier(URL_FICHIER_COMPO);
		if (listeSubstances == null) {
			return false;
		}
		HashMap<Long, HashSet<String>> substancesCodesNoms = new HashMap<>();
		HashMap<Long, Set<AbstractEntiteMedicament.SubstanceActive>> medSubstances = new HashMap<>();
		try {
			logger.info("Parsing en cours...");
			String ligne;
			long startTime = System.currentTimeMillis();
			while ((ligne = listeSubstances.readLine()) != null) {
				String[] elements = ligne.split("\t");
				long codecis = Long.parseLong(elements[0]);
				long codesubstance = Long.parseLong(elements[2]);
				String nom = elements[3].trim();
				String dosage = "";
				String refDosage = "";
				try {
					dosage = elements[4];
					refDosage = elements[5];
				} catch (NullPointerException e) {
				}
				substancesCodesNoms.computeIfAbsent(codesubstance, cle -> new HashSet<>()).add(nom);
				medSubstances.computeIfAbsent(codecis, k -> new HashSet<>())
						.add(new AbstractEntiteMedicament.SubstanceActive(codesubstance, dosage, refDosage));
			}
			double total = substancesCodesNoms.size();
			logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms. " + ((int) total)
					+ " substances trouvées.");
			logger.info("Création des entités en cours...");
			HashSet<EntiteSubstance> entitesSubstances = new HashSet<>();
			HashSet<EntiteMedicamentFrance> entitesMedicaments = new HashSet<>();
			startTime = System.currentTimeMillis();
			for (Entry<Long, HashSet<String>> entree : substancesCodesNoms.entrySet()) {
				EntiteSubstance entite = new EntiteSubstance(Pays.France, entree.getKey());
				entite.ajouterNoms(Langue.Francais, entree.getValue());
				entitesSubstances.add(entite);
			}
			for (Entry<Long, Set<AbstractEntiteMedicament.SubstanceActive>> entree : medSubstances.entrySet()) {
				EntiteMedicamentFrance entite = new EntiteMedicamentFrance(entree.getKey());
				entite.setSubstancesActivesIterable(entree.getValue());
				entitesMedicaments.add(entite);
			}
			logger.info(entitesSubstances.size() + " entités Substance" + " et " + entitesMedicaments.size()
					+ " entités Médicament" + " créées en " + Utils.tempsDepuis(startTime) + " ms. ");
			logger.info("Mise à jour des entités Substance en cours...");
			startTime = System.currentTimeMillis();
			EntiteSubstance.mettreAJourEntitesBatch(entitesSubstances);
			logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms");
			entitesMedsEnCours = entitesMedicaments.stream()
					.collect(Collectors.toMap(e -> e.getCodeMedicament(), Functions.identity()));
		} catch (IOException | StorageException | InvalidKeyException | URISyntaxException e) {
			Utils.logErreur(e, logger);
			return false;
		}
		return true;
	}

	private static boolean majMedicaments() {
		logger.info("Début de la mise à jour des médicaments");
		BufferedReader listeMedicaments = importerFichier(URL_FICHIER_BDPM);
		if (listeMedicaments == null) {
			return false;
		}
		HashMap<Long, HashSet<String>> nomsMed = new HashMap<>();
		HashMap<Long, String[]> caracMed = new HashMap<>();
		try {
			logger.info("Parsing en cours...");
			String ligne;
			long startTime = System.currentTimeMillis();
			while ((ligne = listeMedicaments.readLine()) != null) {
				String[] elements = ligne.split("\t");
				long codecis = Long.parseLong(elements[0]);
				String nom = elements[1].trim();
				String forme = elements[2].trim();
				String autorisation = elements[4].trim();
				String marque = elements[10].trim();
				if (nomsMed.get(codecis) == null) {
					nomsMed.put(codecis, new HashSet<>());
				}
				nomsMed.get(codecis).add(nom);
				caracMed.put(codecis, new String[] { forme, autorisation, marque });
			}
			double total = nomsMed.size();
			logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms. " + ((int) total)
					+ " médicaments trouvés.");
			Map<Long, Set<EntiteMedicamentFrance.PresentationFrance>> presentations = obtenirPresentations(logger);
			logger.info("Mise à jour des entités précédemment créées en cours...");
			// HashSet<EntiteMedicamentFrance> entites = new HashSet<>();
			startTime = System.currentTimeMillis();
			for (Entry<Long, HashSet<String>> entree : nomsMed.entrySet()) {
				long codecis = entree.getKey();
				EntiteMedicamentFrance entite = Optional.ofNullable(entitesMedsEnCours.get(codecis))
						.orElseGet(() -> new EntiteMedicamentFrance(codecis));
				entite.ajouterNoms(Langue.Francais, entree.getValue());
				entite.setForme(caracMed.get(codecis)[0]);
				entite.setAutorisation(caracMed.get(codecis)[1]);
				entite.setMarque(caracMed.get(codecis)[2]);
				entite.setPresentationsIterable(presentations.get(codecis));
			}
			logger.info("Mise à jour de " + entitesMedsEnCours.size() + " entités Médicament en cours...");
			startTime = System.currentTimeMillis();
			AbstractEntiteMedicament.mettreAJourEntitesBatch(entitesMedsEnCours.values());
			logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms. ");
		} catch (StorageException | InvalidKeyException | URISyntaxException | IOException e) {
			Utils.logErreur(e, logger);
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param logger
	 * @return Map avec en clé les codes CIS, en valeur un JSONObject avec pour clés
	 *         les présentations
	 */
	private static Map<Long, Set<EntiteMedicamentFrance.PresentationFrance>> obtenirPresentations(Logger logger) {
		ConcurrentMap<Long, Set<EntiteMedicamentFrance.PresentationFrance>> presentations = new ConcurrentHashMap<>();
		logger.info("Récupération des presentations");
		long startTime = System.currentTimeMillis();
		importerFichier(URL_FICHIER_PRESENTATIONS).lines().parallel().forEach((ligne) -> {
			String[] elements = ligne.split("\t");
			Long codeCis = Long.parseLong(elements[0]);
			String presentation = elements[2];
			Double prixPres = null;
			Double honoraires = null;
			Integer tauxRbst = 0;
			String conditions = "";
			try {
				tauxRbst = Integer.parseInt(elements[8].replaceFirst(" ?%", ""));
				prixPres = formaterPrix(elements[10]);
				honoraires = formaterPrix(elements[11]);
				conditions = elements[12];
			} catch (NullPointerException | NumberFormatException e) {
				System.out.println("NULL OU NUMBER " + ligne);
			} catch (ArrayIndexOutOfBoundsException e) {
			}
			if (prixPres == null)
				prixPres = 0.0;
			if (honoraires == null)
				honoraires = 0.0;
			presentations.computeIfAbsent(codeCis, (k) -> new HashSet<>())
					.add(new EntiteMedicamentFrance.PresentationFrance(presentation, prixPres, tauxRbst, honoraires,
							conditions));
		});
		logger.info("Présentations récupérées en " + Utils.tempsDepuis(startTime) + " ms");
		return presentations;
	}

	public static void importerEffetsIndesirables(Logger logger)
			throws StorageException, URISyntaxException, InvalidKeyException {
		Set<EntiteMedicamentFrance> entitesMedicaments = StreamSupport
				.stream(EntiteMedicamentFrance.obtenirToutesLesEntites().spliterator(), false)
				.collect(Collectors.toSet());
		AtomicInteger compteur = new AtomicInteger(0);
		int total = entitesMedicaments.size();
		entitesMedicaments.stream().parallel().forEach(Unchecker.wrap(logger, (EntiteMedicamentFrance entiteM) -> {
			final String url = "http://base-donnees-publique.medicaments.gouv.fr/affichageDoc.php?specid="
					+ entiteM.getCodeMedicament() + "&typedoc=N";
			Document rep = Jsoup.parse(new URL(url).openStream(), "ISO-8859-1", url);
			String texte = "";
			Boolean balise = null;
			for (Element el : rep.getAllElements()) {
				if (balise != null && !balise)
					break;
				if (el.hasAttr("name") && el.attr("name").contains("EffetsIndesirables"))
					balise = true;
				if (balise != null && balise) {
					if (!el.tagName().equals("h2")) {
						if (el.tagName().equals("p")) {
							if (texte.length() > 0)
								texte += Utils.NEWLINE;
							texte += el.text();
						}
					} else
						balise = false;
				}
			}
			entiteM.setEffetsIndesirables(texte);
			entiteM.mettreAJourEntite();
			logger.info(compteur.incrementAndGet() + "/" + total);
		}));
	}

	private static Double formaterPrix(String prix) {
		if (prix.contains(",")) {
			int virCents = prix.length() - 3;
			prix = prix.substring(0, virCents) + "." + prix.substring(virCents);
			prix = prix.replaceAll(",", "");
		}
		if (!prix.equals(""))
			return Double.parseDouble(prix);
		return null;
	}

	private static BufferedReader importerFichier(String url) {
		BufferedReader br = null;
		try {
			logger.info("Récupération du fichier (url = " + url + ")");
			HttpURLConnection connexion = (HttpURLConnection) new URL(url).openConnection();
			connexion.setRequestMethod("GET");
			logger.info("Fichier de la base à télécharger atteint");
			InputStreamReader isr = new InputStreamReader(connexion.getInputStream());
			br = new BufferedReader(isr);
			// log("Encodage du fichier récupéré : " + isr.getEncoding());
		} catch (IOException e) {
			Utils.logErreur(e, logger);
			;
			return null;
		}
		return br;
	}
}
