package app.mesmedicaments;

import java.util.logging.Logger;

public class Utils {

		public final static String NEWLINE = System.getProperty("line.separator");

		private Utils () {}

		public static void logErreur(Throwable t, Logger logger) {
			logger.warning(t.getMessage());
			for (StackTraceElement trace : t.getStackTrace()) {
				logger.warning("\t" + trace.toString());
			}
		}
		
}