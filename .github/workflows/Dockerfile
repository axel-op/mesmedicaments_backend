FROM openjdk:8

ENV url_cis_bdpm="http://base-donnees-publique.medicaments.gouv.fr/telechargement.php?fichier=CIS_bdpm.txt"
ENV url_cis_compo_bdpm="http://base-donnees-publique.medicaments.gouv.fr/telechargement.php?fichier=CIS_COMPO_bdpm.txt"
ENV url_cis_cip_bdpm="http://base-donnees-publique.medicaments.gouv.fr/telechargement.php?fichier=CIS_CIP_bdpm.txt"
ENV tableazure_utilisateurs="utilisateurs"
ENV tableazure_connexions="connexions"
ENV tableazure_medicaments="medicaments"
ENV tableazure_classes="classesSubstances"
ENV tableazure_interactions="interactions"
ENV tableazure_substances="substances"
ENV FUNCTIONS_WORKER_RUNTIME="java"

COPY target/azure-functions/mesmedicaments/lib/* /project/lib/
COPY target/azure-functions/mesmedicaments/mesmedicaments-1.2-SNAPSHOT.jar /project/main.jar

ENTRYPOINT [ "java", "-cp", "/project/main.jar:/project/lib/*:." ]
CMD [ "app.mesmedicaments.api.privee.MiseAJour" ]