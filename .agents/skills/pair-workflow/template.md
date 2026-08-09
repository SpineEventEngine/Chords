---
status: plan-requested
turn: agent1
plan_round: 1
impl_round: 1
max_rounds: 2
dirty_at_start: no
resume_status: none
question_origin: none
manual_testing: unknown
agent1: AGENT1_NAME
agent2: AGENT2_NAME
claude_model: CLAUDE_MODEL
claude_effort: CLAUDE_EFFORT
codex_model: CODEX_MODEL
codex_effort: CODEX_EFFORT
issue: ISSUE_URL
issue_number: ISSUE_NUMBER
issue_title: ISSUE_TITLE
base_commit: BASE_COMMIT
base_branch: BASE_BRANCH
start_commit: START_COMMIT
pr_base_branch: PR_BASE_BRANCH
pr_base_tip: PR_BASE_TIP
task_branch: TASK_BRANCH
github_repo: GITHUB_REPO
origin_fetch_url: ORIGIN_FETCH_URL
origin_push_url: ORIGIN_PUSH_URL
git_config_state: GIT_CONFIG_STATE
publication_head: none
expected_git_state: EXPECTED_GIT_STATE
expected_worktree_state: EXPECTED_WORKTREE_STATE
changeset_digest: none
reviewed_changeset_digest: none
updated: CREATED_AT
---

# TASK_SLUG — ISSUE_TITLE

## Issue

Copied from [#ISSUE_NUMBER](ISSUE_URL) when the workflow started. This is the
task specification. Neither agent edits it, and neither re-reads the issue from
GitHub; if the issue changes materially, start a new run.

ISSUE_BODY

## Task

<!-- agent1, first turn, immutable afterwards.

     Restate the work in one or two sentences — what will exist or behave
     differently once this is done — then list the acceptance criteria as a
     checklist, quoting the issue rather than inventing them.

     If the issue does not make clear what to do or what is wrong, or states no
     acceptance criteria, do not guess: set status to `blocked`, set turn to
     `human`, and say exactly what the issue needs. -->

## Questions

<!-- agent1 asks, the user answers, nobody else edits.

     Ask only what genuinely changes the work, and propose a default for each.
     The operator relays multiple questions to the user one message at a time:

     **Q1.** Should the copy control appear per entry or once for the panel?
     Default if unanswered: per entry, matching the existing row actions.

     The user replies by adding an **A1.** line under the question. Then:
     re-runs the same command. -->

## Plan

<!-- agent1 -->

## Plan Review — Round 1

<!-- agent2: findings P1-01, P1-02, … under Must fix / Should fix / Nits,
     ending with a verdict. -->

## Plan Dispositions — Round 1

<!-- agent1: one row per finding ID.

| ID | Disposition | Notes |
|----|-------------|-------|

     Then either revise the plan and send it back for another round, or move on
     to implementing. Add new `— Round N` sections rather than overwriting. -->

## Implementation — Round 1

<!-- agent1: files changed and why, deviations from the plan, verification
     commands run, and their results. -->

## Implementation Review — Round 1

<!-- agent2: findings I1-01, I1-02, … and a verdict. Check the change against
     the acceptance criteria in ## Task. -->

## Implementation Dispositions — Round 1

<!-- agent1: one row per finding ID. -->

## Outcome

<!-- agent1: which acceptance criteria are met, what was rejected and why,
     final verification. -->

## Pull Request

<!-- agent1, final turn, only when the prompt says a pull request will be
     opened. Follow "Creating a Pull Request" in AGENTS.md: no verification or
     testing detail, and no agent attribution. The driver appends the closing
     keyword.

     Write the two required sections as `### Summary` and `### Changes` so they
     nest here; at `##` they would end this section instead. The driver
     promotes them to `##` in the published description. -->

## Manual Testing

<!-- agent1, final turn. Set `manual_testing` in the frontmatter to `required`
     or `none`, and fill this in whenever it is `required`.

     This is printed to the terminal when the run finishes, so use this exact
     structure for someone who has not read the rest of this document:

     ### Setup

     What to build or launch and any required environment.

     ### Steps

     1. A concrete action.
        Expected: A result concrete enough to be wrong.
        Covers: The acceptance criterion this verifies.

     Repeat the three-line numbered-step shape for every criterion that
     automated tests do not settle. -->

## Log

<!-- Append one line per turn:
     <timestamp> <role> <from-status> -> <to-status>: <summary> -->
