# Skills

Each skill's frontmatter is the routing source of truth. Choose the narrowest
match and combine overlapping skills. Use `engineer` only for mixed or unclear
implementation ownership. Every `.kt` or `.kts` implementation, refactor, or
review also uses `kotlin-engineer` with the area skill.

## Implementation and Language

- `engineer`: route mixed or unclear implementation and apply design restraint.
- `kotlin-engineer`: govern every `.kt` / `.kts` change: both Kotlin
  toolchains, explicit API, null-safety, `Props`, coroutines, and public types.
- `component-engineer`: implement class-based Compose UI components across
  `core`, `proto`, and `client`, including the shell, forms, and server-backed UI.
- `model-engineer`: change published model Protobuf declarations and Kotlin
  extensions in `proto-values`; `.proto` edits require `AGENTS.md` authorization.
- `codegen-engineer`: implement ProtoData codegen plugins, the codegen runtime,
  their generated contracts, internal schemas, and correctness tests.
- `build-engineer`: change root and `codegen/plugins` Gradle builds, `buildSrc`
  dependencies, publishing, versions, and generated reports.

## Testing and Review

- `tester`: choose coverage and verification across modules.
- `kotlin-jvm-tester`: define JVM suite naming, structure, assertions, and helpers.
- `code-reviewer`: review scoped component, model, codegen, or build diffs for
  correctness, regressions, contracts, and tests; read-only by default.
- `security-reviewer`: review publishing credentials, GitHub Actions secrets,
  provenance, wrapper integrity, agent config, and secret exposure.

## Operations and Coordination

- `ci-engineer`: author or review `.github/workflows/**`; never trigger workflows.
- `pair-workflow`: govern the shared-document workflow through its
  `.agents/workflows/pair.sh` driver.

## Documentation

- `docs-writer`: write or restructure project documentation and change prose.
- `docs-reviewer`: review those documentation forms and change descriptions for
  accuracy and minimum complete prose; read-only by default.
- `proofread`: apply the English catalog to project-owned comments and Markdown
  by branch diff, repository-wide `all`, or path, preserving code and meaning.

## Layout

```text
.agents/skills/<name>/
  SKILL.md            # frontmatter and core policy
  agents/openai.yaml  # UI metadata
  references/*.md     # optional, conditionally loaded detail
```

The frontmatter `name` must match the directory. Keep always-needed policy in
`SKILL.md`; put specialized detail in `references/` and non-policy workflow
material beside it, as `pair-workflow/template.md` does. `openai.yaml` carries
only UI metadata — `display_name`, `short_description`, and `default_prompt` —
never durable policy. Invoke a skill as `$<name>` where supported.

## Shared Guidelines

- [`design-restraint.md`](../guidelines/design-restraint.md): minimum
  sufficient design for every implementation skill.
- [`english-style.md`](../guidelines/english-style.md): the English correction
  catalog for proofreading and documentation.
- [`project-owned-files.md`](../guidelines/project-owned-files.md):
  repository-owned prose and excluded paths.
- [`root-build.md`](../guidelines/root-build.md): root Gradle JDK, platform,
  daemon, and invocation policy.
