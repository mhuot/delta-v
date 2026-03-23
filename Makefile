# ==============================================================================
# OpenNMS Build Facade
#
# Usage:
#   make build              Compile and install (tests skipped)
#   make test               Build and run all tests
#   make assemble           Assemble distribution (default profile)
#   make assemble PROFILE=dir|full|fulldir
#
# Overridable variables (set on command line or in environment):
#   PROFILE      Assembly profile: default | dir | full | fulldir (default: dir)
#   MAVEN_FLAGS  Extra Maven flags (default: -DskipTests -B)
#   MAVEN_OPTS   JVM options for Maven (has a sensible default below)
# ==============================================================================

PROFILE     ?= dir
MAVEN_FLAGS ?= -DskipTests -B
MAVEN_OPTS  ?= -Xmx3g \
               -XX:ReservedCodeCacheSize=512m \
               -XX:+TieredCompilation \
               -XX:TieredStopAtLevel=1 \
               -XX:-UseGCOverheadLimit \
               -XX:+UseParallelGC \
               -XX:-MaxFDLimit \
               -Djdk.util.zip.disableZip64ExtraFieldValidation=true \
               -Dmaven.wagon.http.retryHandler.count=3

MVN         := ./mvnw
COMMON      := -Djava.awt.headless=true \
               -Daether.connector.resumeDownloads=false \
               -Daether.connector.basic.threads=1 \
               -Droot.dir=$(CURDIR)

export MAVEN_OPTS

.PHONY: help build test assemble clean

.DEFAULT_GOAL := help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*##"}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Variables (override on command line):"
	@echo "  PROFILE      Assembly profile: default | dir | full | fulldir  (current: $(PROFILE))"
	@echo "  MAVEN_FLAGS  Extra Maven flags                                  (current: $(MAVEN_FLAGS))"
	@echo "  MAVEN_OPTS   JVM options passed to Maven"

build: ## Compile and package all modules (tests skipped)
	$(MVN) $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=default \
	  install

test: ## Build and run all tests (unit + integration)
	$(MVN) -B $(COMMON) \
	  -Dbuild.profile=default \
	  -DskipTests=false \
	  -DskipITs=false \
	  verify

assemble: ## Assemble the distribution; set PROFILE=dir|full|fulldir (default: dir)
	cd opennms-full-assembly && \
	$(CURDIR)/mvnw $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=$(PROFILE) \
	  install

clean: ## Remove all build artifacts
	$(MVN) -B clean
