# Welcome, Agents

## Orientation

This repository is **Chords**, a suite of open-source libraries by TeamDev for
desktop UI development with the Compose Multiplatform toolkit, built around the
Spine Event Engine ecosystem.

For substantive implementation, review, or documentation work, start by reading:

- `README.md` for the library overview, supported environment, development
  setup, and build commands.
- `.agents/project.md` for the project overview, module map, architecture
  notes, documentation ownership, and CI notes.
- The README closest to the area you are changing, especially:
  - `core/README.md` for the application shell, class-based components, and
    basic UI components.
  - `proto/README.md` for Protobuf-aware UI components and message forms.
  - `proto-values/README.md` for supplementary Protobuf messages and Kotlin extensions.
  - `client/README.md` for server connectivity via Spine Event Engine.
  - `codegen/runtime/README.md` and `codegen/plugins/README.md` for the code
    generation runtime and ProtoData plugins.

Route task policy through the [skill index](.agents/skills/README.md); each
skill's frontmatter is the routing source of truth. Shared guidelines under
`.agents/guidelines/` cut across skills: read
[Design Restraint](.agents/guidelines/design-restraint.md) before any
implementation, and [Root Build Environment](.agents/guidelines/root-build.md)
before any root Gradle command.

## Prose

Write minimum complete prose: keep distinct requirements, decisions,
constraints, outcomes, and actions; remove repetition, exhaustive inventories,
heading restatements, and narration. Preserve details needed for safety,
authorization, compatibility, acceptance criteria, reviewer action, unresolved
decisions, and known risks.

Commit messages, issue and PR descriptions, and code-documentation summaries
must identify the subject or change and its purpose, user need, or outcome.
Omit mechanics and execution order unless they define a caller-visible contract
or a preserved detail above.

## Working Tree Safety

Treat pre-existing worktree and index changes as human-authored unless their
provenance is known. Modify or revert one only when the current prompt explicitly
requests that exact change; general implementation authorization is insufficient.
If it conflicts with policy or appears wrong, explain the conflict and proposed
resolution, await confirmation, and continue only with independent work.

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

The Commit and History Safety rules above apply throughout this procedure.

Do not repeat tests, checks, builds, or other verification solely because the
user asks to commit or push. Rely on verification already performed for the
change unless the current prompt explicitly requests additional verification.
Required report regeneration under "Versioning and Reports" remains part of
this procedure.

1. **Confirm authorization.** Commit or push only when the current prompt
   explicitly asks for it, per "Commit and History Safety" above.
2. **Choose the branch.**
   - If the current branch name matches the task, keep using it.
   - Otherwise create a new branch from the current `HEAD`, whatever branch
     that is. Never commit directly to `master`, and never commit onto a
     branch that belongs to a different task — a new branch cut from the
     current `HEAD` keeps the work off both.
   - Name new branches after the task, in the repository's kebab-case style
     (for example, `dialog-form-dirty-state`); do not include `codex` or other
     agent-specific identifiers in branches you create.
   - **Stacked work is normal.** Starting from a branch whose own pull request
     is still under review is the common case, not a mistake: the work depends
     on changes that have not merged yet. Branch from it as above and target
     `master` anyway (see "Creating a Pull Request"). Until the parent branch
     merges, the new pull request also shows that branch's commits; GitHub
     stops showing them once it merges. Do not wait for the parent, do not
     rebase onto `master` to hide the commits, and do not ask which branch to
     cut from — branching from the current `HEAD` is the answer in both the
     stacked and the plain case.
3. **Check the version and reports.** Apply "Versioning and Reports" below:
   inspect the commits and local state, bump `chordsVersion` in
   `version.gradle.kts` if the changeset has not bumped it yet, and if the
   generated `pom.xml` and `dependencies.md` reports are not updated yet, run
   the focused report-regeneration command in that section and include changed
   reports in the changeset.
4. **Commit in logical steps.** Create one or more logical commits; split the
   work only when each commit is independently coherent. Commit the version
   bump together with the regenerated `pom.xml` and `dependencies.md`, using
   the repository's established message format
   ``Bump version —> `<new-version>`.`` — its position in the sequence does
   not matter.
5. **Push.** Push the branch to its remote (for example,
   `git push -u origin <branch>`).
6. **Offer a pull request.** Ask whether to open a pull request, unless the
   prompt already requested one.

## Creating a Pull Request

Open the PR only once it is authorized (see "Committing and Pushing",
step 6). Creating a PR does not authorize additional verification; follow the
verification rule in that section.

1. **Create it as a draft** (`gh pr create --draft`), targeting `master` as the
   base branch unless the task specifies otherwise. This holds for stacked work
   too: a branch cut from another unmerged branch still targets `master`, so
   the pull request stays mergeable on its own once the parent merges.
