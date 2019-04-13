package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import com.microsoft.sqlserver.jdbc.SQLServerCallableStatement;
import com.microsoft.sqlserver.jdbc.SQLServerConnection;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import app.mesmedicaments.BaseDeDonnees;
import app.mesmedicaments.Utils;

public final class MiseAJourInteractions {

    private static final Charset CHARSET_1252;
    private static final Float TAILLE_NOM_SUBSTANCE;
	private static final Float TAILLE_INTERACTION_SUBSTANCE;
	private static final Float TAILLE_DESCRIPTION_PT;
	private static final Integer TAILLE_BATCH;
	private static final String TABLE_INTERACTIONS;
	private static final String ID_MSI;

    private static Logger logger;
	private static SQLServerConnection conn;
    private static HashMap<String, HashSet<Integer>> substances;
    private static boolean ignorerLigne;
	private static String ajoutSubstances;
	private static HashSet<String> substancesEnCours;
	private static String interactionEnCours;
	private static Integer risqueEnCours;
	private static String descriptifEnCours;
	private static String conduiteATenirEnCours;
	private static Float valeurInconnueDesc;
	private static Float valeurInconnueCond;
	private static String classeEnCours;
	private static HashMap<String, HashSet<String>> correspondancesSubstances;
	private static HashMap<String, HashSet<String>> classes;
	private static HashMap<String, HashSet<String>> cacheRecherche;
	private static HashMap<Long, Integer> interactionsDejaEnvoyees;
	private static boolean reussite;
	private static int compteurBatch;
	private static long dureeMoyenne;
	private static String requete;
	private static SQLServerCallableStatement callableStatement;

	static {
		CHARSET_1252 = Charset.forName("cp1252");
		TAILLE_NOM_SUBSTANCE = (float) 10;
		TAILLE_INTERACTION_SUBSTANCE = (float) 8;
		TAILLE_DESCRIPTION_PT = (float) 6;
		TAILLE_BATCH = BaseDeDonnees.TAILLE_BATCH;
		TABLE_INTERACTIONS = System.getenv("table_interactions");
		ID_MSI = System.getenv("msi_maj");
		ignorerLigne = false;
		correspondancesSubstances = new HashMap<>();
		classes = new HashMap<>();
		cacheRecherche = new HashMap<>();
		interactionsDejaEnvoyees = new HashMap<>();
		reussite = true;
		compteurBatch = 0;
		dureeMoyenne = 0;
		requete = "{call projetdmp.ajouterInteraction(?, ?, ?, ?, ?, ?)}";
	}

	private MiseAJourInteractions () {}
	
	public static boolean handler (Logger logger) {
		MiseAJourInteractions.logger = logger;
		logger.info("Début de la mise à jour des interactions");
		conn = BaseDeDonnees.obtenirConnexion(ID_MSI, logger);
		if (conn == null) { return false; }
		interactionsDejaEnvoyees = BaseDeDonnees.obtenirInteractions(logger);
		substances = BaseDeDonnees.obtenirNomsSubstances(logger);
		if (substances.isEmpty()) { return false; }
		try { 
			conn.setAutoCommit(false);
			callableStatement = (SQLServerCallableStatement) conn.prepareCall(requete);
		}
		catch (SQLException e) {
			Utils.logErreur(e, logger);
			return false;
		}
		String tailleAvant = "Taille de la table " + TABLE_INTERACTIONS + " avant mise à jour : " 
			+ BaseDeDonnees.obtenirTailleTable(TABLE_INTERACTIONS, logger);
		nouveauxChamps();
		if (!mettreAJourInteractions()) { return false; }
		try {
			logger.info("Execution batch SQL avec " 
				+ String.valueOf(compteurBatch % TAILLE_BATCH) + " requêtes");
			callableStatement.executeBatch();
			conn.commit();
            logger.info(tailleAvant);
            logger.info("Taille de la table " + TABLE_INTERACTIONS + " après mise à jour : " 
                + BaseDeDonnees.obtenirTailleTable(TABLE_INTERACTIONS, logger));
			try {
				logger.info("Durée moyenne de l'exécution d'un batch de "
					+ TAILLE_BATCH + " requêtes : "
					+ String.valueOf(dureeMoyenne / (compteurBatch / TAILLE_BATCH)) 
					+ " ms");
			} catch (ArithmeticException e) {}
		}
		catch (SQLException e) {
			Utils.logErreur(e, logger);
			return false;
		}
		BaseDeDonnees.fermer(callableStatement);
		return true;
	}

    private static boolean mettreAJourInteractions () {
		String url = System.getenv("url_interactions");
		try {
			logger.info("Récupération du fichier des interactions (url = " + url + ")");
            HttpsURLConnection connexion = (HttpsURLConnection) new URL(url)
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
			stripper.setStartPage(2);
			stripper.getText(document);
			if (!reussite) { return false; }
			if (!ajouterInteraction()) { return false; }
			nouveauxChamps();
			document.close();
		} catch (IOException e) { 
            Utils.logErreur(e, logger);
            return false;
		}
		return true;
	}
    
