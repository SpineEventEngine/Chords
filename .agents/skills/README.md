# Skills

This index is a quick orientation aid. Each skill's frontmatter remains the
routing source of truth.

- `engineer`: routing skill for mixed or unclear implementation work.
- `component-engineer`: class-based Compose UI components across `core`,
  `proto`, and `client` — the component model, input components, message
  forms, and server-connected components.
- `codegen-engineer`: ProtoData codegen plugins, the codegen runtime,
  generated `MessageField`/`MessageOneof`/`MessageDef` contracts, and
  Protobuf declarations in `proto-values`.
- `build-engineer`: root and `codegen/plugins` Gradle builds, `buildSrc`
  dependency coordinates, publishing wiring, version policy, and generated
  report regeneration.
- `security-reviewer`: publishing credentials, GitHub Actions secrets,
  dependency provenance, agent prompt/configuration safety, and accidental
  secret exposure review.
- `ci-engineer`: GitHub Actions workflow authoring and review for build,
  guard, validation, and publishing pipelines.
- `code-reviewer`: implementation review for component, codegen, and build
  changes.
- `tester`: test authoring and verification strategy for all modules,
  including codegen correctness tests.
- `docs-writer`: documentation authoring, editing, restructuring, and claim
  checks.
- `docs-reviewer`: documentation review for prose, examples, and comments.

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

## Invocation

Skills are invoked via `$<skill-name>` in supported agent interfaces (for
example, `$component-engineer`). Supported runtimes read the corresponding
`SKILL.md` as the skill's durable instructions; `openai.yaml` carries only UI
metadata, not policy.
