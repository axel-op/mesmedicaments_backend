package app.mesmedicaments.misesajour;

import app.mesmedicaments.Utils;
import app.mesmedicaments.entitestables.AbstractEntite.Langue;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament;
import app.mesmedicaments.entitestables.AbstractEntiteMedicament.SubstanceActive;
import app.mesmedicaments.entitestables.EntiteMedicamentBelgique;
import com.microsoft.azure.storage.StorageException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class MiseAJourBelgique {

    private MiseAJourBelgique() {}

    public static boolean handler(Logger logger) {
        try {
            ZipInputStream zis = recupererZip(logger);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                logger.info("zipentry name = " + entry.getName());
                if (entry.getName().startsWith("AMP"))
                    parserXMLproduits(obtenirReader(zis), logger);
            }
        } catch (Exception e) {
            Utils.logErreur(e, logger);
            return false;
        }
        return true;
    }

    private static InputStreamReader obtenirReader(ZipInputStream zis) {
        return new InputStreamReader(
                new FilterInputStream(zis) {
                    @Override
                    public void close() throws IOException {
                        zis.closeEntry();
                    }
                },
                StandardCharsets.UTF_8);
    }

    private static ZipInputStream recupererZip(Logger logger) throws IOException {
        // TODO vérifier que tout fonctionne avec le delta
        final String urlBase = System.getenv("urlbase_belgique");
        final String userAgent = System.getenv("user_agent");
        logger.info("Récupération du fichier zip...");
        long startTime = System.currentTimeMillis();
        Response reponse1 =
                Jsoup.connect(urlBase + "/websamcivics/samcivics/home/home.html")
                        .userAgent(userAgent)
                        .method(Method.GET)
                        .execute();
        Map<String, String> nouveauxCookies = new HashMap<>();
        for (String cookie : reponse1.multiHeaders().get("Set-Cookie")) {
            String[] split = cookie.split("; ")[0].split("=");
            nouveauxCookies.put(split[0], split[1]);
        }
        Document document1 = reponse1.parse();
        Element elForm = document1.getElementById("j_idt16");
        String urlPost = urlBase + elForm.attr("action");
        Map<String, String> inputAttr = new HashMap<>();
        for (Element el : elForm.getElementsByTag("input")) {
            inputAttr.put(el.attributes().get("name"), el.val());
        }
        Elements elsListe = document1.getElementsContainingOwnText("Télécharger Full (Samv2)");
        if (elsListe.size() != 1)
            throw new RuntimeException(
                    "Impossible de trouver l'élément de liste qui renvoie à la page de téléchargement");
        Element elListe = elsListe.first();
        String texteAMatcher = elListe.attributes().get("onclick");
        Matcher matcher = Pattern.compile("\\{.*:.*\\}").matcher(texteAMatcher);
        if (!matcher.find())
            throw new RuntimeException(
                    "Impossible de récupérer les valeurs pour la requête POST inclus dans l'élément onclick");
        String match = matcher.group();
        String[] reqProperty1 = trimQuotes(match.substring(1, match.length() - 1)).split("':'");
        inputAttr.put(trimQuotes(reqProperty1[0]), trimQuotes(reqProperty1[1]));
        Document document2 =
                Jsoup.connect(urlPost)
                        .userAgent(userAgent)
                        .cookies(nouveauxCookies)
                        .data(inputAttr)
                        .post();
        Elements elsListeFichiers = document2.getElementsByAttributeValue("value", "Télécharger");
        if (elsListeFichiers.isEmpty())
            throw new RuntimeException(
                    "Impossible d'afficher la liste des fichiers téléchargeables");
        Element elFichierATelecharger = null;
        Integer version = null;
        for (Element el : elsListeFichiers) {
            Element parent = el.parent().parent();
            for (Element elTd : parent.getElementsByTag("td")) {
                if (elTd.ownText().matches("[0-9]{4}")) {
                    int versionParent = Integer.parseInt(elTd.ownText());
                    if (version == null || versionParent > version) {
                        elFichierATelecharger = el;
                        version = versionParent;
                    }
                }
            }
        }
        inputAttr.clear();
        inputAttr.put(elFichierATelecharger.attributes().get("name"), elFichierATelecharger.val());
        inputAttr.put("formDownload", "formDownload");
        inputAttr.put("formDownload:exportType", "FULL");
        Element formDownload = document2.getElementById("formDownload");
        Element javaxFaces = formDownload.getElementById("javax.faces.ViewState");
        inputAttr.put(javaxFaces.attributes().get("name"), javaxFaces.val());
        Response reponse2 =
                Jsoup.connect(urlBase + formDownload.attr("action"))
                        .userAgent(userAgent)
                        .method(Method.POST)
                        .cookies(nouveauxCookies)
                        .data(inputAttr)
                        .ignoreContentType(true)
                        .maxBodySize(0)
                        .timeout(0)
                        .execute();
        logger.info("Fichier atteint en " + Utils.tempsDepuis(startTime) + " ms");
        return new ZipInputStream(reponse2.bodyStream());
    }

    private static void parserXMLproduits(Reader xmlAMP, Logger logger)
            throws XMLStreamException, StorageException, URISyntaxException, InvalidKeyException {
        Set<EntiteMedicamentBelgique> entitesCreees = new HashSet<>();
        logger.info("Parsing du fichier AMP...");
        long startTime = System.currentTimeMillis();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(xmlAMP);
        EntiteMedicamentBelgique medEnCours = null;
        boolean nomsEnCours = false;
        boolean nomsFormeEnCours = false;
        boolean prescNameEnCours = false;
        boolean companyEnCours = false;
        boolean pharmaFormEnCours = false;
        boolean raiEnCours = false;
        boolean dansAMPC = false;
        boolean dansAMPP = false;
        boolean substanceActive = false;
        Integer codeSubEnCours = null;
        String dosageSubEnCours = null;
        String nomPresEnCours = null;
        Double prixPresEnCours = null;
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamReader.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "Amp":
                        String code = reader.getAttributeValue(null, "code");
                        medEnCours =
                                new EntiteMedicamentBelgique(
                                        formaterCodeAMP(code)); // TODO modifier pour recup entité
                        // existante
                        break;
                    case "AmpComponent":
                        dansAMPC = true;
                        break;
                    case "Ampp":
                        dansAMPP = true;
                        break;
                    case "PrescriptionName":
                        if (dansAMPP) prescNameEnCours = true;
                        break;
                    case "Company":
                        companyEnCours = true;
                        break;
                    case "PharmaceuticalForm":
                        if (dansAMPC) pharmaFormEnCours = true;
                        break;
                    case "RealActualIngredient":
                        raiEnCours = true;
                        break;
                    case "Type":
                        if (raiEnCours)
                            substanceActive = reader.getElementText().equals("ACTIVE_SUBSTANCE");
                        break;
                    case "Substance":
                        codeSubEnCours = Integer.parseInt(reader.getAttributeValue(null, "code"));
                        break;
                    case "Strength":
                        if (raiEnCours) {
                            String unite = reader.getAttributeValue(null, "unit");
                            Double quantite = Double.parseDouble(reader.getElementText());
                            dosageSubEnCours = quantite + unite;
                        }
                    case "Name":
                        if (pharmaFormEnCours) nomsFormeEnCours = true;
                        if (!(dansAMPC || dansAMPP)) nomsEnCours = true;
                        break;
                    case "Status":
                        if (!(dansAMPC || dansAMPP))
                            medEnCours.setAutorisation(reader.getElementText());
                        break;
                    case "Denomination":
                        if (companyEnCours) medEnCours.setMarque(reader.getElementText());
                        break;
                    case "ExFactoryPrice":
                        prixPresEnCours = Double.parseDouble(reader.getElementText());
                        break;
                    case "Fr":
                        if (nomsEnCours)
                            medEnCours.ajouterNom(Langue.Francais, reader.getElementText());
                        if (prescNameEnCours) nomPresEnCours = reader.getElementText();
                        if (nomsFormeEnCours) {
                            String nouvForme = reader.getElementText();
                            String forme = medEnCours.getForme();
                            if (forme == null) forme = "";
                            String[] formes = forme.split(", ");
                            boolean deja = false;
                            for (String f : formes) if (f.equalsIgnoreCase(nouvForme)) deja = true;
                            if (!deja) {
                                if (forme.length() > 0) forme += ", ";
                                forme += nouvForme;
                                medEnCours.setForme(forme);
                            }
                        }
                        break;
                }
            }
            if (eventType == XMLStreamReader.END_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "Name":
                        if (nomsFormeEnCours) nomsFormeEnCours = false;
                        else nomsEnCours = false;
                        break;
                    case "Amp":
                        entitesCreees.add(medEnCours);
                        break;
                    case "AmpComponent":
                        dansAMPC = false;
                        break;
                    case "Ampp":
                        /*
                         * TODO à terminer if (nomPresEnCours != null)
                         * medEnCours.ajouterPresentation(new PresentationBelgique( nomPresEnCours,
                         * prixPresEnCours ));
                         */
                        nomPresEnCours = null;
                        prixPresEnCours = null;
                        dansAMPP = false;
                        break;
                    case "PrescriptionName":
                        if (dansAMPP) prescNameEnCours = false;
                        break;
                    case "Company":
                        companyEnCours = false;
                        break;
                    case "RealActualIngredient":
                        if (substanceActive)
                            medEnCours.ajouterSubstanceActive(
                                    new SubstanceActive(codeSubEnCours, dosageSubEnCours, null));
                        dosageSubEnCours = null;
                        codeSubEnCours = null;
                        substanceActive = false;
                        raiEnCours = false;
                        break;
                    case "PharmaceuticalForm":
                        if (pharmaFormEnCours) pharmaFormEnCours = false;
                        break;
                }
            }
        }
        logger.info("XML AMP parsé en " + Utils.tempsDepuis(startTime) + " ms");
        AbstractEntiteMedicament.mettreAJourEntitesBatch(entitesCreees);
    }

    private static long formaterCodeAMP(String code) {
        if (!code.matches("SAM[0-9]{6}-[0-9]{2}"))
            throw new IllegalArgumentException("Le format des codes AMP a changé");
        return Long.parseLong(code.replaceAll("[^0-9]", ""));
    }

    /*
     * private static void parserXMLSubstances (Reader xmlCompo, Logger logger)
     * throws XMLStreamException, StorageException, URISyntaxException,
     * InvalidKeyException { Set<EntiteSubstance> substancesTrouvees = new
     * HashSet<>(); logger.info("Parsing des substances..."); long startTime =
     * System.currentTimeMillis(); XMLInputFactory factory =
     * XMLInputFactory.newInstance(); XMLStreamReader reader =
     * factory.createXMLStreamReader(xmlCompo); EntiteSubstance subEnCours = null;
     * while (reader.hasNext()) { int eventType = reader.next(); if (eventType ==
     * XMLStreamReader.START_ELEMENT) { System.out.println("start el = " +
     * reader.getLocalName()); if
     * (reader.getLocalName().equalsIgnoreCase("CompoundingIngredient")) { if
     * (subEnCours != null) substancesTrouvees.add(subEnCours); String code =
     * reader.getAttributeValue(null, "code"); subEnCours = new
     * EntiteSubstance(Pays.Belgique, Long.parseLong(code));
     * System.out.println("\tcode = " + code); } if
     * (reader.getLocalName().equalsIgnoreCase("Synonym")) { String langue =
     * reader.getAttributeValue(null, "lang"); String nom = reader.getElementText();
     * if (langue.equalsIgnoreCase("fr")) subEnCours.ajouterNom(Langue.Français,
     * nom); if (langue.equalsIgnoreCase("la")) subEnCours.ajouterNom(Langue.Latin,
     * nom); System.out.println("\tnom = " + nom); } } }
     * logger.info(substancesTrouvees.size() + " substances créées en " +
     * Utils.tempsDepuis(startTime) + " ms");
     * EntiteSubstance.mettreAJourEntitesBatch(substancesTrouvees); }
     */

    private static final ConcurrentMap<String, String> cacheTrimQuotes = new ConcurrentHashMap<>();

    private static String trimQuotes(String str) {
        return cacheTrimQuotes.computeIfAbsent(str, s -> s.replaceAll("^(['\"])(.*)\\1$", "$2"));
    }
}
