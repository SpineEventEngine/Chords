---
name: kotlin-jvm-tester
description: >
  Chords authority on how a JVM test is written: Kotlin, JUnit Jupiter
  structure, Kotest assertions, `internal` `*Spec` classes, hand-rolled
  stubs, and a `testlib` base class when the target's shape calls for one.
  Use when adding, restructuring, or reviewing a test in `core`, `proto`,
  `proto-values`, `client`, `runtime`, or `codegen/tests` — a fresh suite,
  more cases, a rewrite, or judging whether an existing suite follows the
  conventions. `tester` owns which cases are worth writing and how to verify
  them; `kotlin-engineer` remains the baseline for the Kotlin inside each
  test body.
---

# Kotlin JVM tester

This skill is the single source of truth for *how a test is written* in
Chords. It does not decide *what* to test — that comes from the caller: a
bug fix dictates its own reproducing case, a feature change its own
coverage.

Two companions own neighboring concerns; defer to them rather than
restating:

- `.agents/skills/tester/SKILL.md` — what to cover, where it belongs, and
  the Gradle verification commands.
- `.agents/skills/kotlin-engineer/SKILL.md` — the Kotlin baseline. A test
  body is Kotlin, so its null-safety, coroutine scoping, and language
  ceiling obey the same rules as production code.
- `.agents/skills/engineer/SKILL.md` — the router to the area-specific
  engineering skill (`component-engineer`, `model-engineer`,
  `codegen-engineer`, `build-engineer`, …). Use it to find the skill owning
  the code under test when you need its API constraints.

`AGENTS.md` remains authoritative for Git history, versioning, and
verification policy.

## Core policy

1. **Every test is Kotlin.** No module currently contains tests under
   `src/test/java`, and every suite is Kotlin. Do not add a Java test. The
   published Kotlin declarations are callable from Java like any JVM
   library, so a Java-interoperability suite is conceivable — treat it as
   out of scope unless the user asks for that coverage explicitly, and
   agree on its placement and naming with them first.
2. **JUnit Jupiter for structure; Kotest for assertions.** Use the
   class-based Jupiter model — `@Test`, `@Nested`, `@DisplayName`,
   `@BeforeEach`, `@ParameterizedTest`, `@MethodSource` — and assert with
   Kotest matchers (`shouldBe`, `shouldNotBe`, `shouldThrow`, `withClue`).
3. **Kotest spec styles do not run here.** The `setupTests()` function in
   `buildSrc/src/main/kotlin/jvm-module.gradle.kts` registers only the
   Jupiter engine:

   ```kotlin
   useJUnitPlatform {
       includeEngines("junit-jupiter")
   }
   ```

   A `FunSpec`/`StringSpec`/`DescribeSpec` suite compiles and is then
   silently skipped — it never fails, and it never runs. None exist in the
   repository; do not add one.
4. **`@DisplayName` comes from `org.junit.jupiter.api`.** Kotest ships an
   annotation of the same name, and an IDE import completion picks it
   readily. `io.kotest.core.spec.DisplayName` is read by the Kotest engine,
   which is excluded here, so it has no effect on the report. Check the
   import whenever you add or edit the annotation — four existing suites
   (`WallClockSpec`, `DateTimeFieldSpec`, `MoneyFieldSpec`, `MoneyExtsSpec`)
   carry the Kotest one and are therefore reported by method name only.
5. **Mark suites `internal`.** Every suite in the repository is
   `internal class …Spec`. Keep it that way unless the class is an abstract
   base reused from another module.
6. **Stubs, not mocks.** No mocking framework is on the classpath by
   design. Write hand-rolled fakes — see "Fixtures and stubs".
7. **Kotest assertions only — in the assertions you write.** No suite uses
   anything else. AssertK is deprecated in favor of Kotest
   (see the `@Deprecated` annotation on the `AssertK` object in
   `buildSrc/src/main/kotlin/io/spine/internal/dependency/`).
   Truth is not declared directly on any test configuration, but it arrives
   transitively through `spine-testlib` at compile scope, so
   `com.google.common.truth.Truth` will import and compile. Resolving is not
   permission: use Kotest. If a subject genuinely cannot be expressed with
   Kotest matchers, raise it rather than quietly importing a second
   assertion library.

   This rule governs the assertions in your suite, not the internals of the
   helpers it inherits. The `testlib` base classes assert with JUnit and
   Truth inside their own inherited tests; that is theirs, and it is not a
   precedent for the cases you write.

## Workflow

1. **Read first.** Read the class under test in full — public API,
   constructors, branches, `when`/sealed exhaustiveness, error paths. Then
   read the nearest existing suite in the same module and match its
   structure, fixtures, and imports.
