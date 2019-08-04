package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.collect.Sets;
import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteDateMaj;
import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteSubstance;

public final class MiseAJourInteractions {

    private static final Charset CHARSET_1252 = Charset.forName("cp1252");
    private static final float TAILLE_NOM_SUBSTANCE = (float) 10;
	private static final float TAILLE_INTERACTION_SUBSTANCE = (float) 8;
	private static final float TAILLE_DESCRIPTION_PT = (float) 6;
	private static final String URL_FICHIER_INTERACTIONS = System.getenv("url_interactions");
	private static final AtomicBoolean executionEnCours = new AtomicBoolean();
	private static final String REGEX_RISQUE1 = "((?i:((a|à) prendre en compte))|(.*APEC))";
	private static final String REGEX_RISQUE2 = "((?i:pr(e|é)caution d'emploi)|(.*PE))";
	private static final String REGEX_RISQUE3 = "((?i:(association d(e|é)conseill(e|é)e))|(.*ASDEC))";
	private static final String REGEX_RISQUE4 = "((?i:(contre(-| )indication))|(.*CI))";
	private static final String[] regexRisques = new String[] { REGEX_RISQUE1, REGEX_RISQUE2, REGEX_RISQUE3, REGEX_RISQUE4 };

	private static final Map<String, Set<EntiteSubstance>> correspondancesSubstances = new HashMap<>();
	private static final Map<String, Set<String>> classesSubstances = new HashMap<>();
	private static final Map<String, EntiteInteraction> entitesCreeesParCle = new HashMap<>();
	private static final List<String> substances2EnCours = new ArrayList<>(); // doit maintenir l'ordre
	private static final Set<InteractionBrouillon> interactionsBrouillon = new HashSet<>();
	private static final Map<String, Set<EntiteSubstance>> nomsSubImporteesNormalises = new HashMap<>();
	
	private static Logger logger;
	private static String ajoutSubstances;
	private static String substance1EnCours;
	private static Integer risqueEnCours;
	private static String descriptifEnCours;
	private static String conduiteATenirEnCours;
	private static Float valeurInconnueDesc;
	private static Float valeurInconnueCond;
	private static String classeEnCours;
	
	private static boolean reussite = true;
	private static boolean ignorerLigne = false;

	private MiseAJourInteractions () {}
	
	public static boolean handler (Logger logger) {
		if (executionEnCours.compareAndSet(false, true)) {
			MiseAJourInteractions.logger = logger;
			logger.info("Début de la mise à jour des interactions");
			if (nomsSubImporteesNormalises.isEmpty()) { 
				MiseAJourClassesSubstances.importerSubstances(logger).entrySet()
					.stream()
					.map(e -> new AbstractMap.SimpleEntry<>(
						Texte.normaliser.apply(e.getKey()),
						e.getValue()
					))
					.forEach(e -> nomsSubImporteesNormalises
						.computeIfAbsent(e.getKey(), k -> new HashSet<>())
						.addAll(e.getValue())
					);
			}
			nouveauxChamps();
			try {
				long startTime = System.currentTimeMillis();
				PDDocument fichier = recupererFichier();
				if (!parserFichier(fichier)) { return false; }
				logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
				logger.info(interactionsBrouillon.size() + " potentielles interactions trouvées");
				creerEntitesInteraction();
				logger.info("Mise à jour de la base de données en cours...");
				startTime = System.currentTimeMillis();
				int total = entitesCreeesParCle.size();
				EntiteInteraction.mettreAJourEntitesBatch(logger, entitesCreeesParCle.values());
				logger.info(total + " entités mises à jour en " + Utils.tempsDepuis(startTime) + " ms");
			}
			catch (Exception e)
			{
				executionEnCours.set(false);
				Utils.logErreur(e, logger);
				return false;
			}
			executionEnCours.set(false);
			try { EntiteDateMaj.definirDateMajInteractions(); }
			catch (StorageException | URISyntaxException | InvalidKeyException e) {
				Utils.logErreur(e, logger);
			}
			return true;
		}
		else { logger.info("Une exécution est déjà en cours"); }
		return false;
	}

