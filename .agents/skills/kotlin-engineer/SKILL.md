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
  KDoc style, and the Compose 1.5.12 ceiling.
- `.agents/skills/codegen-engineer/SKILL.md` — generated contracts and
  Protobuf declarations.
- `.agents/skills/build-engineer/SKILL.md` — Gradle Kotlin DSL, `buildSrc`
  coordinates, and publishing.
- `.agents/skills/kotlin-jvm-tester/SKILL.md` — how a test suite is
  written. This skill is the baseline for the Kotlin *inside* a test body.
- `.agents/skills/engineer/SKILL.md`, "Design Restraint" — *whether* to
  introduce a sealed hierarchy, base class, or type parameter at all. This
  skill covers how to write one once that decision is made.
- `.agents/skills/code-reviewer/SKILL.md` — review output format and
  verdict. Report Kotlin findings through it; do not invent a second format.
- `AGENTS.md` — verification commands, versioning, and safety policy.

## Toolchain Ceiling

**Two ceilings, and which one applies depends on the file you are editing.**
The root build compiles with the Kotlin Gradle plugin dependency declared as
`kotlinVersion` in `buildSrc/build.gradle.kts` — currently **1.8.22** — on JVM
target 11. The `kotlin("jvm")` declaration earlier in that file is the plugin
used to compile `buildSrc` itself, not the version applied to root modules. The
root toolchain covers every module in
`settings.gradle.kts`: `core`, `proto`, `proto-values`, `client`, `runtime`
(at `codegen/runtime`), and `codegen-tests` (at `codegen/tests`) — note that
both `codegen/` subprojects belong to the *root* build. Only
`codegen/plugins` is separate, using **Kotlin 2.3.20** on JDK 17 (its own
`kotlinVersion` in `codegen/plugins/buildSrc/build.gradle.kts`).

Everything below about the Kotlin 1.8 language ceiling applies to the root
build; in `codegen/plugins` the 2.x language is available. Never carry a
construct from one across to the other because it compiled where you first
wrote it. Both builds enable explicit API mode.

Within the root modules:

- **The stdlib on the classpath is newer than the compiler.**
  `forceProductionDependencies()` in
  `buildSrc/src/main/kotlin/DependencyResolution.kt` pins `kotlin-stdlib` to
  the `Kotlin.version` coordinate — currently 1.9.23 — while the compiler
  stays at 1.8.22. A 1.9 stdlib *function* can therefore resolve and compile,
  even though a 1.9 *language feature* cannot. **"It compiles" is not
  evidence that a construct is within the baseline** — check when the API
  was introduced, and prefer one that predates Kotlin 1.8.20.
- Not available under the root's 1.8 language ceiling: `data object`,
  `enumEntries`, the stable `..<`
  operator (use `until`), and stable context receivers.
- Available and preferred where they fit: sealed interfaces, `@JvmInline
  value class`, `buildList` / `buildMap`, and `kotlin.time.Duration`.
- **Context receivers, not context parameters.** The root build passes
  `-Xcontext-receivers` from `KotlinConfig.setFreeCompilerArgs()`. That enables
  the experimental context-receiver syntax under Kotlin 1.8; it does not
  enable the later context-parameter syntax. `codegen/plugins` enables
  neither feature.
- Coroutines are **1.7.3** (`KotlinX.Coroutines.version`), forced across
  every configuration by the `resolutionStrategy` block in the root
  `build.gradle.kts`. 1.7 APIs are available. Ignore the unused
  `Coroutines` object in the same dependency package — nothing imports it,
  and its version is not what resolves.
- **`failOnVersionConflict()` is enabled.** Adding a dependency that brings a
  different version of an already-forced library fails resolution rather than
  silently choosing one. Adjust the coordinate in
  `buildSrc/src/main/kotlin/io/spine/internal/dependency/` and the force list
  when the conflict is real; do not work around it in a module build file.
- **Explicit API mode is on in both builds** — each calls `explicitApi()`
  in its Kotlin block. The compiler enforces exactly two
  things: an explicit visibility modifier and an explicit return type on
  every public declaration. It does **not** check documentation — KDoc on
  every declaration is a separate repository rule from `AGENTS.md`,
  "Development Conventions". Satisfy both, but do not expect the compiler to
  catch the second.

## Must Do

- **Null-safety through `?.`, `?:`, `let`, and `requireNotNull`.** Reserve
  `!!` for a genuine contract violation, and put the reason on the same
  line. `requireNotNull(x) { "why" }` or `checkNotNull` is almost always the
  better expression of the same intent, because it fails with a message.
- **`lateinit var` is sanctioned for `Props`-style configuration** — the
  established idiom for component properties supplied after construction
  (`public lateinit var onSelectItem: (I?) -> Unit` in `DropdownListBox`).
  Outside that pattern, prefer a constructor parameter or a nullable
  property. Never `lateinit` a primitive or nullable type — that does not
  compile; use `Delegates.notNull()` for a primitive.
- **Structured concurrency by default.** Take a `CoroutineScope` from the
  caller or use `coroutineScope { }`.
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
  published contract.
- **Immutability first**: `val` over `var`, and `copy()` on a data class
  rather than mutation.
- **Named arguments once a Kotlin call takes three or more parameters**,
  which is what stops a silent argument swap between same-typed parameters.
  This applies only where parameter names are available to the caller —
  Kotlin does not permit named arguments for Java methods and constructors,
  which includes the generated Protobuf builders used throughout `proto` and
  `proto-values`. There, keep the call readable by other means: one setter
  per line in a builder chain, or a local variable per value.
- **`data class` for pure value types only** — not for components,
  services, or anything with a lifecycle.
- **Deprecated API only on explicit instruction.** When directed to use one,
  confine the call to the narrowest scope and suppress right there —
  `@Suppress("DEPRECATION")` with a comment naming both the instruction and
  the replacement to migrate to.

## Must Not Do

- **No `catch (e: Throwable)`** — it captures `OutOfMemoryError`,
  `StackOverflowError`, and cancellation. Catch `Exception` and rethrow
  cancellation.
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
- **No language feature newer than Kotlin 1.8**, and no stdlib API added after
  1.8.20 without a deliberate decision — see "Toolchain Ceiling".
- **No new deprecated-API call** without that explicit instruction; use the
  replacement named in the `@Deprecated` or `ReplaceWith` message.
- **No blanket Detekt suppression.** Suppress the narrowest rule at the
  narrowest declaration, matching existing style, and only when that rule is
  genuinely wrong about the site.

## Verification

Compile the narrowest module first; the full command set and the JDK
constraints live in `AGENTS.md`, "Verification and Quality".

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
