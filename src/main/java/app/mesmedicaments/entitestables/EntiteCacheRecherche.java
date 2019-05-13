package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Optional;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

import org.json.JSONArray;

public class EntiteCacheRecherche extends AbstractEntite {

    private static final String TABLE = "cacheRecherche";

    public static Optional<EntiteCacheRecherche> obtenirEntite (String recherche)
		throws URISyntaxException, InvalidKeyException
	{
		try {
			TableOperation operation = TableOperation.retrieve(
				recherche.substring(0, Math.min(3, recherche.length())), 
				recherche, 
				EntiteCacheRecherche.class);
			return Optional.ofNullable(
				obtenirCloudTable(TABLE)
				.execute(operation)
				.getResultAsType()
			);
		}
		catch (StorageException e) {
			return Optional.empty();
		}
	}
	
	String resultats;
	int nombre;
    
    public EntiteCacheRecherche (String recherche) 
		throws StorageException, InvalidKeyException, URISyntaxException
	{
		super(
			TABLE, 
			recherche.substring(0, Math.min(3, recherche.length())), 
			recherche
		);
	}

	/**
	 * NE PAS UTILISER
	 * @throws StorageException
	 * @throws URISyntaxException
	 * @throws InvalidKeyException
	 */
	public EntiteCacheRecherche () 
		throws StorageException, URISyntaxException, InvalidKeyException
	{
		super(TABLE);
	}

	public void setResultats (String resultats) { this.resultats = resultats; }
	public void setNombre (int nombre) { this.nombre = nombre; }
	public String getResultats () { return this.resultats; }
	public int getNombre () { return this.nombre; }
	public JSONArray obtenirResultatsJArray () { return new JSONArray(resultats); }
}