package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteMedicament;
import app.mesmedicaments.entitestables.EntiteSubstance;

//import org.json.JSONArray;

public final class Utils {

	//private static final String XORKEY;
	public static final String NEWLINE;
	private static final Map<String, String> cacheNormalisation;
	public static final ZoneId TIMEZONE;
	private static final Map<Long, EntiteMedicament> cacheEntitesMedicament;
	private static final Map<Long, EntiteSubstance> cacheEntitesSubstance;

	static {
		NEWLINE = System.getProperty("line.separator");
		//XORKEY = System.getenv("cle_XOR");
		cacheNormalisation = new ConcurrentHashMap<>();
		TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);
		cacheEntitesMedicament = new ConcurrentHashMap<>();
		cacheEntitesSubstance = new ConcurrentHashMap<>();
	}

	private Utils () {}

	public static LocalDateTime dateToLocalDateTime (Date date) {
		return LocalDateTime.ofInstant(date.toInstant(), Utils.TIMEZONE);
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

	public static EntiteSubstance obtenirEntiteSubstance (Long codeSubstance)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		EntiteSubstance entiteS;
		if (!cacheEntitesSubstance.containsKey(codeSubstance)) {
			entiteS = EntiteSubstance.obtenirEntite(codeSubstance).orElse(null);
			cacheEntitesSubstance.put(codeSubstance, entiteS);
		} else {
			entiteS = cacheEntitesSubstance.get(codeSubstance);
		}
		return entiteS;
	}

	public static EntiteMedicament obtenirEntiteMedicament (Long codeCis) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		EntiteMedicament entiteM;
		if (!cacheEntitesMedicament.containsKey(codeCis)) {
			entiteM = EntiteMedicament.obtenirEntite(codeCis).orElse(null);
			cacheEntitesMedicament.put(codeCis, entiteM);
		} else {
			entiteM = cacheEntitesMedicament.get(codeCis);
		}
		return entiteM;
	};

	public static JSONArray obtenirInteractions (EntiteMedicament entiteM1, EntiteMedicament entiteM2, Logger logger) 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Function<Set<String>, Set<Long>> strSetToIntSet = setStr -> setStr.stream()
			.map(str -> Long.parseLong(str))
			.collect(Collectors.toSet());
		Set<Long> substances1 = strSetToIntSet.apply(entiteM1.obtenirSubstancesActivesJObject().keySet());
		Set<Long> substances2 = strSetToIntSet.apply(entiteM2.obtenirSubstancesActivesJObject().keySet());
		JSONArray interactions = new JSONArray();
		Set<Long[]> combinaisons = new HashSet<>();
		for (Long codeSub1 : substances1) {
			for (Long codeSub2 : substances2) {
				if (!codeSub1.equals(codeSub2)) {
					Long[] combinaison = new Long[]{codeSub1, codeSub2};
					combinaisons.add(combinaison);
				}
			}
		}
		combinaisons.stream().parallel()
			.forEach((Long[] comb) -> {
				try {
					Optional<EntiteInteraction> optEntiteI = EntiteInteraction.obtenirEntite(comb[0], comb[1]);
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
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Long codeSub1 = entiteI.obtenirCodeSubstance1();
		Long codeSub2 = entiteI.obtenirCodeSubstance2();
		EntiteSubstance entiteS1 = obtenirEntiteSubstance(codeSub1);
		EntiteSubstance entiteS2 = obtenirEntiteSubstance(codeSub2);
		if (entiteS1 == null || entiteS2 == null) 
			throw new RuntimeException("Une des entités Substance parmi les deux suivantes n'a pas été trouvée alors qu'elle aurait dû l'être : " + codeSub1 + codeSub2);
		return new JSONObject()
			.put("substances", new JSONObject()
				.put(entiteS1.obtenirCodeSubstance().toString(), entiteS1.obtenirNomsJArray())
				.put(entiteS2.obtenirCodeSubstance().toString(), entiteS2.obtenirNomsJArray())
			)
			.put("risque", entiteI.getRisque())
			.put("descriptif", entiteI.getDescriptif())
			.put("conduite", entiteI.getConduite());
	}

	public static JSONObject medicamentEnJson (EntiteMedicament entiteM, Logger logger)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		JSONObject substances = entiteM.obtenirSubstancesActivesJObject();
		substances.keySet().stream().parallel()
			.forEach((cle) -> {
				try {
					Long code = Long.parseLong(cle);
					EntiteSubstance entiteS = obtenirEntiteSubstance(code);
					if (entiteS != null) {
						substances.getJSONObject(cle)
							.put("noms", entiteS.obtenirNomsJArray());
					}
				} catch (StorageException | URISyntaxException | InvalidKeyException e) {
					Utils.logErreur(e, logger);
					throw new RuntimeException("Erreur lors de la récupération des substances");
				}
			});
		return new JSONObject()
			.put("noms", entiteM.getNoms())
			.put("forme", entiteM.getForme())
			.put("marque", entiteM.getMarque())
			.put("autorisation", entiteM.getAutorisation())
			.put("codecis", entiteM.getRowKey())
			.put("substances", substances)
			.put("presentations", entiteM.obtenirPresentationsJObject());
	}


	public static void logErreur(Throwable t, Logger logger) {
		logger.warning(t.toString());
		try { logger.warning(t.getCause().getMessage()); }
		catch (NullPointerException e) {
			logger.warning("(Classe Utils) L'objet Throwable n'a pas de méthode getCause().getMessage()"); 
		}
		try {
			for (StackTraceElement trace : t.getCause().getStackTrace()) {
				logger.warning("\t" + trace.toString());
			}
		}
		catch (NullPointerException e) {
			logger.warning("(Classe Utils) L'objet Throwable n'a pas de méthode getCause()");
		}
		try { logger.warning(t.getMessage()); }
		catch (NullPointerException e) {
			logger.warning("(Classe Utils) L'objet Throwable n'a pas de méthode getMessage()");
		}
		try {
			for (StackTraceElement trace : t.getStackTrace()) {
				logger.warning("\t" + trace.toString());
			}
		}
		catch (NullPointerException e) {
			logger.warning("(Classe Utils) L'objet Throwable n'a pas de méthode getStackTrace()");
		}
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