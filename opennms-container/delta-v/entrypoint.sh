#!/bin/sh
#
# Shared entrypoint for all Delta-V Spring Boot daemons.
# Launches the daemon using classpath-based execution.
#
# Environment variables:
#   MAIN_CLASS  — baked into image at build time (from MANIFEST.MF Start-Class)
#   JAVA_OPTS   — JVM options (set in docker-compose.yml per daemon)
#
if [ -z "$MAIN_CLASS" ]; then
    echo "ERROR: MAIN_CLASS not set" >&2
    exit 1
fi

exec java $JAVA_OPTS \
    -cp "/opt/libs/priority/*:/opt/libs/external/*:/opt/libs/internal/*:/opt/libs/daemon/*:/opt/app/*" \
    "$MAIN_CLASS"
