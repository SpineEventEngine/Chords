# Root Build Environment

Use `.agents/workflows/gradle-root.sh` for every root Gradle command and
`.agents/workflows/gradle-codegen.sh` for the separate codegen plugin build.

Both builds require JDK 25 and Gradle 9.8.2. Native Apple Silicon JVMs are supported;
the Protobuf and gRPC tools provide macOS ARM executables.

## Wrapper

The wrapper enters the repository root and uses `CHORDS_JDK25_HOME` when set.
Otherwise, jEnv resolves the repository's `.java-version`, or alias `25` when
that file is absent. An empty or unregistered project selection fails; `JAVA_HOME`
is the fallback when jEnv is unavailable or alias `25` cannot be resolved.
The wrapper verifies Java 25.
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
`mergeAllLicenseReports`, `checkVersionIncrement`, `dependencies`, and
`buildEnvironment`, plus qualified
`:<module>:test`, `:<module>:check`, `:<module>:compileKotlin`, and
`:<module>:compileTestKotlin`, `:<module>:dependencies`, and
`:<module>:buildEnvironment`. Dependency reports accept `--configuration <name>`.
The responsible skill chooses the task.

## Codegen Plugins

The codegen wrapper enters `codegen/plugins`, resolves JDK 25 from
`CHORDS_JDK25_HOME`, jEnv, the macOS JVM registry, or `JAVA_HOME`, and verifies
its version. It accepts `clean`, `build`, Maven-local publication, dependency reports, and routine
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

For direct toolchain diagnosis, verify Java 25:

```bash
java -version
java -XshowSettings:properties -version 2>&1 | rg 'java.home|java.version|os.arch'
```

An ignored local `.java-version` file may still select an older JDK. Update it locally
or pass `CHORDS_JDK25_HOME` to the verification wrapper.
