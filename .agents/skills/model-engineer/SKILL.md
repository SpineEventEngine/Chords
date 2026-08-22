---
name: model-engineer
description: >
  Chords Protobuf model and schema policy. Use when adding, changing,
  reviewing, or removing project-owned `.proto` declarations and Kotlin model
  extensions located in `proto-values`, organizing Protobuf packages, imports,
  and files, or evaluating schema-driven generated-accessor effects. Do not
  use for ProtoData plugin, generator, or codegen-runtime implementation; use
  `codegen-engineer` for those concerns.
---

# Model Engineering

## Scope

- Own published model Protobuf declarations under `proto-values/src/main/proto/**`.
- Own Kotlin extensions for project-owned and external Protobuf types under
  `proto-values/src/main/kotlin/**`.
- Keep schema declarations, Kotlin extensions, and schema-driven generated
  accessors consistent.
- Use `.agents/skills/codegen-engineer/SKILL.md` for ProtoData plugins,
  including plugin-internal Protobuf declarations under
  `codegen/plugins/codegen-plugins/src/main/proto/**`, and for generator
  behavior, codegen-runtime contracts, and test Protobuf files that exist only
  as generator fixtures under `codegen/tests/src/test/proto/**`.
- Use `.agents/skills/component-engineer/SKILL.md` for UI components that
  consume model types, and `.agents/skills/build-engineer/SKILL.md` for Gradle wiring.
- Apply `.agents/skills/kotlin-engineer/SKILL.md` to every Kotlin extension.

## Policy

- Apply the `AGENTS.md` Protobuf gate first: state the exact schema change and
  obtain confirmation before editing a published `.proto` declaration under
  `proto-values/`. Inspection and proposals need none.
- Treat `AGENTS.md` as authoritative. Prefer additive changes for published
  APIs. Rely on Chords' experimental status only under the breaking-change
  authorization below, and report the compatibility impact.
- Add model declarations and make compatible edits as needed. Make an
  incompatible change to an existing message, field, field type, cardinality,
  `oneof` membership, or option only when the current task explicitly requires
  the break or the user confirms it through the active workflow's prescribed
  user-input channel. Do not preserve an obsolete declaration solely for
  source compatibility when an intentional breaking change is in scope.
- Treat source compatibility and Protobuf wire compatibility separately.
  Recompiling in-repository consumers and passing tests can prove source usage
  was updated, but cannot prove that previously serialized or transmitted data
  still decodes correctly.
- Evaluate every change to an existing field's type, singular/repeated
  cardinality, or `oneof` membership, even when its tag stays unchanged.
  Compare Protobuf wire types and value semantics; a shared tag or wire type
  alone does not prove compatibility.
- Evaluate message renames and Protobuf package changes, including a package
  change required when relocating a file. With `(type_url_prefix)` set, either
  change alters the fully qualified message name and type URL, which can break
  existing `Any` values and Spine type registry resolution.
- Never reuse a retired field number or name. When deleting a field, reserve
  its old number and name in the owning message. When renumbering, reserve the
  old number and reserve the old name if the change also retires it.
  Renumbering remains wire-incompatible: old data keeps the old tag and does
  not populate the field at its new number. Apply the breaking-change
  authorization above and report any data or migration consequences.
- Remember that `proto-values` is published and consumed externally even
  during the experimental phase. Update all in-repository consumers and tests
  for an incompatible source change, and separately evaluate existing wire
  data and external consumers.
- Keep published model schemas under
  `proto-values/src/main/proto/spine/chords/proto/value/**`. Make each Protobuf
  `package` match its directory relative to `proto-values/src/main/proto`, and
  keep project-owned import paths aligned with the same structure. Never infer
  the Protobuf package from `java_package`, which controls only generated JVM
  classes. Before moving an existing file or changing its package, apply the
  type-URL compatibility check and breaking-change authorization above.
- Update neighboring Kotlin extensions when a declaration, field, or generated
  accessor they expose changes.
- Do not manually edit generated Protobuf or Chords outputs. Change the source
  model, or use `codegen-engineer` when the generator itself must change.

## Verification

Apply `.agents/guidelines/root-build.md`, then run the narrowest relevant root
command first:

```bash
.agents/workflows/gradle-root.sh :proto-values:test
.agents/workflows/gradle-root.sh :proto-values:check
.agents/workflows/gradle-root.sh clean build
```

Use `:codegen-tests:test` with `codegen-engineer` when a model change exposes
or depends on generator behavior rather than only changing published schema.
