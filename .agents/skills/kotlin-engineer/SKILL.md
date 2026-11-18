---
name: kotlin-engineer
description: >
  Chords Kotlin implementation policy and the pitfalls that recur in review:
  the root compiler/library version split and separate codegen-plugin
  toolchain, null-safety and `!!`, `lateinit` in `Props`, coroutine scoping
  and cancellation in `client`, and read-only public types under explicit API
  mode. Use whenever writing, changing,
  refactoring, or reviewing Kotlin in any module: `.kt`/`.kts` edits,
  turning Java-style Kotlin idiomatic, anything touching coroutines,
  cancellation, or `Flow` (concentrated in `client`), and designing a public
  Kotlin signature. Area skills own their own domains — this one covers the
  language itself.
---

# Kotlin Engineering

Baseline Kotlin knowledge is assumed — data/sealed classes, scope functions,
null-safety operators, extension functions, `suspend`, `Flow`, `when`
exhaustiveness. This skill does not teach the language. It encodes what is
specific to Chords and the traps that keep surfacing in review.

## Defer, Do Not Restate

Each of these owns its area; this skill stays out of them:

- `.agents/skills/component-engineer/SKILL.md` — the component model,
  `mutableStateOf`-backed state, `PascalCase` composables, module layering,
  KDoc style, and the Compose toolchain.
- `.agents/skills/model-engineer/SKILL.md` — published model Protobuf
  declarations and Kotlin model extensions under `proto-values`.
- `.agents/skills/codegen-engineer/SKILL.md` — Spine Compiler plugins and generated
  runtime contracts.
- `.agents/skills/build-engineer/SKILL.md` — Gradle Kotlin DSL, `buildSrc`
  coordinates, and publishing.
- `.agents/skills/kotlin-jvm-tester/SKILL.md` — how a test suite is
  written. This skill is the baseline for the Kotlin *inside* a test body.
- `.agents/guidelines/design-restraint.md` — *whether* to introduce a sealed
  hierarchy, base class, or type parameter at all. This skill covers how to
  write one once that decision is made.
- `.agents/skills/code-reviewer/SKILL.md` — review output format and
  verdict. Report Kotlin findings through it; do not invent a second format.
- `.agents/skills/tester/SKILL.md` — coverage and verification commands.
- `AGENTS.md` — global quality, authorization, and safety policy.

## Toolchain

All modules and the separate `codegen/plugins` build use Kotlin 2.4.21 with JVM
target 25. The compiler and standard library versions match. Use named context parameters rather than the removed context-receiver syntax.

Coroutines are 1.11.0. `KotlinX.Coroutines` owns the coordinates in each build.
`failOnVersionConflict()` requires explicit alignment of direct and transitive
versions in the owning `DependencyResolution.kt`.

Both builds enable explicit API mode: public declarations need explicit visibility
and return types. Documentation is a separate requirement from `AGENTS.md`.

## Must Do

- **Match source filenames to their primary declaration.** Name each
  project-owned `.kt` file after its primary top-level class, object, interface,
  or function, including spelling and case. Rename the file when the primary
  declaration is renamed. Preserve established conventions such as
  `SomeClassExts.kt`, `SomeClassSpec.kt`, and `SomeClassSpecEnv.kt`; test and
  fixture names follow [kotlin-jvm-tester](../kotlin-jvm-tester/SKILL.md).
  If no single declaration is primary, name the file for the related
  declarations' shared purpose.
- **Null-safety through `?.`, `?:`, `let`, and `requireNotNull`.** Reserve
  `!!` for a genuine contract violation, and put the reason on the same
  line. `requireNotNull(x) { "why" }` or `checkNotNull` is almost always the
  better expression of the same intent, because it fails with a message.
- **Express required values with non-null types.** Enforce the invariant once
  at the boundary instead of passing nullable values that every caller must
  reject later.
- **`lateinit var` is sanctioned for `Props`-style configuration** — the
  established idiom for component properties supplied after construction
  (`public lateinit var onSelectItem: (I?) -> Unit` in `DropdownListBox`).
  Outside that pattern, prefer a constructor parameter or a nullable
  property. Never `lateinit` a primitive or nullable type — that does not
  compile; use `Delegates.notNull()` for a primitive.
- **Structured concurrency by default.** Take a `CoroutineScope` from the
  caller or use `coroutineScope { }`. For work tied to a composition, use
  `rememberCoroutineScope` or `LaunchedEffect` so disposal cancels the work.
- **The `GlobalScope` exception, in the shape `client` already uses it.**
  Work that must outlive the composition or call that started it launches on
  `GlobalScope` only with `@OptIn(DelicateCoroutinesApi::class)` and a
  comment *inside* that annotation covering the decision. The existing sites
  state it tersely — "We don't need the posting job to be canceled
  automatically" — which is the established shape; in new code, also give
  the reason, so a later reader can tell a deliberate choice from an
  oversight. Two sanctioned variants, and which one you need follows from
  whether anything must be able to stop the work:
  - **Retained job**, when it must be cancellable — assign the `Job` to a
    property and cancel it explicitly. `DesktopClient.withTimeout` keeps
    `timeoutJob` and cancels it in `cancelTimeout()`.
  - **Fire-and-forget**, when the work must run to completion regardless of
    the caller's lifecycle — do not retain the `Job`.
    `CommandConsequences.postAndProcessConsequences` posts a command this
    way deliberately, since abandoning it half-way would leave the command
    posted but its consequences unobserved.

  What is not acceptable is a `GlobalScope.launch` with neither the opt-in
  nor a stated reason. Discarding the `Job` is a decision to be justified in
  that comment, not a defect on its own.
