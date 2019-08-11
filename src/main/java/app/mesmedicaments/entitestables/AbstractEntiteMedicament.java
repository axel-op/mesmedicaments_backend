package app.mesmedicaments.entitestables;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.CloudTable;
import com.microsoft.azure.storage.table.Ignore;
import com.microsoft.azure.storage.table.TableBatchOperation;

import org.json.JSONArray;
import org.json.JSONObject;

import app.mesmedicaments.AnalyseTexte;
import app.mesmedicaments.JSONArrays;
import app.mesmedicaments.Utils;
import app.mesmedicaments.unchecked.Unchecker;

public abstract class AbstractEntiteMedicament<P extends AbstractEntiteMedicament.Presentation> extends AbstractEntite {

    protected static final String TABLE = System.getenv("tableazure_medicaments");

    protected static <P extends Presentation, E extends AbstractEntiteMedicament<P>> Optional<E> 
        obtenirEntite (Pays pays, long code, Class<E> clazzType)
        throws StorageException, URISyntaxException, InvalidKeyException 
    {
        return obtenirEntite(TABLE, pays.code, String.valueOf(code), clazzType);
    }

    protected static <P extends Presentation, E extends AbstractEntiteMedicament<P>> Set<E>
        obtenirEntites (Pays pays, Set<Long> codes, Class<E> clazzType, Logger logger)
    {
        return codes.parallelStream()
            .map(Unchecker.wrap(logger, (Long c) -> obtenirEntite(pays, c, clazzType)))
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    public static <P extends Presentation, E extends AbstractEntiteMedicament<P>> Iterable<E> 
        obtenirToutesLesEntites (Pays pays, Class<E> clazzType)
        throws StorageException, URISyntaxException, InvalidKeyException 
    {
        return obtenirToutesLesEntites(TABLE, pays.code, clazzType);
    }

    /**
     * Possibilité de mélanger les partitions, car un tri est effectué. 
     * @param <P> Type de l'objet Présentation
     * @param <E> Type de l'entité Médicament
     * @param entites Entités Médicament, éventuellement de partitions (pays) différentes
     * @throws StorageException
     * @throws InvalidKeyException
     * @throws URISyntaxException
     */
    public static <P extends Presentation, E extends AbstractEntiteMedicament<P>> void mettreAJourEntitesBatch 
        (Iterable<E> entites)
        throws StorageException, InvalidKeyException, URISyntaxException
    {
        CloudTable cloudTable = obtenirCloudTable(TABLE);
        Map<String, Set<E>> parPartition = new ConcurrentHashMap<>();
        for (E entite : entites) {
            entite.checkConditions();
            parPartition.computeIfAbsent(entite.getPartitionKey(), k -> new HashSet<>())
                .add(entite);
        }
        for (Entry<String, Set<E>> entree : parPartition.entrySet()) {
            Set<E> entitesPartition = entree.getValue();
            TableBatchOperation batchOp = new TableBatchOperation();
            for (E entite : entitesPartition) {
                batchOp.insertOrMerge(entite);
                if (batchOp.size() >= 100) {
                    cloudTable.execute(batchOp);
                    batchOp.clear();
                }
            }
            if (!batchOp.isEmpty()) cloudTable.execute(batchOp);
        }
    }
    
    String noms;
    String forme;
    String autorisation;
    String marque;
    String substancesActives;
    String presentations;
    String effetsIndesirables;
    String expressionsClesEffets;
    
    protected final Map<Langue, Set<String>> nomsMap = new HashMap<>();
    protected final Set<SubstanceActive> substancesSet = new HashSet<>();
    protected final Set<P> presentationsSet = new HashSet<>();
    protected final Set<String> expressionsClesEffetsSet = new HashSet<>();

    // Constructeurs

    public AbstractEntiteMedicament (Pays pays, long code) {
        super(TABLE, pays.code, String.valueOf(code));
    }
    
    /**
     * NE PAS UTILISER
     */
    public AbstractEntiteMedicament () {
        super(TABLE);
    }

    /* Getters */

    public String getForme () { return forme; }
    public String getAutorisation () { return autorisation; }
    public String getMarque () { return marque; }
    public String getEffetsIndesirables () { return effetsIndesirables; }
    
    public String getExpressionsClesEffets () {
        JSONArray arrayExpr = new JSONArray(expressionsClesEffetsSet);
        return arrayExpr.toString();
    }

    @Ignore
    public Set<String> getExpressionsClesEffetsSet (Logger logger) {
        String effetsInd = getEffetsIndesirables();
        if (expressionsClesEffetsSet.isEmpty() && effetsInd != null) {
            try {
                expressionsClesEffetsSet.addAll(AnalyseTexte
                    .obtenirExpressionsCles(effetsInd));
            }
            catch (IOException e) { Utils.logErreur(e, logger); }
        }
        return new HashSet<>(expressionsClesEffetsSet);
    }
    
    public String getPresentations () {
        JSONArray arrayPres = new JSONArray();
        presentationsSet.forEach(p -> arrayPres.put(p.toJson()));
        return arrayPres.toString();
    }

    @Ignore
    public Set<P> getPresentationsSet () {
        return new HashSet<>(presentationsSet);
    }

    public String getNoms () { 
        JSONObject json = new JSONObject();
        for (Entry<Langue, Set<String>> entree : nomsMap.entrySet())
            json.put(entree.getKey().code, entree.getValue());
        return json.toString();
    }

    @Ignore
    public Map<Langue, Set<String>> getNomsParLangue () {
        return new HashMap<>(nomsMap);
    }

    @Ignore
    public Set<String> getNomsLangue (Langue langue) {
        return Optional
            .ofNullable(nomsMap.get(langue))
            .orElseGet(HashSet::new);
    }

    public String getSubstancesActives () {
        JSONArray arraySub = new JSONArray();
        substancesSet.forEach(s -> arraySub.put(s.toJson()));
        return arraySub.toString();
    }

    @Ignore
    public Set<SubstanceActive> getSubstancesActivesSet () {
        return new HashSet<>(substancesSet);
    }

    @Ignore
    public Long getCodeMedicament () {
        return Long.parseLong(getRowKey());
    }

    @Ignore
    public Pays getPays () {
        return Pays.obtenirPays(getPartitionKey());
    }

    /* Setters */
    
    public void setForme (String forme) { this.forme = forme; }
    public void setAutorisation (String autorisation) { this.autorisation = autorisation; }
    public void setMarque (String marque) { this.marque = marque; }

    public void setEffetsIndesirables (String effets) {
        this.effetsIndesirables = effets
            .replaceFirst("Comme tous les médicaments, ce médicament peut provoquer des effets indésirables, mais ils ne surviennent pas systématiquement chez tout le monde\\.", "")
            .replaceAll("\\?dème", "œdème")
            .replaceAll("c\\?ur", "cœur"); // TODO Ajouter ce que j'ai mis dans l'appli
    }

    public void setExpressionsClesEffets (String expressionsCles) {
        if (expressionsCles != null) {
            Set<String> exprCles = JSONArrays.toSetString(new JSONArray(expressionsCles));
            expressionsClesEffetsSet.addAll(exprCles);
        }
        this.expressionsClesEffets = expressionsCles;
    }
    
    public void setPresentations (String presentations) {
        this.presentations = Optional
            .ofNullable(presentations)
            .orElseGet(() -> new JSONArray().toString());
        JSONArray arrayPres = new JSONArray(this.presentations);
        Set<JSONObject> presJson = new HashSet<>();
        for (int i = 0; i < arrayPres.length(); i++) {
            presJson.add(arrayPres.getJSONObject(i));
        }
        setPresentationsJson(presJson);
    }

    @Ignore
    public abstract void setPresentationsJson (Set<JSONObject> presJson);
    
    /**
     * Cela effacera les présentations déjà présentes
     * @param presentations
     */
    @Ignore
    public void setPresentationsIterable (Iterable<P> presentations) {
        presentationsSet.clear();
        if (presentations != null)
            presentations.forEach(presentationsSet::add);
    }

    public void ajouterPresentation (P presentation) {
        presentationsSet.add(presentation);
    }

    public void setNoms (String noms) {
        this.noms = noms;
        JSONObject nomsJson = new JSONObject(noms);
        for (String cle : nomsJson.keySet()) {
            JSONArray arrayNomsLangue = nomsJson.getJSONArray(cle);
            nomsMap.put(Langue.obtenirLangue(cle), JSONArrays.toSetString(arrayNomsLangue));
        }
    }

    public void ajouterNom (Langue langue, String nom) {
        nomsMap.computeIfAbsent(langue, k -> new HashSet<>()).add(nom);
    }

    public void ajouterNoms (Langue langue, Set<String> noms) {
        nomsMap.computeIfAbsent(langue, k -> new HashSet<>()).addAll(noms);
    }

    public void setSubstancesActives (String substancesActives) {
        this.substancesActives = substancesActives;
        JSONArray json = new JSONArray(substancesActives);
        for (int i = 0; i < json.length(); i++) {
            JSONObject subJson = json.getJSONObject(i);
            substancesSet.add(SubstanceActive.fromJson(subJson));
        }
    }

    @Ignore
    public void setSubstancesActivesIterable (Iterable<SubstanceActive> substances) {
        substances.forEach(substancesSet::add);
    }

    public void ajouterSubstanceActive (SubstanceActive substance) {
        substancesSet.add(substance);
    }


    public static class SubstanceActive {
        public final long codeSubstance;
        public final String dosage;
        public final String referenceDosage;

        static SubstanceActive fromJson (JSONObject json) {
            return new SubstanceActive(
                json.getLong("code"),
                json.getString("dosage"),
                json.getString("referenceDosage")
            );
        }

        public SubstanceActive (long code, String dosage, String referenceDosage) {
            this.codeSubstance = code;
            this.dosage = dosage != null ? dosage : "";
            this.referenceDosage = referenceDosage != null ? referenceDosage : "";
        }

        JSONObject toJson () {
            return new JSONObject()
                .put("code", codeSubstance)
                .put("dosage", dosage)
                .put("referenceDosage", referenceDosage);
        }

        @Override
        public boolean equals (Object o) {
            if (!(o instanceof SubstanceActive)) return false;
            SubstanceActive other = (SubstanceActive) o;
            return Long.compare(this.codeSubstance, other.codeSubstance) == 0;
        }

        @Override
        public int hashCode () { return Long.hashCode(codeSubstance); }
    }

    public static abstract class Presentation {

        Presentation (JSONObject json) { fromJson(json); }
        protected Presentation () {}

        public abstract JSONObject toJson ();
        protected abstract void fromJson (JSONObject json);
    }

}