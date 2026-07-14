---
name: engineer
description: >
  Routes Chords implementation work to the area-specific engineering skill. Use
  for mixed component/codegen/build changes or when the owning area is unclear;
  otherwise prefer the narrowest specialist skill directly.
---

# Engineering Router

Use the narrowest implementation skill that fits the task:

- `.agents/skills/component-engineer/SKILL.md` for class-based Compose UI
  components in `core`, `proto`, and `client`: the component model,
  application shell, input components, message forms, validation display, and
  server-connected components.
- `.agents/skills/codegen-engineer/SKILL.md` for the `codegen/plugins`
  ProtoData project, the codegen runtime (`codegen/runtime`), codegen
  correctness tests (`codegen/tests`), generated
  `MessageField`/`MessageOneof`/`MessageDef` contracts, and Protobuf
  declarations in `proto-values`.
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

Follow the git-history and safety policy in `AGENTS.md`.
