FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

# Keep the supported Ubuntu 24.04 base explicit; the floating tag added an unused Pebble daemon.
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system paymentops \
    && useradd --system --gid paymentops --home-dir /app paymentops \
    && mkdir -p /var/log/paymentops \
    && chown paymentops:paymentops /app /var/log/paymentops
COPY --chown=paymentops:paymentops --from=build /app/target/paymentops-*.jar app.jar
COPY --chown=paymentops:paymentops entrypoint.sh .
RUN chmod 0555 entrypoint.sh
USER paymentops
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["./entrypoint.sh"]
