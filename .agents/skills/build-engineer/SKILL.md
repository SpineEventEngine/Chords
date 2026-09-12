---
name: build-engineer
description: >
  Chords build and release-engineering policy. Use for root and codegen/plugins
  Gradle build logic, buildSrc dependency coordinates, publishing wiring,
  version increments, generated `pom.xml`/`dependencies.md` reports, and the
  config submodule relationship.
---

# Build Engineering

## When to Use

Use this skill for build, dependency, and release-plumbing work:

- Root `build.gradle.kts`, `settings.gradle.kts`, `version.gradle.kts`, and `gradle.properties`.
- `buildSrc/` dependency coordinates, repository helpers, and convention
  plugins (`jvm-module`, publishing, reports).
- The `codegen/plugins` Gradle project's own build logic and its
  `codegen-workspace` resource packaging.
- Publishing configuration: `spinePublishing`, artifact prefix, destination
  repositories, and the `publishCodegenPlugins*` tasks.
- Version policy and the generated `pom.xml` / `dependencies.md` reports.
- Detekt configuration under `quality/`.

For `.github/workflows` YAML, use `.agents/skills/ci-engineer/SKILL.md`.

## Policy

- Two toolchains coexist: the root project uses JDK 11 / Gradle 6.9.4 and the
  compiler/library version split documented in `kotlin-engineer`;
  `codegen/plugins` uses JDK 17 / Gradle 9.4.x / Kotlin 2.3.20. Keep build
  logic compatible with the owning toolchain.
- Dependency coordinates belong in
  `buildSrc/src/main/kotlin/io/spine/internal/dependency/`, one object per
  library, following the existing pattern. Do not inline version strings into
  module build files.
- Do not auto-update external dependencies or toolchain versions outside
  dedicated upgrade tasks; the pins match the Spine 1.9.x consumer ecosystem.
- The `config/` submodule is owned by `SpineEventEngine/config`; do not edit
  its contents here. If shared build logic must change, describe the upstream
  change instead.
- Do not run `publish` or `publishCodegenPlugins` against remote repositories;
  publishing is CI-owned on `master`. Verify locally with `publishToMavenLocal`.
- Preserve the codegen wiring contract: `modulesWithChordsCodegen` lists the
  modules that get the `io.spine.chords` Gradle plugin, and
  `createCodegenWorkspace` depends on `publishCodegenPluginsToMavenLocal`.
- Keep the Gradle plugin version (`io.spine.chords` in `build.gradle.kts` and
  `Spine.kt` in `buildSrc`) consistent in both places when bumping it.

## Versioning and Reports

Before committing, inspect local state and the complete current-branch history,
not only the diff against the base. Do not add another bump when the branch
already contains one for this change; otherwise bump `chordsVersion` in
`version.gradle.kts`.

Commit the version bump with regenerated `pom.xml` and `dependencies.md`, using
``Bump version —> `<new-version>`.`` as the commit subject. Its position among
otherwise coherent task commits does not matter. `AGENTS.md` governs history
operations; preparing a version bump does not authorize a commit or push.

Every PR must increment `chordsVersion` in `version.gradle.kts` (enforced by
the `Check version increment` workflow). The version scheme is
`2.0.0-SNAPSHOT.<N>` where `<N>` grows monotonically.

The `pom.xml` and `dependencies.md` files at the repository root are generated
reports that must stay in sync with the changeset. Include both updated reports
in each PR. The [CI skill](../ci-engineer/SKILL.md#scope) describes the report
guard. Both embed `chordsVersion`, so a version bump alone changes them.

After bumping the version or changing dependencies, regenerate the reports
from the repository root, without running the full build:

```bash
find . -path '*/build/reports/dependency-license' -type d -prune \
    -exec rm -rf {} +
.agents/workflows/gradle-root.sh generatePom mergeAllLicenseReports
```

The wrapper selects and verifies the required JDK; see
[Root Build Environment](../../guidelines/root-build.md).

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

## Verification

Initialize the `config` submodule before building:

```bash
git submodule update --init --recursive
```

From the repository root, follow `.agents/guidelines/root-build.md`:

```bash
.agents/workflows/gradle-root.sh clean build
.agents/workflows/gradle-root.sh publishToMavenLocal
.agents/workflows/gradle-root.sh checkVersionIncrement
```

Codegen plugins build, using the verified JDK 17 wrapper from the repository root:

```bash
.agents/workflows/gradle-codegen.sh build
.agents/workflows/gradle-codegen.sh publishToMavenLocal
```

Run the `publishToMavenLocal` variant when plugin publication is part of the
change, so root modules consume the rebuilt plugins.

After version or dependency changes, confirm `pom.xml` and `dependencies.md`
were regenerated and reflect the new version.
