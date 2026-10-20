# Build from the repository root:
#   docker build -f payment-service/Dockerfile -t ledgerflow/payment-service .

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependency layer: changes only when a POM or the wrapper changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY architecture-rules/pom.xml architecture-rules/
COPY payment-service/pom.xml payment-service/
# Every module the parent POM lists must have its POM, even when it is not built.
COPY ledger-service/pom.xml ledger-service/
RUN ./mvnw -B -q -pl payment-service -am dependency:go-offline

COPY architecture-rules/src architecture-rules/src
COPY payment-service/src payment-service/src
RUN ./mvnw -B -q -pl payment-service -am package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
 && java -Djarmode=tools -jar payment-service/target/payment-service.jar extract --destination /workspace/extracted

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN groupadd --system payment && useradd --system --gid payment payment
WORKDIR /app
COPY --from=build /workspace/extracted/lib lib
COPY --from=build /workspace/extracted/payment-service.jar payment-service.jar
USER payment
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "payment-service.jar"]
