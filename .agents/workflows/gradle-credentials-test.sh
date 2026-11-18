#!/usr/bin/env bash
#
# Verifies that both builds configure without Google credentials or the gcloud CLI.
# Run with CHORDS_JDK25_HOME set, using bash .agents/workflows/gradle-credentials-test.sh.

set -euo pipefail

: "${CHORDS_JDK25_HOME:?Set CHORDS_JDK25_HOME to a JDK 25 installation}"
readonly SUITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

# Keep wrapper tools available while making gcloud impossible to resolve from PATH.
mkdir "$SANDBOX/bin"
for tool in awk bash cat dirname env git ls mkdir rm sed sh tr uname xargs; do
    ln -s "$(command -v "$tool")" "$SANDBOX/bin/$tool"
done

export PATH="$SANDBOX/bin"
export GOOGLE_APPLICATION_CREDENTIALS="$SANDBOX/missing-credentials.json"
export CHORDS_NO_GRADLE_DAEMON=1

if ! "$SUITE_DIR/gradle-root.sh" detekt > "$SANDBOX/root.log" 2>&1; then
    cat "$SANDBOX/root.log"
    exit 1
fi
printf 'PASS: root build without Google credentials or gcloud\n'

if ! "$SUITE_DIR/gradle-codegen.sh" dependencies > "$SANDBOX/codegen.log" 2>&1; then
    cat "$SANDBOX/codegen.log"
    exit 1
fi
printf 'PASS: codegen build without Google credentials or gcloud\n'