	private static PDDocument recupererFichier () throws IOException {
		logger.info("Récupération du fichier des interactions (url = " + URL_FICHIER_INTERACTIONS + ")");
		long startTime = System.currentTimeMillis();
		HttpsURLConnection connexion = (HttpsURLConnection) new URL(URL_FICHIER_INTERACTIONS)
			.openConnection();
		connexion.setRequestMethod("GET");
		PDDocument document = PDDocument.load(connexion.getInputStream());
		logger.info("Fichier récupéré en " + Utils.tempsDepuis(startTime) + " ms");
		return document;
	}

	private static boolean parserFichier (PDDocument fichier) throws IOException {
		logger.info("Début du parsing");
		long startTime = System.currentTimeMillis();
		PDFTextStripper stripper = new PDFTextStripper() {
			@Override
			protected void writeString (String text, List<TextPosition> textPositions) throws IOException {
				if (reussite) { analyserLigne(text, textPositions); }
				super.writeString(text, textPositions);
			}
		};
		int nombrePages = fichier.getNumberOfPages();
		for (int page = 2; page <= nombrePages; page++) {
			logger.info("Parsing de la page " + page + "/" + nombrePages + "...");
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			stripper.getText(fichier);
			if (!reussite) return false;
		}
		ajouterInteractionsBrouillon();
		nouveauxChamps();
		fichier.close();
		logger.info("Fin du parsing en " + Utils.tempsDepuis(startTime) + " ms");
		return true;
	}
    
	private static void analyserLigne (String texte, List<TextPosition> textPositions) {
		Float taille = textPositions.get(0).getFontSize();
		Float tailleInPt = textPositions.get(0).getFontSizeInPt();
		if (ignorerLigne) ignorerLigne = false;
		else {
			String ligne = Texte.normaliser.apply(texte);
			if (ligne.matches("(?i:thesaurus .*)")) ignorerLigne = true;
			else if (!ligne.matches("(?i:ansm .*)")) {
				if (taille.equals(TAILLE_NOM_SUBSTANCE)) {
					ajouterInteractionsBrouillon();
					nouveauxChamps();
					substances2EnCours.add(ligne.trim());
				}
				else if (taille.equals(TAILLE_INTERACTION_SUBSTANCE)) {
					if (!ligne.matches("\\+")) {
						ajouterInteractionsBrouillon();
						List<String> sauvegarde = new ArrayList<>(substances2EnCours);
						nouveauxChamps();
						substances2EnCours.addAll(sauvegarde);
						substance1EnCours = ligne;
					}
				}
				else if (tailleInPt.equals(TAILLE_DESCRIPTION_PT)) {
					if (substance1EnCours != null) {
						if (risqueEnCours == null) {
							for (int i = regexRisques.length - 1; i >= 0; i--) {
								String regex = regexRisques[i];
								if (texte.matches(regex + ".*")) {
									risqueEnCours = i + 1; 
									if (texte.matches(regex)) texte = ""; 
									else if (texte.matches(regex + "[a-zA-Z].*"))
										texte = texte.replaceFirst(regex, ""); 
									break;
								}
							}
						}
						if (!texte.equals("")) { 
							float xposition = textPositions.get(0).getX();
							float[][] matrice = textPositions.get(0).getTextMatrix().getValues();
							float colonneGauche = (float) 97.68;
							//float colonneDroite = (float) 317.3999;
							if (Float.compare(xposition, colonneGauche) <= 0) {
								if (valeurInconnueDesc == null) valeurInconnueDesc = matrice[2][1];
								if (valeurInconnueCond != null && valeurInconnueDesc.compareTo(valeurInconnueCond) < 0) {
									descriptifEnCours = conduiteATenirEnCours + descriptifEnCours;
									conduiteATenirEnCours = "";
								}
								if (!descriptifEnCours.equals("") && texte.matches("[-A-Z].*")) { 
									texte = "\n" + texte; 
								}
								descriptifEnCours += texte + " "; 
							} else { 
								if (valeurInconnueCond == null) valeurInconnueCond = matrice[2][1];
								if (!conduiteATenirEnCours.equals("") && ligne.matches("[-A-Z].*")) { 
									texte = "\n" + texte; 
								}
								conduiteATenirEnCours += texte + " ";
							}
						}
					} else if (
						ajoutSubstances != null
						|| (texte.startsWith("(") && !texte.matches("\\( ?(V|v)oir aussi.*"))
					) {
						if (ajoutSubstances == null && ligne.startsWith("(")) { 
							String classe = String.join("", substances2EnCours);
							nouveauxChamps();
							classeEnCours = classe;
							ligne = ligne.substring(1); 
						}
						int d = 0;
						if (!ligne.endsWith(")")) ajoutSubstances = "";
						else { 
							ligne = ligne.substring(0, ligne.length() - 1);
							if (ajoutSubstances != null) { 
								String aAjouter = "";
								String[] decoupe = ligne.split(",");
								if (decoupe.length > 0) aAjouter = decoupe[0];
								substances2EnCours.add(ajoutSubstances + " " + aAjouter); 
								d = 1;
							}
							ajoutSubstances = null; 
						}
						String[] substances = ligne.split(",");
						for (int i = d; i < substances.length; i++) {
							String substance = substances[i].trim();
							if (!substance.matches(" *")) {
								if (i == substances.length - 1 && ajoutSubstances != null) { 
									ajoutSubstances = substance; 
								}
								else substances2EnCours.add(substance);
							}
						}
					}
				} else {
					/* Ici ne doit se trouver aucune ligne du pdf */
					logger.warning(
						"LIGNE IGNOREE (il ne devrait pas y en avoir) : "
						+ Utils.NEWLINE + "\"" + texte + "\""
					);
				}						
			}
		}
    }

