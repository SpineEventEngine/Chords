# Pair Agents Run Guide

Hand a GitHub issue to two AI agents. One plans and implements it, the other
reviews — the plan before any code is written, and the diff afterward. You get
back an uncommitted worktree and a record of what they agreed and disagreed on.

The value is the second opinion: a different model checks the work against
criteria neither agent wrote.

## When to Use It

**Good fits:** a bug with a clear reproduction, or a scoped feature in one or
two modules — anything where you want a review before you spend your own time
reading a diff.

**Poor fits:** an issue that is really a question or a discussion; anything
touching publishing credentials, workflow secrets, or the `config` submodule;
changes you would not let an agent make unattended, since that is what happens
(see [Safety](#safety)).

## Before You Start

Claude Code open in this repository, and four things on your `PATH`: `claude`,
`codex`, `gh` (run `gh auth status`), and `jq`. If one is missing, the run says
so and stops before doing anything.

Being on `PATH` is not the same as being signed in, and the driver only checks
the former. `claude` and `codex` each hold their own credentials, so either can
be authenticated while the other is not. An unauthenticated CLI produces a turn
that dies immediately having written nothing — `Not logged in · Please run
/login` is the whole transcript. Nothing is lost when that happens: fix the
sign-in and run the same command again, and it resumes from the turn that
failed.

And an issue that makes two things clear:

- **What to do, or what is wrong** — the functionality to add, or the
  misbehavior, concrete enough to act on.
- **Acceptance criteria** — checkable conditions that settle when it is done.

Headings do not matter; nothing looks for particular section names. This is the
one thing worth getting right, because the reviewer checks the work against
these criteria. If they are missing, the run stops on the first turn and tells
you what to add rather than inventing them.

A feature issue can be this short:

```markdown
Add a copy-to-clipboard button to the validation error panel, so a user can
paste the full message into a bug report.

Done when:
- Each error entry has a copy control that copies that entry's full text.
- The control is reachable by keyboard.
- A test covers the copied text matching the displayed message.
```

## Run It

In Claude Code, give it an issue number:

```
/pair 150
```

That is the whole interface. Claude sets the run up, drives it through plan,
review, implementation, and review, and stays between you and it: when the
agents have a question it asks you here, writes your answer back for them, and
carries on. When the run finishes it reports what happened. You never open the
working file.

An issue URL works in place of the number.

What you will hear back, in one of four shapes:

- **Done.** Automated tests cover every acceptance criterion. Read the diff,
  then commit.
- **Done, but it needs manual testing.** Claude gives you the plan. Work through
  it, then commit.
- **It has a question, or it is stuck.** Answer in the conversation, or decide.
- **Something went wrong.** Claude reports the error and what to do.

Nothing is ever committed for you unless you ask — see
[Opening a Pull Request](#opening-a-pull-request).

### Walking away

```
/pair 150 --ad
```

Stops the run pausing on questions: the planner takes the default it would have
proposed and carries on, recording each assumption for the reviewer to check.
Use it when nobody is watching. `--accept-defaults` is the same flag spelled
out.

### Without Claude Code

The command is a wrapper. The workflow itself is a shell script you can run
from any terminal, with the same arguments:

```bash
.agents/workflows/pair.sh 150
```

You then read its output yourself, and **run the same command again** whenever
something stops it — that is always the next step, and it resumes wherever it
left off. Exit codes are in [Reference](#reference).

## When It Needs You

Two things can interrupt a run, and Claude brings both to you here.

**Questions.** The planner hit something that changes what it builds and asked
rather than guessed — for example, *"Should the observation retry on failure?"*
Claude puts each question to you as a choice, with the agent's own proposal
marked as the default. It asks one question per message, records that answer,
then asks the next. Once all recorded questions have answers, the run continues
on its own.

**Blocked.** The task cannot proceed as written: the issue is unusable, the two
agents did not converge within the review rounds, or a call is genuinely yours.
Claude explains which and why. Usually you improve the issue and start again.

Running the script directly, both look the same but land in your terminal: the
questions print, and you write an `**A1.**` line under each one in the document
it names, then run the command again. Answering *is* the whole action — leave
one unanswered and it stops again and says which.

## Opening a Pull Request

By default the run leaves the work uncommitted for you to review. Add `--cp`
and it publishes when it finishes:

```
/pair 150 --cp
```

Branch, commit, push, and a **draft** PR assigned to you, with `Fixes #150` in
the description. Claude confirms with you before starting a run that will
publish, and reports the PR URL at the end.

The agents still never touch Git — the driver does this afterwards, once the
run has actually finished. A run that stopped for you or aborted publishes
nothing.

Two conditions, both checked rather than assumed:

- **Your worktree must be clean when the run starts.** Otherwise the commit
  would sweep up whatever you had in progress — and the reviewer would judge
  your unrelated edits against the issue. Any run refuses to start on a dirty
  worktree; `--allow-dirty` overrides that, at the cost of both, and disables
  publishing for the run.
- **You must be on `master` or on the task's own branch.** On any other branch
  it stops rather than committing somewhere you did not intend.

`--create-pr` is the same flag spelled out. The PR is a draft on purpose: read
the diff before marking it ready.

## When It Finishes

Without `--cp`, nothing was committed — the agents are not allowed near Git, so
what you have is an uncommitted worktree.

1. Read the summary of what shipped and what was rejected — Claude reports it,
   and `## Outcome` in the document holds the same thing.
2. Read the diff.
3. If manual testing was called for, work through the plan. Each step names the
   acceptance criterion it covers.
4. Commit, following [`AGENTS.md`](AGENTS.md).

Worth a look when something seems off: `## Implementation Dispositions` records
every review finding and whether the implementer accepted or rejected it, with
reasons. That is where the two agents actually disagreed.

## Safety

The default commands retain their CLI safety boundaries. Claude runs in
`acceptEdits` mode with project settings only, and Codex runs in its
`workspace-write` sandbox without loading user configuration. A non-interactive
turn stops if it needs an approval those modes cannot grant. Read the diff
before you commit even in this mode.

If you supply an agent command that contains a known approval or sandbox bypass,
the driver refuses it unless `--allow-unsafe-agents` is present. That override
is only for an externally isolated, credential-free environment; the driver
does not create that environment for you.

**The issue body is untrusted input.** Anyone who can file an issue can put
text in it. The skill tells both agents to treat `## Issue` as task data rather
than instructions, and to stop and ask if it contains directives. Keep the CLI
boundaries enabled; if you explicitly remove them, run only somewhere
disposable that holds no credentials.

**They are told not to touch Git, and the run checks afterwards.** No branches,
commits, pushes, or pull requests; every ref and the index are compared after
each turn and the run aborts if anything moved. Be clear about what that is:
a tripwire, not a barrier. It runs after the fact, a change that is undone
again passes it, and effects outside this repository leave no local trace.
It tells you when the rule was broken; it cannot stop the breaking.

It also cannot tell who did it. The check compares the repository before and
after a turn, and switching branches or committing in another window while a
run is live produces exactly the diff an offending agent would. So do neither
during a run — and if the guard trips and the diff is your own doing, that is
all it is: start the run again and it resumes from the turn that was cut off.

`--cp` does not loosen any of this. That flag lets the *driver* publish once
the agents have finished.

## How to Change Models and Efforts for Agents

Both sides run a pinned model at high effort: **Claude Opus 5** plans and
implements, **GPT-5.6 Sol** reviews. They are pinned rather than left to each
CLI's default because the point of the workflow is that a particular second
model checked the work — a default that shifts under you quietly changes what
the review was worth.

Each agent is a whole command line, held in an environment variable. To see the
current ones:

```bash
.agents/workflows/pair.sh
```

**Copy one of those and edit it — do not write a command from scratch.** The
variable replaces the entire default, so anything you leave out is gone: drop
`--ignore-user-config` and your personal Codex config silently comes back; drop
`--permission-mode` and Claude's safety boundary changes; drop `--add-dir` and
the reviewer can no longer write the document the whole workflow runs on. Keep
every flag you are not deliberately changing.

Set it for one run:

```bash
AGENT1_CMD="claude -p --permission-mode acceptEdits --setting-sources project --model sonnet --effort medium" \
  .agents/workflows/pair.sh 150
```

Export the same line from your shell profile to make it permanent. Through
`/pair`, just say which model or effort you want in the message — a slash
command cannot carry an environment prefix.

Swapping the two variables swaps the roles, so the reviewer becomes the planner.

### Claude Code — the planner and implementer

Set with `AGENT1_CMD`, using `--model` and `--effort`.

- `--model` takes an alias for the current model in a family — `opus`,
  `sonnet`, `haiku`, `fable` — or a full identifier such as `claude-opus-5`.
  An alias follows the latest release; a full identifier stays put. Prefer the
  full identifier when you want two runs months apart to be comparable.
- `--effort` takes `low`, `medium`, `high`, `xhigh`, or `max`.

Values are checked locally. A wrong effort prints a warning that lists the valid
values and falls back to the default, so a typo costs you nothing.

`--setting-sources project` is what lets the implementer verify its own work.
It loads `.claude/settings.json` and nothing else — not your personal
settings, and not `.claude/settings.local.json`. The Gradle and `java` commands
[`AGENTS.md`](AGENTS.md) prescribes are allowed there for exactly this reason.
A command missing from that file is refused before it starts, and the run
continues to a review of code that was never compiled. If you add a
verification command the workflow should be able to run, add it there rather
than to your local settings.

### Codex — the reviewer

Set with `AGENT2_CMD`, using `-m` for the model and `-c key="value"` for the
rest.

- `-m` takes a model identifier, for example `gpt-5.6-sol`.
- `-c model_reasoning_effort=` takes `minimal`, `low`, `medium`, or `high`.
- `-c service_tier=` takes `default` for standard speed.

These are passed as flags rather than read from `~/.codex/config.toml`, because
the workflow runs Codex with `--ignore-user-config` so a review does not change
with local configuration.

`--add-dir` is not optional. Codex's sandbox refuses to write gitignored paths,
and `.agents/work/` — where the working document lives — is gitignored on
purpose, because the document is scratch and is never committed. Without that
flag the reviewer reads the plan, forms its findings, and then cannot write
them down; the run ends with `agent2 did not modify … plan.md`.

**Codex does not check these values locally.** An unrecognised effort is
accepted, echoed in the run header, and then rejected by the API — so a typo
surfaces as a failed reviewer turn rather than as a configuration error. If a
first reviewer turn dies for no obvious reason, check the `reasoning effort`
line in `.agents/work/issue-<number>/turns/02-agent2.log`.

### One thing to know

The working document records only which CLI ran, not which model or effort. A
run at `minimal` and a run at `high` leave artifacts that look identical
afterwards, so note it yourself if you are comparing runs.

## Reference

Everything for a task lives in `.agents/work/issue-150/` (gitignored):
`plan.md` is the shared document, `turns/*.log` the transcript of each turn.
The document is what an agent chose to write down; the transcripts are what it
actually did.

- `/pair <issue>` — the normal entry point. Claude Code relays questions and
  results in the conversation.
- `pair.sh <issue>` — the same run from a terminal. It sets up on the first call
  and resumes on later calls.
- `pair.sh status <issue>` — report current state; safe during a run.
- `pair.sh step <issue>` — take one turn, then stop.
- `pair.sh start <issue>` — set up without running.

Exit codes, for scripting `pair.sh run`: `0` done · `1` aborted · `2` done but
needs manual testing · `3` stopped for you. `step` uses the same codes, except
that `0` there means "the turn was taken", which may or may not have finished
the task — check `status` if you are scripting around it.

- `--ad`, `--accept-defaults` (`run`, `step`) — take proposed defaults instead
  of asking.
- `--mr`, `--max-rounds N` (`run`, `start`) — allow `N` review rounds in each
  phase, and therefore at most `N - 1` send-backs. The default is `2`.
- `--cp`, `--create-pr` (`run`) — branch, commit, push, and open a draft PR
  after a finished run. It is off by default.
- `--allow-dirty` (`run`, `start`) — include existing worktree changes in the
  review scope. Publication is refused.
- `--allow-unsafe-agents` (`run`, `step`) — permit configured commands that
  bypass approvals or sandboxing. External isolation is required.

To change models or efforts, swap which agent does what, or narrow an agent's
permissions, set `AGENT1_CMD` and `AGENT2_CMD` — see
[How to Change Models and Efforts for Agents](#how-to-change-models-and-efforts-for-agents).

The protocol the agents follow is
[`.agents/skills/pair-workflow/SKILL.md`](.agents/skills/pair-workflow/SKILL.md).
