# Pair Agents Run Guide

Hand a GitHub issue to two AI agents. One plans and implements it; the other
reviews — the plan before any code is written, and the diff afterward. You get
back an uncommitted worktree and a record of what they agreed and disagreed on.

The value is the second opinion: a different model checks the work against
criteria neither agent wrote.

## When to Use It

**Good fits:** a bug with a clear reproduction, or a scoped feature in one or
two modules — anything where you want a review before you spend your own time
reading a diff.

**Poor fits:** an issue that is really a question or a discussion; anything
touching production deployment, infrastructure, credentials, or workflow
secrets; changes you would not let an agent make unattended, since that is
what happens (see [Safety](#safety)).

## Before You Start

### Required Tools

Run from the repository root with these tools installed and on `PATH`:

| Tool | Homebrew install | Check | Sign in |
|------|------------------|-------|---------|
| `git` | `brew install git` | `git --version` | — |
| `claude` | `brew install --cask claude-code` | `claude auth status` | `claude auth login` |
| `codex` | `brew install --cask codex` | `codex login status` | `codex login` |
| `gh` | `brew install gh` | `gh auth status` | `gh auth login` |
| `jq` | `brew install jq` | `jq --version` | — |

Claude Code requires Anthropic authentication, Codex CLI requires OpenAI
authentication, and GitHub CLI must have access to this repository.

If a check fails, install or sign in to that tool, then run the workflow command
again. An existing run resumes from the failed turn.

### Prepare the GitHub Issue

The GitHub issue must make two things clear:

- **What to do, or what is wrong** — the functionality to add, or the
  misbehavior, concrete enough to act on.
- **Acceptance criteria** — checkable conditions that settle when it is done.

Headings do not matter. If either part is missing, the first turn stops and
tells you what to add.

## Run It with `/pair`

In Claude Code, give it an issue number:

```
/pair 150
```

An issue URL works too. Claude Code operates the workflow, relaying questions
and the final result in the conversation.

### Common Options

| Short form | Full form | Description |
|------------|-----------|-------------|
| `--ad` | `--accept-defaults` | Record and use planner defaults instead of asking questions. |
| `--mr N` | `--max-rounds N` | Set the review limit per phase for a new task; default: `2`. |
| `--cp` | `--create-pr` | Publish a finished run as a draft PR. |
| `--sa` | `--swap-agents` | Swap the implementer and reviewer. |
| — | `--claude-model MODEL` | Select the Claude Code model. |
| — | `--claude-effort LEVEL` | Select the Claude Code effort level. |
| — | `--codex-model MODEL` | Select the Codex model. |
| — | `--codex-effort LEVEL` | Select the Codex reasoning effort. |

Possible results:

- **Done.** Automated tests cover every acceptance criterion. Read the diff,
  then commit.
- **Done, but it needs manual testing.** Claude gives you the plan. Work through
  it, then commit.
- **Question or blocked.** Answer a question in the conversation; a blocked
  run needs manual direction.
- **Something went wrong.** Claude reports the error and what to do.

Nothing is ever committed for you unless you ask — see
[Opening a Pull Request](#opening-a-pull-request).

### Walking Away

```
/pair 150 --ad
```

The planner takes its proposed defaults instead of pausing for answers and
records every assumption for review. `--accept-defaults` is the long form.

### Swap the Agents

The default assignments are:

- `agent1`: Claude, the planner and implementer.
- `agent2`: Codex, the reviewer.

Reverse them with:

```
/pair 150 --sa
```

Codex becomes the planner and implementer; Claude becomes the reviewer.
`--swap-agents` is the long form.

### Choose Models and Effort

The defaults are Claude Opus 5 and GPT-5.6 Sol, both at high effort. Override
one or both engines when starting a task:

```
/pair 150 \
  --claude-model opus --claude-effort xhigh \
  --codex-model gpt-5.6-sol --codex-effort high
```

The driver records all four settings with the task and restores them on
resume. Start a new task to use different settings. `--swap-agents` changes
the engines' roles, not which settings belong to Claude and Codex.

#### Possible Values

- `--claude-model`: `default`, `best`, `opus`, `sonnet`, `haiku`, `opusplan`,
  a supported `[1m]` variant such as `opus[1m]`, or a full model or provider
  name accepted by Claude Code. See
  [Claude Code model configuration](https://code.claude.com/docs/en/model-config).
- `--claude-effort`: `low`, `medium`, `high`, `xhigh`, or `max`. Support varies
  by model; Claude Code may use the nearest supported level.
- `--codex-model`: `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, or another
  model ID available to the installed Codex CLI and its provider. See
  [Codex model selection](https://learn.chatgpt.com/docs/models).
- `--codex-effort`: `minimal`, `low`, `medium`, `high`, or `xhigh`. The pair
  driver configures Codex's `model_reasoning_effort`, whose accepted values do
  not include the app's Max or Ultra modes.

Model availability depends on the installed CLI, account, and provider. The
driver rejects malformed names and unsupported effort values before setup; an
unavailable model is reported by its CLI at the first turn.

## When It Needs You

- **Question:** Claude asks one question at a time and marks the agent's
  proposed default. The run continues after all answers are recorded.
- **Blocked:** the issue is unusable, the agents did not converge, or a human
  decision is required. Claude explains the blocker; the workflow does not
  resume automatically.

## Opening a Pull Request

By default the run leaves the work uncommitted for you to review. Add `--cp`
and it publishes when it finishes:

```
/pair 150 --cp
```

After the reviews finish, the driver creates a branch, commits, pushes, and
opens a **draft** PR assigned to you with `Fixes #150`. The agents never perform
Git writes themselves. An interruption before publication creates no Git
history; if publication fails partway through, the driver reports what
succeeded and a rerun resumes from there.

The task branch starts at the exact commit where the run began and targets
`master` by default. If the run started on a branch with commits not yet in
`master`, the driver reports that inherited history during setup, excludes it
from the agents' implementation review, and adds a `## Reviewer notes` section
for the human reviewer. Moving, renaming, or deleting the starting branch does
not change the review boundary.

`--create-pr` is the long form. Read the diff before marking the PR ready.

## When It Finishes

Claude reports the final outcome in the conversation and explains what, if
anything, still needs your attention. It also summarizes meaningful
disagreements between the agents and how they were resolved, so normal `/pair`
use does not require reading the workflow's internal documents.

Without `--cp`, the changes remain uncommitted for you to review. With `--cp`,
Claude gives you the draft PR link instead.

1. Read Claude's outcome summary and inspect the diff.
2. If manual testing is required, follow the steps Claude provides. Each step
   identifies the acceptance criterion it covers.
3. When you are satisfied with the result, commit the local changes or review
   the draft PR before marking it ready.

## Safety

Claude runs in `acceptEdits` mode with project settings; Codex uses its
`workspace-write` sandbox without user configuration. When `--sa` moves Codex
into the implementer seat, the driver additionally grants that sandbox the
Gradle user home and network access, and says so at startup: the root build
locks inside the Gradle home and runs in a forked daemon that binds a local
port, so it cannot start without both. The reviewer's sandbox is never widened.
The agent instructions treat issue text as untrusted task data rather than as
instructions. The driver checks that:

- agents do not change Git refs or the index;
- the reviewer does not change source files or saved review snapshots;
- completed document sections and log history remain unchanged; and
- every review has a verdict and every finding has one valid disposition.

The Git check is a tripwire, not a sandbox: it detects changes after a turn and
cannot identify who made them. Do not modify Git state in another window while
a run is active.

`--cp` changes only the driver's final publication step; it does not loosen
agent permissions or review checks.

## Advanced Mode: Direct Script Usage

Most users can stop here. Use the workflow driver directly only when Claude
Code is unavailable, you need one-step or status commands for automation, or
you need to customize agent commands, models, or publication settings.

Run advanced commands from the repository root.

### Run Directly

Start or resume the same workflow from a terminal:

```bash
.agents/workflows/pair.sh 150
```

If it stops, follow the message and run the command again. Keep saved options
such as `--sa`, but omit `--mr` or `--max-rounds`: the review limit is recorded
at setup, and those forms are accepted only when creating a task.

When the script stops for questions, add an `**A<n>.**` line under each
question in `.agents/work/issue-150/plan.md`, using the matching question
number, then run the command again.

### Script Commands

- `.agents/workflows/pair.sh <issue>` or
  `.agents/workflows/pair.sh run <issue>` — set up on the first call and resume
  on later calls.
- `.agents/workflows/pair.sh status <issue>` — report the current state; safe
  during a run.
- `.agents/workflows/pair.sh step <issue>` — attempt one workflow step, then
  stop.
- `.agents/workflows/pair.sh start <issue>` — set up without running.

### Script Options

- `--ad`, `--accept-defaults` (`run`, `step`) — take proposed defaults instead
  of asking.
- `--mr`, `--max-rounds N` (`run`, `start`) — allow up to `N` reviews each of
  the plan and implementation. With the default `N=2`, each can be reviewed,
  revised once, and reviewed again. Use it only when creating the task.
- `--cp`, `--create-pr` (`run`) — branch, commit, push, and open a draft PR
  after a finished run. It is off by default.
- `--sa`, `--swap-agents` (`run`, `start`, `step`) — exchange the configured
  planner/implementer and reviewer. Repeat it when resuming the task.
- `--claude-model MODEL`, `--claude-effort LEVEL` (`run`, `start`, `step`) —
  select Claude Code's saved model and effort.
- `--codex-model MODEL`, `--codex-effort LEVEL` (`run`, `start`, `step`) —
  select Codex's saved model and reasoning effort.
- `--allow-dirty` (`run`, `start`) — include existing worktree changes when
  creating the task. It cannot be combined with `--create-pr`.
- `--allow-unsafe-agents` (`run`, `step`) — permit configured commands that
  bypass approvals or sandboxing. External isolation is required.
- `--max-turns N` (`run`) — override the derived loop guard. Normally leave it
  unset.
- `--slug <name>` (`start`) — use a working-directory name other than
  `issue-<number>`. Resume it with the explicit form
  `.agents/workflows/pair.sh run <name>`; the bare issue shortcut accepts
  GitHub issues only.

The driver rejects agent commands that bypass approvals or sandboxing unless
you pass `--allow-unsafe-agents`. Use that option only in a disposable
container or VM whose outer isolation replaces the CLI sandbox, with no host
mounts or unrelated credentials. It is not a shortcut for suppressing
approvals on a development workstation.

### Exit Codes

Exit codes for scripting `.agents/workflows/pair.sh run`:

| Code | Meaning |
|------|---------|
| `0` | Done |
| `1` | Aborted |
| `2` | Done, but needs manual testing |
| `3` | Stopped for you |

The `step` command uses the same codes, except that `0` means the step
completed without an error, not that the task is done. Check
`.agents/workflows/pair.sh status <issue>` when scripting around `step`.

### Publication Settings and Guards

For a repository whose pull requests target another branch, set
`PR_BASE_BRANCH` when creating the run. The driver records that value in the
working document; omitting or changing the environment variable on a later
invocation does not retarget the pull request.

Before the first agent turn of a publishing run, the driver requires the
remote-tracking PR target to exist and have a merge-base with `HEAD`. Before
its first Git write, it also requires:

- a clean worktree when the run starts (`--allow-dirty` cannot be combined
  with `--create-pr`);
- the changeset uncommitted, unless it is already on the task's own branch —
  work committed onto some other branch is left for you to move;
- an `origin/<recorded-target>` merge-base matching the recorded PR baseline;
- `HEAD` still at the recorded starting commit on the first publication
  attempt, or on the task branch for a retry;
- content, file types, and executable bits identical to the reviewed state;
- a `version.gradle.kts` increase made after the recorded starting commit,
  regenerated `pom.xml` and `dependencies.md` reports, and complete `Summary`
  and `Changes` sections; and
- an actionable plan when manual testing is required.

A target-branch update that changes the PR merge-base stops publication; the
run must be repeated against the new scope.

### Override Complete Agent Commands

Use the `/pair` model and effort options for ordinary selection. Override a
complete command only to change its executable or other CLI flags. Print both
defaults with:

```bash
.agents/workflows/pair.sh
```

Copy the complete command and change only the intended settings. Without
`--sa`, the variables map to these default roles:

| Default role | Variable | Model and effort flags |
|--------------|----------|------------------------|
| Planner and implementer | `AGENT1_CMD` | `--model`, `--effort` |
| Reviewer | `AGENT2_CMD` | `-m`, `-c model_reasoning_effort=` |

Set it for one run:

```bash
AGENT1_CMD='claude -p --permission-mode acceptEdits --setting-sources project '\
'--model sonnet --effort medium' \
  .agents/workflows/pair.sh 150
```

Export the variable to use it for the current shell session and child
processes. The slash command cannot set environment variables: export them
before starting Claude Code, or run the driver directly with the assignment as
shown above. To exchange the configured roles without rewriting the variables,
pass `--sa` or `--swap-agents`.

Keep all safety flags from the printed command. In particular:

- Claude needs `--setting-sources project` to load the verification allowlist
  from `.claude/settings.json`.
- Codex needs `--ignore-user-config` for reproducible settings and `--add-dir`
  to write the gitignored working document.

If a model or effort is rejected, read the relevant turn log under
`.agents/work/issue-<number>/turns/`. The document records settings that the
driver can read from direct Claude and Codex commands. An engine hidden by an
opaque wrapper is recorded as `(custom)`; an engine absent because both
commands directly identify the other engine is `(unconfigured)`. Note a
wrapper's hidden settings separately when comparing runs. The `/pair` model
options require a direct `claude` or `codex` command; configure a wrapper's
model internally instead.

### Working Files and Legacy Runs

Everything for a task lives in `.agents/work/issue-150/` (gitignored):
`plan.md` is the shared document, `turns/*.log` contains turn transcripts, and
`rounds/` holds the plan or changeset saved for each review. The document is
what an agent chose to write down; the transcripts are what it actually did.
For a detailed record of disagreements, read `## Plan Dispositions` and
`## Implementation Dispositions` in `plan.md`.

For working documents created by an older driver, the missing PR target is
backfilled as `master`, and missing question provenance is backfilled before
another turn. If the document is already waiting on a question, the driver
recovers the origin from its saved legacy resume status. A non-publishing run
may continue without the older starting-branch or model fields. New model
options cannot be added to such a run. Publication still requires the
starting-branch fields; if they are missing, continue without `--create-pr` or
start a replacement with
`.agents/workflows/pair.sh start <issue> --slug <new-name>`.

### Protocol Reference

The protocol the agents follow is
[`.agents/skills/pair-workflow/SKILL.md`](.agents/skills/pair-workflow/SKILL.md).
