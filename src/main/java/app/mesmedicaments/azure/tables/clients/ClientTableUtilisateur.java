package app.mesmedicaments.azure.tables.clients;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.table.EntityProperty;

import org.json.JSONObject;

import app.mesmedicaments.Environnement;
import app.mesmedicaments.azure.tables.ClientTableAzure;
import app.mesmedicaments.azure.tables.EntiteDynamique;
import app.mesmedicaments.basededonnees.Adapteur;
import app.mesmedicaments.basededonnees.ExceptionTable;
import app.mesmedicaments.objets.Pays;
import app.mesmedicaments.objets.Utilisateur;
import app.mesmedicaments.objets.medicaments.Medicament;
import app.mesmedicaments.utils.JSONArrays;
import app.mesmedicaments.utils.Utils;
import app.mesmedicaments.utils.unchecked.Unchecker;

public
class ClientTableUtilisateur
extends ClientTableAzure<Utilisateur> {

    static private final String partition = "utilisateur";

    public ClientTableUtilisateur() {
        super(
            Environnement.TABLE_UTILISATEURS,
            new AdapteurUtilisateur()
        );
    }

    public Optional<Utilisateur> get(String idDMP) throws ExceptionTable {
        return super.get(partition, idDMP);
    }

    public void set(Utilisateur utilisateur) throws ExceptionTable {
        super.set(utilisateur, partition, utilisateur.idDMP);
    }

    static protected class AdapteurUtilisateur extends Adapteur<Utilisateur, EntiteDynamique> {

        @Override
        public EntiteDynamique fromObject(Utilisateur utilisateur) {
            final String rowKey = utilisateur.idDMP;
            final EntiteDynamique entite = new EntiteDynamique(partition, rowKey);
            final Map<String, EntityProperty> props = entite.getProperties();
            props.put("DateInscription", new EntityProperty(
                Date.from(
                    utilisateur.dateInscription
                        .atZone(Utils.TIMEZONE)
                        .toInstant())));
            props.put("MedicamentsDMP", new EntityProperty(
                toJSON(utilisateur.getMedicamentsDMP()).toString()));
            props.put("MedicamentsPerso", new EntityProperty(
                toJSON(utilisateur.getMedicamentsPerso()).toString()));
            return entite;            
        }

        private JSONObject toJSON(Map<LocalDate, Set<Medicament<?, ?, ?>>> medicaments) {
            final JSONObject json = new JSONObject();
            medicaments.entrySet().forEach(
                e -> json.put(
                    e.getKey().toString(),
                    toJSON(e.getValue())));
            return json;
        }

        private JSONObject toJSON(Set<Medicament<?, ?, ?>> medicaments) {
            final Map<String, Set<Long>> mapJson = new HashMap<>();
            medicaments.forEach(m -> mapJson.computeIfAbsent(
                m.getPays().code,
                k -> new HashSet<>())
                    .add(m.getCode()));
            return new JSONObject(mapJson);
        }

        @Override
        public Utilisateur toObject(EntiteDynamique document) {
            final String idDmp = document.getRowKey();
            final Map<String, EntityProperty> props = document.getProperties();
            final LocalDateTime dateInscription = props.get("DateInscription")
                .getValueAsDate()
                .toInstant()
                .atZone(Utils.TIMEZONE)
                .toLocalDateTime();
            final Map<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsPerso = 
                toMap(new JSONObject(props.get("MedicamentsPerso").getValueAsString()));
            final Map<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsDMP =
                toMap(new JSONObject(props.get("MedicamentsDMP").getValueAsString()));
            return new Utilisateur(idDmp, dateInscription, medicamentsPerso, medicamentsDMP);            
        }

        private Map<LocalDate, Set<Medicament<?, ?, ?>>> toMap(JSONObject json) {
            return json.keySet()
                .parallelStream()
                .collect(Collectors.toConcurrentMap(
                    k -> LocalDate.parse(k),
                    k -> toSetMedicaments(json.getJSONObject(k))
                ));
        }

        private Set<Medicament<?, ?, ?>> toSetMedicaments(JSONObject json) {
            final Set<Integer> codes = JSONArrays.toSetInt(json.getJSONArray(Pays.France.instance.code));
            final ClientTableMedicamentsFrance client = new ClientTableMedicamentsFrance();
            return codes
                .parallelStream()
                .map(Unchecker.panic(client::get))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        }

    }

}