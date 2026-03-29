#!/usr/bin/env bash
# compute-shared-libs.sh — Extract Spring Boot fat JARs and deduplicate shared dependencies.
#
# Produces a staging/ directory with:
#   shared-external/   — 3rd-party JARs present in ALL 12 daemons
#   shared-internal/   — org.opennms project JARs present in ALL 12 daemons
#   <daemon>/libs/     — JARs unique to this daemon
#   <daemon>/app/      — thin application JAR (classes + resources only)
#   <daemon>/.main_class — fully-qualified Start-Class from MANIFEST.MF

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------
if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <repo-root> <version>"
    echo "  e.g. $0 /path/to/delta-v 36.0.0-SNAPSHOT"
    exit 1
fi

REPO_ROOT="$1"
VERSION="$2"

# ---------------------------------------------------------------------------
# Daemon definitions: name → relative path from REPO_ROOT (without .jar)
# ---------------------------------------------------------------------------
declare -A DAEMON_JAR_BASE
DAEMON_JAR_BASE=(
    [alarmd]="core/daemon-boot-alarmd/target/org.opennms.core.daemon-boot-alarmd-${VERSION}"
    [bsmd]="core/daemon-boot-bsmd/target/org.opennms.core.daemon-boot-bsmd-${VERSION}"
    [collectd]="core/daemon-boot-collectd/target/org.opennms.core.daemon-boot-collectd-${VERSION}"
    [discovery]="core/daemon-boot-discovery/target/org.opennms.core.daemon-boot-discovery-${VERSION}"
    [enlinkd]="core/daemon-boot-enlinkd/target/org.opennms.core.daemon-boot-enlinkd-${VERSION}"
    [eventtranslator]="core/daemon-boot-eventtranslator/target/org.opennms.core.daemon-boot-eventtranslator-${VERSION}"
    [perspectivepollerd]="core/daemon-boot-perspectivepollerd/target/org.opennms.core.daemon-boot-perspectivepollerd-${VERSION}"
    [pollerd]="core/daemon-boot-pollerd/target/org.opennms.core.daemon-boot-pollerd-${VERSION}"
    [provisiond]="core/daemon-boot-provisiond/target/org.opennms.core.daemon-boot-provisiond-${VERSION}"
    [syslogd]="core/daemon-boot-syslogd/target/org.opennms.core.daemon-boot-syslogd-${VERSION}"
    [telemetryd]="core/daemon-boot-telemetryd/target/org.opennms.core.daemon-boot-telemetryd-${VERSION}"
    [trapd]="core/daemon-boot-trapd/target/org.opennms.core.daemon-boot-trapd-${VERSION}"
)

