# Advanced test patterns

Supporting reference for `.agents/skills/kotlin-jvm-tester/SKILL.md`. Read
the section that matches the shape in front of you; none of this is needed
for an ordinary suite of flat `@Test` methods.

## Nested case groups

Keep `@Nested inner class` on one line and put the name on the next. Backtick
multi-word names and Kotlin keywords, but not a legal single identifier. A
`@Nested` class carries KDoc like any other declaration.

The outer `@DisplayName` supplies the subject and the `should` lead-in. Make
the nested display name and its test names continue that sentence.

```kotlin
/**
 * Tests how a projection preserves its state.
 */
@DisplayName("`Projection` should")
internal class ProjectionSpec {

    /**
     * Groups state-integrity cases.
     */
    @DisplayName("preserve state")
    @Nested inner class
    StateIntegrity {

        /**
         * Covers repeated delivery of the same message.
         */
        @Test
        fun `after repeated delivery`() {
            // ...
        }
    }
}
```

A legacy suite may keep its full display-name stem until materially reworked.

## Parameterized tests

Use `@ParameterizedTest` with `@MethodSource`. The local pattern in
`CodegenPluginsSpec` is `@TestInstance(PER_CLASS)` with a private instance
factory returning `Stream<Arguments>`; do not replace it with `@JvmStatic`.

```kotlin
/**
 * Verifies classification for supplied values.
 */
@TestInstance(PER_CLASS)
internal class ClassificationSpec {

    /**
     * Classifies each supplied value.
     */
    @ParameterizedTest
    @MethodSource("classificationCases")
    fun `classify each supplied value`(value: String, expected: Boolean) {
        classify(value) shouldBe expected
    }

    /**
     * Returns values paired with their expected classification.
     */
    @Suppress("unused")
    private fun classificationCases(): Stream<Arguments> = Stream.of(
        of("accepted", true),
        of("rejected", false),
    )
}
```

Prefer this to a loop for fixed cases so failures name the case. Otherwise use
`withClue` around each iteration.

## Test-environment helpers

A JVM-wide environment installer is separate from a collaborator stub and
gets its own role-named file rather than a suite-specific `SpecEnv`.

`TestApplication.kt` in `core/src/test` and `client/src/test` provide the local
pattern: they install a minimal real `Application` because targets read the
JVM-wide `app` property and an unassigned value throws.

Make `install()` idempotent and synchronized. Every dependent suite calls it;
none assumes another suite already ran.

## What each `testlib` base contributes

Know each base's inherited tests to avoid duplication or missing coverage.

- **`ClassTest<T>`** adds exactly **one** test: `NullPointerTester` over the
  subject's static methods (public by default; pass a `Visibility` to the
  constructor to widen it, and override `configure(NullPointerTester)` to
  supply default instances). It does **not** check that the class is final
  or that its constructor is private — it only exposes `assertFinal()` and
  `assertHasPrivateParameterlessCtor()` as protected helpers for you to call
  from your own test.
- **`UtilityClassTest<T>`** extends `ClassTest<T>` and adds the two tests
  that invoke those helpers: the class is `final`, and it has a private
  parameterless constructor. Inheriting it covers all three concerns.
- **`SingletonTest<T>`** extends `ClassTest<T>` and adds that the accessor
  returns the same instance, plus a nested pair asserting that no non-private
  constructor exists and at least one private one does. Its constructor takes
  the accessor as a `Supplier<T>` alongside the subject class.

Use `ClassTest` for static null checks and `UtilityClassTest` for the complete
utility-class contract; choosing the former for a utility class drops two checks.
