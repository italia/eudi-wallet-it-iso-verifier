#!/bin/bash

# ----------------------------------------------------------------------------
# update_build_publish_all.sh - SDK release utility script
#
# ⚠️  Questo script richiede Bash (NON sh/dash).
#     Esegui SEMPRE con:
#         bash build_scripts/update_build_publish_all.sh [opzioni]
#     oppure:
#         chmod +x build_scripts/update_build_publish_all.sh
#         ./build_scripts/update_build_publish_all.sh [opzioni]
#
#     NON usare "sh script.sh" o simili!
#
# Opzioni:
#   --dry-run     Mostra i cambiamenti senza applicare nulla
#   --no-publish  Non esegue il publish (solo update/build)
#
# Richiede:
#   - bash, sed, grep, awk, tee, date, sha256sum, mvn, curl, tar
#   - .env nella root con:
#       GITLAB_PRIVATE_TOKEN=...
#       GITLAB_HOST=...
#       PROJECT_ID=...
# ----------------------------------------------------------------------------

if [ -z "$BASH_VERSION" ]; then
  echo "❌ This script must be run with bash, NOT with sh or dash."
  echo "   Use:   bash $0 ..."
  echo "   Or:    chmod +x $0 && ./$0 ..."
  exit 1
fi

set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${0}}" )" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
ENV_FILE="${PROJECT_ROOT}/.env"
LOG_FILE="${PROJECT_ROOT}/build.log"

# Output to logfile & console
exec > >(tee -a "$LOG_FILE") 2>&1

# Dependency check
REQUIRED_CMDS=(sed grep awk tee date sha256sum mvn curl tar)
for cmd in "${REQUIRED_CMDS[@]}"; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "❌ Required command '$cmd' not found in PATH." >&2
    exit 127
  fi
done

# ---- COLOR & PRINT UTILS ----
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
print_step()       { echo -e "${CYAN}▶ $1${NC}"; }
print_success()    { echo -e "${GREEN}✓ $1${NC}"; }
print_info()       { echo -e "${BLUE}ℹ $1${NC}"; }
print_warning()    { echo -e "${YELLOW}⚠ $1${NC}"; }
print_error()      { echo -e "${RED}✗ $1${NC}"; }
print_section()    { echo -e "${BLUE}=== $1 ===${NC}"; }

# ---- CLEANUP ----
cleanup() { [ -f "$ENV_FILE.bak" ] && rm -f "$ENV_FILE.bak"; }
trap cleanup EXIT INT TERM

IS_DRY_RUN=false
NO_PUBLISH=false

# ---- STATIC CONFIG (edit if SDK structure changes) ----
GROUP_ID="it.infocert.proximityverification"
ARTIFACT_ID="proximityverification-android"
PACKAGE_NAME="proximityverification-ios"
GRADLEW_PATH="${PROJECT_ROOT}/gradlew"
RESOLVED_VERSION_TASK=":proximityverification:printResolvedVersion"
ANDROID_OUT_DIR="${PROJECT_ROOT}/Products/Android"
IOS_OUT_DIR="${PROJECT_ROOT}/Products/iOS"

validate_publish_credentials() {
  for v in GITLAB_PRIVATE_TOKEN GITLAB_HOST PROJECT_ID; do
    if [ -z "${!v:-}" ]; then
      print_error "$v is not set. Please export it in environment or set in .env file and export file."
      return 1
    fi
  done
  return 0
}

# ---- USAGE ----
show_usage() {
    print_info "Usage: $0 [--dry-run] [--no-publish]"
    print_info "  --dry-run      Preview all changes without applying"
    print_info "  --no-publish   Do NOT publish to GitLab registries"
    print_info ""
    print_info "Esempi:"
    print_info "  $0"
    print_info "  $0 --dry-run"
    print_info "  $0 --no-publish"
    print_info ""
    print_info "Ciclo:"
    print_info "  1. Risolve la branch-aware version tramite Gradle"
    print_info "  2. Builda la libreria Kotlin Multiplatform"
    print_info "  3. Pubblica su GitLab Registry (a meno che --no-publish)"
}

