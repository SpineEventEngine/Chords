---
name: docs-writer
description: >
  Writes, edits, and restructures Chords documentation. Use when asked to
  create or update Markdown, agent rules, KDoc, comments, and commit, issue,
  or pull-request descriptions. Verifies claims against
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
durable documentation like `.agents/project.md` unless they become maintained documents.

## Write Minimum Complete Prose

Apply the `AGENTS.md` "Prose" rules. State what the
subject or change is and why it matters. Keep each fact once and only when it
helps the reader act, decide, or form a correct expectation. Remove narration
and restated identifiers, signatures, code, or execution order.

## GitHub Issues and Pull Requests

Follow `AGENTS.md` for authorization, branch choice, draft status, assignment,
and public-repository confidentiality. This section defines the prose.
Do not hard-wrap issue or PR prose, even in local drafts; break lines only for
intentional Markdown structure. The repository's 100-character limit does not
apply to GitHub prose fields.

### Issues

Use one short problem-or-outcome paragraph followed only by acceptance criteria
needed to establish completion. Add reproduction, background, proposal, or
affected areas only when they define scope or a decision. Avoid repetition and
file or call-site inventories.

### Pull Requests

- Omit a trailing period from the title.
- Use `## Summary` followed by `## Changes`. Add optional sections such as
  `## Important notes` or `## Reviewer notes` only for material constraints or
  reviewer actions. Omit routine, empty, or redundant sections.
- Include no verification, testing, build, or check information in the
  description, and no agent-attribution section such as `Created by <agent>`.
- For stacked work, `## Reviewer notes` is required: name the source branch
  and exact boundary commit, state that earlier commits are outside this task,
  and direct review to the task commits after that boundary. Do not claim the
  parent PR is open or unmerged unless that state was verified.
- Add a GitHub closing keyword, such as `Fixes #123`, for every resolved issue.

## Keep AI Policy Abstract

Write reusable roles, invariants, effects, and decisions rather than
refactor-sensitive names or messages. Keep a necessary identifier in a labeled
example; framework, toolchain, and test-infrastructure names may remain.

## Verify Against Project Flows

Use `.agents/project.md` to find the owning document or module, then verify
claims against the nearest README, build file, workflow, or source file.

## Follow Local Documentation Conventions

- Follow `.agents/guidelines/english-style.md` for English grammar,
  punctuation, and spelling.
- Write every KDoc and Javadoc in multiline form: `/**` and ` */` on their own
  lines, each content line prefixed with ` *`. Do not use the single-line
  `/** ... */` form.
- Fence examples of commands, Kotlin, Protobuf, YAML, and shell.
- Render file paths, package paths, Gradle tasks, module names, class and
  function names, and command names as code.
- Keep headings hierarchical: one top-level `#`, then ordered levels.
- Use local relative links for repository files.
- Keep examples small enough to verify and copy; follow class-based KDoc,
  including the `Table` and `EntityChooser` examples.
- Use consistent terminology: Chords, Compose Multiplatform, Spine Event
  Engine, Protobuf, ProtoData, codegen plugins, codegen runtime, application
  shell, class-based components.
- Name the precise relationship instead of using vague ownership language.
  Keep literal API terms and state or resource ownership when that is the contract.
- Avoid a one-word final line in added or rewritten paragraphs, list items, and
  table cells. Do not reflow unchanged prose solely to fix a legacy orphan.
- Do not duplicate long explanations between README files, `AGENTS.md`, and
  skills; link to the owning document instead.
- Keep lines within 100 characters, matching the code style limit.

## Comment Guidance

- Lead with purpose, not construction, storage, delegation, or lifecycle
  mechanics, unless they define a caller-visible contract.
- Document every declaration, including private ones, as `AGENTS.md` requires.
- Follow local KDoc: a one-sentence summary, detail paragraphs, `@param` tags
  for type and value parameters, and backticked identifiers.
- Use comments to explain why a constraint exists, not what the next statement does.
- Mention important effects: recomposition triggers, server calls,
  state changes, environment dependencies, generated-code dependencies,
  experimental Compose APIs, and returned errors.
- For APIs that return messages or invoke callbacks, document the possible
  results and their conditions. State ordering only when callers can observe it.
- Explain qualifiers such as `estimated`, `approximate`, and `best-effort`:
  name the source of uncertainty and the resulting behavioral limit.
- Prefer precise API and lifecycle terms to metaphors the reader must interpret.
- Do not add comments that restate names, parameters, or obvious operations.

## Component API Documentation

New UI components, whether class-based or composable functions, require KDoc
with practical usage examples. Apply this to public components, reusable
internal rendering primitives, and new public component overloads. Update the same
documentation when their contract changes.

- State the purpose, then explain the behavior, defaults, state ownership,
  configuration, and constraints that callers need to use the API correctly.
  Cover parameters and relevant effects; omit details that do not affect callers.
- Include at least one small Kotlin example of actual use. Add another only
  when it demonstrates a distinct configuration or state-management pattern.
  Make required context clear and use the supported API and toolchain.
- For internal primitives, name the intended callers and public alternative.
  Private implementation helpers need purpose and constraint documentation,
  but do not require usage examples unless those examples clarify a real contract.
- Follow neighboring KDoc structure. Use short paragraphs, fenced examples,
  and lists where they improve scanning. Avoid filler, repeated signatures,
  implementation narration, and documentation length targets.
- Verify claims against the implementation and compile new or changed examples
  in the owning module. Temporary compilation sources must be removed afterward.
  If compilation is unavailable, report that limitation. For substantial KDoc
  additions, generate the API documentation and check examples and links in the output.

## Make Docs Actionable

- Prefer executable steps, expected outcomes, and concrete examples.
- Include easy-to-miss prerequisites: the owning Gradle wrapper and JDK
  (root/11 vs codegen/17), `config` submodule initialization, and Maven-local
  publication of codegen plugins.
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

Report the location, outcome, verification, and remaining gaps without empty
categories or repeated task and diff context.

Follow the git-history policy in `AGENTS.md`.
