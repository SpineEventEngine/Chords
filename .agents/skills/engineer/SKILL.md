---
name: engineer
description: >
  Routes Chords implementation work to the area-specific engineering skill and
  carries the design restraint shared by all of them. Use for mixed
  component/model/codegen/build changes or when the owning area is unclear;
  otherwise prefer the narrowest specialist skill directly, and apply the
  design-restraint guideline in either case.
---

# Engineering Router

`.agents/skills/kotlin-engineer/SKILL.md` applies to *all* of the areas
below — it owns the Kotlin language baseline (the root compiler/library split,
the separate codegen-plugin toolchain, null-safety, coroutine scoping, and
public types under explicit API mode). Pair it with the area skill that owns
the code being changed:

- `.agents/skills/component-engineer/SKILL.md` for class-based Compose UI
  components in `core`, `proto`, and `client`: the component model,
  application shell, input components, message forms, validation display, and
  server-connected components.
- `.agents/skills/model-engineer/SKILL.md` for published model Protobuf
  declarations and Kotlin model extensions under `proto-values`, including
  schema evolution and package and file organization.
- `.agents/skills/codegen-engineer/SKILL.md` for the `codegen/plugins`
  ProtoData project, the codegen runtime (`codegen/runtime`), codegen
  correctness tests (`codegen/tests`), plugin-internal Protobuf declarations,
  generated `MessageField`/`MessageOneof`/`MessageDef` contracts, and codegen
  Gradle wiring.
- `.agents/skills/build-engineer/SKILL.md` for root and `codegen/plugins`
  Gradle build logic, `buildSrc` dependency coordinates, publishing wiring,
  version policy, generated `pom.xml`/`dependencies.md` reports, and the
  `config` submodule relationship.
- `.agents/skills/ci-engineer/SKILL.md` for GitHub Actions workflows under
  `.github/workflows`: Ubuntu/Windows builds, license-report and
  version-increment guards, wrapper validation, and publishing.
- `.agents/skills/security-reviewer/SKILL.md` for publishing credentials,
  workflow secrets, dependency provenance, agent prompt/configuration safety
  under `.agents/**`, and secret-exposure review.

For changes that cross areas, read each area-specific skill and keep
verification commands separate (the root project and `codegen/plugins` use
different JDKs and Gradle versions).

For documentation changes alongside implementation, also use
`.agents/skills/docs-writer/SKILL.md`. For verification work, use
`.agents/skills/tester/SKILL.md`. To review an implementation diff for
correctness and regressions, use `.agents/skills/code-reviewer/SKILL.md`.

## Design Restraint

Apply [Design Restraint](../../guidelines/design-restraint.md) to every
implementation, including work routed directly to a specialist. It defines when
an abstraction, type parameter, or configuration option earns its place, the
published-extension-point exception that Chords relies on for
`io.spine.chords.core.Component` and `Props`-style configuration, and the limit
that keeps restraint from becoming under-solving.

Follow the git-history and safety policy in `AGENTS.md`.
