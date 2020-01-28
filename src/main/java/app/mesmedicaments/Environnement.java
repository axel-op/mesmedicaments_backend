package app.mesmedicaments;

public
class Environnement {

    public static final String AZURE_SUBSCRIPTIONID = System.getenv("azure_subscriptionid");
    public static final String AZURE_RESOURCEGROUP = System.getenv("azure_resourcegroup");
    public static final String AZUREWEBJOBSSTORAGE = System.getenv("AzureWebJobsStorage");
    public static final String USERAGENT = System.getenv("user_agent");

    // ANALYSE TEXTE

    public static final String ANALYSETEXTE_ADRESSEAPI = System.getenv("analysetexte_adresseapi");
    public static final String ANALYSETEXTE_CLEAPI = System.getenv("analysetexte_cleapi");

    // DMP

    public static final String DMP_REGEX_ACCUEIL = System.getenv("regex_reussite_da");
    public static final String DMP_URL_CHOIX_CODE = System.getenv("url_post_choix_code");
    public static final String DMP_URL_CONNEXION_DMP = System.getenv("url_connexion_dmp");
    public static final String DMP_URL_POST_FORM_DMP = System.getenv("url_post_form_dmp");
    public static final String DMP_URL_ENVOI_CODE = System.getenv("url_post_envoi_code");
    public static final String DMP_URL_INFOS_DMP = System.getenv("url_infos_dmp");
    public static final String DMP_URL_LISTE_DOCS = System.getenv("url_liste_docs_dmp");
    public static final String DMP_URL_BASE = System.getenv("url_base_dmp");

    // LET'S ENCRYPT

    public static final String LETSENCRYPT_USERPASSWORD = System.getenv("letsencrypt_userpwd");
    public static final String LETSENCRYPT_PFXPASSWORD = System.getenv("letsencrypt_pfxpwd");
    public static final String LETSENCRYPT_CLIENTID = System.getenv("letsencrypt:ClientId");
    public static final String LETSENCRYPT_CLIENTSECRET = System.getenv("letsencrypt:ClientSecret");
    public static final String LETSENCRYPT_TENANT = System.getenv("letsencrypt:Tenant");
    public static final String LETSENCRYPT_RESOURCEGROUPNAME = System.getenv("letsencrypt:ResourceGroupName");
    public static final String LETSENCRYPT_SUBSCRIPTIONID = System.getenv("letsencrypt:SubscriptionId");

    // MAINTENANCE

    public static final String MAINTENANCE_DOCKERUSERNAME = maintenance("dockerusername");
    public static final String MAINTENANCE_DOCKERTOKEN = maintenance("dockertoken");
    public static final String MAINTENANCE_IMAGE = maintenance("image");
    public static final String MAINTENANCE_LOGANALYTICSID = maintenance("loganalyticsid");
    public static final String MAINTENANCE_LOGANALYTICSKEY = maintenance("loganalyticskey");

    // MISES A JOUR

    public static final String MISEAJOUR_CLASSES_URL = System.getenv("url_classes");
    public static final String MISEAJOUR_BELGIQUE_URLBASE = System.getenv("urlbase_belgique");
    public static final String MISEAJOUR_INTERACTIONS_URL = System.getenv("url_interactions");
    public static final String MISEAJOUR_FRANCE_URL_FICHIER_BDPM = System.getenv("url_cis_bdpm");
    public static final String MISEAJOUR_FRANCE_URL_FICHIER_COMPO = System.getenv("url_cis_compo_bdpm");
    public static final String MISEAJOUR_FRANCE_URL_FICHIER_PRESENTATIONS = System.getenv("url_cis_cip_bdpm");

    // RECHERCHE

    public static final String RECHERCHE_BASEURL = search("baseurl");
    public static final String RECHERCHE_ADMINKEY = search("adminkey");
    public static final String RECHERCHE_QUERYKEY = search("querykey");
    public static final String RECHERCHE_INDEXNAME = search("indexname");
    public static final String RECHERCHE_APIVERSION = search("apiversion");

    // TABLES

    public static final String TABLE_MEDICAMENTS = table("medicaments");
    public static final String TABLE_SUBSTANCES = table("substances");
    public static final String TABLE_CLASSESSUBSTANCES = table("classes");
    public static final String TABLE_INTERACTIONS = table("interactions");
    public static final String TABLE_UTILISATEURS = table("utilisateurs");
    public static final String TABLE_LEGAL = table("legal");
    public static final String TABLE_EXPRESSIONSCLES = table("expressionscles");

    private static final String maintenance(String var) {
        return System.getenv("maintenance_" + var);
    }

    private static final String table(String table) {
        return System.getenv("tableazure_" + table);
    }

    private static final String search(String var) {
        return System.getenv("search_" + var);
    }

    private Environnement() {}
}