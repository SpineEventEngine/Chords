#!/usr/bin/env bash
#
# Runs a root Gradle command under the JDK the root build requires.
#
# AGENTS.md prescribes `JAVA_HOME="$(jenv prefix)" ./gradlew …` for the root
# project on Apple Silicon. No permission rule can express that command: the
# leading environment assignment and the command substitution mean an allow
# entry for `./gradlew …` never matches it, and `jenv shell 11` does not
# survive into the next tool call. An unattended agent therefore reaches review
# having compiled nothing. This wrapper is the prescribed command behind a
# single allowlistable path.
#
#   .agents/workflows/gradle-root.sh :core:check
#   .agents/workflows/gradle-root.sh :proto:test --tests "…"
#
# Codegen plugin commands are not this script's business: `codegen/plugins` is
# a separate build on JDK 17 and runs its own `./gradlew` directly.
#
# Environment:
#   CHORDS_JDK11_HOME   use this JDK instead of asking jEnv for one

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die()  { printf 'gradle-root: %s\n' "$1" >&2; exit 1; }
info() { printf 'gradle-root: %s\n' "$1" >&2; }

[[ $# -gt 0 ]] || die "usage: gradle-root.sh <gradle-arguments…>"

# This path is wildcard-allowlisted for the unattended implementer. Validate
# every argument here so that wildcard cannot be used to reach publishing,
# deployment, signed packaging, an init script, or another executable Gradle
# input. Task abbreviations are intentionally rejected as well.
validate_arguments() {
    local expect="" argument
    for argument in "$@"; do
        if [[ -n "$expect" ]]; then
            case "$expect" in
                tests)   [[ -n "$argument" && "$argument" != -* ]] \
                             || die "--tests needs a non-option test filter" ;;
                exclude) [[ "$argument" == "applyCodegenPlugins" ]] \
                             || die "only applyCodegenPlugins may be excluded" ;;
            esac
            expect=""
            continue
        fi

        case "$argument" in
            clean|build|check|test|detekt|publishToMavenLocal|\
            publishCodegenPluginsToMavenLocal|generatePom|\
            mergeAllLicenseReports|checkVersionIncrement) ;;
            --tests) expect=tests ;;
            -x|--exclude-task) expect=exclude ;;
            --exclude-task=applyCodegenPlugins|--rerun-tasks|--stacktrace|\
            --info|--warn|--quiet|--no-daemon|--continue) ;;
            *)
                local task_pattern
                task_pattern='^(:[A-Za-z0-9_-]+)+:'
                task_pattern+='(test|check|compileKotlin|compileTestKotlin)$'
                [[ "$argument" =~ $task_pattern ]] \
                    || die "argument '${argument}' is not permitted for "\
"unattended root verification"
                ;;
        esac
    done
    [[ -z "$expect" ]] || die "option at the end of the command needs a value"
}

validate_arguments "$@"

# jEnv is a shell function in an interactive shell and does not exist in this
# one, so resolve the executable rather than relying on the function.
jenv_bin=""
for candidate in "$(command -v jenv 2>/dev/null || true)" \
                 /opt/homebrew/bin/jenv /usr/local/bin/jenv "${HOME}/.jenv/bin/jenv"; do
    [[ -n "$candidate" && -x "$candidate" ]] || continue
    jenv_bin="$candidate"
    break
done

java_home="${CHORDS_JDK11_HOME:-}"
if [[ -z "$java_home" && -n "$jenv_bin" ]]; then
    java_home="$("$jenv_bin" prefix 11 2>/dev/null || true)"
fi
[[ -n "$java_home" ]] || java_home="${JAVA_HOME:-}"
[[ -n "$java_home" ]] \
    || die "no JDK 11 found. Install one, register it with jEnv ('jenv add …'), "\
"or set CHORDS_JDK11_HOME"
[[ -x "${java_home}/bin/java" ]] \
    || die "'${java_home}' has no bin/java; it is not a JDK home"

# Verify rather than trust. AGENTS.md warns that JDK selection here silently
# picks a newer ARM JVM when the expected one is not registered, and the root
# build then fails in ways that look like a code problem.
settings="$("${java_home}/bin/java" -XshowSettings:properties -version 2>&1)"
property() { printf '%s\n' "$settings" | awk -F'= ' -v k="$1" '$0 ~ k { print $2; exit }'; }

version="$(property 'java\.specification\.version')"
[[ "$version" == "11" ]] \
    || die "'${java_home}' reports Java ${version:-unknown}; the root build needs "\
"JDK 11 (see the Apple Silicon section of AGENTS.md)"

arch="$(property 'os\.arch')"
if [[ "$(uname -s)" == "Darwin" && "$arch" != "x86_64" ]]; then
    die "'${java_home}' reports os.arch=${arch:-unknown}; Apple Silicon root "\
"verification must use an x86_64 JDK 11"
elif [[ "$arch" != "x86_64" && "$arch" != "amd64" ]]; then
    info "warning: this JDK reports os.arch=${arch}; the root build pins "\
"platform-specific Protobuf and gRPC tooling that may have no macOS ARM build"
fi

cd "$REPO_ROOT"
exec env JAVA_HOME="$java_home" ./gradlew "$@"
