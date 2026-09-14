---
name: kotlin-jvm-tester
description: >
  Chords JVM test-writing policy: Kotlin, JUnit Jupiter, Kotest assertions,
  `internal` `*Spec` suites, hand-written stubs, fixtures, and `testlib` bases.
  Use when adding, restructuring, or reviewing tests in any JVM module.
---

# Kotlin JVM Tests

This skill defines how tests are written. `tester` decides what to cover and
how to verify it; `kotlin-engineer` governs the Kotlin; the area skill governs
the code under test. `AGENTS.md` remains authoritative for repository policy.

## Core Rules

- Write tests in Kotlin. Add a Java interoperability test only when the user
  requests one and confirms its placement and naming.
- Use JUnit Jupiter for structure (`@Test`, `@Nested`, `@DisplayName`,
  `@BeforeEach`, `@ParameterizedTest`, `@MethodSource`) and Kotest matchers for
  assertions. Do not use JUnit, Truth, or AssertK assertions directly;
  inherited `testlib` checks may use them internally.
- Do not use Kotest spec styles. The build includes only the Jupiter engine:

  ```kotlin
  useJUnitPlatform {
      includeEngines("junit-jupiter")
  }
  ```

  A `FunSpec`, `StringSpec`, or `DescribeSpec` compiles but never runs.
- Import `@DisplayName` from `org.junit.jupiter.api`, not Kotest.
- Declare suites as `internal class …Spec` unless an abstract base must be
  reused across modules.
- Use hand-written stubs; no mocking framework is available.
- Document the suite, every test, and every helper declaration with KDoc.

## Workflow

1. Read the target and the nearest suite in full. Follow this skill when the
   neighboring suite uses an older convention.
2. Name the suite and helpers as below.
3. Choose a `testlib` base only when the target matches the base table.
4. Place the suite under `<module>/src/test/kotlin/…`, mirroring the production
   package and copyright header.
5. Run the narrowest module test from `tester`, or report why it was unavailable.

## Naming

- Use `<Subject>Spec.kt` and `internal class <Subject>Spec`; `*Test` and a
  separate integration-test suffix are not used. Rename a materially changed
  legacy `*Test` suite.
- The subject must be a real production class, object, or UI component,
  including a composable component. Preserve its exact spelling and casing.
  For `InputField`, use `InputFieldSpec.kt`, `InputFieldSpec`, and
  ``@DisplayName("`InputField` should")``. Apply this to every new or
  materially reworked suite.
- Test extensions under their declared receiver type. Do not derive a subject
  from a source file, helper function, property, or scenario description.
- Identify which production subject provides the tested behavior. Split a
  suite that tests multiple subjects. If no subject can be identified, omit
  the suite; do not add a production wrapper just to give a test a name.
- Use `<Subject>SpecEnv` only for a suite-specific object with utility functions
  that create test data or arrange scenarios. Place it in a `given` subpackage.
- Name other test-support types for their role and place them in `given` too.
  Stubs, fakes, fixtures, factories, recorders, and configurable collaborators
  must not use the `SpecEnv` suffix. Do not introduce `TestEnv` names.
- Make method and nested-group names continue the suite's `should` sentence.
  Backtick multi-word names and Kotlin keywords, but not a legal single name.

## Structure

Use `core/src/test/kotlin/io/spine/chords/core/appshell/AppWindowSpec.kt` and
its `given/AppWindowSpecEnv.kt` as the flat-suite reference.

- Keep one subject per suite and separate arrange, act, and assert with blanks.
- Keep only tests, framework lifecycle/setup, required overrides, and `assert*`
  helpers in the suite. A companion `@MethodSource` factory is framework setup;
  move scenario-building utilities, drivers, readers, extractors, and selectors
  to the matching `SpecEnv`. Keep fixture types in role-named files under `given`.
- Prefix assertion helpers with `assert`. Do not disguise a factory, driver,
  or state reader as an assertion helper.
- Do not pin presentation-only copy by repeating labels, tooltips, headings,
  or button text in assertions. Exact text checks are appropriate when text
  is a business result or another externally observed contract.
- Use KDoc for rationale, regression history, or fixture contracts rather than
  repeating the method name.
- Use `shouldThrow<E>` and assert typed properties rather than exception-message
  text, unless that text is itself an externally observed contract:

  ```kotlin
  val failure = shouldThrow<StatusRuntimeException> {
      source.readValue()
  }
  failure.status.code shouldBe UNAVAILABLE
  ```

- Wrap looped samples in `withClue`; prefer `@ParameterizedTest` for fixed,
  enumerable cases so a failure identifies its case.
- Read [Advanced Test Patterns](references/advanced-test-patterns.md) for nested
  and parameterized layouts, application setup, or `testlib` base selection.

## Fixtures and Stubs

- Keep suite-specific setup in its `SpecEnv`; local examples are
  `DialogSpecEnv` and `AppWindowSpecEnv`.
- Name a configurable collaborator `Configurable...`; give other stubs names
  that describe their role. Expose only the controls the cases need.
- Reset shared mutable state in `@BeforeEach`; never depend on suite order.
- Reuse fixtures and remove unused helper APIs. Move one into a role-named
  shared file only after a second suite needs it.
- Keep JVM-wide environment installers in separate role-named files, not a
  `SpecEnv`; see the advanced reference.
- Do not add Gradle `java-test-fixtures`; share plain test-source classes.
- Build Protobuf values with Java builder chains, never `.apply { }`. Use
  `vBuild()` when Spine validation belongs to the case.
- Do not depend on real external services, production resources, credentials,
  signing material, or local absolute paths.

## `testlib` Bases and Helpers

`spine-testlib` and Guava testlib are available in every module. All bases are
in `io.spine.testing`; `EqualsTester` is from Guava.

| Target | Extend |
|---|---|
| Final utility class with private constructor and statics | `UtilityClassTest<T>` |
| Class-level or static concerns only | `ClassTest<T>` |
| Singleton class | `SingletonTest<T>` |

A suite extends at most one base. A Kotlin `object`, component, or ordinary
class normally needs none. Read “What each `testlib` base contributes” in the
advanced reference before choosing; the bases contribute different checks.

Standalone helpers work with or without a base:

| Need | Use |
|---|---|
| `equals()`/`hashCode()` contract | Guava `EqualsTester` |
| Random or sample values | `TestValues` |

Do not use `io.spine.testing.Assertions`; Kotest provides the corresponding
exception assertions. `EqualsTester` remains because it is a contract tester,
not an assertion style.

## Repository Requirements

- A tests-only PR follows
  [Versioning and Reports](../build-engineer/SKILL.md#versioning-and-reports) too.
- Keep test lines within 100 characters.
- Use the existing UI harness where it can prove the behavior; follow `tester`
  for coverage limits and report any remaining manual verification.

## Report

Return the test files, suite name, helpers or stubs, and the verification task
with its result or reason it was not run.
