---
name: codegen-engineer
description: >
  Chords code generation policy. Use for the ProtoData codegen plugins project,
  the codegen runtime library, generated MessageField/MessageOneof/MessageDef
  contracts, codegen correctness tests, and Protobuf declarations with Kotlin
  extensions in proto-values.
---

# Codegen Engineering

## When to Use

Use this skill for code generation and Protobuf model work:

- ProtoData plugins under `codegen/plugins/` (a separate Gradle project).
- The codegen runtime under `codegen/runtime/` (Gradle path `:runtime`):
  `MessageField`, `MessageOneof`, `MessageDef`, and related runtime types.
- Codegen correctness tests under `codegen/tests/` (Gradle path
  `:codegen-tests`).
- Protobuf declarations and Kotlin extensions in `proto-values`.
- The codegen wiring in the root build (`modulesWithChordsCodegen`,
  `publishCodegenPluginsToMavenLocal`, the `io.spine.chords` Gradle plugin
  configuration).

For components that merely consume generated metadata, prefer
`.agents/skills/component-engineer/SKILL.md`. For build-only concerns, use
`.agents/skills/build-engineer/SKILL.md`.

## Policy

- `codegen/plugins` targets JDK 17, Gradle 9.4.x, and Kotlin 2.3.20; the rest
  of the repository targets JDK 11, Gradle 6.9.4, and Kotlin 1.8.20. Never mix
  the two toolchains in one command or assume APIs from one are available in
  the other.
- The generated-code contract is consumed by `proto` and `client` and by
  external projects: changes to `MessageField`/`MessageOneof`/`MessageDef`
  shapes are public API changes on both the generator and runtime sides and
  must stay in sync.
- For Protobuf schema changes in `proto-values`: never delete or renumber
  existing fields, reserve retired field numbers and names, and keep package
  names consistent with the existing `spine/chords/proto/value/**` structure
  under `proto-values/src/main/proto/`.
- Keep `codegen/plugins/src/main/resources/codegen-workspace` resources
  consistent with the build logic that copies `buildSrc` and wrapper files
  into them; that workspace is what the Chords Gradle plugin unpacks in
  consumer projects.
- Do not manually edit generated outputs (`generated/`, `_out/`); change the
  generator and regenerate instead.
- Cover generator behavior changes with tests in `codegen/tests`, which
  exercise generation end-to-end against test Protobuf definitions.

## Verification

Codegen plugin changes (from `codegen/plugins/`, JDK 17):

```bash
./gradlew build
./gradlew publishToMavenLocal
```

Runtime and end-to-end verification (from the repository root, JDK 11):

```bash
./gradlew :runtime:test
./gradlew :codegen-tests:test
./gradlew :proto-values:test
./gradlew clean build
```

The root build republishes codegen plugins to Maven local automatically before
generating; a stale local plugin usually means the `codegen/plugins` build was
not rerun.
