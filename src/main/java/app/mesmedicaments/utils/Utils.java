package app.mesmedicaments.utils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class Utils {

    public static final String NEWLINE = System.getProperty("line.separator");
    public static final ZoneId TIMEZONE = ZoneId.of("ECT", ZoneId.SHORT_IDS);

    private static final Map<String, String> cacheNormalisation = new ConcurrentHashMap<>();

    private Utils() {}

    public static String[] decouperTexte(String texte, int nbrDecoupes) {
        String[] retour = new String[nbrDecoupes];
        for (int i = 1; i <= nbrDecoupes; i++) {
            retour[i - 1] =
                    texte.substring(
                            texte.length() / nbrDecoupes * (i - 1),
                            i == nbrDecoupes ? texte.length() : texte.length() / nbrDecoupes * i);
        }
        return retour;
    }

    public static LocalDateTime dateToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), Utils.TIMEZONE);
    }

    public static void logErreur(Throwable t, Logger logger) {
        logger.severe(throwableToString(t));
    }

    private static String throwableToString(Throwable t) {
        String str = "EXCEPTION:" + NEWLINE + t.toString() + NEWLINE;
        final Throwable cause = t.getCause();
        if (cause != null) {
            str += NEWLINE + "* CAUSE:" + NEWLINE 
                + Arrays.stream(throwableToString(cause).split(NEWLINE))
                    .map(line -> "\t" + line)
                    .collect(Collectors.joining(NEWLINE))
                + NEWLINE; 
        }
        final String message = t.getMessage();
        if (message != null) {
            str += NEWLINE + "* MESSAGE:" + message + NEWLINE;
        }
        final StackTraceElement[] stack = t.getStackTrace();
        if (stack != null) {
            str += NEWLINE + "* STACKTRACE:" + NEWLINE
                + Arrays.stream(stack)
                    .map(line -> "\t> " + line)
                    .collect(Collectors.joining(NEWLINE))
                + NEWLINE;
        }
        return str;
    }

    public static long tempsDepuis(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    public static String normaliser(String original) {
        return cacheNormalisation.computeIfAbsent(
            original, 
            cle -> Normalizer.normalize(cle, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
        );
    }
}
