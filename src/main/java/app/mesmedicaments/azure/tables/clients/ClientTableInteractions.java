package app.mesmedicaments.azure.tables.clients;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.table.EntityProperty;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Interaction;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;

public
class ClientTableInteractions
extends ClientTableAzure<Interaction> {

    static private KeysEntite makeKeys(Substance<?> substance1, Substance<?> substance2) {
        final String key1 = substance1.getPays().code + String.valueOf(substance1.getCode());
        final String key2 = substance2.getPays().code + String.valueOf(substance2.getCode());
        if (key1.compareTo(key2) > 0) {
            ClientTableAzure.getKeysEntite(key2, key1);
        }
        return ClientTableAzure.getKeysEntite(key1, key2);
    }

    public ClientTableInteractions() {
        super(
            Environnement.TABLE_INTERACTIONS,
            new AdapteurInteraction()
        );
    }

    /**
     * Ecrase toute interaction déjà existante sans vérification
     * @param interaction
     * @throws ExceptionTable
     */
    public void set(Set<Interaction> interactions) throws ExceptionTable {
        super.put(interactions
                .stream()
                .collect(Collectors.toMap(
                    i -> {
                        final List<Substance<?>> substances = new ArrayList<>(i.getSubstances());
                        return makeKeys(substances.get(0), substances.get(1));
                    },
                    i -> i
                )));
    }

    /**
     * L'{@link Optional} est vide s'il n'y a pas d'interaction.
     * @param substance1
     * @param substance2
     * @return
     */
    public Optional<Interaction> get(Substance<?> substance1, Substance<?> substance2)
        throws ExceptionTable
    {
        return super.get(makeKeys(substance1, substance2));
    }

    static protected class AdapteurInteraction extends Adapteur<Interaction, EntiteDynamique> {

        @Override
        public EntiteDynamique fromObject(Interaction interaction) {
            final Substance<?>[] substances = interaction.getSubstances().toArray(new Substance<?>[0]);
            if (substances.length != 2) throw new RuntimeException("Il y a plus (ou moins ?) de 2 substances dans l'interaction");
            final KeysEntite keys = makeKeys(substances[0], substances[1]);
            final EntiteDynamique entite = new EntiteDynamique(keys.partitionKey, keys.rowKey);
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
        public Interaction toObject(EntiteDynamique entite) {
            final Map<String, EntityProperty> props = entite.getProperties();
            final int risque = props.get("Risque").getValueAsInteger();
            final String descriptif = props.get("Descriptif").getValueAsString();
            final String conduite = props.get("Conduite").getValueAsString();
            final long codeSub1 = props.get("CodeSubstance1").getValueAsLong();
            final Pays paysSub1 = Pays.fromCode(props.get("PaysSubstance1").getValueAsString());
            final long codeSub2 = props.get("CodeSubstance2").getValueAsLong();
            final Pays paysSub2 = Pays.fromCode(props.get("PaysSubstance2").getValueAsString());
            final ClientTableSubstances clientSub = new ClientTableSubstances();
            try {
                final Substance<?> sub1 = clientSub.get(paysSub1, codeSub1).get();
                final Substance<?> sub2 = clientSub.get(paysSub2, codeSub2).get();
                final Set<Substance<?>> substances = new HashSet<>();
                substances.add(sub1);
                substances.add(sub2);
                return new Interaction(risque, descriptif, conduite, substances);
            } catch (ExceptionTable | NoSuchElementException e) {
                throw new RuntimeException(e);
            }

        }
    }
}