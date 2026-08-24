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
- Match existing KDoc style: `@param` tags for type parameters and
  constructor-like parameters, backticked identifiers, and wrapped lines
  within 100 characters.
- When changing a public component, check the KDoc examples of the changed
  class and its neighbors: examples are not compiled or covered by tests and
  go stale silently.
- Keep the copyright header year current in modified files.

## Hotspots

- Component lifecycle: trace `Component`, `InputComponent`, and the concrete
  component's `beforeComposeContent`/`content` overrides together when
  changing state or recomposition behavior.
- Message forms: trace `MessageForm`, field/oneof registration, validation
  state, and codegen metadata (`MessageField`, `MessageOneof`) as one flow.
- Entity components: trace `app.client` read/observe calls, entity-to-ID
  mapping, and selection state, including `EntityChooser` and `DropdownSelector`.

## Verification

Apply `.agents/guidelines/root-build.md`, then run the narrowest relevant
command first, from the repository root:

```bash
.agents/workflows/gradle-root.sh :<module>:test
.agents/workflows/gradle-root.sh :<module>:check
.agents/workflows/gradle-root.sh clean build
```

UI rendering and interaction cannot be covered by automated tests here. For
visual or interactive changes, verify compilation and existing tests, then
state clearly in the final response what manual verification remains.
