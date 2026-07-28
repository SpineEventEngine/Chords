---
name: proofread
description: >
  Proofreads project-owned Chords comments and documentation for English
  grammar, punctuation, and spelling. Use for branch changes, a repository-wide
  prose sweep, or a path-scoped review of Kotlin/Java comments, Protobuf
  comments, and Markdown. Applies the Chords English catalog, preserves code and
  machine-read text, and reports ambiguous cases instead of guessing.
---

# Proofread

Fix English-language errors in comments and documentation by applying
`.agents/guidelines/english-style.md`. Treat that catalog as the source of truth
for what is an error and when to leave an occurrence unchanged. Use this skill
only for scoping, scanning, editing, and reporting.

Prefer a missed error to an incorrect edit. When a fix is not clearly correct,
leave the text unchanged and report it.

## Workflow

1. Choose the mode from the caller's argument.

   - No argument: use branch-diff mode. Scan the union of:
     - `git diff --name-only --diff-filter=ACMR <base>...HEAD`;
     - `git diff --name-only --diff-filter=ACMR HEAD`; and
     - `git ls-files --others --exclude-standard`.

     Resolve `<base>` to `origin/master`, then `master`. If neither exists, use
     the working-tree lists and report the missing base. The three-dot diff
     includes committed changes since the branch diverged; the other lists add
     staged, unstaged, and untracked non-ignored files. On a stacked branch,
     review the resulting file list before editing because it can include
     changes inherited from the parent branch.

   - Argument `all`: use full-sweep mode. Enumerate candidates with
     `git ls-files`. To scan a path literally named `all`, pass `./all`.

   - Any other argument: use scoped-sweep mode. Treat the argument as a file or
     directory path and enumerate tracked candidates with
     `git ls-files -- <path>`.

2. Identify target files.

   Keep only Chords prose-bearing file types:

   - `*.kt`, `*.kts`, and `*.java`;
   - `*.proto`; and
   - `*.md`.

   In full-sweep and scoped-sweep modes, scan tracked files only. In branch-diff
   mode, retain eligible untracked, non-ignored files.

   Drop generated content and build outputs, as listed in `AGENTS.md`:
   `generated/` folders, a module's `build/` or `.gradle/` output directory,
   codegen `_out/` workspaces, and the generated root `pom.xml` and
   `dependencies.md` reports.

   Match those as output directories, not as any path segment with the same
   name. A source package directory named `build` is ordinary project-owned
   code, as in
   `codegen/plugins/buildSrc/src/main/kotlin/io/spine/dependency/build/`.

   Apply `.agents/guidelines/project-owned-files.md` in every mode. In
   particular, do not edit the `config/` submodule or files distributed by it.
   Deciding whether `buildSrc/<path>` is distributed needs a working-tree check
   for `config/buildSrc/<path>`, such as `Glob`. `git ls-files` cannot answer
   it: the submodule is tracked as a single gitlink, so it reports none of the
   submodule's contents and every `buildSrc` file looks project-owned.

3. Scan and edit each file.

   Read the complete English catalog before editing. Restrict changes to the
   prose identified in its "Where English Prose Lives" section:

   - In Kotlin, Kotlin script, Java, and Protobuf files, edit only comment text.
   - In Markdown, edit only headings and body prose.
   - Never edit code tokens, string literals, identifiers, code examples,
     link targets, file paths, commands, generated content, copyright headers,
     or machine-read directives listed in the catalog.
   - Never edit text that a style guide cites as an example of an error.
     Full-sweep mode includes `.agents/guidelines/english-style.md` itself, and
     the "Before" column of its tables is deliberately wrong. Correcting those
     entries destroys the catalog.

   Apply every relevant catalog topic, but only when none of its leave-alone
   guards matches. Preserve the author's meaning and voice. Make the smallest
   change that fixes the error and keep each file's spelling dialect and list
   punctuation internally consistent.

   Group a file's corrections into as few edits as the editing tool allows,
   and anchor every one. The same comment line recurs across overloads and
   `@param` blocks, so a match that carries no surrounding context can land on
   the wrong occurrence. Include enough context to make each match unique, and
   replace every occurrence at once only when they all need the same fix.

   If an occurrence is ambiguous, leave it unchanged and add it to `Skipped[]`
   with its catalog topic and the reason `ambiguous`.

4. Review the diff.

   Read the whole `git diff`, not just the list of changed files. Confirm that
   every changed line is prose, no code or machine-read text changed, and no
   excluded or generated file was touched. Revert any speculative or
   out-of-scope edit.

   A comments-only sweep normally keeps insertions and deletions equal per
   file, so check `git diff --stat` and account for any file where they
   differ. Rewrapping a paragraph explains it; a moved code line does not.

5. Report the result.

## Report

Return:

- `Mode`: `branch-diff`, `all`, or `path:<path>`;
- `FilesScanned` and `FilesChanged`;
- `Changes[]`, grouped by catalog topic, with file, line, and
  `before` → `after`. When one topic covers many instances of the same
  correction, state that correction once with a count and cite the files,
  instead of repeating an identical `before` → `after` per line; and
- `Skipped[]`, with file, line, catalog topic, and reason.

If no file qualifies, report zero counts. If the base ref was unavailable,
state that branch-diff mode covered only the working tree.
