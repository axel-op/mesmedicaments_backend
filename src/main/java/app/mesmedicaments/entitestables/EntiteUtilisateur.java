package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Date;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.TableOperation;

public class EntiteUtilisateur extends AbstractEntite {

    //private static final CloudTable TABLE_UTILISATEURS;
    private static final String CLE_PARTITION;

    static {
        CLE_PARTITION = "utilisateur";
        //TABLE_UTILISATEURS = obtenirCloudTable();
    }

    public static EntiteUtilisateur obtenirEntite (String id) 
        throws StorageException,
        URISyntaxException,
        InvalidKeyException
    {
        TableOperation operation = TableOperation.retrieve(CLE_PARTITION, id, EntiteUtilisateur.class);
        return obtenirCloudTable(System.getenv("tableazure_utilisateurs")).execute(operation).getResultAsType();
    }

    String prenom;
    String genre;
    String email;
    //String motDePasse;
    Date dateInscription;
    byte[] jwtSalt;
    Date derniereConnexion;

    /**
     * NE PAS UTILISER
     * @throws StorageException
     * @throws InvalidKeyException
     * @throws URISyntaxException
     */
    public EntiteUtilisateur ()
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        super(System.getenv("tableazure_utilisateurs"));
    }

    public EntiteUtilisateur (String id)
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        super(System.getenv("tableazure_utilisateurs"), CLE_PARTITION, id);
    }

    // Getters
    public String getPrenom () { return prenom; }
    public String getGenre () { return genre; }
    public String getEmail () { return email; }
    //public String getMotDePasse () { return motDePasse; }
    public Date getDateInscription () { return dateInscription; }
    public byte[] getJwtSalt () { return jwtSalt; }
    public Date getDerniereConnexion () { return derniereConnexion; }

    // Setters
    public void setPrenom (String prenom) { this.prenom = prenom; }
    public void setGenre (String genre) {
        char g = genre.charAt(0);
        if (g == 'M' || g == 'm') { this.genre = "M"; }
        else if (g == 'F' || g == 'f') { this.genre = "F"; }
        else { throw new IllegalArgumentException(); }
    }
    public void setEmail (String email) {
        // checker avec regex
        this.email = email;
    }
    //public void setMotDePasse (String motDePasse) { this.motDePasse = motDePasse; }
    public void setDateInscription (Date dateInscription) { this.dateInscription = dateInscription; }
    public void setJwtSalt (byte[] salt) { jwtSalt = salt; }
    public void setDerniereConnexion (Date date) { derniereConnexion = date; }

    // Affichage
	public String toString () {
		String s = "***EntiteUtilisateur***\nid = " + rowKey 
			+ "\nprenom = " + prenom 
			+ "\ngenre = " + genre 
			+ "\nemail = " + email
            + "\ndate inscription = " + dateInscription.toString()
            + "\ntimestamp = " + getTimestamp().toString()
			+ "\n******************";
		return s;
    }
}