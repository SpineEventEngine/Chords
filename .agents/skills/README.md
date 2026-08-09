# Skills

This index is a quick orientation aid. Each skill's frontmatter remains the
routing source of truth.

- `engineer`: routing skill for mixed or unclear implementation work, and
  the home of the shared design-restraint policy.
- `kotlin-engineer`: the Kotlin language itself — the root/compiler/library
  version split, the separate codegen-plugin toolchain, null-safety,
  `lateinit`, coroutine scoping, and public-type rules. Pairs with whichever
  area skill owns the code being changed.
- `component-engineer`: class-based Compose UI components across `core`,
  `proto`, and `client` — the component model, input components, message
  forms, and server-connected components.
- `model-engineer`: published model Protobuf declarations and Kotlin model
  extensions in `proto-values`, including schema evolution and package and
  file organization.
- `codegen-engineer`: ProtoData codegen plugins, the codegen runtime,
  generated `MessageField`/`MessageOneof`/`MessageDef` contracts,
  plugin-internal Protobuf declarations, and codegen correctness tests.
- `build-engineer`: root and `codegen/plugins` Gradle builds, `buildSrc`
  dependency coordinates, publishing wiring, version policy, and generated
  report regeneration.
- `security-reviewer`: publishing credentials, GitHub Actions secrets,
  dependency provenance, agent prompt/configuration safety, and accidental
  secret exposure review.
- `ci-engineer`: GitHub Actions workflow authoring and review for build,
  guard, validation, and publishing pipelines.
- `code-reviewer`: implementation review for component, model, codegen, and
  build changes.
- `tester`: what to cover and how to verify it across all modules,
  including codegen correctness tests.
- `kotlin-jvm-tester`: how a test suite is written — JUnit Jupiter
  structure, Kotest assertions, naming, fixtures, and `testlib` bases.
- `docs-writer`: documentation authoring, editing, restructuring, and claim
  checks.
- `docs-reviewer`: documentation review for prose, examples, and comments.
- `proofread`: minimal English grammar, punctuation, and spelling corrections
  in project-owned comments and documentation.
- `pair-workflow`: two-agent plan-review-implement-review protocol for an
  issue or bug fix, driven by `.agents/workflows/pair.sh`.

## Skill Directory Layout

Each skill lives in its own directory:

```
.agents/skills/<skill-name>/
  SKILL.md              — frontmatter (name, description) + policy body
  agents/openai.yaml    — UI metadata for the OpenAI-based agent interface
```

`openai.yaml` fields:
- `display_name`: label shown in the UI
- `short_description`: hint shown in the UI
- `default_prompt`: starter prompt shown by the interface when the skill is invoked

The `name` in `SKILL.md` frontmatter must match the directory name.

A skill may add supporting files its own policy body references — for example,
`pair-workflow/template.md`, the working-document template its driver copies,
or `kotlin-jvm-tester/references/advanced-test-patterns.md`, loaded only when
a task reaches the shapes it covers. Core policy still belongs in `SKILL.md`;
a `references/` file carries detail most tasks do not need.

## Invocation

Skills are invoked via `$<skill-name>` in supported agent interfaces (for
example, `$component-engineer`). Supported runtimes read the corresponding
`SKILL.md` as the skill's durable instructions; `openai.yaml` carries only UI
metadata, not policy.
