#!/usr/bin/env bash
#
# Verifies root JDK selection without starting Gradle or changing installed JDKs.
# Run with: bash .agents/workflows/gradle-root-test.sh

set -euo pipefail

readonly SUITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

mkdir -p "$SANDBOX/repo/.agents/workflows" "$SANDBOX/bin" \
    "$SANDBOX/selected jdk/bin" "$SANDBOX/fallback/bin" "$SANDBOX/override/bin"
cp "$SUITE_DIR/gradle-root.sh" "$SANDBOX/repo/.agents/workflows/"

cat > "$SANDBOX/selected jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
printf '    java.specification.version = %s\n' "${TEST_JAVA_VERSION:-11}"
printf '    os.arch = %s\n' "${TEST_JAVA_ARCH:-x86_64}"
JAVA
cp "$SANDBOX/selected jdk/bin/java" "$SANDBOX/fallback/bin/java"
cp "$SANDBOX/selected jdk/bin/java" "$SANDBOX/override/bin/java"

cat > "$SANDBOX/bin/jenv" <<'JENV'
#!/usr/bin/env bash
case "$1" in
    version-file-read)
        awk 'NF { print $1; found = 1; exit } END { if (!found) exit 1 }' "$2" ;;
    prefix)
        case "${2:-}" in
            selected) printf '%s/selected jdk\n' "$TEST_ROOT" ;;
            11) printf '%s/fallback\n' "$TEST_ROOT" ;;
            *) exit 1 ;;
        esac ;;
    *) exit 1 ;;
esac
JENV
cat > "$SANDBOX/bin/uname" <<'UNAME'
#!/usr/bin/env bash
printf 'Darwin\n'
UNAME
cat > "$SANDBOX/repo/gradlew" <<'GRADLE'
#!/usr/bin/env bash
printf 'Gradle JDK: %s\n' "$JAVA_HOME"
GRADLE
chmod +x "$SANDBOX/bin/"* "$SANDBOX/"*/bin/java "$SANDBOX/repo/gradlew"

export TEST_ROOT="$SANDBOX"
export PATH="$SANDBOX/bin:$PATH"
export CHORDS_NO_GRADLE_DAEMON=1
unset CHORDS_JDK11_HOME JAVA_HOME TEST_JAVA_VERSION TEST_JAVA_ARCH
readonly WRAPPER="$SANDBOX/repo/.agents/workflows/gradle-root.sh"
failures=0

# Checks both the exit status and the selected JDK or the reason for rejecting it.
assert_run() {
    local name="$1" expected_status="$2" expected_output="$3"
    shift 3
    local output status=0
    output="$("$@" 2>&1)" || status=$?
    if [[ "$status" -eq "$expected_status" && "$output" == *"$expected_output"* ]]; then
        printf 'PASS: %s\n' "$name"
    else
        printf 'FAIL: %s (status %s)\n%s\n' "$name" "$status" "$output"
        failures=$((failures + 1))
    fi
}

# The caller's directory and shell selection must not override the repository file.
cd "$SANDBOX"
printf 'selected\n' > "$SANDBOX/repo/.java-version"
assert_run 'select the repository JDK from outside the repository' 0 \
    "Gradle JDK: $SANDBOX/selected jdk" env JENV_VERSION=11 bash "$WRAPPER" test
assert_run 'honor an explicit JDK path override' 0 "Gradle JDK: $SANDBOX/override" \
    env CHORDS_JDK11_HOME="$SANDBOX/override" bash "$WRAPPER" test

printf 'unregistered\n' > "$SANDBOX/repo/.java-version"
assert_run 'reject an unregistered project JDK instead of falling back' 1 \
    'not registered with jEnv' env JAVA_HOME="$SANDBOX/fallback" bash "$WRAPPER" test
: > "$SANDBOX/repo/.java-version"
assert_run 'reject an empty project version file' 1 'cannot read a Java version' \
    bash "$WRAPPER" test

printf 'selected\n' > "$SANDBOX/repo/.java-version"
assert_run 'reject a project JDK with the wrong Java version' 1 'needs JDK 11' \
    env TEST_JAVA_VERSION=17 bash "$WRAPPER" test
assert_run 'reject an ARM project JDK on macOS' 1 'must use an x86_64 JDK 11' \
    env TEST_JAVA_ARCH=aarch64 bash "$WRAPPER" test

rm "$SANDBOX/repo/.java-version"
assert_run 'retain alias 11 when there is no project version file' 0 \
    "Gradle JDK: $SANDBOX/fallback" bash "$WRAPPER" test
assert_run 'reject remote publishing' 1 'not permitted' bash "$WRAPPER" publish
assert_run 'reject arbitrary init scripts' 1 'not permitted' \
    bash "$WRAPPER" --init-script untrusted.gradle test

[[ "$failures" -eq 0 ]]
