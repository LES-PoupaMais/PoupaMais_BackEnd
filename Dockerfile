# API do PoupaMais: build com o Maven wrapper (JDK 21) e runtime enxuto (JRE 21, usuário não-root)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true \
    && cp target/poupamais-backend-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S poupamais && adduser -S poupamais -G poupamais
COPY --from=build /app/app.jar app.jar
USER poupamais
EXPOSE 8080
# Porta 8080 aberta = contexto subiu (Flyway e validação do Hibernate já passaram)
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 CMD nc -z localhost 8080 || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
