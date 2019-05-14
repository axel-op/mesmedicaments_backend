package app.mesmedicaments;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.function.Function;
import java.util.logging.Logger;

//import org.json.JSONArray;

public final class Utils {

	//private static final String XORKEY;
	public static final String NEWLINE;
	private static HashMap<String, String> cacheNormalisation;
	public static final ZoneId TIMEZONE;

	static {
		NEWLINE = System.getProperty("line.separator");
		//XORKEY = System.getenv("cle_XOR");
		cacheNormalisation = new HashMap<>();
		TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);
	}

	private Utils () {}

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