package app.mesmedicaments.azure.tables.clients;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.ClasseSubstances;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.substances.Substance;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.unchecked.Unchecker;

public
class ClientTableClasseSubstances
extends ClientTableAzure<ClasseSubstances> {

    static private final String partition = "classe";

    private final Logger logger;

    public ClientTableClasseSubstances(Logger logger) {
        super(
            Environnement.TABLE_CLASSESSUBSTANCES,
            new AdapteurClasseSubstances()
        );
        this.logger = logger;
    }

    /**
     * N'écrase pas la classe mais fusionne l'ensemble de {@link Substance}s qu'elle contient
     * avec celui qui existe déjà (s'il y en a un).
     * 
     * @param classe
     */
    public void update(ClasseSubstances classe) throws ExceptionTable {
        final String rowKey = classe.nom;
        final Optional<ClasseSubstances> actuelle = super.get(partition, rowKey);
        if (!actuelle.isPresent()) super.set(classe, partition, rowKey);
        else {
            final Set<Substance<?>> union = classe.getSubstances();
            final boolean hasChanged = union.addAll(actuelle.get().getSubstances());
            if (hasChanged)
                super.set(new ClasseSubstances(classe.nom, union), partition, rowKey);
        }
    }

    static private class AdapteurClasseSubstances
    extends Adapteur<ClasseSubstances, EntiteDynamique> {

        @Override
        public EntiteDynamique fromObject(ClasseSubstances object) {
            final EntiteDynamique entite = new EntiteDynamique(partition, object.nom);
            final Map<String, Set<String>> substancesMap = new HashMap<>();
            for (Substance<?> substance : object.getSubstances()) {
                substancesMap.computeIfAbsent(substance.getPays().code, k -> new HashSet<>())
                    .add(String.valueOf(substance.getCode()));
            }
            entite.getProperties().put(
                "Substances", 
                new EntityProperty(new JSONObject(substancesMap).toString()));
            return entite;
        }

        @Override
        public ClasseSubstances toObject(EntiteDynamique entite) {
            final JSONObject subsJson = new JSONObject(
                entite.getProperties()
                    .get("Substances")
                    .getValueAsString());
            final ClientTableSubstances client = new ClientTableSubstances();
            final Set<Substance<?>> substances = subsJson
                .keySet()
                .stream()
                .flatMap(k -> JSONArrays.toSetString(subsJson.getJSONArray(k))
                    .stream()
                    .map(c -> new Tuple<>(Pays.fromCode(k), Long.parseLong(c))))
                .parallel()
                .map(Unchecker.panic((Tuple<Pays, Long> t) -> client.get(t.a, t.b)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
            return new ClasseSubstances(entite.getRowKey(), substances);
        }
        
    }

    static private class Tuple<A, B> {
        public final A a;
        public final B b;

        public Tuple(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }
}