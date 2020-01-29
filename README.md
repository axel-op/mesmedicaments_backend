# mesmedicaments_backend

Ce dépôt contient le *back office* de l'application [Mes médicaments](https://play.google.com/store/apps/details?id=app.mesmedicaments).

Il s'agit d'une [application de fonctions](https://docs.microsoft.com/fr-fr/azure/azure-functions/functions-overview) hébergée sur Microsoft Azure.

Toutes les fonctions sont contenues dans le dossier `azure/fonctions` et chacune constitue un des points de terminaison de l'API, décrite ci-dessous.

## Description de l'API

### Définitions

* ***Produit*** : un *produit* peut désigner aussi bien un médicament qu'une substance.

* ***Code de langue*** ou ***code de pays*** : chaque pays et chaque langue est représenté(e) par un identifiant unique appelé *code de pays* et *code de langue*. Leur casse doit être respectée. Actuellement, l'API comporte un pays et deux langues :

    Pays | Code du pays
    ---- | ------------
    France | `france`

    Langue | Code de langue
    ------ | --------------
    Français | `francais`
    Latin | `latin`

* ***Représentation de désignations*** : chaque *produit* peut être désigné par un ou plusieurs noms, dans une ou plusieurs langues. L'expression *représentation de désignations* désigne un objet JSON (JSONObject) contenant ces noms. Les clés de l'objet sont les *codes des langues* dans lesquelles le *produit* possède au moins une désignation. Ces clés sont chacune associées à un tableau JSON (JSONArray) contenant l'ensemble des désignations du *produit* dans la langue correspondante. Les clés n'ayant pas de nom associé ne sont pas incluses.

    Exemple :

    ```json
    {
        "francais": ["MARRON D'INDE ECORCE", "MARRONIER ECORCE"],
        "latin": ["AESCULUS HIPPOCASTANUM CORTEX"]
    }
    ```

    Les *représentations de désignations* sont associées à une clé `noms` dans les *représentations de produit* (voir ci-dessous).

* ***Représentation de substance*** : un objet JSON représentant une substance, et contenant au minimum les trois champs suivants :

    Champ | Type | Description
    ----- | ---- | -----------
    `pays` | string | Le *code du pays* dans lequel cette substance a été enregistrée.
    `code` | integer | Code identifiant de manière unique cette substance parmi toutes celles du même pays.
    `noms` | objet | La *représentation de désignations* de cette substance.

    Lorsqu'elle est incluse dans une *représentation de médicament* (voir ci-dessous), une *représentation de substance* contient en plus des champs supplémentaires spécifiques au médicament.

    Pour la France :

    Champ | Type | Description
    ----- | ---- | -----------
    `dosage` | string | Dosage de la substance dans le médicament.
    `referenceDosage` | string | Unité de médicament sur laquelle est mesuré le dosage (ex. "un comprimé").

* ***Représentation de médicament*** : un objet JSON représentant un médicament. Ses champs diffèrent selon qu'il s'agit d'une requête à l'API ou d'une réponse de l'API.

  * Pour une requête :
  
    Champ | Type | Description
    ----- | ---- | -----------
    `pays` | string | Le *code de pays* du médicament.
    `code` | integer | Code identifiant de manière unique le médicament au sein du pays.

  * Pour une réponse :

    Champ | Type | Description
    ----- | ---- | -----------
    `pays` | string | Le *code de pays* du médicament.
    `code` | integer | Code identifiant de manière unique le médicament au sein du pays.
    `marque` | string | La marque du médicament.
    `effetsIndesirables` | string | Les potentiels effets indésirables du médicament (peut être vide mais pas *null*).
    `expressionsCles` | [string] | Les expressions clés identifiées parmi les effets indésirables.
    `substances` | [objet] | Les *représentations des substances* du médicament. Le code pays de ces substances est le même que celui du médicament.
    `noms` | objet | La *représentation de désignations* de ce médicament.
    `presentations` | [objet] | Les *représentations des présentations* du médicament (voir ci-dessous).

    Pour chaque pays, des champs supplémentaires peuvent être inclus.

    Pour la France :

    Champ | Type | Description
    ----- | ---- | -----------
    `forme` | string | La forme du médicament.

* ***Représentation de présentation*** : chaque médicament peut avoir un ou plusieurs formats (appelés présentations), représentés par un objet JSON dont les champs sont spécifiques à chaque pays.

    Pour la France :

    Champ | Type | Description | Valeur par défaut
    ----- | ---- | ----------- | ----------------
    `nom` | string | Nom de cette présentation. |
    `prix` | float | Prix (en euros) de cette présentation. | 0.0
    `tauxRemboursement` | integer | Pourcentage indiquant le taux de remboursement par l'Assurance Maladie de cette présentation. | 0
    `honorairesDispensation` | float | Honoraires de dispensation (en euros) de la pharmacie en cas de remboursement. | 0.0
    `conditionsRemboursement` | string | Conditions de remboursement par l'Assurance Maladie. | *null*

### Points de terminaison

* `/medicaments`
* `/recherche`
  * `/recherche/nombredocuments`
  * `/recherche/recherche`
* `/interactions`
* `/connexion`
  * `/connexion/1`
  * `/connexion/2`
* `/dmp`

A documenter.
