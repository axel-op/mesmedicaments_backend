/*
package app.mesmedicaments.connexion;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class EntiteUtilisateur extends TableServiceEntity {
    
    private static final String CLE_PARTITION_INFORMATIONS;

    static { CLE_PARTITION_INFORMATIONS = System.getenv("clepartition_informations"); }

    
    protected static boolean stockerInfosUtilisateur (String id, String mdp, String prenom, String email) throws StorageException {
		EntiteUtilisateur entite = obtenirEntiteUtilisateur(id);
		boolean existeDeja = entite != null;
		if (!existeDeja
			|| !entite.getPrenom().equals(prenom)
			|| !entite.getEmail().equals(email)
		) {
			//EntiteUtilisateur nouvelleEntite = new EntiteUtilisateur(id, mdp, prenom, email);
			EntiteUtilisateur nouvelleEntite = new EntiteUtilisateur("", id);
			nouvelleEntite.setMotDePasseTemporaire(mdp);
			nouvelleEntite.setPrenom(prenom);
			nouvelleEntite.setEmail(email);
			CLOUDTABLE_UTILISATEURS.execute(
				TableOperation.insertOrMerge(nouvelleEntite));
		}
		return existeDeja;
    }
    
    protected EntiteUtilisateur obtenirEntiteUtilisateur (String id) throws StorageException {
		TableOperation operation = TableOperation.retrieve(
			CLE_PARTITION_INFORMATIONS, 
			id, 
			EntiteUtilisateur.class);
		return CLOUDTABLE_UTILISATEURS
			.execute(operation)
			.getResultAsType();
	}

    private String prenom;
    private String email;
    private String motDePasseTemporaire;

    public EntiteUtilisateur () {}

    String getPrenom() { return prenom; }
    void setPrenom(String prenom) { this.prenom = prenom; }
    String getEmail() { return email; }
    void setEmail(String email) { this.email = email; }
    String getMotDePasseTemporaire() { return motDePasseTemporaire; }
    void setMotDePasseTemporaire(String mdp) { motDePasseTemporaire = mdp; }
}
*/