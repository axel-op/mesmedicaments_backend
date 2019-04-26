package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.azure.storage.StorageException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.EntiteInteraction;

public final class MiseAJourInteractions {

    private static final Charset CHARSET_1252;
    private static final float TAILLE_NOM_SUBSTANCE;
	private static final float TAILLE_INTERACTION_SUBSTANCE;
	private static final float TAILLE_DESCRIPTION_PT;
	private static final String URL_FICHIER_INTERACTIONS;
	private static final AtomicBoolean executionEnCours;
	private static final Map<String, Set<Long>> correspondancesSubstances;
	private static final Map<String, Set<String>> classesSubstances;
	private static final Map<Long, Set<EntiteInteraction>> entitesInteractionsParPartition;
	private static Logger logger;
    private static Map<String, Set<Long>> substances;

    private boolean ignorerLigne;
	private String ajoutSubstances;
	private HashSet<String> substancesEnCours;
	private String interactionEnCours;
	private Integer risqueEnCours;
	private String descriptifEnCours;
	private String conduiteATenirEnCours;
	private Float valeurInconnueDesc;
	private Float valeurInconnueCond;
	private String classeEnCours;
	private boolean reussite;

	static {
		executionEnCours = new AtomicBoolean();
		URL_FICHIER_INTERACTIONS = System.getenv("url_interactions");
		CHARSET_1252 = Charset.forName("cp1252");
		TAILLE_NOM_SUBSTANCE = (float) 10;
		TAILLE_INTERACTION_SUBSTANCE = (float) 8;
		TAILLE_DESCRIPTION_PT = (float) 6;
		correspondancesSubstances = new HashMap<>();
		classesSubstances = new HashMap<>();
		entitesInteractionsParPartition = new TreeMap<>();
	}

	private MiseAJourInteractions () {
		ignorerLigne = false;
		reussite = true;
	}
	
	public static boolean handler (Logger logger) {
		if (executionEnCours.compareAndSet(false, true)) {
			MiseAJourInteractions.logger = logger;
			MiseAJourInteractions majInstance = new MiseAJourInteractions();
			logger.info("Début de la mise à jour des interactions");
			if (substances == null || substances.isEmpty()) { 
				substances = MiseAJourClassesSubstances.importerSubstances(logger); 
			}
			majInstance.nouveauxChamps();
			try {
				long startTime = System.currentTimeMillis();
				if (!majInstance.mettreAJourInteractions()) { return false; }
				logger.info("Parsing terminé en " + Utils.tempsDepuis(startTime) + " ms");
				exporterEntitesInteractions(entitesInteractionsParPartition);
			}
			catch (Exception e)
			{
				executionEnCours.set(false);
				Utils.logErreur(e, logger);
				return false;
			}
			executionEnCours.set(false);
			return true;
		}
		else { logger.info("Une exécution est déjà en cours"); }
		return false;
	}

	private boolean mettreAJourInteractions () 
		throws StorageException, URISyntaxException, InvalidKeyException, IOException 
	{
		logger.info("Récupération du fichier des interactions (url = " + URL_FICHIER_INTERACTIONS + ")");
		HttpsURLConnection connexion = (HttpsURLConnection) new URL(URL_FICHIER_INTERACTIONS)
			.openConnection();
		connexion.setRequestMethod("GET");
		PDDocument document = PDDocument.load(connexion.getInputStream());
		logger.info("Fichier récupéré ; début du parsing");
		PDFTextStripper stripper = new PDFTextStripper() {
			@Override
			protected void writeString (String text, List<TextPosition> textPositions) throws IOException {
				if (reussite) { analyseLigne(text, textPositions); }
				super.writeString(text, textPositions);
			}
		};
		int nombrePages = document.getNumberOfPages();
		for (int page = 2; page <= nombrePages; page++) {
			logger.info("Parsing de la page " + page + "/" + nombrePages + "..."
			);
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			stripper.getText(document);
		}
		if (!reussite) { return false; }
		if (risqueEnCours != null) { 
			for (String substance : substancesEnCours) {
				ajouterInteraction(
					interactionEnCours, 
					substance, 
					risqueEnCours, 
					descriptifEnCours, 
					conduiteATenirEnCours
				); 
			}
		}
		nouveauxChamps();
		document.close();
		return true;
	}
    