    private static void analyseLigne (String texte, List<TextPosition> textPositions) {
		Float taille = textPositions.get(0).getFontSize();
		Float tailleInPt = textPositions.get(0).getFontSizeInPt();
		String risque1 = "((?i:((a|à) prendre en compte))|(.*APEC))";
		String risque2 = "((?i:pr(e|é)caution d'emploi)|(.*PE))";
		String risque3 = "((?i:(association d(e|é)conseill(e|é)e))|(.*ASDEC))";
		String risque4 = "((?i:(contre(-| )indication))|(.*CI))";
		String[] regexRisques = new String[]{risque1, risque2, risque3, risque4};
		if (!ignorerLigne) {
			String ligne = normaliser(texte);
			if (ligne.matches("(?i:thesaurus .*)")) { ignorerLigne = true; }
			else if (!ligne.matches("(?i:ansm .*)")) {
				if (taille.equals(TAILLE_NOM_SUBSTANCE)) {
					if (!ajouterInteraction()) { reussite = false; }
					nouveauxChamps();
					substancesEnCours.add(ligne.trim());
				}
				else if (taille.equals(TAILLE_INTERACTION_SUBSTANCE)) {
					if (!ligne.matches("\\+")) {
						if (!ajouterInteraction()) { reussite = false; }
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
							Float colonneGauche = (float) 97.68;
							//Float colonneDroite = (float) 317.3999;
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
						+ Utils.NEWLINE + "\"" 
                        + texte 
                        + "\""
                    );
				}						
			}
		} else { ignorerLigne = false; }
    }

