# Project-Owned Files

Use this boundary for repository-wide or path-scoped edits. Chords owns its
library sources, module documentation, repository-specific build files,
`AGENTS.md`, `CLAUDE.md`, and `.agents/` content. It does not own the `config`
submodule or files that the pinned `config/pull` script distributes into the
project tree.

## Chords Exclusions

Skip these paths in every proofreading mode:

- `config/` and anything beneath it. `.gitmodules` declares this directory as
  the `SpineEventEngine/config` submodule.
- Any `buildSrc/<path>` for which `config/buildSrc/<path>` exists. The
  `config/pull` script copies these paths into the root `buildSrc/` tree.
  Chords-specific root files with no matching submodule path remain
  project-owned.
- `CODE_OF_CONDUCT.md`, which `config/pull` copies unconditionally.
- `CONTRIBUTING.md`, which `config/pull` copies unconditionally.

The exclusions apply even though copied files are ordinary tracked files.
A later `config` update can overwrite edits to them.

For proofreading, generated sources, build outputs, codegen `_out/`
workspaces, and generated root `pom.xml` and `dependencies.md` reports are
also out of scope.

## Resolving Ownership

Enumerate tracked files with `git ls-files`. A submodule appears as one gitlink
rather than as its contents, but explicitly drop its path from diff-based
candidate lists.

Determine submodule ownership from `.gitmodules`, not from a directory name.
If Chords adds another submodule, exclude its declared path and descendants.

When `config` is unavailable, continue excluding the fixed distributed paths
and conservatively skip all of `buildSrc/`. A missed proofreading case is
preferable to changing an upstream-owned file.

Do not exclude a path merely because a same-named file exists somewhere under
`config/`. The submodule contains files that it does not distribute.
