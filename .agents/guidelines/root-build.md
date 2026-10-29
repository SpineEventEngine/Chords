# Root Build Environment

Use `.agents/workflows/gradle-root.sh` for every root Gradle command and
`.agents/workflows/gradle-codegen.sh` for the separate codegen plugin build.

The root build requires JDK 11 and, on Apple Silicon, an x86_64 JVM. Its pinned
toolchain resolves `io.grpc:protoc-gen-grpc-java:1.28.1`, which has a macOS
x86_64 executable but no `osx-aarch_64` one. An ARM JVM therefore fails during
Protobuf dependency resolution. Do not rely on `/usr/libexec/java_home -v 11`;
when no registered JDK 11 exists, macOS may select a newer ARM JDK.

## Wrapper

The wrapper enters the repository root and uses `CHORDS_JDK11_HOME` when set.
Otherwise, jEnv resolves the repository's `.java-version`, or alias `11` when
that file is absent. An empty or unregistered project selection fails; `JAVA_HOME`
is the fallback when jEnv is unavailable or alias `11` cannot be resolved.
The wrapper verifies Java 11 and the required architecture.
It accepts only routine verification, report generation, and
Maven-local publication; it rejects remote publishing, deployment, signed
packaging, task abbreviations, and arbitrary Gradle inputs.

```bash
.agents/workflows/gradle-root.sh :<module>:compileKotlin
.agents/workflows/gradle-root.sh :<module>:test --tests "…"
.agents/workflows/gradle-root.sh :<module>:check
```

Accepted tasks are `clean`, `build`, `check`, `test`, `detekt`,
`publishToMavenLocal`, `publishCodegenPluginsToMavenLocal`, `generatePom`,
`mergeAllLicenseReports`, and `checkVersionIncrement`, plus qualified
`:<module>:test`, `:<module>:check`, `:<module>:compileKotlin`, and
`:<module>:compileTestKotlin`. The responsible skill chooses the task.

## Codegen Plugins

The codegen wrapper enters `codegen/plugins`, resolves JDK 17 from
`CHORDS_JDK17_HOME`, jEnv, the macOS JVM registry, or `JAVA_HOME`, and verifies
its version. It accepts only `build` and Maven-local publication, plus routine
output and rerun flags:

```bash
.agents/workflows/gradle-codegen.sh build
.agents/workflows/gradle-codegen.sh publishToMavenLocal
```

## Sandboxes

A reusable Gradle daemon retains its launcher's sandbox and can break later
terminal builds; the Kotlin daemon then cannot create its marker file and
falls back behind a misleading stack trace. When its daemon directory is
unwritable, the root wrapper uses a single-use Gradle daemon and in-process
Kotlin compilation. Set `CHORDS_NO_GRADLE_DAEMON=1` to force that mode;
`pair.sh` does so for every agent turn. The codegen wrapper always uses it.

## Diagnostics

For direct toolchain diagnosis, require Java 11 and, on Apple Silicon,
`os.arch = x86_64`:

```bash
java -version
java -XshowSettings:properties -version 2>&1 |
    rg 'java.home|java.version|os.arch'
```

For direct-invocation diagnosis, run from the repository root. Use `.java-version`
when present, or jEnv alias `11` when absent:

```bash
(
    java_version=11
    if [[ -f .java-version ]]; then
        java_version="$(jenv version-file-read .java-version)" || {
            echo "Cannot read a Java version from .java-version" >&2
            exit 1
        }
    fi
    java_home="$(jenv prefix "$java_version")" || exit 1
    JAVA_HOME="$java_home" ./gradlew :<module>:check
)
```
