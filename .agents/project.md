# Project

This document gives agents and contributors the Chords project overview, module
map, architecture notes, and documentation ownership. For agent
operating policy, read [`AGENTS.md`](../AGENTS.md).

## Overview

**Chords** is a suite of open-source Kotlin/JVM libraries for desktop UI
development with the Compose Multiplatform toolkit. The libraries introduce a
class-based component model on top of Compose, domain-specific input components
that operate on Protobuf messages, and server connectivity based on the Spine
Event Engine framework. The libraries are currently at an experimental stage.

The main concerns are:

- A class-based UI component model and application shell built over Compose (`core`).
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
- `version.gradle.kts`: the single `chordsVersion` for all Chords libraries.
- `buildSrc/`: dependency coordinates, repository helpers, and shared Gradle
  convention plugins for the root project.
- `quality/detekt-config.yml`: Detekt rules for the repository.
- `config/`: Git submodule from `SpineEventEngine/config` with shared build
  scripts and CI helpers.
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
  generate Kotlin extensions for Protobuf messages. It uses a different
  toolchain from the root build; see [Build Engineering](skills/build-engineer/SKILL.md).
  It is applied to consuming projects through the
  [Chords Gradle plugin](https://github.com/SpineEventEngine/Chords-Gradle-plugin).
- `pom.xml`, `dependencies.md`: generated dependency and license reports.
- `.github/workflows/`: CI for Ubuntu/Windows builds, license-report and
  version-increment guards, Gradle wrapper validation, and publishing.
- `.github/copilot-instructions.md`: always-on review rules for GitHub Copilot,
  derived from `AGENTS.md`.
- `.agents/guidelines/`: policy shared across skills — design restraint,
  English style, project-owned file boundaries, and the root build environment.
- `.agents/skills/`: task-specific agent policy; see `.agents/skills/README.md`.
- `.agents/workflows/`: local agent workflow drivers and regression suites.

Gradle group: `io.spine.chords`. Artifact prefix: `spine-chords-`. Preserve
existing package roots, including `io.spine.chords` and `io.spine.money`.

## Architecture Notes

The `core` module defines the class-based component model and application
shell. [Component Engineering](skills/component-engineer/SKILL.md) defines
lifecycle, configuration, state, and layering rules.

The `proto` module builds `MessageForm` and related editors on top of `core`,
using generated `MessageField`/`MessageOneof`/`MessageDef` metadata from the
codegen runtime to bind form fields to Protobuf message fields with validation.

The `client` module connects components to a Spine Event Engine server through
the application shell's `app.client` API (reading, observing, and posting
commands). Components such as `EntityChooser` read and observe entity states.

Code generation connects the separate plugin build to root modules through
Maven-local publication and generated workspaces. See
[Generation Flow](skills/codegen-engineer/SKILL.md#generation-flow) for the
Gradle dependency chain and its configuration.

## Documentation Ownership

| Location | Content |
|---|---|
| `README.md` | Library list, supported environment, consumption, and development setup |
| `AGENTS.md` | Global agent operation, authorization, safety, Git, and quality policy |
| `CLAUDE.md`, `.github/copilot-instructions.md` | Entry points routing each agent to `AGENTS.md` |
| `.agents/skills/`, `.agents/guidelines/` | Task and shared agent policy |
| `.agents/skills/docs-writer/` | Comment, Markdown, issue, and PR writing rules |
| `.agents/skills/build-engineer/` | Versioning, reports, and build policy |
| `.agents/skills/ci-engineer/` | Workflow map and CI policy |
| `.agents/project.md` | Project map, architecture, and documentation ownership |
| `PAIR_AGENTS_RUN_GUIDE.md` | Pair-workflow operator instructions |
| `core/README.md` | Application shell, component model, and core components |
| `proto/README.md` | Protobuf-aware components and message forms |
| `proto-values/README.md` | Supplementary Protobuf messages and extensions |
| `client/README.md` | Server connectivity facilities |
| `codegen/runtime/README.md` | Codegen runtime ownership |
| `codegen/plugins/README.md` | ProtoData plugin project, requirements, and layout |
| `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` | Contribution policy, distributed by `config/pull` |

Keep usage instructions in the nearest library README and API contracts in
KDoc. This file holds the project map and architecture. `AGENTS.md` holds
operating policy, skills hold task-specific rules, and shared guidelines hold
rules used across skills. Link to the responsible document instead of copying it.

## CI

GitHub Actions builds the libraries, checks version and report updates,
validates Gradle wrappers, and publishes artifacts. See
[CI Engineering](skills/ci-engineer/SKILL.md#scope) for workflow triggers,
commands, and credential handling.
