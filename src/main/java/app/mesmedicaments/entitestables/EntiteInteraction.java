package app.mesmedicaments.entitestables;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.Ignore;
import com.microsoft.azure.storage.table.TableBatchOperation;

import app.mesmedicaments.entitestables.AbstractEntiteMedicament.Presentation;
import app.mesmedicaments.unchecked.Unchecker;

public class EntiteInteraction extends AbstractEntite {

    private static final String TABLE = System.getenv("tableazure_interactions");

    public static Optional<EntiteInteraction> obtenirEntite(EntiteSubstance entite1, EntiteSubstance entite2)
            throws StorageException, URISyntaxException, InvalidKeyException {
        return obtenirEntite(obtenirCle(entite1), obtenirCle(entite2));
    }

    private static Optional<EntiteInteraction> obtenirEntite(String cle1, String cle2)
            throws StorageException, URISyntaxException, InvalidKeyException {
        String[] clesOrdonnees = obtenirClesOrdonnees(cle1, cle2);
        return obtenirEntite(TABLE, clesOrdonnees[0], clesOrdonnees[1], EntiteInteraction.class);
    }

    /**
     * 
     * @param entites Les entités à mettre à jour, éventuellement de partitions
     *                différentes
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InvalidKeyException
     */
    public static void mettreAJourEntitesBatch(Logger logger, Collection<EntiteInteraction> entites)
            throws StorageException, URISyntaxException, InvalidKeyException {
        Map<String, Set<EntiteInteraction>> parPartition = new HashMap<>();
        for (EntiteInteraction entite : entites) {
            entite.checkConditions();
            parPartition.computeIfAbsent(entite.getPartitionKey(), k -> new HashSet<>()).add(entite);
        }
        CloudTable cloudTable = obtenirCloudTable(TABLE);
        parPartition.values().parallelStream().forEach(set -> {
            StreamSupport.stream(Iterables.partition(set, 100).spliterator(), true)
                    .forEach(Unchecker.wrap(logger, groupe -> {
                        TableBatchOperation batchOp = new TableBatchOperation();
                        groupe.forEach(batchOp::insertOrMerge);
                        cloudTable.execute(batchOp);
                    }));
        });
    }

    public static <E extends AbstractEntiteMedicament<? extends Presentation>> Set<EntiteInteraction> obtenirInteractions(
            Logger logger, Set<E> medicaments) {
        if (medicaments.size() < 2)
            return new HashSet<>();
        return Sets.combinations(medicaments, 2).parallelStream().map(ArrayList::new)
                .map(Unchecker.wrap(logger, (List<E> comb) -> obtenirInteractions(logger, comb.get(0), comb.get(1))))
                .flatMap(Set::stream).collect(Collectors.toSet());
    }

    public static Set<EntiteInteraction> obtenirInteractions(Logger logger,
            AbstractEntiteMedicament<? extends Presentation> medicament1,
            AbstractEntiteMedicament<? extends Presentation> medicament2) {
        Function<AbstractEntiteMedicament<? extends Presentation>, Set<String>> subEnCles = med -> med
                .getSubstancesActivesSet().stream().map(sub -> med.getPays().code + sub.codeSubstance)
                .collect(Collectors.toSet());
        Set<String> clesSub1 = subEnCles.apply(medicament1);
        Set<String> clesSub2 = subEnCles.apply(medicament2);
        Set<List<String>> requetes = Sets.cartesianProduct(clesSub1, clesSub2);
        return requetes.stream().parallel().filter(cles -> !cles.get(0).equals(cles.get(1))).flatMap(
                Unchecker.wrap(logger, (List<String> cles) -> obtenirEntite(cles.get(0), cles.get(1)).map(e -> {
                    e.setMedicament1(medicament1);
                    e.setMedicament2(medicament2);
                    return Stream.of(e);
                }).orElseGet(Stream::empty))).collect(Collectors.toSet());
    }

    private static String obtenirCle(EntiteSubstance entiteSubstance) {
        return entiteSubstance.getPays().code + entiteSubstance.getCode();
    }

    private static String[] obtenirClesOrdonnees(EntiteSubstance entiteSubstance1, EntiteSubstance entiteSubstance2) {
        return obtenirClesOrdonnees(obtenirCle(entiteSubstance1), obtenirCle(entiteSubstance2));
    }

