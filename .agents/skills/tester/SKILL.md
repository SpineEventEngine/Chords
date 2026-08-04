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
  assertions, fixtures, stubs, and `testlib` bases — is owned by
  `.agents/skills/kotlin-jvm-tester/SKILL.md`. Follow it once the cases are
  known.
- Put focused regression tests in the module that owns the behavior, under
  `src/test/kotlin` mirroring the production package.
- Prefer public APIs, generated messages, and observable component state over
  private implementation details.
- Cover both success and failure paths for validation, value parsing,
  extensions, and codegen output shape.
- UI rendering and interaction are not covered by automated tests in this
  repository. Do not force UI snapshot tooling in; verify what is testable
  (logic, extensions, codegen) and report the manual-verification remainder
  explicitly.
- Codegen behavior is verified end-to-end in `codegen/tests`
  (`:codegen-tests`), which runs generation against test Protobuf definitions
  and asserts on the generated API; add coverage there for generator changes.
- Do not skip codegen-related Gradle tasks when generator behavior, Protobuf
  schemas, or generated API contracts are part of the change: rebuild
  `codegen/plugins` and run `:codegen-tests:test` so assertions run against
  freshly generated code, not outputs left over from a previous build.
- Avoid tests that depend on a real Spine server, network resources, or local
  absolute paths.

## Verification

Run the smallest useful command while iterating (repository root, JDK 11):

```bash
./gradlew :<module>:test
./gradlew :<module>:test --tests "io.spine.chords.proto.money.MoneyFieldSpec"
./gradlew :codegen-tests:test
./gradlew clean build
```

Module Gradle paths: `core`, `proto`, `proto-values`, `client`, `runtime`,
`codegen-tests`. The `codegen/plugins` project verifies separately from
`codegen/plugins/` with JDK 17 (`./gradlew build`).

Read the task list, not only the final line. A build whose compile and test
tasks all report `UP-TO-DATE` finished in seconds without compiling or running
anything, and its `BUILD SUCCESSFUL` describes a previous build rather than the
change in the worktree. When that happens, force the work with `--rerun-tasks`
before reporting a result:

```bash
./gradlew :<module>:test --tests "…" --rerun-tasks
```

Gradle's up-to-date check is content-based, so an `UP-TO-DATE` task is normally
sound. It is misleading only when it stands in as evidence for a change that
was never built. Report a verification result as green only when the tasks
covering the change actually executed.

Follow the git-history and safety policy in `AGENTS.md`.
