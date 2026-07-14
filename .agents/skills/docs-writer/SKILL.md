---
name: docs-writer
description: >
  Writes, edits, and restructures Chords documentation. Use when asked to
  create or update README.md files, .agents/project.md, AGENTS.md, skills,
  KDoc comments, or inline explanatory comments. Verifies claims against
  current code, tests, workflows, and build files.
---

# Documentation Writing

## Decide the Target and Audience

- Identify the target reader: library consumer, component subclass author,
  contributor, maintainer, or agent.
- Identify the task type: new doc, update, restructure, or documentation audit.
- Identify the acceptance criteria: what is correct when the reader is done?
- Build a short plan before editing. Ask all clarification questions needed to
  close uncovered spots in the plan when the audience, scope, ownership, or
  expected output file is unclear.

## Choose Where The Content Should Live

Prefer updating an existing document over creating a new one. Use
`.agents/project.md` as the source of truth for documentation ownership and
the project map. API-level documentation belongs in KDoc on the public
declarations, not in READMEs.

Do not link temporary artifacts, such as audit reports or task plans, from
durable documentation like `.agents/project.md` unless they become maintained
documents.

## Verify Against Project Flows

Use `.agents/project.md` to find the owning document or module, then verify
claims against the nearest README, build file, workflow, or source file.

## Follow Local Documentation Conventions

- Use fenced code blocks for commands, Kotlin, Protobuf, YAML, and shell
  examples.
- Render file paths, package paths, Gradle tasks, module names, class and
  function names, and command names as code.
- Keep headings hierarchical: one top-level `#`, then ordered levels.
- Use local relative links for repository files.
- Keep examples small enough to verify and copy; component examples follow
  the class-based pattern shown in existing KDoc (e.g., the `Table` and
  `EntityChooser` class docs).
- Use consistent terminology: Chords, Compose Multiplatform, Spine Event
  Engine, Protobuf, ProtoData, codegen plugins, codegen runtime, application
  shell, class-based components.
- Do not leave orphans: avoid wrapping any paragraph, list item, or table cell
  so that a single word is left on its own final line.
- Do not duplicate long explanations between README files, `AGENTS.md`, and
  skills; link to the owning document instead.
- Keep lines within 100 characters, matching the code style limit.

## Comment Guidance

- Document public APIs: explicit API mode makes every public declaration a
  contract, and KDoc is expected on public classes, functions, and properties.
- Follow the local KDoc idiom: a one-sentence summary paragraph, detail
  paragraphs, `@param` tags for type and value parameters, and backticked
  identifiers.
- Use comments to explain why a constraint exists, not what the next line
  does.
- Mention important effects: recomposition triggers, server calls,
  generated-code dependencies, experimental Compose API usage, and returned
  errors.
- Do not add comments that restate names, parameters, or obvious operations.

## Make Docs Actionable

- Prefer executable steps, expected outcomes, and concrete examples.
- Include easy-to-miss prerequisites: working directory (root vs
  `codegen/plugins`), JDK version (11 vs 17), the `config` submodule
  initialization, and Maven-local publication of codegen plugins.
- When documenting failure behavior, include the concrete reason and where the
  user should look.
- When documenting architecture, describe ownership boundaries and the normal
  flow rather than every helper function.

## Validate Changes

- Verify every referenced path exists.
- Verify Gradle tasks, module names, versions, and defaults against build
  files or README ownership.
- Verify Markdown examples and local links.
- Run focused commands only when documentation changes depend on behavior that
  should be proven by build/test output.

## Output Format (for interactive sessions)

When writing documentation:

1. State the target audience and file location.
2. Summarize the documentation changed.
3. List source files, workflows, or docs used to verify claims.
4. Report validation commands run and any remaining unverified claims.

Follow the git-history policy in `AGENTS.md`.
