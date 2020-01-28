FROM openjdk:8

COPY target/azure-functions/mesmedicaments/lib/* /project/lib/
COPY target/azure-functions/mesmedicaments/mesmedicaments-1.2-SNAPSHOT.jar /project/main.jar

ENTRYPOINT [ "java", "-cp", "/project/main.jar:/project/lib/*:." ]
CMD [ "app.mesmedicaments.azure.fonctions.privees.MiseAJour" ]