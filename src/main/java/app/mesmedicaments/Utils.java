package app.mesmedicaments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteSubstance;

//import org.json.JSONArray;

public final class Utils {

	//private static final String XORKEY;
	public static final String NEWLINE;
	private static final Map<String, String> cacheNormalisation;
	public static final ZoneId TIMEZONE;
	private static final Map<Long, Optional<EntiteMedicamentFrance>> cacheEntitesMedicamentFrance;
	private static final Map<Long, Optional<EntiteSubstance>> cacheEntitesSubstance;
	private static final Map<String, Optional<EntiteInteraction>> cacheEntitesInteraction;

	static {
		NEWLINE = System.getProperty("line.separator");
		//XORKEY = System.getenv("cle_XOR");
		cacheNormalisation = new ConcurrentHashMap<>();
		TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);
		cacheEntitesMedicamentFrance = new ConcurrentHashMap<>();
		cacheEntitesSubstance = new ConcurrentHashMap<>();
		cacheEntitesInteraction = new ConcurrentHashMap<>();
	}

	private Utils () {}

	public static String[] decouperTexte (String texte, int nbrDecoupes) {
		String[] retour = new String[nbrDecoupes];
		for (int i = 1; i <= nbrDecoupes; i++) {
			retour[i - 1] = texte.substring(
				texte.length() / nbrDecoupes * (i - 1), 
				i == nbrDecoupes ? texte.length() : texte.length() / nbrDecoupes * i
			);
		}
		return retour;
	}

	public static LocalDateTime dateToLocalDateTime (Date date) {
		return LocalDateTime.ofInstant(date.toInstant(), Utils.TIMEZONE);
	}

	public static JSONObject convertirJsonDatesCodesEnJsonDatesDetails (JSONObject json, Logger logger) {
		JSONObject medsEnJson = new JSONObject();
		for (String cle : json.keySet()) {
			JSONArray codes = json.getJSONArray(cle);
			JSONArray enJson = new JSONArray();
			Utils.jsonArrayToSetLong(codes).stream().parallel().forEach((Long codeCis) -> {
				try {
					EntiteMedicamentFrance entiteM = Utils.obtenirEntiteMedicamentFrance(codeCis).get();
					enJson.put(Utils.medicamentFranceEnJson(entiteM, logger));
				} catch (StorageException | URISyntaxException | InvalidKeyException e) {
					Utils.logErreur(e, logger);
					throw new RuntimeException();
				}
			});
			medsEnJson.put(cle, enJson);
		}
		return medsEnJson;
	}

	/**
	 * Convertit tous les éléments du JSONArray en objet Long et les place dans un Set (donc supprime les doublons)
	 * @param jsonArray
	 * @return Set<Long>
	 * @throws JSONException Si un élément du JSONArray ne peut être converti en Long
	 */
	public static Set<Long> jsonArrayToSetLong (JSONArray jsonArray) 
		throws JSONException
	{
		Set<Long> set = new HashSet<>();
		for (int i = 0; i < jsonArray.length(); i++) {
			set.add(jsonArray.getLong(i));
		}
		return set;
	}

	/**
	 * Ajoute tous les éléments du JSONArray comme des nombres de type Long à la Collection
	 * @param collection La Collection à laquelle ajouter les Long
	 * @param jArray
	 * @throws JSONException Si l'un des éléments du JSONArray ne peut être converti en Long
	 */
	public static void ajouterTousLong(Collection<Long> collection, JSONArray jArray) 
		throws JSONException
	{
		for (int i = 0; i < jArray.length(); i++)
			collection.add(jArray.getLong(i));
	}

	public static Optional<EntiteInteraction> obtenirEntiteInteraction (long codeSubstance1, long codeSubstance2) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		String cle = String.valueOf(Math.min(codeSubstance1, codeSubstance2)) + String.valueOf(Math.max(codeSubstance1, codeSubstance2));
		Optional<EntiteInteraction> optEntiteI = cacheEntitesInteraction.get(cle);
		if (optEntiteI == null) {
			optEntiteI = EntiteInteraction.obtenirEntite(codeSubstance1, codeSubstance2);
			cacheEntitesInteraction.put(cle, optEntiteI);
		}
		return optEntiteI;
	}

	
	public static Optional<EntiteSubstance> obtenirEntiteSubstance (long codeSubstance)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Optional<EntiteSubstance> optEntiteS = cacheEntitesSubstance.get(codeSubstance);
		if (optEntiteS == null) {
			optEntiteS = EntiteSubstance.obtenirEntite(codeSubstance);
			cacheEntitesSubstance.put(codeSubstance, optEntiteS);
		}
		return optEntiteS;
	}

	public static Optional<EntiteMedicamentFrance> obtenirEntiteMedicamentFrance (long codeCis) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Optional<EntiteMedicamentFrance> optEntiteM = cacheEntitesMedicamentFrance.get(codeCis);
		if (optEntiteM == null) {
			optEntiteM = EntiteMedicamentFrance.obtenirEntite(codeCis);
			cacheEntitesMedicamentFrance.put(codeCis, optEntiteM);
		}
		return optEntiteM;
	};

	public static <P extends Object> JSONArray obtenirInteractions (AbstractEntiteMedicament<P> entiteM1, AbstractEntiteMedicament<P> entiteM2, Logger logger) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Set<AbstractEntiteMedicament.SubstanceActive> substances1 = entiteM1.obtenirSubstancesActives();
		Set<AbstractEntiteMedicament.SubstanceActive> substances2 = entiteM2.obtenirSubstancesActives();
		JSONArray interactions = new JSONArray();
		Set<Long[]> combinaisons = new HashSet<>();
		for (AbstractEntiteMedicament.SubstanceActive sub1 : substances1) {
			for (AbstractEntiteMedicament.SubstanceActive sub2 : substances2) {
				if (!sub1.codeSubstance.equals(sub2.codeSubstance)) {
					Long[] combinaison = new Long[]{sub1.codeSubstance, sub2.codeSubstance};
					combinaisons.add(combinaison);
				}
			}
		}
		combinaisons.stream().parallel()
			.forEach((Long[] comb) -> {
				try {
					Optional<EntiteInteraction> optEntiteI = Utils.obtenirEntiteInteraction(comb[0], comb[1]);
					if (optEntiteI.isPresent()) {
						interactions.put(interactionEnJson(optEntiteI.get(), logger)
							.put("medicaments", new JSONArray()
								.put(entiteM1.obtenirCodeCis())
								.put(entiteM2.obtenirCodeCis())
							)
						);
					}
				}
				catch (StorageException | URISyntaxException | InvalidKeyException e) {
					Utils.logErreur(e, logger);
					throw new RuntimeException();
				}
			});
		return interactions;
	}

	public static JSONObject interactionEnJson (EntiteInteraction entiteI, Logger logger) 
		throws StorageException, URISyntaxException, InvalidKeyException, NoSuchElementException
	{
		Long codeSub1 = entiteI.obtenirCodeSubstance1();
		Long codeSub2 = entiteI.obtenirCodeSubstance2();
		EntiteSubstance entiteS1 = Utils.obtenirEntiteSubstance(codeSub1).get();
		EntiteSubstance entiteS2 = Utils.obtenirEntiteSubstance(codeSub2).get();
		return new JSONObject()
			.put("substances", new JSONObject()
				.put(entiteS1.obtenirCodeSubstance().toString(), entiteS1.obtenirNomsJArray())
				.put(entiteS2.obtenirCodeSubstance().toString(), entiteS2.obtenirNomsJArray())
			)
			.put("risque", entiteI.getRisque())
			.put("descriptif", entiteI.getDescriptif())
			.put("conduite", entiteI.getConduite());
	}

	public static JSONObject medicamentFranceEnJson (EntiteMedicamentFrance entiteM, Logger logger)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Set<AbstractEntiteMedicament.SubstanceActive> substances = entiteM.obtenirSubstancesActives();
		JSONObject jsonSubstances = new JSONObject();
		substances.stream().parallel()
			.forEach(substance -> {
				try {
					Optional<EntiteSubstance> optEntiteS = Utils.obtenirEntiteSubstance(substance.codeSubstance);
					if (optEntiteS.isPresent()) {
						jsonSubstances.put(substance.codeSubstance.toString(), new JSONObject()
							.put("dosage", substance.dosage)
							.put("referenceDosage", substance.referenceDosage)
							.put("noms", optEntiteS.get().obtenirNomsJArray())
						);
					}
				} catch (StorageException | URISyntaxException | InvalidKeyException e) {
					Utils.logErreur(e, logger);
					throw new RuntimeException("Erreur lors de la récupération des substances");
				}
			});
		JSONObject jsonPresentations = new JSONObject();
		for (EntiteMedicamentFrance.Presentation presentation : entiteM.obtenirPresentations()) {
			jsonPresentations.put(presentation.nom, new JSONObject()
				.put("prix", presentation.prix)
				.put("conditionsRemboursement", presentation.conditionsRemboursement)
				.put("tauxRemboursement", presentation.tauxRemboursement)
				.put("honorairesDispensation", presentation.honorairesDispensation)
			);
		}
		JSONObject retour = new JSONObject()
			.put("noms", entiteM.obtenirNomsJArray())
			.put("forme", entiteM.getForme())
			.put("marque", entiteM.getMarque())
			.put("autorisation", entiteM.getAutorisation())
			.put("codecis", entiteM.getRowKey())
			.put("substances", jsonSubstances)
			.put("presentations", jsonPresentations)
			.put("effetsIndesirables", Optional.ofNullable(entiteM.getEffetsIndesirables()).orElse(""));
		try {
			retour.put("expressionsCles", new JSONArray(AnalyseTexte.obtenirExpressionsClesEffets(entiteM)));
		}
		catch (IOException e) {
			Utils.logErreur(e, logger);
		}
		return retour;
	}


	public static void logErreur(Throwable t, Logger logger) {
		String message = t.toString();
		try { message += NEWLINE + t.getCause().getMessage(); }
		catch (NullPointerException e) {
			message += NEWLINE + "(Classe Utils) L'objet Throwable n'a pas de méthode getCause().getMessage()"; 
		}
		try {
			for (StackTraceElement trace : t.getCause().getStackTrace()) {
				message += NEWLINE + "\t" + trace.toString();
			}
		}
		catch (NullPointerException e) {
			message += NEWLINE + "(Classe Utils) L'objet Throwable n'a pas de méthode getCause()";
		}
		try { logger.warning(t.getMessage()); }
		catch (NullPointerException e) {
			message += NEWLINE + "(Classe Utils) L'objet Throwable n'a pas de méthode getMessage()";
		}
		try {
			for (StackTraceElement trace : t.getStackTrace()) {
				message += NEWLINE + "\t" + trace.toString();
			}
		}
		catch (NullPointerException e) {
			message += NEWLINE + "(Classe Utils) L'objet Throwable n'a pas de méthode getStackTrace()";
		}
		logger.warning(message);
	}

	public static long tempsDepuis (long startTime) {
		return System.currentTimeMillis() - startTime;
	}

	private static final Function<String, String> normaliser = original ->
		Normalizer.normalize(original, Normalizer.Form.NFD)
			.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

	public static String normaliser (String original) {
		return cacheNormalisation.computeIfAbsent(original, cle -> normaliser.apply(cle));
	}

	/*public static int[] XOREncrypt (String str) {
		int[] output = new int[str.length()];
		for (int i = 0; i < output.length; i++) {
			output[i] = (Integer.valueOf(str.charAt(i)) 
				^ Integer.valueOf(XORKEY.charAt(i % (XORKEY.length() - 1))))
				+ '0';
		}
		return output;
	}

	public static String XORDecrypt (int[] input) {
		String output = "";
		for (int i = 0; i < input.length; i++) {
			output += (char) ((input[i] - 48)
				^ (int) XORKEY.charAt(i % (XORKEY.length() - 1)));
		}
		return output;
	}

	public static int[] JSONArrayToIntArray (JSONArray ja) {
		if (ja == null) { return new int[0]; }
		int[] tab = new int[ja.length()];
		for (int i = 0; i < ja.length(); i++) {
			tab[i] = Integer.parseInt(ja.get(i).toString());
		}
		return tab;
	}*/
}