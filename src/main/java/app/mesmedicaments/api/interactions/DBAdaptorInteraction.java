package app.mesmedicaments.api.interactions;

import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import com.microsoft.azure.storage.table.EntityProperty;
import app.mesmedicaments.api.medicaments.ClientTableSubstances;
import app.mesmedicaments.database.DBAdaptor;
import app.mesmedicaments.database.DBException;
import app.mesmedicaments.database.azuretables.DBDocumentTableAzure;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;
import lombok.experimental.PackagePrivate;

@PackagePrivate
class DBAdaptorInteraction extends DBAdaptor<DBDocumentTableAzure, Interaction> {

    @Override
    public DBDocumentTableAzure fromObjectToDocument(Interaction interaction) {
        final Substance<?>[] substances = interaction.getSubstances().toArray(new Substance<?>[0]);
        if (substances.length != 2)
            throw new RuntimeException(
                    "Il y a plus (ou moins ?) de 2 substances dans l'interaction");
        final String[] keys = ClientTableInteractions.makeKeys(substances[0], substances[1]);
        final var entite = new DBDocumentTableAzure(keys[0], keys[1]);
        final Map<String, EntityProperty> props = entite.getProperties();
        props.put("Risque", new EntityProperty(interaction.risque));
        props.put("Descriptif", new EntityProperty(interaction.descriptif));
        props.put("Conduite", new EntityProperty(interaction.conduite));
        props.put("CodeSubstance1", new EntityProperty(substances[0].getCode()));
        props.put("PaysSubstance1", new EntityProperty(substances[0].getPays().code));
        props.put("CodeSubstance2", new EntityProperty(substances[1].getCode()));
        props.put("PaysSubstance2", new EntityProperty(substances[1].getPays().code));
        return entite;
    }

    @Override
    public Interaction fromDocumentToObject(DBDocumentTableAzure entite) {
        final Map<String, EntityProperty> props = entite.getProperties();
        final int risque = props.get("Risque").getValueAsInteger();
        final String descriptif = props.get("Descriptif").getValueAsString();
        final String conduite = props.get("Conduite").getValueAsString();
        final long codeSub1 = props.get("CodeSubstance1").getValueAsLong();
        final var paysSub1 = Pays.fromCode(props.get("PaysSubstance1").getValueAsString());
        final long codeSub2 = props.get("CodeSubstance2").getValueAsLong();
        final var paysSub2 = Pays.fromCode(props.get("PaysSubstance2").getValueAsString());
        try {
            final var clientSub = new ClientTableSubstances();
            final Substance<?> sub1 = clientSub.get(paysSub1, codeSub1).get();
            final Substance<?> sub2 = clientSub.get(paysSub2, codeSub2).get();
            final Set<Substance<?>> substances = new HashSet<>();
            substances.add(sub1);
            substances.add(sub2);
            return new Interaction(risque, descriptif, conduite, substances);
        } catch (DBException | NoSuchElementException e) {
            throw new RuntimeException(e);
        }

    }

}