    private static void nouveauxChamps () {
		if (classeEnCours != null) { 
            classesSubstances.put(
                Texte.normaliser.apply(classeEnCours).toLowerCase(), 
                new HashSet<>(substances2EnCours)
            ); 
        }
		substances2EnCours.clear();
		substance1EnCours = null;
		risqueEnCours = null;
		descriptifEnCours = "";
		conduiteATenirEnCours = "";
		valeurInconnueDesc = null;
		valeurInconnueCond = null;
		ajoutSubstances = null;
		classeEnCours = null;
	}

	private static void ajouterInteractionsBrouillon () {
		if (risqueEnCours != null) {
			for (String substance2 : substances2EnCours) {
				interactionsBrouillon.add(new InteractionBrouillon(
					substance1EnCours, 
					substance2, 
					risqueEnCours, 
					descriptifEnCours, 
					conduiteATenirEnCours
				));
			}
		}
	}

	private static void creerEntitesInteraction () {
		// Si le descriptif commence par "ainsi que", "et pendant", remplacer "ainsi que" par "Cette interaction se poursuit"
		// Voir aussi ceux qui commencent avec "Dans l'indication..."
		logger.info("Création des entités...");
		long startTime = System.currentTimeMillis();
		for (InteractionBrouillon intBrouillon : interactionsBrouillon) {
			String descriptif;
			String conduite;
			if (intBrouillon.descriptif.matches(" *")) {
				descriptif = Texte.corrigerApostrophes.apply(intBrouillon.conduite);
				conduite = "";
			} else {
				descriptif = Texte.corrigerApostrophes.apply(intBrouillon.descriptif);
				conduite = Texte.corrigerApostrophes.apply(intBrouillon.conduite
					.replaceAll(
					"((CI ?)"
					+ "|(ASDEC ?)"
					+ "|(PE )"
					+ "|(APEC ?))", 
					"")
					.replaceFirst(" ?(- )+\n", "")
				);
			}
			Set<EntiteSubstance> substances1 = Recherche.obtenirCorrespondances.apply(intBrouillon.substance1);
			Set<EntiteSubstance> substances2 = Recherche.obtenirCorrespondances.apply(intBrouillon.substance2);
			Set<List<EntiteSubstance>> combinaisons = Sets.cartesianProduct(substances1, substances2);
			combinaisons.stream()
				.map(c -> {
					EntiteInteraction entite = new EntiteInteraction(c.get(0), c.get(1));
					entite.setRisque(intBrouillon.risque);
					entite.setDescriptif(descriptif);
					entite.setConduite(conduite);
					return entite;
				})
				.forEach(e -> {
					String cleUnique = e.getPartitionKey() + e.getRowKey();
					EntiteInteraction doublon = entitesCreeesParCle.get(cleUnique);
					if (doublon == null || doublon.getRisque() < e.getRisque())
						entitesCreeesParCle.put(cleUnique, e);
				});
		}
		logger.info(entitesCreeesParCle.size() + " entités créées en " + Utils.tempsDepuis(startTime) + " ms");
	}

