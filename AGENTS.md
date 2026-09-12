# Welcome, Agents

## Orientation

Chords is TeamDev's suite of open-source desktop UI libraries built with
Compose Multiplatform and Spine Event Engine.

Before substantive work, read `README.md`, the [project map](.agents/project.md),
and the nearest area README listed in its
[documentation map](.agents/project.md#documentation-ownership).
Use the [skill index](.agents/skills/README.md) to select the narrowest matching
skills; their frontmatter is the routing source of truth.

Apply [Design Restraint](.agents/guidelines/design-restraint.md) to every
implementation. Before root Gradle commands, read
[Root Build Environment](.agents/guidelines/root-build.md).

## Prose

Write as one developer to another: short sentences, familiar words, and direct
verbs. Keep only facts the reader needs.

Write minimum complete prose: keep distinct requirements, decisions,
constraints, outcomes, and actions; remove repetition, exhaustive inventories,
heading restatements, and narration. Preserve details needed for safety,
authorization, compatibility, acceptance criteria, reviewer action, unresolved
decisions, and known risks.

Commit messages, issue and PR descriptions, and code-documentation summaries
must identify the subject or change and its purpose, user need, or outcome.
Omit mechanics and execution order unless they define a caller-visible contract
or a preserved detail above. Follow
[Documentation Writing](.agents/skills/docs-writer/SKILL.md) for comments,
Markdown, and issue or PR descriptions.

## Working Tree Safety

Human changes take precedence over agent work. This applies equally to code and
documentation, including edits made before or during an agent's turn. If a human
edits, removes, or replaces content, preserve the latest human version and do not
reapply an earlier agent version without explicit approval for that exact content.

Treat pre-existing worktree and index changes as human-authored unless their
provenance is known. Modify or revert one only when the current prompt explicitly
requests that exact change; general implementation authorization is insufficient.
If it conflicts with policy or appears wrong, explain the conflict and proposed
resolution, await confirmation, and continue only with independent work.

Treat ignore configuration and ignored paths as user-owned. Do not edit an
ignore rule, use `git add -f`, or bypass ignore behavior without explicit
authorization for that exact action. A request to commit all changes does not
authorize including ignored files.

## Protobuf Authorization

Before editing a published `.proto` declaration under `proto-values/`, state
the exact change and obtain explicit confirmation; external projects consume
these schemas as a source API and wire contract. Inspection and proposals need no
confirmation. Follow
[`model-engineer`](.agents/skills/model-engineer/SKILL.md) for schema evolution
and verification.

The gate covers only `proto-values/src/main/proto/`. Plugin declarations under
`codegen/plugins/codegen-plugins/src/main/proto/` and fixtures under
`codegen/tests/src/test/proto/` carry no external contract; they follow the
[`codegen-engineer`](.agents/skills/codegen-engineer/SKILL.md) skill.

## Commit and History Safety

Before committing changes or opening a pull request, agents must re-read this
`AGENTS.md` policy in full, even if they read it earlier in the task.

Do not commit, push, tag, rebase, merge, cherry-pick, or otherwise write to Git
history unless the user's current prompt explicitly asks for it.

Authorization does not carry over between turns or sessions. When in doubt,
leave changes unstaged, show the diff or summarize it, and let the user decide.

When moving or renaming tracked files, use `git mv` so file history is preserved.

## Committing and Pushing

A commit or push request does not authorize additional verification. Use existing
results unless the current prompt asks for new checks. Required report
regeneration remains part of preparing a commit.

1. Confirm authorization for each history operation under "Commit and History Safety".
2. Keep the current branch only if it matches the task. Otherwise branch from
   current `HEAD`; never commit to `master` or an unrelated task branch. Use
   repository-style kebab-case without an agent prefix. Stacked work starts at
   current `HEAD` and still targets `master`: do not wait, rebase to hide inherited
   commits, or ask which base to use.
3. Apply [Versioning and Reports](.agents/skills/build-engineer/SKILL.md#versioning-and-reports)
   before committing, including the branch-history check and report regeneration.
4. Commit coherent steps, grouping the version and reports as that skill requires.
5. Push the branch when authorized, normally with `git push -u origin <branch>`.
6. Unless already requested, ask whether to open a pull request.

## Creating a Pull Request

Create a PR only when authorized. This does not authorize additional verification.
Create it as a draft against `master` unless directed otherwise, including for
stacked work. Assign the authenticated GitHub user (`--assignee @me`) and report
the URL.

Apply [Versioning and Reports](.agents/skills/build-engineer/SKILL.md#versioning-and-reports)
before opening the PR.
Follow [PR and issue rules](.agents/skills/docs-writer/SKILL.md#github-issues-and-pull-requests)
for titles, descriptions, resolved-issue links, and stacked-review notes.

## Safety Rules

- This is a public open-source repository (Apache 2.0). Do not add secrets,
  credentials, tokens, private keys, or TeamDev-internal data to it.
- Pull requests and issues opened in this repository are public. Keep their
  titles, descriptions, comments, and linked references free of any information
  about dependent private projects. Do not name those projects, describe their
  domain or business logic, link to their issues, PRs, or repositories, or
  reference their internal identifiers. Describe the change only in terms of
  this repository's own libraries and public API.
- Do not modify files under the `config/` Git submodule; it is owned by the
  [SpineEventEngine/config](https://github.com/SpineEventEngine/config)
  repository, and changes belong upstream.
- Do not publish artifacts or trigger publishing tasks (`publish`,
  `publishCodegenPlugins`) unless the user's current prompt explicitly asks for
  it. Publishing is normally performed by CI on pushes to `master`. This does
  not restrict `publishToMavenLocal` or `publishCodegenPluginsToMavenLocal`,
  which stay on the workstation and are part of routine verification.
- Do not edit the encrypted key files under `.github/keys/` or the decryption
  scripts' credential wiring.
- Do not auto-update external dependencies outside dedicated update tasks.
  Follow the toolchain limits in `kotlin-engineer` and `build-engineer`.
- Do not add analytics, telemetry, or tracking code.
- Avoid reflection, unsafe code, broad global state, and hidden background work
  unless explicitly justified by the task (reflection is already used
  deliberately in a few places, such as resolving component type parameters).
- Preserve existing package structure, module boundaries, naming conventions,
  and Gradle patterns.
- Do not manually edit generated sources or build outputs: `generated/`
  folders, codegen workspace outputs (`_out/`), Gradle wrapper files, or the
  generated `pom.xml` / `dependencies.md` reports; regenerate them with Gradle.
- Public API changes require care: all libraries are consumed by external
  projects, and Kotlin explicit API mode is enabled. Avoid breaking existing
  public signatures; prefer additive changes.

## Verification and Quality

Never mark a non-trivial change done without the smallest verification that
proves it. Broaden when shared behavior or contracts change, and report why any
required verification could not run.

Use [Testing](.agents/skills/tester/SKILL.md) for coverage, commands, local
component diagnosis, and remaining manual checks. Apply the relevant area skill
when verification crosses model, component, or codegen contracts.

## Development Conventions

- Apply [Kotlin Engineering](.agents/skills/kotlin-engineer/SKILL.md) to every
  Kotlin implementation, refactor, or review, paired with the area skill.
- Follow [Component Engineering](.agents/skills/component-engineer/SKILL.md)
  for UI components and [Kotlin JVM Testing](.agents/skills/kotlin-jvm-tester/SKILL.md)
  before adding or restructuring a suite.
- Every declaration in project-owned source, including private ones, needs a
  standard documentation comment describing purpose, behavior, or constraints.
  Follow `docs-writer`; do not merely restate the declaration's name.
- Keep modified source files' copyright years current and lines within 100 characters.
- After the final source edit, remove unused imports and sort the rest in local
  order, including after a move or rename. Never add wildcard imports. Add a
  Kotlin alias only with explicit human direction; qualify collisions instead.

## Bug Fixes

When fixing a bug, fix the root cause rather than adding a workaround. If the
root cause cannot be fixed, ask for confirmation before implementing a
workaround and explain why the root cause cannot be addressed.

Cover the fix with a test that reproduces the bug and fails without the fix. If
a test cannot be added, state this in the final response and explain why.

## Code Review

Use [Code Review](.agents/skills/code-reviewer/SKILL.md) for implementation,
[Documentation Review](.agents/skills/docs-reviewer/SKILL.md) for prose, and
[Security Review](.agents/skills/security-reviewer/SKILL.md) for security concerns.
They define scope, exclusions, verification authority, and reporting.

## Planning and Questions

Start each task by forming an agent-owned plan before editing or running
non-trivial commands. While composing that plan, identify missing requirements,
risks, affected areas, and verification needs.

Ask the clarification questions needed to close uncovered spots in the plan,
following these rules:

- Ask at most one question per message. When a decision has a small set of
  options, include those options in that question.
- Do not bundle unrelated questions. Ask the next one only after the user
  answers the previous.
- Apply this both when you need clarification and when the prompt means
  "ask questions".
- Prefer a reasonable assumption over another question when the answer would
  not materially change the plan, implementation, safety posture, or
  verification path.
