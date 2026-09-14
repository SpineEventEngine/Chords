---
name: component-engineer
description: >
  Chords UI component implementation policy. Use for the class-based component
  model, application shell, input components, layouts, message forms,
  validation display, dropdown/table/entity components, and server-connected
  components across the core, proto, and client modules.
---

# Component Engineering

## When to Use

Use this skill for UI component and component-infrastructure work:

- The `Component`/`InputComponent` class hierarchy, component lifecycle, and
  `Props`-style configuration in `core`.
- The application shell (`appshell`), views, and navigation support in `core`.
- Basic components: dropdowns, selectors, layouts, dialogs, tables, and
  wizards in `core`.
- Protobuf-aware components in `proto`: `MessageForm`, field editors, oneof
  support, and validation message display.
- Server-connected components in `client`: command posting, entity
  subscriptions, and entity-backed components such as `EntityChooser`.

For published model Protobuf declarations and Kotlin model extensions under
`proto-values`, prefer `.agents/skills/model-engineer/SKILL.md`. For generated
`MessageField`/`MessageOneof`/`MessageDef` contracts, prefer
`.agents/skills/codegen-engineer/SKILL.md`. For Gradle logic, see the
`.agents/skills/build-engineer/SKILL.md` guidance.

## Policy

- Follow the class-based component pattern: rendering in `content()`,
  pre-composition state updates in `beforeComposeContent()`, configuration via
  companion-object `invoke` operators with `Props`-style lambdas.
- Name composable functions and composable-emitting methods in `PascalCase`.
- Hold state that must trigger recomposition in `mutableStateOf`-backed
  properties (`by mutableStateOf(...)` with `getValue`/`setValue` imports).
- Respect module layering: `core` must not depend on `proto` or `client`;
  `proto` must not depend on `client`. Put behavior in the lowest module that
  owns it.
- Avoid breaking public API: signatures, property names, and visibility of
  published declarations are external contracts. Prefer additive changes;
  `protected` members are API for component subclasses.
- Target Compose Multiplatform 1.5.12; do not use newer Compose APIs. Some
  Compose APIs in use are experimental
  (`@OptIn(ExperimentalComposeUiApi::class)`); keep such opt-ins localized
  and documented.
- For the Kotlin language itself — the root compiler/library split, explicit
  API mode, null-safety, `lateinit` in `Props`, and coroutine scoping — use the
  `.agents/skills/kotlin-engineer/SKILL.md` rules.
- New components and new public component overloads must include useful KDoc and
  usage examples as part of the implementation. Follow
  [Component API Documentation](../docs-writer/SKILL.md#component-api-documentation)
  for content, scope, and verification.
- When changing a public component, check the KDoc examples of the changed
  component and affected callers. KDoc examples are not compiled by ordinary
  builds and can go stale silently.
- Keep the copyright header year current in modified files.

## Hotspots

- Component lifecycle: trace `Component`, `InputComponent`, and the concrete
  component's `beforeComposeContent`/`content` overrides together when
  changing state or recomposition behavior.
- Message forms: trace `MessageForm`, field/oneof registration, validation
  state, and codegen metadata (`MessageField`, `MessageOneof`) as one flow.
- Entity components: trace `app.client` read/observe calls, entity-to-ID
  mapping, and selection state, including `EntityChooser` and `DropdownSelector`.

## Server Connections

- Keep transport adapters focused on the external capability and its result.
  Application-specific decisions belong in consumer callbacks or application
  code. Expose metadata only when a current caller needs its defined meaning.
- UI validation and action eligibility guide the user; the server remains
  authoritative for business invariants and authorization. Do not embed a
  consuming application's business rules in a reusable component.
- Keep business rejections, server failures, and transport failures distinct.
  A failed read or malformed response must not become an ordinary empty result
  or a business rejection. Initial and retained observation values remain valid
  only with the status that describes their freshness or failure.
- Trace subscription, callback, and job cleanup on close, disposal, cancellation,
  and reuse. Keep lifecycle changes consistent with the public contract.

## Verification

Apply `.agents/guidelines/root-build.md`, then run the narrowest relevant
command first, from the repository root:

```bash
.agents/workflows/gradle-root.sh :<module>:test
.agents/workflows/gradle-root.sh :<module>:check
.agents/workflows/gradle-root.sh clean build
```

Follow [Testing](../tester/SKILL.md) for off-screen layout and interaction
coverage. Compilation alone does not verify rendering or interaction. Report
native window or visual checks that remain, with the action and expected result.
