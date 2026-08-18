FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
ARG SOURCE_CACHE_BUST=20260730-knowledge-delete
COPY src ./src
RUN for attempt in 1 2 3; do \
      mvn -q -DskipTests -Dmaven.wagon.http.retryHandler.count=5 package && exit 0; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "Maven package attempt $attempt failed; retrying in 5 seconds..."; \
      sleep 5; \
    done

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/hragentv1-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