	private static class InteractionBrouillon {
		final String substance1;
		final String substance2;
		final int risque;
		final String descriptif;
		final String conduite;
		InteractionBrouillon (String substance1, String substance2, int risque, String descriptif, String conduite) {
			this.substance1 = substance1;
			this.substance2 = substance2;
			this.risque = risque;
			this.descriptif = Optional.ofNullable(descriptif).orElse("");
			this.conduite = Optional.ofNullable(conduite).orElse("");
		}
	}

	private static class Recherche {

		private static final Map<String, Set<EntiteSubstance>> cacheCorrespondances = new HashMap<>();
		private static final Map<String, Set<EntiteSubstance>> cacheMeilleuresSubstances = new HashMap<>();
		private static final Map<String, Set<String>> cacheSousExpressions = new HashMap<>();
		private static final Map<String, Set<EntiteSubstance>> cacheRechercheSubstances = new HashMap<>();

		protected static Function<String, Set<EntiteSubstance>> obtenirCorrespondances = recherche -> 
			cacheCorrespondances.computeIfAbsent(recherche, exp -> {
				if (exp == null) return new HashSet<>();
				exp = exp.toLowerCase();
				if (exp.startsWith("autres ")) exp = exp.replaceFirst("autres ", "");
				if (classesSubstances.containsKey(exp)) { 
					return classesSubstances.get(exp).stream()
						.flatMap(substance -> 
							Recherche.obtenirCorrespondances.apply(substance).stream())
						.collect(Collectors.toSet());
				}
				return correspondancesSubstances.computeIfAbsent(exp, e -> rechercherMeilleuresSubstances(e));
			})
		;

		private static Set<EntiteSubstance> rechercherMeilleuresSubstances (String recherche) {
			return cacheMeilleuresSubstances.computeIfAbsent(recherche, terme -> {
				HashMap<EntiteSubstance, Double> classement = new HashMap<>();
				if (terme.matches(".+\\(.+\\)")) {
					String debut = terme.split("\\(")[0];
					Set<EntiteSubstance> resultats1 = rechercherMeilleuresSubstances(debut);
					if (resultats1.isEmpty()) return resultats1;
					return rechercherMeilleuresSubstances(terme.replaceFirst(debut, ""))
						.stream()
						.filter(resultats1::contains)
						.collect(Collectors.toSet());
				}
				String regexExclus = "(?i:"
					+ "(fruit)" 
					+ "|(acide)"
					+ "|(alpha))"
					+ "|(?i:.*par voie.*)";
				for (String expression : (Iterable<String>) () -> 
					obtenirSousExpressions(terme.trim().replaceAll("[,\\(\\)]", "")).stream()
						.map(Texte.normaliser::apply)
						.map(String::toLowerCase)
						.filter(exp -> !exp.matches(regexExclus))
						.filter(exp -> exp.equals("fer") || !exp.matches("([^ ]{1,3} ?\\b)+"))
						.iterator()
				) {
					if (expression.matches("(?i:(sauf)|(hors))")) break;
					if (expression.matches("(?i:[^ ]*s)")) { 
						expression = expression.substring(0, expression.length() - 1); 
					}
					Set<EntiteSubstance> resultats = rechercheSimple(expression);
					for (EntiteSubstance resultat : resultats) { 
						double score = (1.0 * expression.length()) / resultats.size();
						double scorePrecedent = Optional
							.ofNullable(classement.get(resultat))
							.orElse(0.0);
						classement.put(resultat, score + scorePrecedent); 
					}
				}
				return trouverMeilleurs(classement);
			});
		}

