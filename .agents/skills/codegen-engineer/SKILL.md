---
name: codegen-engineer
description: >
  Chords code generation policy. Use for the Spine Compiler plugins project,
  the codegen runtime library, generated MessageField/MessageOneof/MessageDef
  contracts, plugin-internal Protobuf declarations, codegen correctness tests,
  and codegen Gradle wiring.
---

# Codegen Engineering

## When to Use

Use this skill for code generation work:

- Spine Compiler plugins under `codegen/plugins/` (a separate Gradle project).
- Plugin-internal schemas: `codegen/plugins/codegen-plugins/src/main/proto/**`.
- The codegen runtime under `codegen/runtime/` (Gradle path `:runtime`):
  `MessageField`, `MessageOneof`, `MessageDef`, and related runtime types.
- Codegen correctness tests under `codegen/tests/` (Gradle path `:codegen-tests`).
- Root codegen wiring: `modulesWithChordsCodegen`,
  `publishCodegenPluginsToMavenLocal`, and the `spineCompiler` configuration.

For published model Protobuf declarations and Kotlin model extensions under
`proto-values`, prefer `.agents/skills/model-engineer/SKILL.md`. For components
that merely consume generated metadata, prefer
`.agents/skills/component-engineer/SKILL.md`. For build-only concerns, use the
`.agents/skills/build-engineer/SKILL.md` guidance.

## Policy

- Both builds target JDK 25, Gradle 9.8.2, and Kotlin 2.4.21. `codegen/plugins`
  remains a separate build; always invoke its own verification wrapper.
- The generated-code contract is consumed by `proto` and `client` and by
  external projects: changes to `MessageField`/`MessageOneof`/`MessageDef`
  shapes are public API changes on both the generator and runtime sides and
  must stay in sync.
- Treat `.proto` files under `codegen/tests/src/test/proto/**` as generator
  fixtures. Change them only to express a code-generation scenario; published
  model declarations under `proto-values` belong to `model-engineer`.
- Do not manually edit generated outputs (`generated/`, `_out/`); change the
  generator and regenerate instead.
- Cover generator behavior changes with tests in `codegen/tests`, which
  exercise generation end-to-end against test Protobuf definitions.

## Generation Flow

The separate `codegen/plugins` build produces Spine Compiler plugins. The root includes
that build and substitutes the generator and runtime with their checkout projects, so a
new version compiles before publication. The Chords generator runs alongside Spine's
validation generator in the same Compiler invocation. `modulesWithChordsCodegen` selects
`proto-values` and `codegen-tests`; their `spineCompiler` dependencies carry the generator's
build dependencies. `publishCodegenPluginsToMavenLocal` publishes the included generator
and root runtime for external consumers.

## Verification

Codegen plugin changes, from the repository root:

```bash
.agents/workflows/gradle-codegen.sh build
.agents/workflows/gradle-codegen.sh publishToMavenLocal
```

Runtime and end-to-end verification, following
`.agents/guidelines/root-build.md`, from the repository root:

```bash
.agents/workflows/gradle-root.sh :runtime:test
.agents/workflows/gradle-root.sh :codegen-tests:test
.agents/workflows/gradle-root.sh :proto-values:test
.agents/workflows/gradle-root.sh clean build
```

Generation builds the checkout's generator and runtime through composite dependencies.
Use `publishCodegenPluginsToMavenLocal` when verifying an external consumer.