    private static String[] obtenirClesOrdonnees(String cle1, String cle2) {
        int comparaison = cle1.compareToIgnoreCase(cle2);
        return new String[] { comparaison == -1 ? cle1 : cle2, comparaison == -1 ? cle2 : cle1 };
    }

    int risque;
    String descriptif;
    String conduite;
    String paysSubstance1;
    String paysSubstance2;
    String codeSubstance1;
    String codeSubstance2;

    private AbstractEntiteMedicament<? extends Presentation> entiteMedicament1;
    private AbstractEntiteMedicament<? extends Presentation> entiteMedicament2;

    public EntiteInteraction(EntiteSubstance entite1, EntiteSubstance entite2) {
        super(TABLE, obtenirClesOrdonnees(entite1, entite2)[0], obtenirClesOrdonnees(entite1, entite2)[1]);
        this.paysSubstance1 = entite1.getPays().code;
        this.paysSubstance2 = entite2.getPays().code;
        this.codeSubstance1 = String.valueOf(entite1.getCode());
        this.codeSubstance2 = String.valueOf(entite2.getCode());
    }

    /**
     * NE PAS UTILISER
     */
    public EntiteInteraction() {
        super(TABLE);
    }

    @Override
    public boolean conditionsARemplir() {
        return true;
    }

    /* Getters */

    public int getRisque() {
        return risque;
    }

    public String getDescriptif() {
        return descriptif;
    }

    public String getConduite() {
        return conduite;
    }

    public String getPaysSubstance1() {
        return paysSubstance1;
    }

    public String getPaysSubstance2() {
        return paysSubstance2;
    }

    public String getCodeSubstance1() {
        return codeSubstance1;
    }

    public String getCodeSubstance2() {
        return codeSubstance2;
    }

    @Ignore
    public EntiteSubstance getEntiteSubstance1() throws StorageException, InvalidKeyException, URISyntaxException {
        return EntiteSubstance.obtenirEntite(Pays.obtenirPays(getPaysSubstance1()), Long.parseLong(getCodeSubstance1()))
                .get();
    }

    @Ignore
    public EntiteSubstance getEntiteSubstance2() throws StorageException, InvalidKeyException, URISyntaxException {
        return EntiteSubstance.obtenirEntite(Pays.obtenirPays(getPaysSubstance2()), Long.parseLong(getCodeSubstance2()))
                .get();
    }

    @Ignore
    public ImmutableList<EntiteSubstance> getEntitesSubstance(Logger logger) {
        return IntStream.range(0, 2).boxed().parallel()
                .map(Unchecker.wrap(logger,
                        (Integer i) -> i.intValue() == 1 ? getEntiteSubstance1() : getEntiteSubstance2()))
                .collect(ImmutableList.toImmutableList());
    }

    @Ignore
    public AbstractEntiteMedicament<? extends Presentation> getMedicament1() {
        return entiteMedicament1;
    }

    @Ignore
    public AbstractEntiteMedicament<? extends Presentation> getMedicament2() {
        return entiteMedicament2;
    }

    /* Setters */

    public void setRisque(int risque) {
        if (risque < 1 || risque > 4) {
            throw new IllegalArgumentException();
        }
        this.risque = risque;
    }

    public void setDescriptif(String descriptif) {
        this.descriptif = descriptif;
    }

    public void setConduite(String conduite) {
        this.conduite = conduite;
    }

    public void setPaysSubstance1(String pays) {
        this.paysSubstance1 = pays;
    }

    public void setPaysSubstance2(String pays) {
        this.paysSubstance2 = pays;
    }

    public void setCodeSubstance1(String code) {
        this.codeSubstance1 = code;
    }

    public void setCodeSubstance2(String code) {
        this.codeSubstance2 = code;
    }

    @Ignore
    protected <P extends Presentation> void setMedicament1(AbstractEntiteMedicament<P> entiteMedicament) {
        entiteMedicament1 = entiteMedicament;
    }

    @Ignore
    protected <P extends Presentation> void setMedicament2(AbstractEntiteMedicament<P> entiteMedicament) {
        entiteMedicament2 = entiteMedicament;
    }

}