    private static void nouveauxChamps () {
		if (classeEnCours != null) { 
            classes.put(
                normaliser(classeEnCours).toLowerCase(), 
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
	
	private static boolean ajouterInteraction () {
		// Si le descriptif commence par "ainsi que", "et pendant", remplacer "ainsi que" par "Cette interaction se poursuit"
		// Voir aussi ceux qui commencent avec "Dans l'indication..."
		if (risqueEnCours != null) {
			if (descriptifEnCours == null) { descriptifEnCours = ""; }
			if (conduiteATenirEnCours == null) { conduiteATenirEnCours = ""; }
			if (descriptifEnCours.matches(" *")) {
				descriptifEnCours = conduiteATenirEnCours;
				conduiteATenirEnCours = ""; 
			}
			conduiteATenirEnCours = conduiteATenirEnCours.replaceAll(
                "((CI ?)"
                + "|(ASDEC ?)"
                + "|(PE )"
                + "|(APEC ?))", 
                "");
			conduiteATenirEnCours = conduiteATenirEnCours.replaceFirst(" ?(- )+\n", "");
			descriptifEnCours = corrigerApostrophes(descriptifEnCours);
			conduiteATenirEnCours = corrigerApostrophes(conduiteATenirEnCours);
			for (String substance : substancesEnCours) { 
				if (!preparerInteraction(new String[] {
					String.valueOf(risqueEnCours), 
					substance, 
					interactionEnCours, 
					descriptifEnCours, 
					conduiteATenirEnCours
				})) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean preparerInteraction (String[] interaction) {
		String risque = interaction[0];
		String descriptif = interaction[3];
		String conduite = interaction[4];
		HashSet<String> substances1 = obtenirCorrespondances(interaction[1]);
		HashSet<String> substances2 = obtenirCorrespondances(interaction[2]);
		boolean etat = true;
		for (String s1 : substances1) {
			for (String s2 : substances2) {
				String id;
				for (int code1 : substances.get(s1)) {
					for (int code2 : substances.get(s2)) {
						if (code1 < code2) { id = String.valueOf(code1) + String.valueOf(code2); }
						else { id = String.valueOf(code2) + String.valueOf(code1); }
						if (!exporterInteraction( new String[]{
							id,
							risque,
							String.valueOf(code1),
							String.valueOf(code2),
							descriptif,
							conduite
						})) {
							etat = false;
						}
					}
				}
			}
		}
		return etat;
	}
	
	private static boolean exporterInteraction (String[] interaction) {
		try {
			Long id = Long.parseLong(interaction[0]);
			Integer risque = Integer.parseInt(interaction[1]);
			if (interactionsDejaEnvoyees.get(id) == null
				|| interactionsDejaEnvoyees.get(id) < risque) {
				callableStatement.setLong(1, id);
				callableStatement.setInt(2, Integer.parseInt(interaction[2]));
				callableStatement.setInt(3, Integer.parseInt(interaction[3]));
				callableStatement.setInt(4, risque);
				callableStatement.setString(5, interaction[4]);
				callableStatement.setString(6, interaction[5]);
				callableStatement.addBatch();
				compteurBatch++;
				if (compteurBatch % TAILLE_BATCH == 0) {
					logger.info("Execution batch de "
						+ TAILLE_BATCH + " requêtes (" 
						+ String.valueOf(compteurBatch / TAILLE_BATCH) + ")" );
					long start = System.currentTimeMillis();
					callableStatement.executeBatch();
					dureeMoyenne += System.currentTimeMillis() - start;
				}
			}
		} 
		catch (SQLException e) {
            Utils.logErreur(e, logger);;
            return false;
        }
		return true;
	}
	
	private static HashSet<String> obtenirCorrespondances (String substance) {
		if (substance == null) { return new HashSet<>(); }
		substance = substance.toLowerCase();
		if (substance.matches("(?i:autres .*)")) { substance = substance.replaceFirst("autres ", ""); }
		if (classes.containsKey(substance)) { 
			HashSet<String> aRetourner = new HashSet<>();
			for (String s : classes.get(substance)) { 
                aRetourner.addAll(obtenirCorrespondances(s));
            }
			return aRetourner;
		}
		if (correspondancesSubstances.containsKey(substance)) { 
            return correspondancesSubstances.get(substance); 
        }
		correspondancesSubstances.put(
            substance, 
            rechercherMeilleuresSubstances(substance));
		return correspondancesSubstances.get(substance);
	}
	
	private static HashSet<String> rechercherMeilleuresSubstances (String recherche) {
		HashMap<String, Double> classement = new HashMap<>();
		if (recherche.matches(".+\\(.+\\)")) {
			String debut = recherche.split("\\(")[0];
			HashSet<String> resultats1 = rechercherMeilleuresSubstances(debut);
			if (resultats1.isEmpty()) { return new HashSet<String>(); }
			HashSet<String> resultats2 = new HashSet<>();
			for (String r : rechercherMeilleuresSubstances(recherche.replaceFirst(debut, ""))) {
				if (resultats1.contains(r)) { resultats2.add(r); }
			}
			return resultats2;
		}
		recherche = recherche.trim().replaceAll("[,\\(\\)]", "");
		for (String expression : obtenirSousExpressions(recherche)) {
				expression = normaliser(expression);
				if (expression.matches("(?i:(sauf)|(hors))")) { break; }
                if (expression.matches("(?i:"
                    + "(fruit)" 
                    + "|(acide)"
                    + "|(alpha))"
                )) { expression = ""; }
				if (expression.matches("(?i:.*par voie.*)")) { expression = ""; }
                if (!expression.toLowerCase().equals("fer") 
                    && expression.matches("([^ ]{1,3} ?\\b)+"
                )) { expression = ""; }
				if (expression.matches("(?i:[^ ]*s)")) { expression = expression.substring(0, expression.length() - 1); }
				if (!expression.equals("")) {
					HashSet<String> resultats = rechercherSubstances(expression);
					if (!resultats.isEmpty()) { 			
						for (String resultat : resultats) { 
							Double score = (1.0 * expression.length()) / resultats.size();
							Double scorePrecedent = classement.get(resultat); 
							if (scorePrecedent == null) { scorePrecedent = 0.0; }
							classement.put(resultat, score + scorePrecedent); 
						}
					}
				}
		}
		return trouverMeilleurs(classement);
	}
	
	private static HashSet<String> obtenirSousExpressions (String expression) {
		HashSet<String> sousExpressions = new HashSet<>();
		String[] mots = expression.split(" ");
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
	}
	
	private static HashSet<String> rechercherSubstances (String recherche) {
		HashSet<String> resultats = cacheRecherche.get(recherche);
		if (resultats != null) { return resultats; }
		resultats = new HashSet<String>();
		for (int i = 0; i < 2; i++) {
			for (String nom : substances.keySet()) {
				String regex;
				if (i == 0) { regex = "(?i:.*" + recherche + "\\b.*)"; }
				else { regex = "(?i:.*" + recherche + ".*)"; }
				if (normaliser(nom).matches(regex)) { resultats.add(nom); } 
			}
			if (!resultats.isEmpty()) { 
				cacheRecherche.put(recherche, resultats);
				return resultats; 
			}
		}
		return resultats;
	}
	
	private static HashSet<String> trouverMeilleurs (HashMap<String, Double> classement) {
		if (classement.size() == 1) { 
			return new HashSet<String>(classement.keySet()); 
		}
		double scoremax = 0;
		HashSet<String> trouves = new HashSet<>();
		for (String membre : classement.keySet()) {
			if (classement.get(membre) > scoremax) {
				trouves = new HashSet<>();
				scoremax = classement.get(membre);
			}
			if (classement.get(membre) == scoremax) { trouves.add(membre); }
		}
		return trouves;
	}
	
    
    private static String normaliser (String original) {
        original = Normalizer.normalize(original, Normalizer.Form.NFD)
            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		byte[] ancien = original.getBytes(CHARSET_1252);
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
		String s = new String(nouveau, CHARSET_1252);
		s = s.trim();
		s = s.replaceAll("  ", " ");
		return s;
    }
    
    private static String corrigerApostrophes (String original) {
		byte[] ancien = original.getBytes(CHARSET_1252);
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
		String s = new String(nouveau, CHARSET_1252);
		s = s.trim();
		s = s.replaceAll("  ", " ");
		return s;
    }
}