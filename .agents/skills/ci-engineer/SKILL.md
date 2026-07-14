---
name: ci-engineer
description: >
  Chords CI/CD workflow guidance. Use for authoring or reviewing GitHub Actions
  workflows under `.github/workflows`: Ubuntu/Windows builds, license-report
  and version-increment guards, Gradle wrapper validation, and artifact
  publishing.
---

# CI Engineering

Use this skill for GitHub Actions workflow work under `.github/workflows/`.
Publishing to Maven repositories happens automatically on `master`, but changes
to the publishing pipeline and its credentials remain human-reviewed; this
skill authors and reviews workflow definitions, it does not trigger them.

## Scope

- `.github/workflows/build-on-ubuntu.yml`: JDK 11 build on every push.
- `.github/workflows/build-on-windows.yml`: Windows build on pull requests.
- `.github/workflows/ensure-reports-updated.yml`: guard that `dependencies.md`
  / `pom.xml` (or `version.gradle.kts`) changed in the PR, via
  `config/scripts/ensure-reports-updated.sh`.
- `.github/workflows/increment-guard.yml`: the `checkVersionIncrement` guard.
- `.github/workflows/gradle-wrapper-validation.yml`: wrapper JAR integrity.
- `.github/workflows/publish.yml`: publishing from `master`, including GPG
  decryption of credentials from `.github/keys/`.
- Workflow triggers, branch filters, job/step wiring, JDK setup, caching,
  working directories, and secret references by name.

For secret handling and supply-chain safety, also use
`.agents/skills/security-reviewer/SKILL.md`. For the Gradle tasks the
workflows invoke, use `.agents/skills/build-engineer/SKILL.md`.

## Rules

- Do not weaken the guards that gate merges: the version-increment check, the
  license-report check, wrapper validation, and the build steps.
- Preserve existing triggers, branch filters, JDK versions, and working
  directories unless the task explicitly asks to change them. Note that the
  repository checkout must initialize the `config` submodule where scripts
  from it are used.
- Reference secrets by name through `secrets.*`. Never inline secret values,
  tokens, or credentials into a workflow.
- Do not add steps that publish artifacts or rotate credentials as routine
  changes. Describe the required human action instead.
- Keep in mind the two-toolchain layout: root builds need JDK 11; any step
  building `codegen/plugins` directly needs JDK 17 and runs from
  `codegen/plugins/`.

## Verification

Workflows run in GitHub Actions, not locally. Verify changes by static review:

- Confirm referenced Gradle tasks, module paths, scripts, and working
  directories exist and match the owning `build.gradle.kts`, `config/scripts`,
  and README files.
- Confirm secret names, environment variables, branch filters, and job
  dependencies match the intended trigger and the rest of the pipeline.
- Confirm that jobs using scripts from the `config/` submodule configure
  `actions/checkout` with submodule initialization.
- Validate YAML structure and the versions of referenced actions.

State clearly in the final response that full validation happens in CI.

Follow the git-history and safety policy in `AGENTS.md`.
