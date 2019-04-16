package app.mesmedicaments;

import java.util.logging.Logger;

//import org.json.JSONArray;

public final class Utils {

	//private static final String XORKEY;
	public static final String NEWLINE;

	static {
		NEWLINE = System.getProperty("line.separator");
		//XORKEY = System.getenv("cle_XOR");
	}

	private Utils () {}

	public static void logErreur(Throwable t, Logger logger) {
		//debug
		if (t == null) { System.err.println("throwable est null"); }
		if (logger == null) { System.err.println("logger est null"); }
		logger.warning(t.toString());
		logger.warning(t.getCause().getMessage());
		for (StackTraceElement trace : t.getCause().getStackTrace()) {
			logger.warning("\t" + trace.toString());
		}
		logger.warning(t.getMessage());
		for (StackTraceElement trace : t.getStackTrace()) {
			logger.warning("\t" + trace.toString());
		}
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