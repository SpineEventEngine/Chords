# GitHub Copilot Instructions

Read [`AGENTS.md`](../AGENTS.md), the [project map](../.agents/project.md), and
the nearest area README. Route task-specific policy through the
[skill index](../.agents/skills/README.md). `AGENTS.md` is authoritative.

## Implementation

Use the narrowest engineering skill and
[Design Restraint](../.agents/guidelines/design-restraint.md). For root Gradle,
follow the [root-build guideline](../.agents/guidelines/root-build.md).
`codegen/plugins` uses `.agents/workflows/gradle-codegen.sh` with JDK 17.

## Review

Apply [`code-reviewer`](../.agents/skills/code-reviewer/SKILL.md),
[`docs-reviewer`](../.agents/skills/docs-reviewer/SKILL.md), or
[`security-reviewer`](../.agents/skills/security-reviewer/SKILL.md) as needed.
Lead with severity-ordered file-and-line findings. Prioritize:

- correctness, regressions, missing tests, and external API breaks;
- Protobuf source, wire, and generated-code contracts;
- module boundaries, Compose state, build and release policy, and documentation;
- secrets, credentials, or dependent private-project information.

Skip `gradlew*`, `gradle/wrapper/**`, generated reports and outputs, `_out/`,
`config/`, and IDE metadata. Review both `buildSrc` trees and AI configuration.
Review is read-only unless checks are requested.
