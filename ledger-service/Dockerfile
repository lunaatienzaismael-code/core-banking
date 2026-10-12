# Build from the repository root:
#   docker build -f ledger-service/Dockerfile -t ledgerflow/ledger-service .

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependency layer: changes only when a POM or the wrapper changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY architecture-rules/pom.xml architecture-rules/
COPY ledger-service/pom.xml ledger-service/
RUN ./mvnw -B -q -pl ledger-service -am dependency:go-offline

COPY architecture-rules/src architecture-rules/src
COPY ledger-service/src ledger-service/src
RUN ./mvnw -B -q -pl ledger-service -am package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
 && java -Djarmode=tools -jar ledger-service/target/ledger-service.jar extract --destination /workspace/extracted

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN groupadd --system ledger && useradd --system --gid ledger ledger
WORKDIR /app
COPY --from=build /workspace/extracted/lib lib
COPY --from=build /workspace/extracted/ledger-service.jar ledger-service.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "ledger-service.jar"]
