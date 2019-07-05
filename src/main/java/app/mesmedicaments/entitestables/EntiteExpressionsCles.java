package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.JSONObjectUneCle;

public class EntiteExpressionsCles extends AbstractEntite {

    private static final String TABLE = "expressionsCles";
    private static final String CLE_PARTITION = "effetsIndesirables";
    private static final String CLE_JSON = "effetsIndesirables";

    public static Optional<EntiteExpressionsCles> obtenirEntite (Long codeCis) 
        throws URISyntaxException, InvalidKeyException, StorageException
    {
        TableOperation operation = TableOperation.retrieve(
            CLE_PARTITION, 
            codeCis.toString(), 
            EntiteExpressionsCles.class
        );
        return Optional.ofNullable(
            obtenirCloudTable(TABLE)
                .execute(operation)
                .getResultAsType()
        );
    }

    String effetsIndesirables;

    public EntiteExpressionsCles (Long codeCis) 
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        super(TABLE, CLE_PARTITION, codeCis.toString());
    }

    /**
     * NE PAS UTILISER
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InvalidKeyException
     */
    public EntiteExpressionsCles ()
        throws StorageException, URISyntaxException, InvalidKeyException
    {
        super(TABLE);
    }

    public void setEffetsIndesirables (String effets) { this.effetsIndesirables = effets; }

    // TODO découper si trop grand
    public void definirEffetsIndesirablesCollection (Collection<String> effets) {
        this.effetsIndesirables = new JSONObjectUneCle(
            CLE_JSON, 
            new JSONArray(effets)
        ).toString();
    }

    public String getEffetsIndesirables () { return effetsIndesirables; }

    public Set<String> obtenirEffetsIndesirablesSet () {
        if (effetsIndesirables == null || effetsIndesirables.equals("")) 
            return new HashSet<>();
        JSONArray effets = new JSONObject(effetsIndesirables).getJSONArray(CLE_JSON);
        Set<String> retour = new HashSet<>();
        for (int i = 0; i < effets.length(); i++) {
            retour.add(effets.getString(i));
        }
        return retour;
    }

}