2. **Assign it to the authenticated GitHub user** (`--assignee @me`).
3. **Omit a trailing period.** Do not end a pull request title with a period
   (`.`).
4. **Write the description** with a `## Summary` section followed by a
   `## Changes` section. Add optional sections such as `## Important notes` or
   `## Reviewer notes` only when they contain material information that
   reviewers need; omit routine, empty, or redundant sections. Do not include
   verification, testing, build, or check information anywhere in the PR
   description. Do not add any agent-attribution section such as
   `Created by <agent>`.
   - For stacked work, `## Reviewer notes` is material rather than optional:
     name the branch and exact commit the work was cut from, explain that the
     starting point contains commits outside the target branch, and tell the
     reviewer to review the task commits after that boundary. Do not claim the
     parent pull request is open or unmerged unless that state was verified.
     Without the note, the extra commits read as part of this change.
5. **Link resolved issues.** For each issue the PR implements or fixes, add a
   GitHub closing keyword in the description (for example, `Fixes #123`) so the
   issue appears under "Successfully merging this pull request may close these
   issues" on GitHub.
6. **Report the PR URL** in the final response.

Do not hard-wrap pull request prose. Break lines only for intentional Markdown
structure, including in a local draft. The 100-character limit under
"Development Conventions" governs repository files, not GitHub prose fields.

## GitHub Issues

Use one short problem-or-outcome paragraph followed only by the acceptance
criteria needed to establish completion. Add reproduction, background,
proposal, or affected areas only when they define scope or a decision. Avoid
repetition and file or call-site inventories. Issue prose follows the same
no-hard-wrap rule as pull request prose, including in local drafts.

Issues opened here are public; the confidentiality rule under "Safety Rules"
applies to their titles, bodies, and comments.

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
- Do not auto-update external dependencies outside dedicated update tasks. The
  toolchain versions are deliberately pinned (see Development Conventions) and
  upgrading them is a project-level decision.
- Do not add analytics, telemetry, or tracking code.
- Avoid reflection, unsafe code, broad global state, and hidden background work
  unless explicitly justified by the task (reflection is already used
  deliberately in a few places, such as resolving component type parameters).
- Preserve existing package structure, module boundaries, naming conventions,
  and Gradle patterns.
- Do not overengineer. Apply
  [Design Restraint](.agents/guidelines/design-restraint.md) to every
  implementation: abstract only over implementors that exist, judge a type
  parameter by the relationship it preserves, and treat deliberate public
  extension points such as `io.spine.chords.core.Component` as the exception.
- Do not manually edit generated sources or build outputs: `generated/`
  folders, codegen workspace outputs (`_out/`), Gradle wrapper files, or the
  generated `pom.xml` / `dependencies.md` reports; regenerate them with Gradle.
- Public API changes require care: all libraries are consumed by external
  projects, and Kotlin explicit API mode is enabled. Avoid breaking existing
  public signatures; prefer additive changes.

## Versioning and Reports

Every PR must increment `chordsVersion` in `version.gradle.kts` (enforced by
the `Check version increment` workflow). The version scheme is
`2.0.0-SNAPSHOT.<N>` where `<N>` grows monotonically.

The `pom.xml` and `dependencies.md` files at the repository root are generated
reports that must stay in sync with the changeset. The
`Ensure license reports updated` workflow requires both files to be modified
in every pull request. Both embed `chordsVersion`, so a version bump alone
changes them.

After bumping the version or changing dependencies, regenerate the reports
from the repository root, without running the full build:

```bash
find . -path '*/build/reports/dependency-license' -type d -prune \
    -exec rm -rf {} +
.agents/workflows/gradle-root.sh generatePom mergeAllLicenseReports
```

The wrapper selects and verifies the required JDK; see
[Root Build Environment](.agents/guidelines/root-build.md).

The `generatePom` task regenerates `pom.xml`, and `mergeAllLicenseReports`
merges the per-module license reports into `dependencies.md`. Deleting the
per-module reports first is required: otherwise Gradle considers
`generateLicenseReport` up to date, and the merge silently reuses reports
that still carry the previous version, leaving `dependencies.md` unchanged
and the workflow failing.

Afterwards, confirm that the `# Dependencies of ...` headings in
`dependencies.md` carry the new version, and include both regenerated reports
in the changeset. A full `.agents/workflows/gradle-root.sh build` regenerates
the files as well, but is unnecessary solely for this purpose.

Source files carry a copyright header; when modifying a file, keep the header
year current (files touched in a given year carry that year).

## Verification and Quality

Never mark a non-trivial change done without verification. Choose the smallest
command that proves the touched behavior, then broaden when shared behavior or
contracts are affected.

Useful root commands (run from the repository root, JDK 11):