# Sorted daemon list for deterministic iteration
DAEMONS=($(printf '%s\n' "${!DAEMON_JAR_BASE[@]}" | sort))
DAEMON_COUNT=${#DAEMONS[@]}

echo "=== compute-shared-libs.sh ==="
echo "Repo root : ${REPO_ROOT}"
echo "Version   : ${VERSION}"
echo "Daemons   : ${DAEMON_COUNT}"
echo ""

# ---------------------------------------------------------------------------
# Resolve fat JAR paths (prefer -boot.jar, fall back to plain .jar)
# ---------------------------------------------------------------------------
declare -A FAT_JAR
missing=0
for daemon in "${DAEMONS[@]}"; do
    base="${REPO_ROOT}/${DAEMON_JAR_BASE[$daemon]}"
    if [[ -f "${base}-boot.jar" ]]; then
        FAT_JAR[$daemon]="${base}-boot.jar"
    elif [[ -f "${base}.jar" ]]; then
        FAT_JAR[$daemon]="${base}.jar"
    else
        echo "ERROR: No fat JAR found for ${daemon}"
        echo "  Tried: ${base}-boot.jar"
        echo "  Tried: ${base}.jar"
        missing=1
    fi
done
if [[ $missing -ne 0 ]]; then
    echo "Aborting — missing fat JARs."
    exit 1
fi

# ---------------------------------------------------------------------------
# Prepare directories
# ---------------------------------------------------------------------------
STAGING="${SCRIPT_DIR}/staging"
EXTRACT_DIR="${SCRIPT_DIR}/.extract-tmp"

rm -rf "${STAGING}" "${EXTRACT_DIR}"
mkdir -p "${STAGING}/shared-external" "${STAGING}/shared-internal"
mkdir -p "${EXTRACT_DIR}"

# ---------------------------------------------------------------------------
# Step 1: Extract all fat JARs
# ---------------------------------------------------------------------------
echo "--- Extracting fat JARs ---"
for daemon in "${DAEMONS[@]}"; do
    jar="${FAT_JAR[$daemon]}"
    dest="${EXTRACT_DIR}/${daemon}"
    echo "  ${daemon}: $(basename "${jar}")"
    java -Djarmode=tools -jar "${jar}" extract --destination "${dest}" 2>&1 | sed 's/^/    /'
done
echo ""

# ---------------------------------------------------------------------------
# Step 2: Extract Start-Class from each fat JAR's MANIFEST.MF
# ---------------------------------------------------------------------------
echo "--- Extracting Start-Class ---"
for daemon in "${DAEMONS[@]}"; do
    jar="${FAT_JAR[$daemon]}"
    # MANIFEST.MF uses 72-byte lines with "\r\n " continuation; merge them before extracting
    main_class=$(unzip -p "${jar}" META-INF/MANIFEST.MF \
        | tr -d '\r' \
        | sed -e ':a' -e 'N' -e '$!ba' -e 's/\n //g' \
        | grep "^Start-Class:" \
        | sed 's/^Start-Class: *//')
    if [[ -z "${main_class}" ]]; then
        echo "ERROR: No Start-Class found in ${jar}"
        exit 1
    fi
    mkdir -p "${STAGING}/${daemon}"
    echo "${main_class}" > "${STAGING}/${daemon}/.main_class"
    echo "  ${daemon}: ${main_class}"
done
echo ""

# ---------------------------------------------------------------------------
# Step 3: Compute strict intersection of lib/ directories
# ---------------------------------------------------------------------------
echo "--- Computing shared libraries (strict intersection of all ${DAEMON_COUNT} daemons) ---"

# Build a count of how many daemons contain each JAR filename
declare -A JAR_COUNT
for daemon in "${DAEMONS[@]}"; do
    lib_dir="${EXTRACT_DIR}/${daemon}/lib"
    if [[ ! -d "${lib_dir}" ]]; then
        echo "ERROR: No lib/ directory found for ${daemon} at ${lib_dir}"
        exit 1
    fi
    for jar_file in "${lib_dir}"/*.jar; do
        name="$(basename "${jar_file}")"
        JAR_COUNT[$name]=$(( ${JAR_COUNT[$name]:-0} + 1 ))
    done
done

# Partition: shared (count == DAEMON_COUNT) vs. per-daemon
shared_jars=()
for name in "${!JAR_COUNT[@]}"; do
    if [[ ${JAR_COUNT[$name]} -eq ${DAEMON_COUNT} ]]; then
        shared_jars+=("${name}")
    fi
done

# Sort shared_jars for deterministic output
IFS=$'\n' shared_jars=($(printf '%s\n' "${shared_jars[@]}" | sort)); unset IFS

echo "  Total unique JAR filenames across all daemons: ${#JAR_COUNT[@]}"
echo "  Shared across all ${DAEMON_COUNT} daemons: ${#shared_jars[@]}"
echo ""

# ---------------------------------------------------------------------------
# Step 4: Partition shared libs into external vs. internal
# ---------------------------------------------------------------------------
echo "--- Partitioning shared libs into external / internal ---"

# Use the first daemon as source for copying shared JARs (they're identical)
first_daemon="${DAEMONS[0]}"
first_lib="${EXTRACT_DIR}/${first_daemon}/lib"

shared_external_count=0
shared_internal_count=0

# Build a set of shared JAR names for fast lookup
declare -A SHARED_SET
for name in "${shared_jars[@]}"; do
    SHARED_SET[$name]=1
done

for name in "${shared_jars[@]}"; do
    # Internal: starts with org.opennms. or opennms-
    if [[ "${name}" == org.opennms.* ]] || [[ "${name}" == opennms-* ]]; then
        cp "${first_lib}/${name}" "${STAGING}/shared-internal/"
        shared_internal_count=$(( shared_internal_count + 1 ))
    else
        cp "${first_lib}/${name}" "${STAGING}/shared-external/"
        shared_external_count=$(( shared_external_count + 1 ))
    fi
done

echo "  shared-external: ${shared_external_count} JARs"
echo "  shared-internal: ${shared_internal_count} JARs"
echo ""

# ---------------------------------------------------------------------------
# Step 5: Stage per-daemon unique libs and thin app JAR
# ---------------------------------------------------------------------------
echo "--- Staging per-daemon unique libs and app JARs ---"
for daemon in "${DAEMONS[@]}"; do
    lib_dir="${EXTRACT_DIR}/${daemon}/lib"
    daemon_libs_dir="${STAGING}/${daemon}/libs"
    daemon_app_dir="${STAGING}/${daemon}/app"
    mkdir -p "${daemon_libs_dir}" "${daemon_app_dir}"

    unique_count=0
    for jar_file in "${lib_dir}"/*.jar; do
        name="$(basename "${jar_file}")"
        if [[ -z "${SHARED_SET[$name]+x}" ]]; then
            cp "${jar_file}" "${daemon_libs_dir}/"
            unique_count=$(( unique_count + 1 ))
        fi
    done

    # Copy the thin application JAR (the non-lib file in the extraction directory)
    thin_jar_count=0
    for f in "${EXTRACT_DIR}/${daemon}"/*.jar; do
        cp "${f}" "${daemon_app_dir}/"
        thin_jar_count=$(( thin_jar_count + 1 ))
    done

    echo "  ${daemon}: ${unique_count} unique libs, ${thin_jar_count} app JAR(s)"
done
echo ""

# ---------------------------------------------------------------------------
# Step 6: Safety check — no JAR in both shared and daemon-specific
# ---------------------------------------------------------------------------
echo "--- Safety check: no overlap between shared and per-daemon ---"
overlap_found=0
for daemon in "${DAEMONS[@]}"; do
    daemon_libs_dir="${STAGING}/${daemon}/libs"
    for jar_file in "${daemon_libs_dir}"/*.jar; do
        [[ -e "${jar_file}" ]] || continue
        name="$(basename "${jar_file}")"
        if [[ -n "${SHARED_SET[$name]+x}" ]]; then
            echo "  OVERLAP: ${name} in both shared and ${daemon}/libs/"
            overlap_found=1
        fi
    done
done

if [[ ${overlap_found} -eq 1 ]]; then
    echo "ERROR: Overlap detected — aborting."
    rm -rf "${EXTRACT_DIR}"
    exit 1
fi
echo "  OK — no overlaps."
echo ""

# ---------------------------------------------------------------------------
# Step 7: Clean up intermediate extraction directory
# ---------------------------------------------------------------------------
rm -rf "${EXTRACT_DIR}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "==========================================="
echo "  SUMMARY"
echo "==========================================="
echo "  shared-external : ${shared_external_count} JARs"
echo "  shared-internal : ${shared_internal_count} JARs"
echo "  shared total    : ${#shared_jars[@]} JARs"
echo ""
printf "  %-22s %s\n" "DAEMON" "UNIQUE LIBS"
printf "  %-22s %s\n" "------" "-----------"
for daemon in "${DAEMONS[@]}"; do
    count=$(ls -1 "${STAGING}/${daemon}/libs/"*.jar 2>/dev/null | wc -l | tr -d ' ')
    main_class=$(cat "${STAGING}/${daemon}/.main_class")
    printf "  %-22s %3s   %s\n" "${daemon}" "${count}" "${main_class}"
done
echo ""
echo "Staging directory: ${STAGING}"
echo "Done."
