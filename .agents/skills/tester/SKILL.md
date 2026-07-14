---
name: tester
description: >
  Chords test and verification policy. Use for test authoring and verification
  strategy across core, proto, proto-values, client, the codegen runtime, and
  codegen correctness tests, including Gradle verification commands.
---

# Testing

## Core Policy

- Follow existing local style. Tests are named `*Spec.kt` and use JUnit
  Jupiter with Truth, AssertK, or Kotest matchers depending on the owning
  module; check neighboring tests before introducing a new dependency.
- Put focused regression tests in the module that owns the behavior, under
  `src/test/kotlin` mirroring the production package.
- Keep fixtures small and near the tests (`Given.kt`-style files), named after
  the behavior they represent.
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

Follow the git-history and safety policy in `AGENTS.md`.