		private static Set<String> obtenirSousExpressions (String expression) {
			return cacheSousExpressions.computeIfAbsent(expression, terme -> {
				Set<String> sousExpressions = new HashSet<>();
				String[] mots = terme.split(" ");
				for (int k = mots.length; k >= 1; k--) {
					for (int i = 0; i + k <= mots.length; i++) {
						sousExpressions.add(
							String.join(
								" ", 
								Arrays.copyOfRange(mots, i, i + k)
							)
						);
					}
				}
				return sousExpressions;
			});
		}

		private static Set<EntiteSubstance> rechercheSimple (String recherche) {
			return cacheRechercheSubstances.computeIfAbsent(recherche, mot -> {
				return Optional.ofNullable(nomsSubImporteesNormalises.entrySet().stream()
						.filter(e -> e.getKey().matches("(?i:.*" + mot + "\\b.*)"))
						.flatMap(e -> e.getValue().stream())
						.collect(Collectors.toSet())
				)
					.orElseGet(() -> nomsSubImporteesNormalises.entrySet().stream()
						.filter(e -> e.getKey().matches("(?i:.*" + mot + ".*)"))
						.flatMap(e -> e.getValue().stream())
						.collect(Collectors.toSet()));
			});
		}
	}

	private static <T> Set<T> trouverMeilleurs (HashMap<T, Double> classement) {
		if (classement.isEmpty()) { return new HashSet<>(); }
		if (classement.size() == 1) { 
			return classement.keySet();
		}
		final double scoremax = classement.values().stream()
			.max(Double::compare)
			.get();
		return classement.keySet().stream()
			.filter(cle -> classement.get(cle) == scoremax)
			.collect(Collectors.toSet());
	}

	private static class Texte {

		private static final Map<String, String> cacheNormalisation = new HashMap<>();
		private static final Map<String, String> cacheApostrophes = new HashMap<>();

		protected static Function<String, String> normaliser = original ->
			cacheNormalisation.computeIfAbsent(original, mot -> {
				mot = Utils.normaliser(mot).replaceAll("  ", " ").trim();
				byte[] ancien = mot.getBytes(CHARSET_1252);
				byte[] nouveau = new byte[ancien.length];
				for (int i = 0; i < ancien.length; i++) {
					switch (Integer.valueOf(ancien[i])) {
						//a
						case -32: 
						case -30:
							nouveau[i] = 97;
							break;
						//e
						case -23:
						case -24:
						case -22:
							nouveau[i] = 101; 
							break;
						//i
						case -17:
						case -18:
							nouveau[i] = 105;
							break;
						//o
						case -12:
						case -10:
							nouveau[i] = 111;
							break;
						//u
						case -4:
							nouveau[i] = 117;
							break;
						//œ
						case -100:
							nouveau = Arrays.copyOf(nouveau, nouveau.length + 1);
							nouveau[i] = 111; 
							nouveau[i+1] = 101 ;
							i++;
							break;
						//apostrophe
						case -110: 
							nouveau[i] = 39; 
							break;
						default:
							nouveau[i] = ancien[i];
					}
				}
				return new String(nouveau, CHARSET_1252);
			})
		;

		protected static Function<String, String> corrigerApostrophes = original ->
			cacheApostrophes.computeIfAbsent(original, mot -> {
				byte[] ancien = mot.getBytes(CHARSET_1252);
				byte[] nouveau = new byte[ancien.length];
				for (int i = 0; i < ancien.length; i++) {
					switch (Integer.valueOf(ancien[i])) {
						case -110:
							nouveau[i] = 39;
							break;
						default:
							nouveau[i] = ancien[i];
					}
				}
				return new String(nouveau, CHARSET_1252)
					.trim()
					.replaceAll("  ", " ");
			})
		;
	}
}