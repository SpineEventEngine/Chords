---
description: >
  Fix English grammar, punctuation, and spelling errors in project-owned
  Chords comments and documentation.
argument-hint: "[all | <path>]"
allowed-tools: >-
  Read, Edit, Grep, Glob, Bash(git diff:*), Bash(git ls-files:*),
  Bash(git status:*), Bash(git rev-parse:*)
model: sonnet
---

Follow the [proofread skill](../../.agents/skills/proofread/SKILL.md) exactly,
passing `$ARGUMENTS` as its argument; empty input selects branch-diff mode.
