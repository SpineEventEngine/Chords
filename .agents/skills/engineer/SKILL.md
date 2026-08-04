---
name: engineer
description: >
  Routes Chords implementation work to the area-specific engineering skill and
  carries the design-restraint policy shared by all of them. Use for mixed
  component/codegen/build changes or when the owning area is unclear; otherwise
  prefer the narrowest specialist skill directly, and follow "Design Restraint"
  below in either case.
---

# Engineering Router

`.agents/skills/kotlin-engineer/SKILL.md` applies to *all* of the areas
below — it owns the Kotlin language baseline (the pinned 1.8.20 ceiling,
null-safety, coroutine scoping, public types under explicit API mode). Pair
it with the area skill that owns the code being changed:

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

## Design Restraint

This section applies to every area-specific skill above, whichever one the
work routes to. It does not replace the API and safety rules in `AGENTS.md`.

Prefer the simplest construct that satisfies the requirement in front of
you. Reach for a layered, hierarchical, or polymorphic design only when the
present case needs it — not because a future case might.

- **Abstract only over what exists.** Introduce a base class or an interface
  once at least two concrete implementors exist, either already in the
  repository or in the changeset you are writing. One implementor plus an
  anticipated second is one. When a second is coming, write it first and let
  the shared shape fall out of the two.
- **Type parameters answer to a different test.** They have no implementors,
  so the two-implementation heuristic does not apply. A type parameter earns
  its place when it preserves a type relationship the signature would
  otherwise lose — tying an argument to a return type, or a component to the
  value type it edits — and that can be true at a single call site. What
  does not earn its place is a parameter that every use instantiates
  identically, or one that could be replaced by the concrete type with no
  loss of type-safety at the call site.
- **The published-extension-point exception.** Chords is a library, and some
  abstractions are the product rather than speculation:
  `io.spine.chords.core.Component` and the `Props`-style configuration exist
  so that consuming applications subclass them. A public extension point can
  be justified by a single in-repo inheritor when external extension is its
  stated purpose — state that purpose in its KDoc. This exception covers
  deliberate API surface, not internal helpers.
- **Do not add unused capability.** No interface with one implementation and
  no external implementor, no type parameter every use instantiates the
  same way, no configuration option nothing sets, no `open` without a
  subclass, no indirection that only forwards. Explicit API mode makes every
  `public` declaration a compatibility commitment, and the cheapest
  abstraction to change is the one not yet published.
- **Extend the shape already in place** — package structure, module
  boundaries, and the component patterns named in "Safety Rules" and
  "Development Conventions" of `AGENTS.md`. A second hierarchy parallel to
  an existing one needs a reason stated in the final response.
- **Restraint is not an excuse to under-solve.** It does not license
  duplicating non-trivial logic, substituting a workaround for a root-cause
  fix (`AGENTS.md`, "Bug Fixes"), or dropping error handling. The target is
  the smallest design that fully covers the requirement — not the smallest
  diff.

When simplicity and generality are genuinely balanced, take the option that
is cheaper to reverse, and note the trade-off in the final response.

Follow the git-history and safety policy in `AGENTS.md`.
