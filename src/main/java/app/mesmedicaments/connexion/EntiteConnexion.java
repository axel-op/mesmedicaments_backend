package app.mesmedicaments.connexion;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.TableOperation;
import com.microsoft.azure.storage.table.TableResult;
import com.microsoft.azure.storage.table.TableServiceEntity;

import org.json.JSONObject;

public class EntiteConnexion extends TableServiceEntity {

    private static final String CLE_PARTITION_CONNEXIONS;
    private static final String CHAINE_CONN_TABLES;
    private static final String TABLE_UTILISATEURS;

    static { 
        CHAINE_CONN_TABLES = System.getenv("connexion_tablesazure");
        System.err.println("chaine de connexion = " + CHAINE_CONN_TABLES);
        ///////// échanger la clé avec KeyVault
        CLE_PARTITION_CONNEXIONS = System.getenv("clepartition_connexions"); 
        TABLE_UTILISATEURS = System.getenv("tableazure_utilisateurs");
    }

    protected static boolean creerEntite (String id, String sid, String tformdata, HashMap<String, String> cookies) 
        throws StorageException,
        URISyntaxException,
        InvalidKeyException
    {
        EntiteConnexion entite = new EntiteConnexion(id, sid, tformdata, cookies);
        System.err.println("Création de l'objet CloudTable");
        CloudTable tableUtilisateurs = CloudStorageAccount
            .parse(CHAINE_CONN_TABLES)
            .createCloudTableClient()
            .getTableReference(TABLE_UTILISATEURS); 
        TableOperation operation = TableOperation.insertOrMerge(entite);
        System.err.println("Execution de la requete");
        TableResult resultat = tableUtilisateurs.execute(operation);
        System.err.println(resultat.toString());
        System.err.println(resultat.getHttpStatusCode());
        return resultat.getHttpStatusCode() >= 200
            && resultat.getHttpStatusCode() < 300;
	}

    protected static EntiteConnexion obtenirEntite (String id) 
        throws IllegalArgumentException,
        InvalidKeyException,
        URISyntaxException,
        StorageException
    {
        CloudTable tableUtilisateurs = CloudStorageAccount
            .parse(CHAINE_CONN_TABLES)
            .createCloudTableClient()
            .getTableReference(TABLE_UTILISATEURS); 
        TableOperation operation = TableOperation.retrieve(
            CLE_PARTITION_CONNEXIONS, 
            id, 
            EntiteConnexion.class);
        return tableUtilisateurs
            .execute(operation)
            .getResultAsType();
	}

    String sid;
    String tformdata;
    String cookies;

    /*public EntiteConnexion (String partition, String id) {
        this.partitionKey = CLE_PARTITION_CONNEXIONS;
        this.rowKey = id;
    }*/
    // Le type HashMap n'est pas supporté et doit être String dès en entrant pour les cookies
    public EntiteConnexion (String id, String sid, String tformdata, String cookies) {
        this.partitionKey = CLE_PARTITION_CONNEXIONS;
        this.rowKey = id;
        this.sid = sid;
        this.tformdata = tformdata;
        this.cookies = cookies;
    }

    public EntiteConnexion (String id, String sid, String tformdata, HashMap<String, String> cookies) {
        this(id, sid, tformdata, new JSONObject(cookies).toString());
    }

    public EntiteConnexion () {}

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }
    public String getTformdata() { return tformdata; }
    public void setTformdata(String tformdata) { this.tformdata = tformdata; }
    public String getCookies() { return cookies; }
    public HashMap<String, String> obtenirCookiesMap() {
        HashMap<String, String> map = new HashMap<>();
        HashMap<String, Object> cookiesMap = new HashMap<>(new JSONObject(cookies).toMap());
        for (String cookie : cookiesMap.keySet()) {
            map.put(cookie, cookiesMap.get(cookie).toString());
        }
        return map;
    }
    public void setCookies(String cookies) { this.cookies = cookies; }
    public void definirCookiesMap(HashMap<String,String> cookies) {
        this.cookies = new JSONObject(cookies).toString();
    }

    public String toString () {
        String s = "id = " + rowKey 
            + "\nsid = " + sid 
            + "\ntformdata = " + tformdata 
            + "\ntimestamp = " + getTimestamp().toString()
            + "\ncookies = " + cookies;
        return s;
    }
}