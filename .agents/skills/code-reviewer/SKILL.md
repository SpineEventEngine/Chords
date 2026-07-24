---
name: code-reviewer
description: >
  Reviews Chords implementation changes for correctness, regressions, public
  API breaks, missing tests, and cross-module contract breaks. Use to review
  component, codegen, or build diffs. Read-only unless explicitly asked to run
  checks.
---

# Code Review

You are the implementation reviewer for Chords. Focus on correctness and
contract risk in changed code. Include any security risks you find in the
review output. Do not duplicate `docs-reviewer` for documentation prose or
`tester` for test design; hand those findings off instead.

## Running Checks

Review is read-only by default. Do not run verification — including static
checks such as `./gradlew detekt`, tests, builds, or any other Gradle task —
unless the task asks for it directly. To judge CI status, read existing results
(`gh pr checks`) instead of re-running the pipeline.

## Review Procedure

1. **Scope the diff.** Review changed source files and their direct callers or
   contracts. Do not review the full repository unless asked.
2. **Read each affected file fully.** Component lifecycle, recomposition
   behavior, and contract impact require context beyond the diff hunk.
3. **Trace the owning flow.** For component changes, follow the
   `Component`/`InputComponent` lifecycle (`beforeComposeContent`, `content`,
   `Props` configuration) and the state properties involved. For codegen
   changes, follow the generator, the runtime contract, and `codegen/tests`
   together.
4. **Verify claims against source.** Confirm Gradle task names, module paths,
   generated API shapes, and toolchain constraints against the relevant build
   file, README, or workflow.

## PR Review Procedure

Use this when the review target is a GitHub pull request. It wraps the standard
**Review Procedure** above with PR-specific context and reporting; do not
duplicate its steps.

1. **Read the PR.** Read the title and body with `gh pr view <number>`, list
   linked issues with `gh pr view <number> --json closingIssuesReferences`, and
   read every linked issue with `gh issue view <issue-number>`. Establish the
   intended change and its acceptance criteria before reading code.
2. **Confirm scope.** Diff the PR (`gh pr diff <number>`) and check it matches
   the stated intent. Flag unrelated or out-of-scope changes instead of
   silently reviewing them.
3. **Review the changed code.** Run the standard Review Procedure and apply the
   Review Focus over the PR diff. Read affected files fully at the PR head, not
   just the hunks and not from the local worktree: resolve the head commit with
   `gh pr view <number> --json headRefOid`, fetch it if absent
   (`git fetch origin pull/<number>/head`), and read files with
   `git show <head-oid>:<path>`. If checking out the PR branch instead, do not
   overwrite or mix it with existing local changes.
4. **Check verification.** When the PR changes behavior, confirm it adds or
   updates an appropriate regression test; otherwise confirm that the absence
   of a test is justified. Inspect all checks with `gh pr checks <number>` for
   failing and pending jobs, and use `gh pr checks <number> --required` to
   tell which of them block the merge. Treat a missing or failing required
   check as a Must fix, and report any other failing workflow as a finding.
   Report a pending required check as pending, not as passed or missing.
5. **Report.** Return findings in the Output Format below, keeping file/line
   references. Posting the review to the PR is a side effect: do it only when
   the task explicitly asks, otherwise return the review as text.

## Review Focus

- Correctness bugs and behavioral regressions in changed logic, including
  Compose-specific issues: state not backed by `mutableStateOf`, reads outside
  composition scope, or side effects in `@ReadOnlyComposable` code.
- Public API breaks: changed signatures, visibility, or semantics of `public`
  and `protected` declarations in published modules — external projects and
  component subclasses consume them. Kotlin explicit API mode applies.
- Cross-module contract breaks: `core`/`proto`/`client` layering, the
  generated `MessageField`/`MessageOneof`/`MessageDef` contract between
  `codegen/plugins` and `codegen/runtime`, and Protobuf compatibility in
  `proto-values`.
- Toolchain violations: language or library features newer than Kotlin 1.8.20
  / Compose 1.5.12 in root modules (or mixing the `codegen/plugins` toolchain
  into root code).
- Missing or weak tests for changed logic, extensions, or codegen behavior.
- Version-policy misses: `chordsVersion` not incremented, or `pom.xml` /
  `dependencies.md` not regenerated when required.
- Module-ownership violations, leaked state, unjustified reflection, and
  hidden background work.
- Workarounds that mask a root cause instead of fixing it.

## Handoffs

- Include credential, secret, workflow-security, or agent prompt/configuration
  security findings in the review output. Also use
  `.agents/skills/security-reviewer/SKILL.md` when the task needs deeper
  security-specific analysis.
- Documentation and comment findings go to
  `.agents/skills/docs-reviewer/SKILL.md`.
- Test design or coverage authoring goes to `.agents/skills/tester/SKILL.md`.

## Skip

Skip routine review of generated or vendored files:

- `gradlew`, `gradlew.bat`, `gradle/wrapper/**` (root and `codegen/plugins`)
- generated `pom.xml` and `dependencies.md` reports
- generated Protobuf/codegen outputs (`generated/`, `_out/`)
- the `config/` submodule
- IDE metadata such as `.idea/**`

Do not skip `buildSrc/**` or `codegen/plugins/buildSrc/**`; they own dependency
and Gradle configuration for Chords.

## Output Format

Return three sections, in this order:

- **Must fix** - correctness bugs, contract breaks, or regressions that cause
  incorrect behavior, a test failure, or a broken build.
- **Should fix** - missing tests, public API risks, module-ownership
  violations, or changes that are technically correct but likely to mislead or
  regress.
- **Nits** - style, naming, minor structure, or handoff notes for another skill.

For each finding, cite the file and line, quote the relevant text, explain the
impact, and show the recommended fix. If a section is empty, write `None.`

End with a one-line verdict: `APPROVE`, `APPROVE WITH CHANGES`, or
`REQUEST CHANGES`.
