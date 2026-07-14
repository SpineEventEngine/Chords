# Project

This document gives agents and contributors the Chords project overview, module
map, architecture notes, documentation ownership, and CI notes. For agent
operating policy, read [`AGENTS.md`](../AGENTS.md).

## Overview

**Chords** is a suite of open-source Kotlin/JVM libraries for desktop UI
development with the Compose Multiplatform toolkit. The libraries introduce a
class-based component model on top of Compose, domain-specific input components
that operate on Protobuf messages, and server connectivity based on the Spine
Event Engine framework. The libraries are currently at an experimental stage.

The main concerns are:

- A class-based UI component model and application shell on top of Compose
  (`core`).
- Input components, message forms, and validation for Protobuf-based domain
  models (`proto`).
- Supplementary Protobuf messages and Kotlin extensions (`proto-values`).
- Server connectivity, command posting, and entity subscriptions via Spine
  Event Engine (`client`).
- Code generation that enriches Protobuf message APIs with `MessageField`,
  `MessageOneof`, and `MessageDef` implementations (`codegen/*`).

Artifacts are published as `io.spine.chords:spine-chords-<module>` to the Spine
snapshots repository (`https://europe-maven.pkg.dev/spine-event-engine/snapshots`)
and GitHub Packages.

## Project Map

- `settings.gradle.kts`: root Gradle module registry (`core`, `runtime`,
  `proto-values`, `proto`, `client`, `codegen-tests`).
- `build.gradle.kts`: root Gradle configuration for group/version, the Chords
  codegen Gradle plugin wiring, publishing, license reports, and Jacoco.
- `version.gradle.kts`: the single `chordsVersion` for all Chords libraries;
  must be incremented in every PR.
- `buildSrc/`: dependency coordinates, repository helpers, and shared Gradle
  convention plugins for the root project.
- `quality/detekt-config.yml`: Detekt rules for the repository.
- `config/`: Git submodule shared across Spine repositories with build scripts
  and CI helpers; owned by `SpineEventEngine/config`, do not edit here.
- `core/`: application shell, class-based `Component` model, input component
  base classes, layouts, and primitive UI components.
- `proto/`: Protobuf-aware UI components — `MessageForm`, field editors, oneof
  support, and validation display for Protobuf domain models.
- `proto-values/`: supplementary Protobuf message declarations (e.g., money
  types) with Kotlin extensions; uses Chords code generation.
- `client/`: server connectivity components — application shell client
  extensions, command posting, entity subscriptions, and entity-backed
  components such as `EntityChooser`.
- `codegen/runtime/` (Gradle path `:runtime`): the runtime library required by
  `proto` and `client` to use generated `MessageField`/`MessageOneof`/
  `MessageDef` implementations.
- `codegen/tests/` (Gradle path `:codegen-tests`): tests that check the
  correctness of code generation; uses Chords code generation.
- `codegen/plugins/`: **separate Gradle project** with ProtoData plugins that
  generate Kotlin extensions for Protobuf messages. Requires JDK 17,
  Gradle 9.4.x, and Kotlin 2.3.20, unlike the root project (JDK 11,
  Gradle 6.9.4, Kotlin 1.8.20). Applied to consuming projects through the
  [Chords Gradle plugin](https://github.com/SpineEventEngine/Chords-Gradle-plugin).
- `pom.xml`, `dependencies.md`: generated dependency/license reports; must be
  regenerated (not hand-edited) when the version or dependencies change.
- `.github/workflows/`: CI for Ubuntu/Windows builds, license-report and
  version-increment guards, Gradle wrapper validation, and publishing.

Gradle group: `io.spine.chords`. Artifact prefix: `spine-chords-`. Preserve
package roots such as `io.spine.chords` and `io.spine.money` where already
present.

## Architecture Notes

The `core` module defines the class-based component model. Components extend
`io.spine.chords.core.Component` (or `InputComponent` for value-editing
components), render in `content()`, and perform pre-composition state updates
in `beforeComposeContent()`. Components are configured declaratively with
`Props`-style configuration lambdas and are instantiated per usage site via
companion-object `invoke` operators. State that must trigger recomposition is
held in `mutableStateOf`-backed properties.

The `proto` module builds `MessageForm` and related editors on top of `core`,
using generated `MessageField`/`MessageOneof`/`MessageDef` metadata from the
codegen runtime to bind form fields to Protobuf message fields with validation.

The `client` module connects components to a Spine Event Engine server through
the application shell's `app.client` API (reading, observing, and posting
commands). Components such as `EntityChooser` read and observe entity states.

Code generation flow: the `codegen/plugins` project builds ProtoData plugins;
the root build's `publishCodegenPluginsToMavenLocal` task publishes them to
Maven local; the `io.spine.chords` Gradle plugin then applies them to the
modules listed in `modulesWithChordsCodegen` (`proto-values`, `codegen-tests`)
inside a generated codegen workspace. This chain is wired automatically into
the root build; touching it usually means `build.gradle.kts` or
`buildSrc` work.

The versions of the toolchain are intentionally conservative to match consumer
projects (Spine 1.9.x ecosystem). Do not casually bump Kotlin, Compose, Gradle,
or Spine versions; such upgrades are dedicated tasks with wide impact.

## Documentation Ownership

- `README.md`: project entry point — library list, supported environment,
  consumption instructions, and development setup.
- `AGENTS.md`: repository operating policy for agents.
- `.agents/project.md`: project map, architecture notes, documentation
  ownership, and CI notes.
- `core/README.md`: application shell, component model, and core components.
- `proto/README.md`: Protobuf-aware components and message forms.
- `proto-values/README.md`: supplementary Protobuf messages and extensions.
- `client/README.md`: server connectivity facilities.
- `codegen/runtime/README.md`: codegen runtime ownership.
- `codegen/plugins/README.md`: ProtoData plugin project — requirements,
  workspace resources, and module layout.
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`: standard contribution policy.

Keep usage instructions in the nearest library README. Keep architecture,
module ownership, and CI notes in this file. Keep agent policy in `AGENTS.md`.
API-level documentation belongs in KDoc on the public declarations.

## CI

This repository is configured with these GitHub workflows:

- `Build under Ubuntu` (`.github/workflows/build-on-ubuntu.yml`): runs on every
  push, sets up JDK 11, and runs `./gradlew build --stacktrace`.
- `Build under Windows` (`.github/workflows/build-on-windows.yml`): runs on
  pull requests and builds with `gradlew.bat build --stacktrace --no-daemon`.
- `Ensure license reports updated` (`.github/workflows/ensure-reports-updated.yml`):
  runs on pull requests and fails when `dependencies.md` / `pom.xml` (or
  `version.gradle.kts`) were not updated in the changeset.
- `Check version increment` (`.github/workflows/increment-guard.yml`): runs
  `./gradlew checkVersionIncrement` to enforce that `chordsVersion` grows.
- `Validate Gradle Wrapper` (`.github/workflows/gradle-wrapper-validation.yml`):
  validates wrapper JAR integrity.
- `Publish` (`.github/workflows/publish.yml`): runs on pushes to `master`,
  decrypts publishing credentials from `.github/keys/*.gpg`, and publishes
  artifacts to the configured Maven repositories.

Publishing credentials and their rotation are human-owned.
