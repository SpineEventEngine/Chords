---
name: codegen-engineer
description: >
  Chords code generation policy. Use for the ProtoData codegen plugins project,
  the codegen runtime library, generated MessageField/MessageOneof/MessageDef
  contracts, plugin-internal Protobuf declarations, codegen correctness tests,
  and codegen Gradle wiring.
---

# Codegen Engineering

## When to Use

Use this skill for code generation work:

- ProtoData plugins under `codegen/plugins/` (a separate Gradle project).
- Plugin-internal Protobuf declarations under
  `codegen/plugins/codegen-plugins/src/main/proto/**`.
- The codegen runtime under `codegen/runtime/` (Gradle path `:runtime`):
  `MessageField`, `MessageOneof`, `MessageDef`, and related runtime types.
- Codegen correctness tests under `codegen/tests/` (Gradle path
  `:codegen-tests`).
- The codegen wiring in the root build (`modulesWithChordsCodegen`,
  `publishCodegenPluginsToMavenLocal`, the `io.spine.chords` Gradle plugin
  configuration).

For published model Protobuf declarations and Kotlin model extensions under
`proto-values`, prefer `.agents/skills/model-engineer/SKILL.md`. For components
that merely consume generated metadata, prefer
`.agents/skills/component-engineer/SKILL.md`. For build-only concerns, use
`.agents/skills/build-engineer/SKILL.md`.

## Policy

- `codegen/plugins` targets JDK 17, Gradle 9.4.x, and Kotlin 2.3.20; the root
  build targets JDK 11 and Gradle 6.9.4, with the Kotlin version split described
  in `.agents/skills/kotlin-engineer/SKILL.md`. Never mix the two toolchains in
  one command or assume APIs from one are available in the other.
- The generated-code contract is consumed by `proto` and `client` and by
  external projects: changes to `MessageField`/`MessageOneof`/`MessageDef`
  shapes are public API changes on both the generator and runtime sides and
  must stay in sync.
- Treat `.proto` files under `codegen/tests/src/test/proto/**` as generator
  fixtures. Change them only to express a code-generation scenario; published
  model declarations under `proto-values` belong to `model-engineer`.
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
.agents/workflows/gradle-root.sh :runtime:test
.agents/workflows/gradle-root.sh :codegen-tests:test
.agents/workflows/gradle-root.sh :proto-values:test
.agents/workflows/gradle-root.sh clean build
```

The root build republishes codegen plugins to Maven local automatically before
generating; a stale local plugin usually means the `codegen/plugins` build was
not rerun.
