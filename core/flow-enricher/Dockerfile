FROM eclipse-temurin:21-jre-alpine

# curl is needed by the docker-compose healthcheck.
RUN apk add --no-cache curl && \
    addgroup -g 10001 deltav && \
    adduser -u 10001 -G deltav -D deltav

COPY --chown=deltav:deltav target/flow-enricher-*.jar /app/flow-enricher.jar

USER deltav

EXPOSE 8080

# JAVA_TOOL_OPTIONS is read natively by the JVM — override heap via the
# docker-compose environment block (e.g. JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m).
ENTRYPOINT ["java", "-jar", "/app/flow-enricher.jar"]