- **Rethrow `CancellationException`.** A `catch (e: Exception)` that
  swallows it silently disables cancellation. Cancellation is the caller's
  decision, not a failure to be converted into an error state.
- **Confine `runBlocking` to a bridge** from a non-suspend API into suspend
  code. Inside a `suspend` function it is always a bug.
- **Expose read-only types from public API** — `List` over `MutableList`,
  `StateFlow` over `MutableStateFlow`, `State` over `MutableState`, and never
  the mutable backing property itself. Explicit API mode makes each of these a
  published contract. An existing component contract that deliberately accepts
  caller-owned `MutableState` for two-way value binding is different: preserve
  that contract and document who updates the state.
- **Immutability first**: `val` over `var`, and `copy()` on a data class
  rather than mutation.
- **Use `T` for an undifferentiated type parameter.** Use `K`, `V`, `R`, `E`,
  or a descriptive name when the parameter's role needs distinction.
- **Named arguments once a Kotlin call takes three or more parameters**,
  which is what stops a silent argument swap between same-typed parameters.
  This applies only where parameter names are available to the caller —
  Kotlin does not permit named arguments for Java methods and constructors,
  which includes the generated Protobuf builders used throughout `proto` and
  `proto-values`. There, keep the call readable by other means: one setter
  per line in a builder chain, or a local variable per value.
- **Put each subsequent call in a chain on a new indented line**, starting
  with `.` or `?.`, even when the chain fits on one line. Keep a property
  receiver together and put its following call on the next line. Apply this
  inside arguments and after scope-function lambdas too, including terminal
  builder calls such as `build()`.
- **Kotlin property syntax for Java getters** — `isDeleted`, not `isDeleted()`;
  `message.value`, not `message.getValue()`. Keep calls for operations or
  methods that expose no Kotlin property.
- **One declaration per import.** Never add wildcards or an alias without
  explicit human direction. Import clear members directly, including recognizable
  enum constants and nested members. Apply this to all source sets; Java uses
  `import static`. Qualify collisions and generic names such as `of`, `from`,
  `get`, `create`, `valueOf`, `newBuilder`, `newUuid`, `generate`, `pack`, or `unpack`.
- **`data class` for pure value types only** — not for components,
  services, or anything with a lifecycle.
- **Deprecated API only on explicit instruction.** When directed to use one,
  confine the call to the narrowest scope and suppress right there —
  `@Suppress("DEPRECATION")` with a comment naming both the instruction and
  the replacement to migrate to.

## Must Not Do

- **No `catch (e: Throwable)`** — it captures `OutOfMemoryError`,
  `StackOverflowError`, and cancellation. Catch `Exception` and rethrow cancellation.
- **No `.first()` on a flow with no guaranteed emission, without a
  timeout.** The risk is the absence of a value to take, not heat:
  `StateFlow.first()` returns the current value immediately and is safe,
  and so is a `SharedFlow` with a non-zero `replay` that has already
  emitted. A `replay = 0` `SharedFlow`, a channel-backed flow, or any
  source whose first emission depends on an external event will suspend
  indefinitely — bound those with `withTimeout`.
- **`single()` is a stronger claim than `first()`** — it waits for the flow
  to *complete* and fails if a second value arrives. On any flow that does
  not terminate on its own it hangs even when values are emitted. Use it
  only on a finite flow, and use `first()` when you mean "the next value".
- **No sequential `async { }.await()`** when the point was parallelism;
  start both, then await both.
- **No platform-type leak in public API.** A value crossing from Java
  arrives as `String!`; give the public declaration an explicit nullable or
  non-null type rather than letting the platform type propagate.
- **Keep language and standard library APIs within Kotlin 2.4.21.**
- **No new deprecated-API call** without that explicit instruction; use the
  replacement named in the `@Deprecated` or `ReplaceWith` message.
- **No blanket Detekt suppression.** Suppress the narrowest rule at the
  narrowest declaration, matching existing style, and only when that rule is
  genuinely wrong about the site.

## Verification

Compile the narrowest module first. The JDK, architecture, and invocation
constraints live in `.agents/guidelines/root-build.md`; the full command set is
in [Testing](../tester/SKILL.md#verification).
Configure IntelliJ IDEA Detekt with `quality/detekt-config.yml`.

```bash
.agents/workflows/gradle-root.sh :<module>:compileKotlin
.agents/workflows/gradle-root.sh :<module>:test
.agents/workflows/gradle-root.sh detekt
```

Detekt runs over these modules — do not introduce new violations, and keep
lines within 100 characters.

## Report

State what changed, and call out any non-obvious rule above that the change
depends on — a `!!` you kept, a `GlobalScope` opt-in you added, a stdlib API
whose version you checked. Findings from reviewing someone else's Kotlin go
through `code-reviewer`'s output format, not a second verdict here.
