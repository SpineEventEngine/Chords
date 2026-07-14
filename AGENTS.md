# Welcome, Agents

## Orientation

This repository is **Chords**, a suite of open-source libraries by TeamDev for
desktop UI development with the Compose Multiplatform toolkit, built around the
Spine Event Engine ecosystem.

For substantive implementation, review, or documentation work, start by reading:

- `README.md` for the library overview, supported environment, development
  setup, and build commands.
- `.agents/project.md` for the project overview, module map, architecture
  notes, documentation ownership, and CI notes.
- The README closest to the area you are changing, especially:
  - `core/README.md` for the application shell, class-based components, and
    basic UI components.
  - `proto/README.md` for Protobuf-aware UI components and message forms.
  - `proto-values/README.md` for supplementary Protobuf messages and Kotlin
    extensions.
  - `client/README.md` for server connectivity via Spine Event Engine.
  - `codegen/runtime/README.md` and `codegen/plugins/README.md` for the code
    generation runtime and ProtoData plugins.

Local task-specific skills live under `.agents/skills/`. Use them when their
frontmatter matches the task.

## Commit and History Safety

Do not commit, push, tag, rebase, merge, cherry-pick, or otherwise write to Git
history unless the user's current prompt explicitly asks for it.

Authorization does not carry over between turns or sessions. When in doubt,
leave changes unstaged, show the diff or summarize it, and let the user decide.

When moving or renaming tracked files, use `git mv` so file history is preserved.

## Safety Rules

- This is a public open-source repository (Apache 2.0). Do not add secrets,
  credentials, tokens, private keys, or TeamDev-internal data to it.
- Do not modify files under the `config/` Git submodule; it is owned by the
  [SpineEventEngine/config](https://github.com/SpineEventEngine/config)
  repository, and changes belong upstream.
- Do not publish artifacts or trigger publishing tasks (`publish`,
  `publishCodegenPlugins`) unless the user's current prompt explicitly asks for
  it. Publishing is normally performed by CI on pushes to `master`.
- Do not edit the encrypted key files under `.github/keys/` or the decryption
  scripts' credential wiring.
- Do not auto-update external dependencies outside dedicated update tasks. The
  toolchain versions are deliberately pinned (see Development Conventions) and
  upgrading them is a project-level decision.
- Do not add analytics, telemetry, or tracking code.
- Avoid reflection, unsafe code, broad global state, and hidden background work
  unless explicitly justified by the task (reflection is already used
  deliberately in a few places, such as resolving component type parameters).
- Preserve existing package structure, module boundaries, naming conventions,
  and Gradle patterns.
- Do not manually edit generated sources or build outputs: `generated/`
  folders, codegen workspace outputs (`_out/`), Gradle wrapper files, or the
  generated `pom.xml` / `dependencies.md` reports (regenerate them with Gradle
  instead).
- Public API changes require care: all libraries are consumed by external
  projects, and Kotlin explicit API mode is enabled. Avoid breaking existing
  public signatures; prefer additive changes.

## Versioning and Reports

Every PR must increment `chordsVersion` in `version.gradle.kts` (enforced by
the `Check version increment` workflow). The version scheme is
`2.0.0-SNAPSHOT.<N>` where `<N>` grows monotonically.

The `pom.xml` and `dependencies.md` files at the repository root are generated
reports that must stay in sync with the changeset (enforced by the
`Ensure license reports updated` workflow). They are regenerated as part of
`./gradlew build`. When your change affects the version or dependencies, make
sure regenerated reports are part of the changeset.

Source files carry a copyright header; when modifying a file, keep the header
year current (files touched in a given year carry that year).

## Verification and Quality

Never mark a non-trivial change done without verification. Choose the smallest
command that proves the touched behavior, then broaden when shared behavior or
contracts are affected.

Useful root commands (run from the repository root, JDK 11):

```bash
./gradlew :<module>:test
./gradlew :<module>:test --tests "io.spine.chords.proto.money.MoneyFieldSpec"
./gradlew :<module>:check
./gradlew detekt
./gradlew clean build
./gradlew publishToMavenLocal
```

Module names for Gradle paths: `core`, `proto`, `proto-values`, `client`,
`runtime` (located at `codegen/runtime`), and `codegen-tests` (located at
`codegen/tests`).

The `codegen/plugins` directory is a **separate Gradle project** requiring
JDK 17 and Gradle 9.4.x; run its commands from `codegen/plugins/`:

```bash
./gradlew build
./gradlew publishToMavenLocal
```

Modules that use Chords code generation (`proto-values`, `codegen-tests`)
automatically depend on `publishCodegenPluginsToMavenLocal`, which builds and
publishes the codegen plugins locally before they are applied.

Chords libraries are UI libraries; automated tests cannot cover rendering
behavior. For visual/interactive component changes, state clearly in the final
response that behavior was verified by compilation and tests only, and describe
what manual verification remains.

If verification cannot be run, state the reason clearly in the final response.

## Development Conventions

- Use JDK 11 for the root project and JDK 17 for `codegen/plugins`.
- The supported environment is deliberately conservative: Kotlin 1.8.20,
  Compose Multiplatform 1.5.12, Spine Event Engine 1.9.0, Gradle 6.9.4 for the
  root project. Do not assume newer language or library features are available.
- Kotlin explicit API mode is enabled: public declarations require explicit
  `public` modifiers and public API should have KDoc.
- Configure IntelliJ IDEA Detekt with `quality/detekt-config.yml`.
- Keep lines within 100 characters (Detekt `MaxLineLength`).
- UI components follow the class-based component pattern from `core` (see
  `io.spine.chords.core.Component` and its inheritors): composition happens in
  `content()`, pre-composition updates in `beforeComposeContent()`, and
  instance configuration via the `Props`-style lambdas. Composable functions
  and composable-emitting methods are named in `PascalCase`.
- Tests are named `*Spec.kt`, use JUnit Jupiter with Truth/AssertK/Kotest
  matchers depending on the owning module, and keep fixtures in `Given.kt`-style
  files near the test.
- Dependency coordinates live in `buildSrc/src/main/kotlin/io/spine/internal/dependency/`;
  add or change them there, following the existing object-per-library pattern.
- Get the `config` submodule content with
  `git submodule update --init --recursive` before building.

## Code Review

For reviews, lead with findings ordered by severity and include file/line
references. Focus on bugs, regressions, public API breaks, missing tests, and
convention violations.

Skip routine review of generated or vendored files, including:

- `gradlew`, `gradlew.bat`, `gradle/wrapper/**` (root and `codegen/plugins`)
- generated `pom.xml` and `dependencies.md` reports
- generated Protobuf/codegen outputs
- the `config/` submodule
- IDE metadata such as `.idea/**`

Do not skip `buildSrc/**` or `codegen/plugins/buildSrc/**`: they own dependency
and Gradle configuration for Chords.

## Planning and Questions

Start each task by forming an agent-owned plan before editing or running
non-trivial commands. While composing that plan, identify missing requirements,
risks, affected areas, and verification needs.

Ask all clarification questions needed to close uncovered spots in the plan.
Group related questions together when they are blocking the same decision.
Prefer a reasonable assumption only when the answer would not materially change
the plan, implementation, safety posture, or verification path.