# ---- RESOLVED VERSION FROM GRADLE ----
detect_current_version() {
    if [ ! -x "$GRADLEW_PATH" ]; then
        print_error "Cannot find executable gradlew at $GRADLEW_PATH"
        return 1
    fi
    local resolved_version_output
    resolved_version_output=$("$GRADLEW_PATH" -q "$RESOLVED_VERSION_TASK") || return 1
    CURRENT_VERSION=$(printf '%s\n' "$resolved_version_output" | tail -n 1 | tr -d '\r')
    if [ -z "$CURRENT_VERSION" ]; then
        print_error "Could not detect resolved version from Gradle"
        return 1
    fi
    print_info "Detected resolved version from Gradle: $CURRENT_VERSION"
    return 0
}

cleanup_backup_files() {
    print_step "Cleaning up backup files"
    find "$PROJECT_ROOT" -name "*.bak" -type f -delete 2>/dev/null || true
    print_success "Removed backup files"
}

print_artifact_paths() {
    local version="$1"
    print_section "Build Artifact Locations"
    local android_path="${ANDROID_OUT_DIR}/${version}/${ARTIFACT_ID}"
    local ios_path="${IOS_OUT_DIR}/${version}/proximityverification.framework"
    [ -d "$android_path" ] && print_success "Android artifacts: $android_path" || print_warning "Android artifacts not found: $android_path"
    [ -d "$ios_path" ] && print_success "iOS artifacts: $ios_path" || print_warning "iOS artifacts not found: $ios_path"
    return 0
}

handle_script_error() {
    local code="$1"; local ctx="$2"; local sugg="$3"
    print_error "$code"
    [ -n "$ctx" ] && print_info "Context: $ctx"
    [ -n "$sugg" ] && print_info "Suggestion: $sugg"
    return 1
}

validate_file_exists() {
    local path="$1"; local desc="$2"; local sug="$3"
    [ ! -f "$path" ] && handle_script_error "missing_file" "$desc not found at: $path" "$sug" && return 1
    return 0
}
validate_directory_exists() {
    local path="$1"; local desc="$2"; local sug="$3"
    [ ! -d "$path" ] && handle_script_error "missing_dir" "$desc not found at: $path" "$sug" && return 1
    return 0
}

create_backup() {
    local f="$1"
    [ -f "$f" ] && cp "$f" "$f.bak" && print_info "Created backup: $f.bak"
    return 0
}

build_kmp_library() {
    print_section "Building KMP Library"
    validate_directory_exists "$PROJECT_ROOT" "Project root" "Clona tutto correttamente"
    if ! validate_file_exists "${SCRIPT_DIR}/build_all.sh" "KMP build script" "Serve build_scripts/build_all.sh"; then
        return 1
    fi
    print_step "Building KMP library"
    show_progress "Building Kotlin Multiplatform library" 5
    if $IS_DRY_RUN; then
        print_info "[DRY RUN] Would execute: bash ${SCRIPT_DIR}/build_all.sh"
    else
        if bash "${SCRIPT_DIR}/build_all.sh"; then
            print_success "KMP library built successfully"
        else
            print_error "KMP library build failed"
            return 1
        fi
    fi
    return 0
}

publish_android_aar() {
    local version="$1"
    print_step "Publishing Android AAR to GitLab Maven Registry"
    local base_path="Products/Android/${version}/${ARTIFACT_ID}"
    local aar_path="${PROJECT_ROOT}/${base_path}/${ARTIFACT_ID}-${version}.aar"
    local pom_path="${PROJECT_ROOT}/${base_path}/${ARTIFACT_ID}-${version}.pom"
    local src_path="${PROJECT_ROOT}/${base_path}/${ARTIFACT_ID}-${version}-sources.jar"
    for f in "$aar_path" "$pom_path" "$src_path"; do
      [ ! -f "$f" ] && print_error "Required artifact missing: $f" && return 1
    done
    print_info "Publishing $ARTIFACT_ID:$version to $GITLAB_HOST..."
    mvn deploy:deploy-file \
      -DrepositoryId=gitlab-maven \
      -Durl="${GITLAB_HOST}/api/v4/projects/${PROJECT_ID}/packages/maven" \
      -DgroupId="${GROUP_ID}" \
      -DartifactId="${ARTIFACT_ID}" \
      -Dversion="${version}" \
      -Dpackaging=aar \
      -Dfile="${aar_path}" \
      -DpomFile="${pom_path}" \
      -Dsources="${src_path}" \
      -s ./.m2/settings.xml
    if [ $? -eq 0 ]; then
      print_success "Android AAR published successfully."
    else
      print_error "Failed to publish AAR."
      return 1
    fi
}