2. **Name the suite** per "Naming": `<ClassUnderTest>Spec.kt`.
3. **Classify the target, then pick a base or a helper**, per "Pick a base or
   helper". Most Chords targets are Kotlin `object`s, Compose component
   classes, or ordinary classes, for which a bare `internal class …Spec` is
   correct. A base class is required only when the target's shape matches a
   row in the *base class* table — never hand-roll a check a base already
   provides. The standalone helpers are independent of that choice: reach
   for them from any suite, with or without a base.
4. **Write the test** following "Structure and formatting". Place it under
   `<module>/src/test/kotlin/…`, mirroring the package of the code under
   test. Reuse the surrounding files' copyright header.
5. **Verify** by running the narrowest Gradle test task for the module,
   unless the user forbids verification or it is unavailable in this
   environment — in which case report why it was not run instead of running
   it anyway. Follow the verification policy in
   `.agents/skills/tester/SKILL.md`, including its warning about
   `UP-TO-DATE` tasks standing in as evidence.

## Naming

**A suite is `<subject>Spec.kt`** — the single naming form in this
repository, and the one `AGENTS.md` states. There is no `*Test` suffix for
a suite, and no separate suffix for an integration test; `Spec` covers
every suite until a source set exists that needs otherwise, at which point
the convention is settled with the user and recorded in both `AGENTS.md`
and this skill.

- **A file of extensions takes the file's name**, not a class name —
  `MoneyExts.kt` → `MoneyExtsSpec.kt` in `proto-values`.
- **Give every new suite a `@DisplayName`** with a "should" lead-in and the
  subject in backticks: ``@DisplayName("`WallClock` should")``. For an
  extension file the subject reads naturally:
  ``@DisplayName("Extensions for `Money` should")``. Older suites in
  `client` and `core` predate this rule; leave them unless you are already
  editing them.
- **Test method names read as sentences, backticked only when they must
  be.** A multi-word name is a backticked sentence:
  `` fun `read initial data and apply subscription updates`() ``. A name
  that is a single legal Kotlin identifier is written plainly —
  `fun initialize()` — unless it is a hard keyword (`is`, `in`, `object`,
  `fun`, …), which still needs backticks: `` fun `is`() ``. The same rule
  governs `@Nested` class names.

## Structure and formatting

A canonical suite, following
`client/src/test/kotlin/io/spine/chords/client/DataObservationImplSpec.kt`
for body shape and
`codegen/tests/src/test/kotlin/io/spine/chords/codegen/CodegenPluginsSpec.kt`
for the `@DisplayName` import:

```kotlin
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [DataObservationImpl] publishes initial data and reacts to
 * failures reported by its subscription source.
 */
@DisplayName("`DataObservationImpl` should")
internal class DataObservationImplSpec {

    /**
     * A subscription update must overwrite the value read initially.
     */
    @Test
    fun `read initial data and apply subscription updates`() {
        val source = FakeObservationSource("initial")
        val observation = source.createObservation()

        observation.initialize()
        source.emit("updated")

        observation.value shouldBe "updated"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    /**
     * Cancellation is a caller's decision rather than an observation
     * failure, so it propagates instead of becoming a failure status.
     */
    @Test
    fun `propagate cancellation without converting it into failure status`() {
        val source = FakeObservationSource("unused")
        source.readFailure = CancellationException("Caller cancelled refresh.")
        val observation = source.createObservation()

        shouldThrow<CancellationException> {
            observation.initialize()
        }

        observation.status.value shouldBe DataObservationStatus.Refreshing
    }
}
```

Arrange, act, and assert are separated by blank lines, as above. Both cases
exercise the same subject; a suite does not mix unrelated subjects.

**KDoc is required, on the suite and on every test method**, per the
repository convention in `AGENTS.md`. A sentence-like method name states
*what* the case does and does not waive the requirement — use the KDoc for
what the name cannot carry: why the behavior matters, the bug a regression
test pins down, or a fixture's contract. Existing suites are inconsistent
here; new ones are not.

**Throwing.** Use Kotest's `shouldThrow<E> { … }`
(`io.kotest.assertions.throwables.shouldThrow`), not JUnit's
`assertThrows`, as in the second case above. `shouldThrow` returns the
thrown exception, so bind it when the exception itself carries something
worth asserting. Assert a typed property, not the message — the standing
repository rule is that tests do not assert text-message content unless the
user explicitly asks for it. The shape, in the abstract:

```kotlin
val failure = shouldThrow<StatusRuntimeException> {
    source.readValue()
}
failure.status.code shouldBe UNAVAILABLE
```

**Loop assertions carry a clue.** When a single test walks a sample table,
wrap each iteration in `withClue` so a failure names the offending input —
see `proto-values/src/test/kotlin/io/spine/money/MoneyExtsSpec.kt`.
Prefer `@ParameterizedTest` for a fixed, enumerable set of cases.

