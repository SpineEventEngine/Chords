---
name: tester
description: >
  Chords test and verification policy. Use to decide what to cover and how to
  verify it across core, proto, proto-values, client, the codegen runtime, and
  codegen correctness tests, including Gradle verification commands.
  `kotlin-jvm-tester` owns how a suite is written.
---

# Testing

## Core Policy

- How a suite is written — naming, JUnit Jupiter structure, Kotest
  assertions, fixtures, stubs, and `testlib` bases — follows
  `.agents/skills/kotlin-jvm-tester/SKILL.md` once cases are known.
- Put focused regression tests in the module that owns the behavior, under
  `src/test/kotlin` mirroring the production package.
- Prefer public APIs, generated messages, and observable component state over
  private implementation details.
- Cover both success and failure paths for validation, value parsing,
  extensions, and codegen output shape.
- UI rendering and interaction are not covered by automated tests in this
  repository. Do not force UI snapshot tooling in; verify what is testable
  (logic, extensions, codegen) and report manual verification explicitly.
- Codegen behavior is verified end-to-end in `codegen/tests`
  (`:codegen-tests`), which runs generation against test Protobuf definitions
  and asserts on the generated API; add coverage there for generator changes.
- For published model Protobuf declarations or Kotlin model extensions in
  `proto-values`, run `:proto-values:test` or `:proto-values:check` so the
  module generates and compiles accessors against the changed model.
- When generator behavior or generated API contracts change, rebuild
  `codegen/plugins` and run `:codegen-tests:test` so assertions run against
  freshly generated code, not outputs left over from a previous build.
- Avoid tests that depend on a real Spine server, network resources, or local
  absolute paths.

## Verification

Apply `.agents/guidelines/root-build.md`, then run the smallest useful command
while iterating, from the repository root:

```bash
.agents/workflows/gradle-root.sh :<module>:test
.agents/workflows/gradle-root.sh :<module>:test \
    --tests "io.spine.chords.proto.money.MoneyFieldSpec"
.agents/workflows/gradle-root.sh :codegen-tests:test
.agents/workflows/gradle-root.sh clean build
```

Module Gradle paths: `core`, `proto`, `proto-values`, `client`, `runtime`,
`codegen-tests`. The separate `codegen/plugins` project verifies with
`.agents/workflows/gradle-codegen.sh build`, which selects JDK 17.

Read the task list, not only the final line. Gradle's up-to-date check is
content-based, so `UP-TO-DATE` normally means the task's inputs are unchanged
and its previous result still holds. Do not force a rerun merely to make the
task execute again.

What `UP-TO-DATE` cannot prove is that the right build and tasks were selected.
Suspect the result, and only then rerun, when tasks that should cover a changed
input remain up to date. Check that the command used the wrapper for the owning
build and that the task declares the changed input. Fix the invocation before
rerunning; a rerun of the wrong build is still the wrong build.

Where a rerun is genuinely required, the root build's Gradle 6.9.4 supports the
whole-graph flag:

```bash
.agents/workflows/gradle-root.sh :<module>:test --tests "…" --rerun-tasks
```

Report a verification result as green only when the tasks covering the change
were evaluated in this worktree and the correct build, then either executed or
were legitimately up to date.

Follow the git-history and safety policy in `AGENTS.md`.