publish_ios_framework() {
    local version="$1"
    print_step "Publishing iOS Framework to GitLab Generic Registry"
    local framework_dir="${PROJECT_ROOT}/Products/iOS/${version}/proximityverification.framework"
    local package_name="proximityverification-ios"
    local archive_name="${package_name}-${version}.tar.gz"
    if [ ! -d "$framework_dir" ]; then
      print_error "Framework directory not found at: $framework_dir"
      return 1
    fi
    print_info "Archiving framework..."
    tar -czf "$archive_name" -C "$(dirname "$framework_dir")" "$(basename "$framework_dir")"
    local upload_url="${GITLAB_HOST}/api/v4/projects/${PROJECT_ID}/packages/generic/${package_name}/${version}/${archive_name}"
    print_info "Uploading to GitLab package registry..."
    curl --fail --silent --show-error --location \
      --header "PRIVATE-TOKEN: ${GITLAB_PRIVATE_TOKEN}" \
      --upload-file "$archive_name" \
      "$upload_url"
    if [ $? -eq 0 ]; then
      print_success "Upload completed successfully."
      print_info "Package URL: $upload_url"
      rm -f "$archive_name"
    else
      print_error "Upload failed."
      return 1
    fi
}

show_progress() {
    local message="$1"; local duration="${2:-3}"
    print_step "$message"
    for i in $(seq 1 $duration); do echo -n "."; sleep 1; done; echo ""
}

print_operation_summary() {
    local operation="$1"; local status="$2"; local details="$3"
    print_section "Operation Summary"
    print_info "Operation: $operation"
    [ "$status" = "success" ] && print_success "Status: Completed successfully" || print_error "Status: Failed"
    [ -n "$details" ] && print_info "Details: $details"
}

main() {
    print_section "SDK Update and Build Script"
    print_info "This script resolves the SDK version via Gradle, builds the KMP lib, and publishes artifacts"
    echo ""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run) IS_DRY_RUN=true; shift ;;
            --no-publish) NO_PUBLISH=true; shift ;;
            -h|--help) show_usage; exit 0 ;;
            *) break ;;
        esac
    done
    if ! $NO_PUBLISH && ! $IS_DRY_RUN; then
        validate_publish_credentials || { print_error "Set GITLAB_PRIVATE_TOKEN, GITLAB_HOST, PROJECT_ID before running in publish mode."; exit 1; }
    fi
    if ! detect_current_version; then
        handle_script_error "invalid_version" "Cannot detect resolved version" "Ensure Gradle can resolve the branch-aware version"
        exit 1
    fi
    local version="$CURRENT_VERSION"
    print_info "Using resolved version from Gradle: $version"
    echo ""
    if ! build_kmp_library; then exit 1; fi
    if ! $IS_DRY_RUN; then cleanup_backup_files; fi
    print_artifact_paths "$version"
    print_operation_summary "SDK Build" "success" "Resolved version: $version. Library built successfully."
    echo ""
    if $IS_DRY_RUN; then
        print_section "DRY RUN COMPLETE"
        echo "• No changes applied"
        echo "• No build or deployment performed"
        echo "• Use script without --dry-run to apply these changes"
        echo ""
        exit 0
    fi
    if $NO_PUBLISH; then
        print_section "PUBLISH SKIPPED (--no-publish)"
        echo "• Deploy to GitLab registries skipped (use without --no-publish to enable deploy)"
        echo ""
        print_section "SDK Build Complete"
        echo "• Resolved version: $version"
        echo "• KMP library built successfully"
        echo "• Artifacts ready in output directories"
        echo ""
        exit 0
    fi
    print_section "Deploying Artifacts to GitLab Registry"
    validate_publish_credentials || { handle_script_error "missing_credentials" "GitLab publish credentials not set" "Export GITLAB_PRIVATE_TOKEN, GITLAB_HOST, PROJECT_ID before publishing"; exit 1; }
    publish_android_aar "$version" || { handle_script_error "publish_android_failed" "Failed to publish Android AAR" "Check .env variables and artifact paths"; exit 1; }
    publish_ios_framework "$version" || { handle_script_error "publish_ios_failed" "Failed to publish iOS framework" "Check .env variables and framework path"; exit 1; }
    echo ""
    print_section "SDK Build and Publish Complete"
    echo "• Resolved version: $version"
    echo "• KMP library built successfully"
    echo "• Artifacts published to GitLab"
    echo ""
}

main "$@"