**Nested groups and parameterized tests** have their own layout rules —
the one-line `@Nested` declaration with the name on the next line, and the
`@TestInstance(PER_CLASS)` + private-instance-method `@MethodSource` shape
Chords uses. See `references/advanced-test-patterns.md` when you reach for
either.

## Fixtures and stubs

- **Shared test data goes in a `Given.kt`** next to the suites that use it,
  as an `object Given` of factory functions named for the data they
  produce — see
  `codegen/tests/src/test/kotlin/io/spine/chords/codegen/Given.kt`.
- **A stub used by one suite is a `private class Fake…` at the bottom of
  that suite's file**, below the test methods — see `FakeObservationSource`
  in `DataObservationImplSpec.kt` and `FakeConnectivityChannel` in
  `ConnectionMonitorSpec.kt`, both in `client`. Give it mutable knobs the
  test sets before acting (`readFailure`, `onSubscribe`, call counters)
  rather than constructor-only configuration.
- **A stub used by several suites gets its own file** in the same package.
  No such stub exists yet — every stub in the repository is `private` to one
  suite — so there is no local example to copy. Move one out only when a
  second suite actually needs it, and keep it `internal`.
- **A helper that sets up the test *environment*** — installing JVM-wide
  state the code under test reads — is a separate concern from a stub, and
  does get its own file. See `references/advanced-test-patterns.md`.
- Chords does **not** use Gradle's `java-test-fixtures` plugin. Do not
  introduce a `testFixtures` source set to share a stub; a plain class in
  `src/test/kotlin` is the local pattern.
- **Protobuf stubs follow the neighboring file.** Existing fixtures build
  messages with Java builder chains (`Timestamp.newBuilder().setSeconds(1)
  .build()`). Never wrap a proto builder in `.apply { }`.
- Avoid fixtures that depend on a real Spine server, network resources, or
  local absolute paths.

## Pick a base or helper

`spine-testlib` and Guava's test library are added to every module by
`addDependencies()` in `buildSrc/src/main/kotlin/jvm-module.gradle.kts`
(`Spine.testlib` and `Guava.testLib`), so these are available without a
build change. All but `EqualsTester` live in `io.spine.testing`:

**Base classes.** A suite extends at most one, and only when the target's
shape matches:

| Target shape | Extend |
|---|---|
| Utility class (final, private ctor, statics) | `UtilityClassTest<T>` |
| A class's static/class-level concerns | `ClassTest<T>` |
| A singleton class | `SingletonTest<T>` |

**Standalone helpers.** Not base classes — call them from any suite,
whether or not it extends a base:

| Need | Use |
|---|---|
| `equals()`/`hashCode()` contract | `EqualsTester` (Guava) |
| Random/sample test values | `TestValues` |

`io.spine.testing.Assertions` (`assertNpe`, `assertIllegalArgument`, …) is
deliberately **not** listed. It implements those checks with JUnit's
`assertThrows` and Truth, which is the assertion style rule 7 rules out —
and Kotest already covers it: `shouldThrow<NullPointerException> { … }` is
the direct equivalent. `EqualsTester` stays because it is a contract tester
with no Kotest counterpart, not an assertion style.

These bases contribute *different* tests — `ClassTest` alone does far less
than its name suggests, and picking it for a utility class silently drops
two checks. Before extending one, read "What each `testlib` base
contributes" in `references/advanced-test-patterns.md`.

**A base is the default only when a base-class row fits.** A Kotlin
`object` takes no `testlib` base — no row covers it, and a bare
`internal class …Spec` is correct. That is the common case in Chords, which
is why no suite currently extends one; it is not a reason to skip a base
when the shape does match.

## Repo notes

- **Bump the version even for a tests-only change.** Every PR must
  increment `chordsVersion` in `version.gradle.kts`, and `pom.xml` plus
  `dependencies.md` must be regenerated with it. See "Versioning and
  Reports" in `AGENTS.md`.
- **KDoc every declaration**, including private ones and every test method,
  per the repository convention in `AGENTS.md`. See "Structure and
  formatting" for what the KDoc should add beyond the method name.
- **Keep lines within 100 characters** (Detekt `MaxLineLength`), which
  applies to test sources too.
- **Rendering and interaction are not covered by automated tests** in this
  repository. Test the logic behind a component, and report the
  manual-verification remainder explicitly.

## Report

Return: **Files** (test files added or edited), **Naming** (the suite name
chosen), **Helpers** (base classes, fixtures, or stubs used or added), and
**Verification** — the Gradle test task run and its result, or, when it was
not run, which task would have covered the change and why it was skipped.
