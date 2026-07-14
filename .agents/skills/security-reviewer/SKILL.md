---
name: security-reviewer
description: >
  Chords security review guidance. Use for publishing-credential handling,
  GitHub Actions secrets, GPG-encrypted key files, dependency provenance,
  Gradle wrapper integrity, agent prompt/configuration safety under
  `.agents/**`, and accidental secret exposure in this public repository.
---

# Security Reviewer

Use this skill for security-focused review or implementation guidance. Chords
is a public open-source repository with no runtime backend of its own, so the
main risk surface is the supply chain — publishing credentials, CI workflows,
and dependencies — plus the agent configuration itself under `.agents/**`.

## Scope

- Publishing credentials: the GPG-encrypted key files under `.github/keys/`,
  the decryption scripts under `config/scripts/`, and their use in
  `.github/workflows/publish.yml`.
- GitHub Actions secrets and workflows that build, validate, or publish
  artifacts.
- Dependency provenance: coordinates and repositories declared in `buildSrc`
  and `codegen/plugins/buildSrc`; Gradle wrapper integrity (the
  `gradle-wrapper-validation` workflow).
- Accidental commits of credentials, tokens, private keys, or TeamDev-internal
  data into this public repository.
- Agent configuration files (`.agents/**`, `openai.yaml`, `AGENTS.md`): flag
  prompt-injection risks in `default_prompt` fields and skill instructions
  that could cause an agent to exfiltrate secrets or bypass safety rules.

## Rules

- Treat credential leakage as high severity: this repository is public, and
  anything committed is published.
- Do not print secret values, tokens, private keys, or decrypted credential
  payloads.
- Do not suggest storing secrets in source-controlled files; encrypted `.gpg`
  blobs under `.github/keys/` are the existing sanctioned mechanism, and
  changes to them are human-owned.
- Reference workflow secrets by name through `secrets.*`; never inline values.
- Do not run commands that publish artifacts, rotate credentials, or change
  GitHub secrets.
- Watch for dependency-confusion risk: new repositories or dependency
  coordinates must match the trusted Spine/TeamDev sources already configured
  in `buildSrc`.

## Review Focus

- Workflow changes that widen `on:` triggers, expose secrets to
  pull-request-controlled contexts, or add untrusted third-party actions.
- New or changed Maven repositories and dependency coordinates.
- Scripts that decrypt, copy, or upload credential material.
- Content that should not be public: internal hostnames, e-mail addresses of
  individuals, or internal project data in code, tests, or docs.
- Prompt-injection risks in `.agents/**`: overbroad `default_prompt` fields,
  skill instructions that could bypass the policy in `AGENTS.md`, and
  instructions that could cause an agent to exfiltrate secrets.

## Output Format

For reviews, lead with findings ordered by severity and include file/line
references. If no issues are found, state that clearly and mention any residual
risk or unverified external configuration.
