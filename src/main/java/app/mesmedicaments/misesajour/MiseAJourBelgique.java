package app.mesmedicaments.misesajour;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

import app.mesmedicaments.Utils;

public final class MiseAJourBelgique {

    private MiseAJourBelgique () {}

    public static boolean handler (Logger logger) {
        try {
            //FilterInputStream;
            ZipInputStream zip = recupererZip();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                logger.info("entry name = " + entry.getName());
                
            }
        }
        catch (Exception e) {
            Utils.logErreur(e, logger);
            return false;
        }
        return true;
    }

    private static ZipInputStream recupererZip () throws IOException {
        final String url = "https://www.cbip.be/fr/downloads/file?type=EMD&name=/csv4Emd_Fr_1907A.zip"; // TODO trouver un moyen de générer l'URL automatiquement
        HttpsURLConnection connexion = (HttpsURLConnection) new URL(url).openConnection();
        connexion.setRequestMethod("GET"); // TODO vérifier encodage à la connexion puis à la création du ZIS
        return new ZipInputStream(connexion.getInputStream());
    }
}