---
description: >
  Run a GitHub issue through the two-agent pair workflow, relaying its
  questions, confirmations, and results here in the conversation.
argument-hint: >-
  <issue> [--ad] [--mr N] [--cp] [--sa] [--allow-dirty]
  [--claude-model MODEL] [--claude-effort LEVEL] [--codex-model MODEL]
  [--codex-effort LEVEL] [--allow-unsafe-agents] [--max-turns N]
allowed-tools: >-
  Read, Edit, AskUserQuestion, Bash(.agents/workflows/pair.sh:*),
  Bash(git status:*), Bash(git diff:*), Bash(git log:*),
  Bash(gh issue view:*), Bash(gh pr view:*)
---

Operate `.agents/workflows/pair.sh` on the user's behalf for `$ARGUMENTS` and
be their interface to it: the run is unattended, so you are the only thing
standing between it and a user who has to go read a Markdown file to find out
what happened.

You are the operator, not a participant. Do not edit source files, do not
review the agents' work, and do not commit — the `## Git Is Off Limits` rule in
[the skill](../../.agents/skills/pair-workflow/SKILL.md) binds you here too.
Your one exception is writing the user's answers into `## Questions`.

## Start

1. Read the issue number or URL from `$ARGUMENTS`. Without one, ask for it and stop.
2. If `--cp` or `--create-pr` is present, say what it will do when the run
   finishes — new branch, commit, push, draft PR against `master` — and get a
   yes before starting. The flag is their instruction, but publishing is worth
   one confirmation while they are still at the keyboard.
3. If `--sa` or `--swap-agents` is present, recommend the default seating —
   Claude implements, Codex reviews — then confirm the swap. A Codex
   implementer gains shared Gradle-home writes and sandbox network access;
   prompt injection could alter dependencies or exfiltrate readable data.
   Git and review guards do not isolate it.
4. If `--allow-unsafe-agents` is present, explain that an agent command removes
   its CLI approval or sandbox boundary. Confirm that the run is inside an
   externally isolated environment with no host mounts or unrelated
   credentials; otherwise stop.
5. Start the run in the background, passing `$ARGUMENTS` through unchanged:

   ```bash
   .agents/workflows/pair.sh $ARGUMENTS
   ```

   Background it because a turn can run for many minutes. Tell the user it has
   started and what you will do when it stops. Do not poll it — you are
   notified when it exits. If they ask meanwhile, run
   `.agents/workflows/pair.sh status <issue>`.

## When It Stops

The exit code says what happened. The working document is
`.agents/work/issue-<number>/plan.md` for that task.

**`0` — done, automated tests cover it.** Report `## Outcome` in your own
words, list the changed files (`git status --short`), and state plainly that
nothing is committed. Offer to walk the diff.

**`2` — done, needs manual testing.** Same as above, then reproduce
`## Manual Testing` in full. Do not summarise it away: it is the only thing
standing between "the agents finished" and "this actually works". Say clearly
that the change is unverified until they run it.

**`3` — it needs the user.** Read the document to tell which:

- *Questions.* Read `## Questions`. Put the first unanswered question to the
  user with AskUserQuestion — one option per answer the agent proposed, with
  its own default first and labelled as the default. After the user answers,
  write its `**A<n>.**` line directly under that question, changing nothing
  else. Then ask the next unanswered question in a new message. Do not restart
  the driver until every question has an answer. Never invent an answer, answer
  on the user's behalf, or bundle multiple questions into one message. Restart
  with the original `$ARGUMENTS` so options such as `--sa` remain in effect,
  but remove `--mr N` or `--max-rounds N`: the review limit is already stored,
  and the driver accepts that option only while creating the task.
- *Blocked.* Explain what blocked it and why, in your own words. If it is an
  unusable issue, say exactly what the issue is missing and offer to draft
  that text — but do not edit the issue yourself. If the two agents failed to
  converge, show both positions and ask how to proceed.

**`1` — aborted.** Report the driver's message, say what it means, and propose
the fix. The Git guard tripping is worth flagging loudly: Git state changed
during an agent turn, and the repository should be inspected before
continuing. The driver cannot distinguish an agent write from a user's
simultaneous Git operation.

## Throughout

- Report outcomes faithfully. If verification failed, say so and quote it; if
  the agents disagreed, say so. Never soften a bad result into a good one.
- Keep the user's decisions theirs. Questions, blocks, and whether to commit
  are all their calls; you carry the information, not the judgment.
- The document records what an agent chose to write down. The transcripts in
  `.agents/work/issue-<number>/turns/` record what it actually did — read those
  when the two disagree, or when a result looks too clean.
- When the run publishes a PR, report its URL.
