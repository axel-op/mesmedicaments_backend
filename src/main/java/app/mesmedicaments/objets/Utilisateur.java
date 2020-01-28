package app.mesmedicaments.objets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.Sets;

import app.mesmedicaments.objets.medicaments.Medicament;

public
class Utilisateur {

    public final String idDMP;
    public final LocalDateTime dateInscription;
    private final ConcurrentMap<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsPerso;
    private final ConcurrentMap<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsDMP;

    public Utilisateur(
        String idDMP,
        LocalDateTime dateInscription,
        Map<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsPerso,
        Map<LocalDate, Set<Medicament<?, ?, ?>>> medicamentsDMP
    ) {
        this.idDMP = idDMP;
        this.dateInscription = dateInscription;
        this.medicamentsPerso = new ConcurrentHashMap<>(medicamentsPerso);
        this.medicamentsDMP = new ConcurrentHashMap<>(medicamentsDMP);
    }

    public Map<LocalDate, Set<Medicament<?, ?, ?>>> getMedicamentsDMP() {
        return new HashMap<>(medicamentsDMP);
    }

    public Map<LocalDate, Set<Medicament<?, ?, ?>>> getMedicamentsPerso() {
        return new HashMap<>(medicamentsPerso);
    }
    public void ajouterMedicamentsDMP(LocalDate date, Collection<? extends Medicament<?, ?, ?>> medicaments) {
        ajouterMedicaments(medicamentsDMP, date, medicaments);
    }

    public void ajouterMedicamentsPerso(LocalDate date, Collection<? extends Medicament<?, ?, ?>> medicaments) {
        ajouterMedicaments(medicamentsPerso, date, medicaments);
    }

    public void supprimerMedicamentsDMP(LocalDate date, Collection<? extends Medicament<?, ?, ?>> medicaments) {
        supprimerMedicaments(medicamentsDMP, date, medicaments);
    }

    public void supprimerMedicamentsPerso(LocalDate date, Collection<? extends Medicament<?, ?, ?>> medicaments) {
        supprimerMedicaments(medicamentsPerso, date, medicaments);
    }

    private void ajouterMedicaments(
        Map<LocalDate, Set<Medicament<?, ?, ?>>> map, 
        LocalDate date, 
        Collection<? extends Medicament<?, ?, ?>> medicaments
    ) {
        map.computeIfAbsent(date, k -> Sets.newConcurrentHashSet())
            .addAll(medicaments);
    }

    private void supprimerMedicaments(
        Map<LocalDate, Set<Medicament<?, ?, ?>>> map,
        LocalDate date,
        Collection<? extends Medicament<?, ?, ?>> medicaments
    ) {
        final Set<Medicament<?, ?, ?>> actuels = map.get(date);
        if (actuels != null) actuels.removeAll(medicaments);
    }

}