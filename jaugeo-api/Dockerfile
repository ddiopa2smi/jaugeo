# Image de l'API Jaugeo (recette et production).
# Construction : docker build -t jaugeo-api jaugeo-api
# Les tests ne tournent pas ici : la CI les passe avant (mvn verify, Testcontainers).

FROM maven:3.9-eclipse-temurin-17 AS construction
WORKDIR /src
# Dépendances d'abord : la couche reste en cache tant que le pom ne change pas
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/jaugeo-api-*.jar extract --layers --launcher --destination /extrait

FROM eclipse-temurin:17-jre
ENV TZ=Africa/Dakar \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
RUN groupadd --system jaugeo && useradd --system --gid jaugeo --no-create-home jaugeo
WORKDIR /app
COPY --from=construction /extrait/dependencies/ ./
COPY --from=construction /extrait/spring-boot-loader/ ./
COPY --from=construction /extrait/snapshot-dependencies/ ./
COPY --from=construction /extrait/application/ ./
USER jaugeo
EXPOSE 8080
# Sonde sans outil supplémentaire : le port répond
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD bash -c '</dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