```bash
.agents/workflows/gradle-root.sh :<module>:test
.agents/workflows/gradle-root.sh :<module>:test \
    --tests "io.spine.chords.proto.money.MoneyFieldSpec"
.agents/workflows/gradle-root.sh :<module>:check
.agents/workflows/gradle-root.sh detekt
.agents/workflows/gradle-root.sh clean build
.agents/workflows/gradle-root.sh publishToMavenLocal
```

### Toolchain

The root build needs JDK 11 and an x86_64 JVM on Apple Silicon. Read
[Root Build Environment](.agents/guidelines/root-build.md) before invoking it;
the guideline defines JDK selection, allowed tasks, and diagnosis.

### Module-Specific Verification

Gradle modules are `core`, `proto`, `proto-values`, `client`, `runtime` at
`codegen/runtime`, and `codegen-tests` at `codegen/tests`.

The `codegen/plugins` directory is a **separate Gradle project** requiring
JDK 17 and Gradle 9.4.x. Invoke its verified wrapper from the repository root:

```bash
.agents/workflows/gradle-codegen.sh build
.agents/workflows/gradle-codegen.sh publishToMavenLocal
```

Modules that use Chords code generation (`proto-values`, `codegen-tests`)
automatically depend on `publishCodegenPluginsToMavenLocal`, which builds and
publishes the codegen plugins locally before they are applied.

Chords libraries are UI libraries; automated tests cannot cover rendering
behavior. For visual/interactive component changes, state clearly in the final
response that behavior was verified by compilation and tests only, and describe
what manual verification remains.

If verification cannot be run, state the reason clearly in the final response.

## Development Conventions

- Use JDK 11 for the root project and JDK 17 for `codegen/plugins`.
- The supported environment is deliberately conservative. The root build uses
  the Kotlin Gradle plugin at 1.8.22 and forces production Kotlin libraries to
  1.9.23, while the supported consumer baseline remains Kotlin 1.8.20. Treat
  Kotlin 1.8 as the language ceiling and do not introduce post-1.8.20 standard
  library APIs without a deliberate compatibility decision. Compose
  Multiplatform is 1.5.12, Spine Event Engine is 1.9.0, and root Gradle is 6.9.4.
- Kotlin explicit API mode is enabled: public declarations require explicit
  `public` modifiers.
- Every declaration in project-owned source, including declarations explicitly
  marked `private`, must have a documentation comment in the language's
  standard format, such as KDoc or Javadoc. Explain its purpose, behavior, or
  constraints; do not merely restate its name.
- Configure IntelliJ IDEA Detekt with `quality/detekt-config.yml`.
- Keep lines within 100 characters (Detekt `MaxLineLength`).
- Do not introduce constants for text messages unless the user explicitly
  requests them.
- Do not add tests that assert text-message content unless the user explicitly
  requests such tests.
- UI components follow the class-based component pattern from `core` (see
  `io.spine.chords.core.Component` and its inheritors): composition happens in
  `content()`, pre-composition updates in `beforeComposeContent()`, and
  instance configuration via the `Props`-style lambdas. Composable functions
  and composable-emitting methods are named in `PascalCase`.
- Tests are named `*Spec.kt` and use JUnit Jupiter structure with Kotest
  matchers. The full convention — engine constraints, naming, fixtures, and
  `testlib` bases — is `.agents/skills/kotlin-jvm-tester/SKILL.md`; consult
  it before adding or restructuring a suite.
- Dependency coordinates live in `buildSrc/src/main/kotlin/io/spine/internal/dependency/`;
  add or change them there, following the existing object-per-library pattern.
- After the final source edit, remove unused imports and sort the rest in local
  order, including after a move or rename. Never add a wildcard import. Add a
  Kotlin alias only with explicit human direction; qualify collisions instead.
- Get the `config` submodule content with
  `git submodule update --init --recursive` before building.

## Bug Fixes

When fixing a bug, fix the root cause rather than adding a workaround. If the
root cause cannot be fixed, ask for confirmation before implementing a
workaround and explain why the root cause cannot be addressed.

Cover the fix with a test that reproduces the bug and fails without the fix. If
a test cannot be added, state this in the final response and explain why.

## Code Review

For reviews, lead with findings ordered by severity and include file/line
references. Focus on bugs, regressions, public API breaks, missing tests,
security risks, release hazards, and convention violations.

Skip routine review of generated or vendored files, including:

- `gradlew`, `gradlew.bat`, `gradle/wrapper/**` (root and `codegen/plugins`)
- generated `pom.xml` and `dependencies.md` reports
- generated Protobuf/codegen outputs
- the `config/` submodule
- IDE metadata such as `.idea/**`

Do not skip `buildSrc/**` or `codegen/plugins/buildSrc/**`: they own dependency
and Gradle configuration for Chords.

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
