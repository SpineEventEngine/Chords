#!/usr/bin/env bash
#
# Runs permitted codegen plugin tasks under a verified JDK 25.
#
#   .agents/workflows/gradle-codegen.sh build
#   .agents/workflows/gradle-codegen.sh publishToMavenLocal
#
# Environment:
#   CHORDS_JDK25_HOME   use this JDK instead of asking jEnv for one

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die() { printf 'gradle-codegen: %s\n' "$1" >&2; exit 1; }

[[ $# -gt 0 ]] || die "usage: gradle-codegen.sh <gradle-arguments…>"

expect_configuration=false
for argument in "$@"; do
    if $expect_configuration; then
        [[ "$argument" =~ ^[A-Za-z][A-Za-z0-9_]*$ ]] \
            || die "--configuration needs a configuration name"
        expect_configuration=false
        continue
    fi
    case "$argument" in
        clean|build|publishToMavenLocal|dependencies|buildEnvironment|\
        :codegen-plugins:dependencies|:codegen-plugins:buildEnvironment|\
        :buildSrc:dependencies|:buildSrc:buildEnvironment|--stacktrace|--info|--warn|--quiet|\
        --rerun-tasks|--continue) ;;
        --configuration) expect_configuration=true ;;
        *) die "argument '${argument}' is not permitted for codegen verification" ;;
    esac
done
$expect_configuration && die "--configuration needs a configuration name"

jenv_bin=""
for candidate in "$(command -v jenv 2>/dev/null || true)" \
                 /opt/homebrew/bin/jenv /usr/local/bin/jenv "${HOME}/.jenv/bin/jenv"; do
    [[ -n "$candidate" && -x "$candidate" ]] || continue
    jenv_bin="$candidate"
    break
done

java_home="${CHORDS_JDK25_HOME:-}"
if [[ -z "$java_home" && -n "$jenv_bin" ]]; then
    java_home="$("$jenv_bin" prefix 25 2>/dev/null || true)"
fi
if [[ -z "$java_home" && -x /usr/libexec/java_home ]]; then
    java_home="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
fi
[[ -n "$java_home" ]] || java_home="${JAVA_HOME:-}"
[[ -n "$java_home" ]] \
    || die "no JDK 25 found. Register one with jEnv or set CHORDS_JDK25_HOME"
[[ -x "${java_home}/bin/java" ]] \
    || die "'${java_home}' has no bin/java; it is not a JDK home"

settings="$("${java_home}/bin/java" -XshowSettings:properties -version 2>&1)"
version="$(printf '%s\n' "$settings" | awk -F'= ' \
    '$0 ~ /java\.specification\.version/ { print $2; exit }')"
[[ "$version" == "25" ]] \
    || die "'${java_home}' reports Java ${version:-unknown}; codegen plugins need JDK 25"

cd "$REPO_ROOT/codegen/plugins"
exec env JAVA_HOME="$java_home" ./gradlew \
    --no-daemon -Pkotlin.compiler.execution.strategy=in-process "$@"
