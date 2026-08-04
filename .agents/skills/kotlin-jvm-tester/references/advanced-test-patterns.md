# Advanced test patterns

Supporting reference for `.agents/skills/kotlin-jvm-tester/SKILL.md`. Read
the section that matches the shape in front of you; none of this is needed
for an ordinary suite of flat `@Test` methods.

## Nested case groups

Keep `@Nested` (with any visibility and `inner class`) on **one line** and
put the name on the **next** line, for backticked and plain names alike. The
backtick rule is the same as for a test method: backtick a multi-word
sentence or a Kotlin hard keyword, and write a single legal identifier
plainly. A `@Nested` class is a declaration, so it carries KDoc like any
other.

```kotlin
// Correct — multi-word name, so backticked and wrapped to the next line
/**
 * Groups the cases covering the extension-based factories.
 */
@Nested inner class
`create instances by extension which` {
    // ...
}

// Correct — single valid identifier, no backticks, still on its own line
/**
 * Groups the constructor-argument validation cases.
 */
@Nested inner class
Construction {
    // ...
}
```

```kotlin
// Avoid — name on the same line as the declaration
@Nested
internal inner class `check that a value is positive` {
}
```

No suite uses `@Nested` today; the rule applies to the first one that does.

## Parameterized tests

Use `@ParameterizedTest` with `@MethodSource`. Chords supplies the data from
a **private instance method** on a suite annotated
`@TestInstance(TestInstance.Lifecycle.PER_CLASS)`, rather than from a
`companion object` with `@JvmStatic` — `CodegenPluginsSpec` in
`codegen/tests` is the reference. Follow the neighboring file.

Prefer a parameterized test over a loop when the cases are a fixed,
enumerable set: a failure then names the case instead of failing the whole
method. When a loop is the better fit, wrap each iteration in `withClue` so
the failure still identifies the offending input.

## Test-environment helpers

A helper that sets up the test *environment* is a separate concern from a
stub of a collaborator, and it gets its own file.

`TestApplication.kt` in `client/src/test` is the example. It installs the
JVM-wide `app` property with a minimal **real** `Application` — not a mock —
because types under test read application-wide defaults on construction, and
reading an unassigned `app` throws.

The part worth copying is its `install()`: idempotent and synchronized,
because all test classes share one JVM and their execution order is not
fixed. Every suite that needs the environment calls `install()` itself
rather than assuming another suite already ran. Follow that shape for any
future environment helper.

## What each `testlib` base contributes

The three bases contribute *different* tests. Know which ones you inherit,
so you neither duplicate them nor assume coverage you did not get.

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

So `ClassTest` is the narrow choice: pick it for a class whose static methods
you want null-checked, and add the final/private-constructor assertions
yourself if they matter. Where the target is a genuine utility class,
`UtilityClassTest` is the right base — choosing `ClassTest` there silently
drops two checks.
