package app.mesmedicaments;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;

import org.json.JSONArray;

public class Utils {

	public final static String NEWLINE;
	private static String XORKey;

	static {
		NEWLINE = System.getProperty("line.separator");
		XORKey = System.getenv("cle_XOR");
	}

	private Utils () {}

	public static void logErreur(Throwable t, Logger logger) {
		logger.warning(t.getMessage());
		for (StackTraceElement trace : t.getStackTrace()) {
			logger.warning("\t" + trace.toString());
		}
	}

	public static int[] XOREncrypt (String str) {
		int[] output = new int[str.length()];
		for (int i = 0; i < output.length; i++) {
			output[i] = (Integer.valueOf(str.charAt(i)) 
				^ Integer.valueOf(XORKey.charAt(i % (XORKey.length() - 1))))
				+ '0';
		}
		return output;
	}

	public static String XORDecrypt (int[] input) {
		String output = "";
		for (int i = 0; i < input.length; i++) {
			output += (char) ((input[i] - 48)
				^ (int) XORKey.charAt(i % (XORKey.length() - 1)));
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
	}

	public static LocalDateTime obtenirHeure () {
		return LocalDateTime.now(ZoneId.of("ECT", ZoneId.SHORT_IDS));
	}

	public static boolean verifierHeure (String heure) {
		return verifierHeure(heure, 2);
	}
	
	public static boolean verifierHeure (String heure, long intervalle) {
		LocalDateTime heureObtenue;
		LocalDateTime maintenant = obtenirHeure();
		if (heure == null) { return false; }
		try { heureObtenue = LocalDateTime.parse(heure); }
		catch (DateTimeParseException e) { return false; }
		if (heureObtenue.isBefore(maintenant.minusMinutes(intervalle))) { return false; }
		if (heureObtenue.isAfter(maintenant.plusMinutes(intervalle))) { return false; }
		return true;
	}
}