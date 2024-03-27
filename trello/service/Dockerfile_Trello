FROM openjdk:17
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} Trelloapp.jar
COPY src/main/resources/application.properties application.properties
ENTRYPOINT ["java","-jar","/Trelloapp.jar"]