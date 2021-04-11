package app.mesmedicaments.api.interactions;

import java.util.Optional;
import app.mesmedicaments.Environnement;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBClientTableAzure;
import app.mesmedicaments.database.azuretables.DBExceptionTableAzure;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.substances.Substance;
import lombok.experimental.PackagePrivate;

public class ClientTableInteractions extends DBClientTableAzure<Interaction> {

    static @PackagePrivate String[] makeKeys(Substance<?> substance1, Substance<?> substance2) {
        final String key1 = substance1.getPays().code + String.valueOf(substance1.getCode());
        final String key2 = substance2.getPays().code + String.valueOf(substance2.getCode());
        if (key1.compareTo(key2) > 0)
            return new String[] {key2, key1};
        return new String[] {key1, key2};
    }

    @PackagePrivate
    ClientTableInteractions() throws DBExceptionTableAzure {
        super(Environnement.TABLE_INTERACTIONS, Environnement.AZUREWEBJOBSSTORAGE,
                new DBAdaptorInteraction());
    }

    /**
     * L'{@link Optional} est vide s'il n'y a pas d'interaction.
     * 
     * @param substance1
     * @param substance2
     * @return
     * @throws DBException
     */
    public Optional<Interaction> get(Substance<?> substance1, Substance<?> substance2)
            throws DBException {
        final String[] keys = makeKeys(substance1, substance2);
        return super.get(new String[] {keys[0], keys[1]});
    }

}
