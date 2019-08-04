package app.mesmedicaments;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.microsoft.azure.storage.StorageException;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntite.Pays;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.SubstanceActive;
import app.mesmedicaments.entitestables.EntiteInteraction;
import app.mesmedicaments.entitestables.EntiteMedicamentFrance;
import app.mesmedicaments.entitestables.EntiteSubstance;
import app.mesmedicaments.unchecked.Unchecker;

public final class Utils {

	public static final String NEWLINE = System.getProperty("line.separator");
	public static final ZoneId TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);
	
	private static final Map<String, String> cacheNormalisation = new ConcurrentHashMap<>();

	private Utils () {}

	//@Deprecated
	public static JSONObject mapDatesCodesEnJsonDatesDetails (Map<LocalDate, Set<Long>> medsParDate, Logger logger) {
		JSONObject medsEnJson = new JSONObject();
		for (LocalDate date : medsParDate.keySet()) {
			JSONArray enJson = new JSONArray();
			medsParDate.get(date).stream().parallel()
				.forEach(Unchecker.wrap(logger, (Long codeCis) -> {
					EntiteMedicamentFrance entiteM = EntiteMedicamentFrance.obtenirEntite(codeCis).get();
					enJson.put(Utils.medicamentFranceEnJsonDepreciee(entiteM, logger));
				}));
			medsEnJson.put(date.toString(), enJson);
		}
		return medsEnJson;
	}

	/**
	 * A utiliser à partir de la version 25 de l'application
	 * @param entiteInteraction
	 * @param logger
	 * @return
	 */
	public static JSONObject interactionEnJson (EntiteInteraction entiteInteraction, Logger logger) {
		ImmutableList<EntiteSubstance> entitesS = entiteInteraction.getEntitesSubstance(logger);
		Set<JSONObject> substancesJson = entitesS.stream()
			.map(Utils::substanceEnJson)
			.collect(Collectors.toSet());
		return new JSONObject()
			.put("risque", entiteInteraction.getRisque())
			.put("descriptif", entiteInteraction.getDescriptif())
			.put("conduite", entiteInteraction.getConduite())
			.put("substances", substancesJson)
			.put("medicaments", new JSONArray()
				.put(medicamentEnJson(entiteInteraction.getMedicament1(), logger))
				.put(medicamentEnJson(entiteInteraction.getMedicament2(), logger))
			);
	}

	private static JSONObject substanceEnJson (EntiteSubstance entiteSubstance) {
		return new JSONObject()
			.put("pays", entiteSubstance.getPays().code)
			.put("noms", nomsParLangueEnJson(entiteSubstance.getNomsParLangue()))
			.put("codeSubstance", entiteSubstance.getCode());
	}

	/**
	 * A utiliser à partir de la version 25 de l'application
	 * @param <P>
	 * @param entiteMedicament
	 * @param logger
	 * @return
	 */
	public static <P extends Presentation> JSONObject medicamentEnJson (AbstractEntiteMedicament<P> entiteMedicament, Logger logger) {
		Pays pays = entiteMedicament.getPays();
		Map<SubstanceActive, EntiteSubstance> substancesEntites = entiteMedicament.getSubstancesActivesSet()
			.parallelStream()
			.collect(Collectors.toConcurrentMap(
				Function.identity(),
				Unchecker.wrap(logger, (SubstanceActive sa) -> EntiteSubstance.obtenirEntite(pays, sa.codeSubstance).get())
			));
		Set<JSONObject> jsonSubstances = substancesEntites.entrySet().stream()
			.map(e -> substanceActiveEnJson(e.getKey(), e.getValue()))
			.collect(Collectors.toSet());
		Set<JSONObject> jsonPresentations = entiteMedicament.getPresentationsSet().stream()
			.map(Presentation::toJson)
			.collect(Collectors.toSet());
		return new JSONObject()
			.put("pays", pays.code)
			.put("noms", nomsParLangueEnJson(entiteMedicament.getNomsParLangue()))
			.put("forme", entiteMedicament.getForme())
			.put("marque", entiteMedicament.getMarque())
			.put("autorisation", entiteMedicament.getAutorisation())
			.put("codeMedicament", entiteMedicament.getCodeMedicament())
			.put("substances", jsonSubstances)
			.put("presentations", jsonPresentations)
			.put("expressionsCles", entiteMedicament.getExpressionsClesEffetsSet(logger));
	}

	private static JSONObject substanceActiveEnJson (SubstanceActive substanceActive, EntiteSubstance entiteSubstance) {
		return new JSONObject()
			.put("codeSubstance", substanceActive.codeSubstance)
			.put("dosage", substanceActive.dosage)
			.put("referenceDosage", substanceActive.referenceDosage)
			.put("noms", nomsParLangueEnJson(entiteSubstance.getNomsParLangue()));
	}

	private static JSONObject nomsParLangueEnJson (Map<Langue, Set<String>> nomsParLangue) {
		JSONObject json = new JSONObject();
		nomsParLangue.forEach((l, n) -> json.put(l.code, n));
		return json;
	}


	/* Méthodes dépréciées à partir de la version 25 de l'application
	-> à utiliser si le code de version reçu est null ou inférieur
	*/

	//@Deprecated
	public static JSONObject interactionEnJsonDepreciee (EntiteInteraction entiteI, Logger logger) 
		throws StorageException, URISyntaxException, InvalidKeyException, NoSuchElementException
	{
		ImmutableList<EntiteSubstance> entitesS = entiteI.getEntitesSubstance(logger);
		// TODO effacer après débogage
		logger.info("(débogage) (interactionEnJsonDepreciee) entiteI " + entiteI.getPartitionKey() + " " + entiteI.getRowKey() + " entiteS.size = " + entitesS.size());
		JSONObject jsonSubstances = new JSONObject();
		entitesS.forEach(e -> jsonSubstances.put(
			String.valueOf(e.getCode()),
			Optional.ofNullable(e.getNomsParLangue().get(Langue.Francais))
				.orElseGet(() -> e.getNomsParLangue().get(Langue.Latin))
		));
		return new JSONObject()
			.put("substances", jsonSubstances)
			.put("risque", entiteI.getRisque())
			.put("descriptif", entiteI.getDescriptif())
			.put("conduite", entiteI.getConduite())
			.put("medicaments", new JSONArray()
				.put(entiteI.getMedicament1().getCodeMedicament())
				.put(entiteI.getMedicament2().getCodeMedicament())
			);
	}

	//@Deprecated
	public static JSONObject medicamentFranceEnJsonDepreciee (EntiteMedicamentFrance entiteM, Logger logger)
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		Set<AbstractEntiteMedicament.SubstanceActive> substances = entiteM.getSubstancesActivesSet();
		JSONObject jsonSubstances = new JSONObject();
		substances.stream().parallel()
			.forEach(Unchecker.wrap(logger, (SubstanceActive substance) -> {
				Optional<EntiteSubstance> optEntiteS = EntiteSubstance.obtenirEntite(Pays.France, substance.codeSubstance);
				if (optEntiteS.isPresent()) {
					jsonSubstances.put(String.valueOf(substance.codeSubstance), new JSONObject()
						.put("dosage", substance.dosage)
						.put("referenceDosage", substance.referenceDosage)
						.put("noms", 
							Optional.ofNullable(optEntiteS.get().getNomsParLangue().get(Langue.Francais))
								.orElseGet(() -> optEntiteS.get().getNomsParLangue().get(Langue.Latin))
						)
					);
				}
			}));
		JSONObject jsonPresentations = new JSONObject();
		for (EntiteMedicamentFrance.PresentationFrance presentation : entiteM.getPresentationsSet()) {
			jsonPresentations.put(presentation.getNom(), new JSONObject()
				.put("prix", presentation.getPrix())
				.put("conditionsRemboursement", presentation.getConditionsRemboursement())
				.put("tauxRemboursement", presentation.getTauxRemboursement())
				.put("honorairesDispensation", presentation.getHonoraires())
			);
		}
		return new JSONObject()
			.put("noms", entiteM.getNomsLangue(Langue.Francais))
			.put("forme", entiteM.getForme())
			.put("marque", entiteM.getMarque())
			.put("autorisation", entiteM.getAutorisation())
			.put("codecis", String.valueOf(entiteM.getCodeMedicament()))
			.put("substances", jsonSubstances)
			.put("presentations", jsonPresentations)
			.put("effetsIndesirables", Optional
				.ofNullable(entiteM.getEffetsIndesirables())
				.orElse("")
			)
			.put("expressionsCles", entiteM.getExpressionsClesEffetsSet(logger));
	}

	
	/* 
	 * Autres méthodes utiles
	 */

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

}