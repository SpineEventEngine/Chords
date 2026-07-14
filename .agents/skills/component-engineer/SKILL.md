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

For generated `MessageField`/`MessageOneof`/`MessageDef` contracts or Protobuf
declarations, prefer `.agents/skills/codegen-engineer/SKILL.md`. For Gradle
build logic, use `.agents/skills/build-engineer/SKILL.md`.

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
- All libraries use Kotlin explicit API mode: public declarations need
  explicit `public` modifiers and public API needs KDoc.
- Avoid breaking public API: signatures, property names, and visibility of
  published declarations are contracts for external consumers. Prefer additive
  changes; when a member is `protected`, it is part of the API for component
  subclasses.
- Target the pinned toolchain: Kotlin 1.8.20 and Compose Multiplatform 1.5.12.
  Do not use newer language features or Compose APIs. Some Compose APIs in use
  are experimental (`@OptIn(ExperimentalComposeUiApi::class)`); keep such
  opt-ins localized and documented.
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
  state, and the codegen runtime metadata (`MessageField`, `MessageOneof`)
  together.
- Entity components: trace `app.client` read/observe calls, entity-to-ID
  mapping, and selection state together (e.g., `EntityChooser`,
  `DropdownSelector`).

## Verification

Run the narrowest relevant command first (repository root, JDK 11):

```bash
./gradlew :<module>:test
./gradlew :<module>:check
./gradlew clean build
```

UI rendering and interaction cannot be covered by automated tests here. For
visual or interactive changes, verify compilation and existing tests, then
state clearly in the final response what manual verification remains.
