# Build from the repository root:
#   docker build -f risk-service/Dockerfile -t ledgerflow/risk-service .

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependency layer: changes only when a POM or the wrapper changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY architecture-rules/pom.xml architecture-rules/
COPY messaging-contracts/pom.xml messaging-contracts/
COPY messaging-support/pom.xml messaging-support/
COPY risk-service/pom.xml risk-service/
# Every module the parent POM lists must have its POM, even when it is not built.
COPY ledger-service/pom.xml ledger-service/
COPY payment-service/pom.xml payment-service/
RUN ./mvnw -B -q -pl risk-service -am dependency:go-offline

COPY architecture-rules/src architecture-rules/src
COPY messaging-contracts/src messaging-contracts/src
COPY messaging-support/src messaging-support/src
COPY risk-service/src risk-service/src
RUN ./mvnw -B -q -pl risk-service -am package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
 && java -Djarmode=tools -jar risk-service/target/risk-service.jar extract --destination /workspace/extracted

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN groupadd --system risk && useradd --system --gid risk risk
WORKDIR /app
COPY --from=build /workspace/extracted/lib lib
COPY --from=build /workspace/extracted/risk-service.jar risk-service.jar
USER risk
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "risk-service.jar"]