	private void analyseLigne (String texte, List<TextPosition> textPositions) {
		Float taille = textPositions.get(0).getFontSize();
		Float tailleInPt = textPositions.get(0).getFontSizeInPt();
		String risque1 = "((?i:((a|à) prendre en compte))|(.*APEC))";
		String risque2 = "((?i:pr(e|é)caution d'emploi)|(.*PE))";
		String risque3 = "((?i:(association d(e|é)conseill(e|é)e))|(.*ASDEC))";
		String risque4 = "((?i:(contre(-| )indication))|(.*CI))";
		String[] regexRisques = new String[]{risque1, risque2, risque3, risque4};
		try {
			if (!ignorerLigne) {
				String ligne = Texte.normaliser.apply(texte);
				if (ligne.matches("(?i:thesaurus .*)")) { ignorerLigne = true; }
				else if (!ligne.matches("(?i:ansm .*)")) {
					if (taille.equals(TAILLE_NOM_SUBSTANCE)) {
						if (risqueEnCours != null) { 
							for (String substance : substancesEnCours) {
								ajouterInteraction(
									interactionEnCours, 
									substance, 
									risqueEnCours, 
									descriptifEnCours, 
									conduiteATenirEnCours
								); 
							}
						}
						nouveauxChamps();
						substancesEnCours.add(ligne.trim());
					}
					else if (taille.equals(TAILLE_INTERACTION_SUBSTANCE)) {
						if (!ligne.matches("\\+")) {
							if (risqueEnCours != null) { 
								for (String substance : substancesEnCours) {
									ajouterInteraction(
										interactionEnCours, 
										substance, 
										risqueEnCours, 
										descriptifEnCours, 
										conduiteATenirEnCours
									); 
								}
							}
							HashSet<String> sec = substancesEnCours;
							nouveauxChamps();
							substancesEnCours = sec;
							interactionEnCours = ligne;
						}
					}
					else if (tailleInPt.equals(TAILLE_DESCRIPTION_PT)) {
						if (interactionEnCours != null) {
							if (risqueEnCours == null) {
								for (int i = regexRisques.length - 1; i >= 0; i--) {
									String regex = regexRisques[i];
									if (texte.matches(regex + ".*")) {
										risqueEnCours = i + 1; 
										if (texte.matches(regex)) { 
											texte = ""; 
										}
										if (texte.matches(regex + "[a-zA-Z].*")) { 
											texte = texte.replaceFirst(regex, ""); 
										}
										break;
									}
								}
							}
							if (!texte.equals("")) { 
								Float xposition = textPositions.get(0).getX();
								float[][] matrice = textPositions.get(0).getTextMatrix().getValues();
								float colonneGauche = (float) 97.68;
								//float colonneDroite = (float) 317.3999;
								if (xposition.compareTo(colonneGauche) <= 0) {
									if (valeurInconnueDesc == null) { valeurInconnueDesc = matrice[2][1]; }
									if (valeurInconnueCond != null && valeurInconnueDesc.compareTo(valeurInconnueCond) < 0) {
										descriptifEnCours = conduiteATenirEnCours + descriptifEnCours;
										conduiteATenirEnCours = "";
									}
									if (!descriptifEnCours.equals("") && texte.matches("[-A-Z].*")) { 
										texte = "\n" + texte; 
									}
									descriptifEnCours += texte + " "; 
								} else { 
									if (valeurInconnueCond == null) { valeurInconnueCond = matrice[2][1]; }
									if (!conduiteATenirEnCours.equals("") && ligne.matches("[-A-Z].*")) { 
										texte = "\n" + texte; 
									}
									conduiteATenirEnCours += texte + " ";
								}
							}
						} else if (
								(texte.startsWith("(") && !texte.matches("\\( ?(V|v)oir aussi.*")) 
								|| ajoutSubstances != null
							) {
							if (ligne.startsWith("(") && ajoutSubstances == null) { 
								String classe = "";
								for (String s : substancesEnCours) { classe += s; }
								nouveauxChamps();
								classeEnCours = classe;
								ligne = ligne.substring(1); 
							}
							int d = 0;
							if (!ligne.endsWith(")")) { ajoutSubstances = ""; } 
							else { 
								ligne = ligne.substring(0, ligne.length() - 1);
								if (ajoutSubstances != null) { 
									String aAjouter = "";
									if (ligne.split(",").length > 0) { aAjouter = ligne.split(",")[0]; }
									substancesEnCours.add(ajoutSubstances + " " + aAjouter); 
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
									else { substancesEnCours.add(substance); }
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
			} else { ignorerLigne = false; }
		}
		catch (StorageException | URISyntaxException | InvalidKeyException e) {
			Utils.logErreur(e, logger);
			reussite = false;
		}
    }

    private void nouveauxChamps () {
		if (classeEnCours != null) { 
            classesSubstances.put(
                Texte.normaliser.apply(classeEnCours).toLowerCase(), 
                substancesEnCours
            ); 
        }
		substancesEnCours = new HashSet<>();
		interactionEnCours = null;
		risqueEnCours = null;
		descriptifEnCours = "";
		conduiteATenirEnCours = "";
		valeurInconnueDesc = null;
		valeurInconnueCond = null;
		ajoutSubstances = null;
		classeEnCours = null;
	}
	
	private static void ajouterInteraction (String substance1, String substance2, int risque, String descriptif, String conduite) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		// Si le descriptif commence par "ainsi que", "et pendant", remplacer "ainsi que" par "Cette interaction se poursuit"
		// Voir aussi ceux qui commencent avec "Dans l'indication..."
		if (descriptif == null) { descriptif = ""; }
		if (conduite == null) { conduite = ""; }
		if (descriptif.matches(" *")) {
			descriptif = conduite;
			conduite = ""; 
		}
		conduite = conduite.replaceAll(
			"((CI ?)"
			+ "|(ASDEC ?)"
			+ "|(PE )"
			+ "|(APEC ?))", 
			"");
		conduite = conduite.replaceFirst(" ?(- )+\n", "");
		descriptif = Texte.corrigerApostrophes.apply(descriptif);
		conduite = Texte.corrigerApostrophes.apply(conduite);
		Set<Long> substances1 = Recherche.obtenirCorrespondances.apply(substance1);
		Set<Long> substances2 = Recherche.obtenirCorrespondances.apply(substance2);
		for (long code1 : substances1) {
			for (long code2 : substances2) {
				EntiteInteraction entite = new EntiteInteraction(code1, code2);
				entite.setRisque(risque);
				entite.setDescriptif(descriptif);
				entite.setConduite(conduite);
				long clePartition = Math.min(code1, code2);
				if (!entitesInteractionsParPartition.containsKey(clePartition)) {
					entitesInteractionsParPartition.put(clePartition, new HashSet<>());
				}
				entitesInteractionsParPartition.get(clePartition).add(entite);
			}
		}
	}

	/**
	 * Filtre la {@link Collection} d'{@link EntiteInteraction}s en ne retenant que celles avec le plus haut niveau de risque en cas de doublon
	 * @see EntiteInteraction
	 * @see EntiteInteraction#risque
	 * @param entites La {@link Collection} d'entités à filtrer
	 */
	private static void supprimerDoublons (Collection<EntiteInteraction> entites) {
		ArrayList<EntiteInteraction> listeEntites = new ArrayList<>(entites);
		for (int i = 0; i < listeEntites.size(); i++) {
			for (int j = i + 1; j < listeEntites.size(); j++) {
				EntiteInteraction e1 = listeEntites.get(i);
				EntiteInteraction e2 = listeEntites.get(j);
				if (e1.getPartitionKey().equals(e2.getPartitionKey())
					&& e1.getRowKey().equals(e2.getRowKey())) 
				{
					if (e1.getRisque() > e2.getRisque()) {
						if (entites.contains(e2)) {
							entites.remove(e2);
						}
					}
					else { 
						if (entites.contains(e1)) {
							entites.remove(e1); 
						}
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @param entitesRegroupees Les entités appartiennent à une même partition pour une même clé
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 * @throws IllegalArgumentException Si les entités n'appartiennent pas à la même partition pour une même clé 
	 */
	private static void exporterEntitesInteractions (Map<? extends Object, ? extends Collection<EntiteInteraction>> entitesRegroupees) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		logger.info("Suppression des doublons...");
		long startTime = System.currentTimeMillis();
		entitesRegroupees.values().stream().forEach(MiseAJourInteractions::supprimerDoublons);
		logger.info("Doublons supprimés en " + Utils.tempsDepuis(startTime) + " ms");
		logger.info("Mise à jour de la base de données en cours...");
		startTime = System.currentTimeMillis();
		for (Collection<EntiteInteraction> entites : entitesRegroupees.values()) {
			EntiteInteraction.mettreAJourEntitesBatch(entites);
		}
		logger.info("Base mise à jour en " + Utils.tempsDepuis(startTime) + " ms");
	}

	private static class Recherche {

		private static final Map<String, Set<Long>> cacheCorrespondances = new HashMap<>();
		private static final Map<String, Set<String>> cacheMeilleuresSubstances = new HashMap<>();
		private static final Map<String, Set<String>> cacheSousExpressions = new HashMap<>();
		private static final Map<String, Set<String>> cacheRechercheSubstances = new HashMap<>();

		protected static Function<String, Set<Long>> obtenirCorrespondances = recherche -> 
			cacheCorrespondances.computeIfAbsent(recherche, exp -> {
				if (exp == null) { return new HashSet<>(); }
				exp = exp.toLowerCase();
				if (exp.matches("(?i:autres .*)")) { exp = exp.replaceFirst("autres ", ""); }
				if (classesSubstances.containsKey(exp)) { 
					return classesSubstances.get(exp).stream()
						.flatMap(substance -> 
							Recherche.obtenirCorrespondances.apply(substance).stream())
						.collect(Collectors.toSet());
				}
				if (correspondancesSubstances.containsKey(exp)) { 
					return correspondancesSubstances.get(exp); 
				}
				Set<Long> codesSubstances = rechercherMeilleuresSubstances(exp)
					.stream()
					.flatMap(substance -> Optional.ofNullable(substances.get(substance))
						.orElseGet(HashSet::new)
						.stream())
					.collect(Collectors.toSet());
				correspondancesSubstances.put(exp, codesSubstances);
				return codesSubstances;
			})
		;

		private static Set<String> rechercherMeilleuresSubstances (String recherche) {
			return cacheMeilleuresSubstances.computeIfAbsent(recherche, terme -> {
				HashMap<String, Double> classement = new HashMap<>();
				if (terme.matches(".+\\(.+\\)")) {
					String debut = terme.split("\\(")[0];
					Set<String> resultats1 = rechercherMeilleuresSubstances(debut);
					if (resultats1.isEmpty()) { return resultats1; }
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
					if (expression.matches("(?i:(sauf)|(hors))")) { break; }
					if (expression.matches("(?i:[^ ]*s)")) { 
						expression = expression.substring(0, expression.length() - 1); 
					}
					Set<String> resultats = rechercherSubstances(expression);
					for (String resultat : resultats) { 
						double score = (1.0 * expression.length()) / resultats.size();
						double scorePrecedent = Optional.ofNullable(classement.get(resultat)).orElse(0.0);
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

		private static Set<String> rechercherSubstances (String recherche) {
			return cacheRechercheSubstances.computeIfAbsent(recherche, mot -> {
				Supplier<Stream<String>> noms = () -> substances.keySet().stream()
					.map(Texte.normaliser::apply);
				return Optional.ofNullable(
					noms.get()
						.filter(nom -> nom.matches("(?i:.*" + mot + "\\b.*)"))
						.collect(Collectors.toSet()))
					.orElseGet(() -> noms.get()
						.filter(nom -> nom.matches("(?i:.*" + mot + ".*)"))
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
				mot = Utils.normaliser(mot);
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