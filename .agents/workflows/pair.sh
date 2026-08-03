#!/usr/bin/env bash
#
# Driver for the two-agent pair workflow.
#
# The agents do not watch the working document. This script reads its
# frontmatter, invokes whichever agent owns the turn, and repeats until the
# status reaches a terminal state. See
# `.agents/skills/pair-workflow/SKILL.md` for the protocol.
#
# The agent commands are overridable; the prompt is appended as the final
# argument. The defaults permit workspace edits without removing the CLIs'
# approval or sandbox boundaries. An explicitly unsafe override is available
# for callers that provide isolation outside this script.

set -euo pipefail

usage() {
    cat >&2 <<'USAGE'
Driver for the two-agent pair workflow.

A GitHub issue is the only input. It must describe what to do or what is wrong,
and state its acceptance criteria. The worktree must be clean at the start
unless --allow-dirty is given.

Usage:
  pair.sh <issue> [--ad] [--mr N] [--cp]

Runs the issue to completion: sets up on first call, resumes on later ones.
Run the same command again after anything stops it. <issue> is a number (123),
#123, or a full issue URL.

  --ad, --accept-defaults   do not stop to ask questions; take the proposed
                            default and record it, for a run nobody is watching
  --mr, --max-rounds N      review rounds allowed in each phase; N rounds
                            permit at most N - 1 send-backs (default 2)
  --allow-dirty             start even though the worktree has uncommitted
                            changes. They land in the reviewer's scope and are
                            reviewed as if the agents wrote them, and PR
                            publication is refused for the run.
  --allow-unsafe-agents     permit an AGENT1_CMD or AGENT2_CMD that disables
                            approvals or sandboxing. Use only inside an
                            externally isolated, credential-free environment.
  --cp, --create-pr         when the run finishes, branch, commit, push, and
                            open a draft PR. Off by default: the normal result
                            is an uncommitted worktree you review yourself.
                            The agents never touch Git either way — the driver
                            does this afterwards, and only on a finished run
                            from a worktree that was clean at the start.

Less often:
  pair.sh step   <issue>   take exactly one turn and stop
  pair.sh status <issue>   print the current state, safe during a run
  pair.sh start  <issue>   set up without running

  --slug <name>       name the working directory something other than
                      issue-<number> (start only)
  --max-turns N       loop guard: abort after N turns without reaching a
                      terminal state. Defaults to whatever the round limit
                      needs (4 x max-rounds + 2, at least 12). Insurance
                      against a bug in this script, not a tuning knob.

Requires `gh` (authenticated) and `jq`.

Environment:
  AGENT1_CMD   planner/implementer
               (default: claude -p --permission-mode acceptEdits
                         --setting-sources project
                         --model claude-opus-5 --effort high)
  AGENT2_CMD   reviewer
               (default: codex exec --sandbox workspace-write
                         --add-dir <repo>/.agents/work --ephemeral
                         --ignore-user-config -m gpt-5.6-sol
                         -c model_reasoning_effort="high"
                         -c service_tier="default")

Model and effort are pinned so a review is reproducible. Codex's are passed as
flags because --ignore-user-config discards ~/.codex/config.toml by design.
--add-dir is required because that sandbox refuses to write gitignored paths,
and the working document lives in one. Keep it when overriding AGENT2_CMD.

Exit codes (run):
  0   done — automated tests cover every acceptance criterion
  1   aborted: a guard tripped, or an agent failed
  2   done — the change needs manual testing; the plan is printed
  3   stopped for you: waiting on answers, or `blocked`

`step` uses the same codes, except that 0 means "a turn was taken" and does not
imply the task is finished. Use `status` to tell the difference.

The defaults keep the CLIs' safety boundaries. A non-interactive turn stops if
it needs an approval the selected mode cannot grant. Commands that contain a
known bypass flag are refused unless --allow-unsafe-agents is explicit.
USAGE
}

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly WORK_ROOT="${REPO_ROOT}/.agents/work"
readonly SKILL="${REPO_ROOT}/.agents/skills/pair-workflow/SKILL.md"
readonly TEMPLATE="${REPO_ROOT}/.agents/skills/pair-workflow/template.md"

# Models and effort are pinned rather than left to each CLI's default, so a
# review is reproducible and the two sides stay the models this workflow was
# tuned against. Codex's settings are passed explicitly because
# --ignore-user-config deliberately discards ~/.codex/config.toml — the run
# must not depend on local configuration that differs between machines.
#
# --add-dir names WORK_ROOT because Codex's workspace-write sandbox excludes
# gitignored paths from the writable set, and .agents/work/ is gitignored by
# design — the working document is a scratch artifact that is never committed.
# Without it agent2 can read the document but not write its review, and the
# turn ends with the driver aborting on an unmodified document. This widens
# the sandbox by exactly one directory inside the repository; it is not a
# bypass flag, and unsafe_agent_roles() does not treat it as one.
AGENT1_CMD="${AGENT1_CMD:-claude -p --permission-mode acceptEdits --setting-sources project --model claude-opus-5 --effort high}"
AGENT2_CMD="${AGENT2_CMD:-codex exec --sandbox workspace-write --add-dir ${WORK_ROOT} --ephemeral --ignore-user-config -m gpt-5.6-sol -c model_reasoning_effort=\"high\" -c service_tier=\"default\"}"

readonly DEFAULT_MAX_TURNS=12

# What the script exits with. 1 stays the error code so an unexpected shell
# failure lands there rather than being read as one of the outcomes below.
readonly EXIT_OK=0
readonly EXIT_ERROR=1
readonly EXIT_MANUAL=2
readonly EXIT_NEEDS_YOU=3

# What take_turn returns, kept above the external range so an internal signal
# can never escape as an exit code.
readonly TURN_ADVANCED=0
readonly TURN_DONE=10
readonly TURN_DONE_MANUAL=11
readonly TURN_NEEDS_YOU=12

# Set by --accept-defaults on run/step: agent1 proceeds on its own proposed
# defaults instead of stopping to ask.
ACCEPT_DEFAULTS=0

# Set when `run` creates the document itself, so `start` skips the hint that
# tells you to run the command you are already inside.
STARTED_FROM_RUN=0

# Set by --create-pr: after the run reaches `done`, the driver branches,
# commits, pushes, and opens a draft PR. The agents are never given Git access
# for this — the prohibition in the skill stays absolute, and this flag is the
# explicit authorization AGENTS.md requires for writing history.
CREATE_PR=0

# Set by --allow-dirty: start even though the worktree has uncommitted changes,
# accepting that they land in the review scope.
ALLOW_DIRTY=0

# Set only when the caller acknowledges that custom agent commands remove the
# CLIs' normal execution boundary.
ALLOW_UNSAFE_AGENTS=0

die() { printf 'pair: %s\n' "$1" >&2; exit 1; }
# Same message, but returns instead of exiting. Helpers that may be called from
# inside a command substitution must use this: there, `die` ends only the
# subshell, so validation would report a problem and let the run continue.
# Callers propagate with `|| exit "$EXIT_ERROR"`.
fail() { printf 'pair: %s\n' "$1" >&2; return 1; }
info() { printf 'pair: %s\n' "$1" >&2; }

doc_for() { printf '%s/%s/plan.md' "$WORK_ROOT" "$1"; }

# Reads one frontmatter key from the document's leading `---` block. Only that
# block is scanned, so a `status:` line quoted in the body cannot be mistaken
# for state.
frontmatter() {
    local doc="$1" key="$2"
    awk -v key="$key" '
        NR == 1 && $0 == "---" { inside = 1; next }
        inside && $0 == "---" { exit }
        inside && index($0, key ":") == 1 {
            sub(/^[^:]*:[[:space:]]*/, "")
            gsub(/^[\"'\'']|[\"'\'']$/, "")
            print
            exit
        }
    ' "$doc"
}

# Prints one Markdown section exactly, including comments. Fenced headings are
# content rather than section boundaries.
section_raw() {
    local doc="$1" name="$2"
    awk -v want="## $name" '
        function marker(line, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            if (text ~ /^```/) {
                match(text, /^`+/)
                return substr(text, RSTART, RLENGTH)
            }
            if (text ~ /^~~~/) {
                match(text, /^~+/)
                return substr(text, RSTART, RLENGTH)
            }
            return ""
        }
        function closes(line, mark, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            text = substr(text, length(mark) + 1)
            return text ~ /^[[:space:]]*$/
        }
        {
            mark = marker($0)
            if (mark != "") {
                if (fence == "") fence = mark
                else if (substr(fence, 1, 1) == substr(mark, 1, 1) &&
                         length(mark) >= length(fence) && closes($0, mark)) {
                    fence = ""
                }
            }
        }
        fence == "" && $0 == want { inside = 1; next }
        inside && fence == "" && /^## / { exit }
        !inside { next }
        { print }
    ' "$doc"
}

# Counts exact section headings outside fenced code so a duplicate protected
# section cannot create a second, unverified specification.
section_heading_count() {
    local doc="$1" name="$2"
    awk -v want="## $name" '
        function marker(line, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            if (text ~ /^```/) {
                match(text, /^`+/)
                return substr(text, RSTART, RLENGTH)
            }
            if (text ~ /^~~~/) {
                match(text, /^~+/)
                return substr(text, RSTART, RLENGTH)
            }
            return ""
        }
        function closes(line, mark, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            text = substr(text, length(mark) + 1)
            return text ~ /^[[:space:]]*$/
        }
        {
            mark = marker($0)
            if (mark != "") {
                if (fence == "") fence = mark
                else if (substr(fence, 1, 1) == substr(mark, 1, 1) &&
                         length(mark) >= length(fence) && closes($0, mark)) {
                    fence = ""
                }
            }
            if (fence == "" && $0 == want) count++
        }
        END { print count + 0 }
    ' "$doc"
}

# Prints a section for display, dropping template comments that are noise to a
# person while retaining the lossless parser for integrity checks.
section() {
    section_raw "$1" "$2" | awk '
        /<!--/ { incomment = 1 }
        incomment { if (/-->/) incomment = 0; next }
        { print }
    '
}

# Snapshot of everything the workflow forbids an agent from touching: the
# checked-out commit and branch, every local branch and tag, every
# remote-tracking ref, and the staged index. Remote-tracking refs are in here
# because `git push` moves them, which makes a push detectable locally — and a
# PR is detectable in turn, since it needs a push first.
git_state() {
    git -C "$REPO_ROOT" rev-parse HEAD
    git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD
    # Every ref, not selected namespaces: `refs/stash` belongs here too, or
    # `git stash` could move the user's work out of the tree and pass.
    git -C "$REPO_ROOT" for-each-ref --format='%(refname) %(objectname)'
    # Staged blob ids, modes, and index flags. `--stage` alone misses flags,
    # so `assume-unchanged` or `skip-worktree` could hide a file from status,
    # from review, and from later staging while passing this check.
    git -C "$REPO_ROOT" ls-files -v -s
}

require_doc() {
    local doc="$1"
    [[ -f "$doc" ]] || die "no working document at ${doc} — run 'pair.sh start' first"
}

# Frontmatter the driver owns. An agent that rewrote these could retarget the
# issue, move the review's diff baseline, or clear `dirty_at_start` and make a
# worktree that was already dirty publishable.
readonly IMMUTABLE_KEYS="issue issue_number issue_title base_commit dirty_at_start max_rounds"

immutable_snapshot() {
    local doc="$1" protect_task="$2" k
    for k in $IMMUTABLE_KEYS; do
        printf '%s=%s\n' "$k" "$(frontmatter "$doc" "$k")"
    done
    # The issue copy is the specification both agents are judged against. If an
    # agent could rewrite it, it could rewrite the acceptance criteria and then
    # satisfy them — exactly the property the second opinion exists to prevent.
    printf 'issue-headings=%s\n' "$(section_heading_count "$doc" "Issue")"
    printf 'issue-section=%s\n' "$(section_raw "$doc" "Issue" | cksum)"
    # ## Task is agent1's restatement, immutable after its first turn. Before
    # that turn it is still template scaffolding, so an empty digest is normal.
    printf 'task-headings=%s\n' "$(section_heading_count "$doc" "Task")"
    if [[ "$protect_task" -eq 1 ]]; then
        printf 'task-section=%s\n' "$(section_raw "$doc" "Task" | cksum)"
    fi
}

verify_immutable() {
    local doc="$1" before="$2" who="$3" protect_task="$4" now
    now="$(immutable_snapshot "$doc" "$protect_task")"
    [[ "$before" == "$now" ]] && return 0
    info "protected document state changed during ${who}'s turn (- before, + after):"
    diff <(printf '%s\n' "$before") <(printf '%s\n' "$now") >&2 || true
    die "${who} rewrote protected fields or sections; "\
"the document is left as written for you to inspect"
}

# A round counter must move by exactly the delta the transition calls for: 1 on
# the loopback that spends a round, 0 everywhere else. "Stay or increase" was
# too loose — a loopback that left the counter alone never reached the ceiling,
# so a disagreement ran until the turn guard cut it off instead of reaching a
# person.
round_delta_ok() {
    local from="$1" to="$2" delta="$3"
    [[ "$to" =~ ^[0-9]+$ ]] || return 1
    [[ "$to" -eq $(( from + delta )) ]]
}

# Rewrites frontmatter keys in place, as `key=value` pairs. Done with awk and a
# rename rather than `sed -i`, whose in-place flag differs between BSD and GNU:
# this script is documented as runnable from any terminal.
set_frontmatter() {
    local doc="$1"; shift
    local tmp; tmp="$(mktemp "${doc}.XXXXXX")"
    local pairs=""
    local pair
    for pair in "$@"; do
        pairs="${pairs}${pair}"$'\n'
    done
    PAIRS="$pairs" awk '
        BEGIN {
            n = split(ENVIRON["PAIRS"], lines, "\n")
            for (i = 1; i <= n; i++) {
                if (lines[i] == "") continue
                eq = index(lines[i], "=")
                want[substr(lines[i], 1, eq - 1)] = substr(lines[i], eq + 1)
            }
        }
        NR == 1 && $0 == "---" { inside = 1; print; next }
        inside && $0 == "---"  { inside = 0; print; next }
        inside {
            c = index($0, ":")
            if (c > 0) {
                k = substr($0, 1, c - 1)
                if (k in want) { print k ": " want[k]; next }
            }
        }
        { print }
    ' "$doc" > "$tmp" && mv "$tmp" "$doc" || { rm -f "$tmp"; die "could not update ${doc}"; }
}

# Two drivers on one slug would interleave turns and corrupt the document, and
# the failure would look like an agent misbehaving rather than a collision.
# `mkdir` is the atomic primitive here: it succeeds for exactly one caller.
LOCK_DIR=""
release_lock() {
    [[ -n "$LOCK_DIR" ]] && rmdir "$LOCK_DIR" 2>/dev/null
    return 0
}
acquire_lock() {
    [[ -z "$LOCK_DIR" ]] || return 0   # already held by an outer command
    local slug="$1" lock dir
    dir="$(dirname "$(doc_for "$slug")")"
    mkdir -p "$dir"
    lock="${dir}/.lock"
    mkdir "$lock" 2>/dev/null \
        || die "another pair.sh is already running for '${slug}' (delete ${lock} if it is stale)"
    LOCK_DIR="$lock"
    trap release_lock EXIT INT TERM
}

# Accepts 123, #123, or a full GitHub issue URL, and yields the bare number.
# Anything else is rejected here rather than being passed to `gh`, so the error
# names the real problem instead of surfacing a gh usage message.
issue_number_from() {
    local raw="${1#\#}"
    case "$raw" in
        *://*)
            # A URL carries a host and owner/repo that the number alone loses.
            # Resolve both from GitHub rather than silently treating any URL
            # ending in a number as an issue in the current repository.
            local remainder="${raw#*://}"
            local host="${remainder%%/*}"
            local path="${remainder#*/}"
            local want="${path%%/issues/*}"
            local here here_url here_remainder here_host
            here="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
            here_url="$(gh repo view --json url --jq .url 2>/dev/null || true)"
            [[ -n "$here" && "$here_url" == *://* ]] \
                || { fail "cannot resolve the current GitHub repository; "\
"pass an issue number or fix 'gh repo view'"; return 1; }
            here_remainder="${here_url#*://}"
            here_host="${here_remainder%%/*}"
            [[ -n "$host" && -n "$want" && "$path" == */issues/* ]] \
                || { fail "'$1' is not a supported GitHub issue URL"; return 1; }
            if [[ "$host" != "$here_host" || "$want" != "$here" ]]; then
                fail "that URL is for ${host}/${want}, but this repository is ${here_host}/${here}"; return 1
            fi
            raw="${path#*/issues/}"
            [[ "$raw" != */* && "$raw" != *\?* && "$raw" != *\#* ]] \
                || { fail "'$1' is not a supported GitHub issue URL"; return 1; }
            ;;
    esac
    [[ "$raw" =~ ^[0-9]+$ && "$raw" != 0 ]] \
        || { fail "'$1' is not a GitHub issue; pass an issue number or its URL"; return 1; }
    printf '%s' "$raw"
}

# Options that end up in arithmetic or in a path. Rejected at parse time so a
# bad value surfaces as a usage error rather than as a confusing ceiling
# message several turns later.
require_positive_int() {
    [[ "$2" =~ ^[0-9]+$ && "$2" -ge 1 ]] \
        || { fail "$1 needs a positive whole number, got '$2'"; return 1; }
}

require_safe_slug() {
    # The slug is joined onto WORK_ROOT, so `../` would place the working
    # document outside .agents/work entirely.
    [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] \
        || { fail "--slug must be a plain name (letters, digits, dot, dash, underscore), got '$1'"; return 1; }
}

# A task enters this workflow only as a GitHub issue, so the slug is derived
# from the issue number. Bare numbers are accepted everywhere a slug is, which
# keeps `run 123` working after `start 123`.
resolve_slug() {
    local arg="${1:-}"
    [[ -n "$arg" ]] || { fail "missing issue number or slug"; return 1; }
    case "$arg" in
        '#'*|*://*)
            local n
            n="$(issue_number_from "$arg")" || return 1
            printf 'issue-%s' "$n" ;;
        # A literal slug is joined onto WORK_ROOT and used for the lock path,
        # so it needs the same check `--slug` gets — `run`, `step`, and
        # `status` all reach this with user input.
        *[!0-9]*)         require_safe_slug "$arg" || return 1
                          printf '%s' "$arg" ;;
        *)                printf 'issue-%s' "$arg" ;;
    esac
}

cmd_start() {
    local issue_arg="${1:-}"; shift || true
    [[ -n "$issue_arg" ]] \
        || die "usage: pair.sh start <issue-number|issue-url> [--slug <name>] [--max-rounds N]"

    local slug="" max_rounds="2"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --slug)       slug="${2:-}"; require_safe_slug "$slug" || exit "$EXIT_ERROR"; shift 2 ;;
            --allow-dirty) ALLOW_DIRTY=1; shift ;;
            --max-rounds|--mr)
                max_rounds="${2:-}"
                require_positive_int --max-rounds "$max_rounds" || exit "$EXIT_ERROR"
                shift 2 ;;
            --task) die "a GitHub issue is the only input; pass an issue number or URL" ;;
            *) die "unknown option: $1" ;;
        esac
    done

    command -v gh >/dev/null 2>&1 \
        || die "'gh' is not on PATH; it is required to read the issue"
    command -v jq >/dev/null 2>&1 \
        || die "'jq' is not on PATH; it is required to parse the issue"

    local number; number="$(issue_number_from "$issue_arg")" || exit "$EXIT_ERROR"
    [[ -n "$slug" ]] || slug="issue-${number}"

    acquire_lock "$slug"

    local doc; doc="$(doc_for "$slug")"
    [[ -e "$doc" ]] && die "${doc} already exists; pass --slug or delete it"

    # Fetch once, here, so the document is self-contained. Every later turn is
    # a cold start and must not depend on the issue still being reachable or
    # unchanged.
    local issue_json
    issue_json="$(gh issue view "$number" --json number,title,body,url,state 2>/dev/null)" \
        || die "cannot read issue #${number}; check the number, the repository, "\
"and 'gh auth status'"

    local title url state body
    title="$(printf '%s' "$issue_json" | jq -r '.title')"
    url="$(printf '%s' "$issue_json" | jq -r '.url')"
    state="$(printf '%s' "$issue_json" | jq -r '.state')"
    body="$(printf '%s' "$issue_json" | jq -r '.body // ""')"

    [[ "$state" == "OPEN" ]] \
        || info "warning: issue #${number} is ${state}; continuing anyway"

    # The issue is the whole specification, so an empty or near-empty one has
    # nothing for agent1 to plan from and nothing for agent2 to review against.
    # Reject that here instead of spending two agent turns discovering it.
    [[ ${#body} -ge 80 ]] \
        || die "issue #${number} has no usable description (${#body} characters); "\
"describe what to do or what is wrong, and its acceptance criteria, then retry"

    # Whether the issue is actually usable is agent1's call — it can read prose,
    # and criteria are written in many shapes across bug and feature issues.
    # This is only a cheap heads-up for the obvious case, so it stays a warning
    # and never blocks a well-written issue that happens to phrase things
    # differently.
    local criteria_pattern
    criteria_pattern='acceptance criteria|definition of done|done when|'
    criteria_pattern+='expected (behaviou?r|result)|should (be able to|result in)'
    printf '%s' "$body" | grep -qiE "$criteria_pattern" \
        || info "warning: issue #${number} has no obvious acceptance criteria; "\
"agent1 will block if it cannot find them"

    local base_commit; base_commit="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
    local now; now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

    # A dirty start is refused rather than merely noted. agent2 reviews every
    # uncommitted change against the issue, so work you already had in progress
    # would be reviewed as though the agents wrote it for this task — and
    # agent1 may edit it. Recorded either way, because once the run begins the
    # two are indistinguishable.
    local dirty=no
    [[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ]] || dirty=yes
    if [[ "$dirty" == "yes" && "$ALLOW_DIRTY" -eq 0 ]]; then
        die "the worktree has uncommitted changes; commit or stash them first, "\
"or pass --allow-dirty to review them alongside the agents' work"
    fi
    [[ "$dirty" == "no" ]] \
        || info "warning: --allow-dirty — your existing changes are in the "\
"review scope, and --create-pr will refuse to publish"

    mkdir -p "$(dirname "$doc")"
    # Record the agent binaries, not their flags: the names are documentation
    # for whoever reads the document later, not something the driver reads back.
    local a1 a2
    a1="$(printf '%s' "$AGENT1_CMD" | awk '{print $1}')"
    a2="$(printf '%s' "$AGENT2_CMD" | awk '{print $1}')"

    # The body goes in verbatim from a file rather than through a substitution:
    # issue text routinely contains backslashes and ampersands, which awk's
    # gsub would silently reinterpret.
    local body_file; body_file="$(dirname "$doc")/.issue-body"
    printf '%s\n' "$body" > "$body_file"

    NUMBER="$number" TITLE="$title" ISSUE="$url" ROUNDS="$max_rounds" \
    SLUG="$slug" BASE="$base_commit" NOW="$now" A1="$a1" A2="$a2" DIRTY="$dirty" \
    awk -v bodyfile="$body_file" '
        /ISSUE_BODY/ {
            # Demote the issue is own headings by one level so they nest under
            # `## Issue` instead of colliding with the document sections that
            # section ownership is defined over. Lines inside fenced code are
            # left exactly as written.
            fence = ""
            while ((getline line < bodyfile) > 0) {
                text = line
                sub(/^[ \t]*/, "", text)
                mark = ""
                if (text ~ /^```/) {
                    match(text, /^`+/)
                    mark = substr(text, RSTART, RLENGTH)
                } else if (text ~ /^~~~/) {
                    match(text, /^~+/)
                    mark = substr(text, RSTART, RLENGTH)
                }
                if (mark != "") {
                    if (fence == "") fence = mark
                    else {
                        rest = substr(text, length(mark) + 1)
                        same = substr(fence, 1, 1) == substr(mark, 1, 1)
                        if (same && length(mark) >= length(fence) &&
                            rest ~ /^[ \t]*$/) fence = ""
                    }
                }
                if (fence == "" && line ~ /^#/) line = "#" line
                print line
            }
            close(bodyfile)
            next
        }
        # Literal replacement, never gsub: in a gsub replacement string `&`
        # expands to the matched text and backslashes are escapes, so an issue
        # titled "A & B" would render as "A ISSUE_TITLE B". Titles routinely
        # contain both characters.
        function put(line, ph, val,   at, out) {
            while ((at = index(line, ph)) > 0) {
                out = out substr(line, 1, at - 1) val
                line = substr(line, at + length(ph))
            }
            return out line
        }
        { $0 = put($0, "ISSUE_NUMBER", ENVIRON["NUMBER"])
          $0 = put($0, "ISSUE_TITLE",  ENVIRON["TITLE"])
          $0 = put($0, "ISSUE_URL",    ENVIRON["ISSUE"])
          $0 = put($0, "BASE_COMMIT",  ENVIRON["BASE"])
          $0 = put($0, "CREATED_AT",   ENVIRON["NOW"])
          $0 = put($0, "TASK_SLUG",    ENVIRON["SLUG"])
          $0 = put($0, "AGENT1_NAME",  ENVIRON["A1"])
          $0 = put($0, "AGENT2_NAME",  ENVIRON["A2"])
          if ($0 ~ /^max_rounds: /)     $0 = "max_rounds: "     ENVIRON["ROUNDS"]
          if ($0 ~ /^dirty_at_start: /) $0 = "dirty_at_start: " ENVIRON["DIRTY"]
          print }
    ' "$TEMPLATE" > "$doc"

    rm -f "$body_file"

    info "created ${doc#"$REPO_ROOT"/} from issue #${number} at base ${base_commit}"
    info "  ${title}"
    [[ "$STARTED_FROM_RUN" -eq 1 ]] \
        || info "next: .agents/workflows/pair.sh run ${slug}"
}

cmd_status() {
    local slug; slug="$(resolve_slug "${1:-}")" || exit "$EXIT_ERROR"
    local doc; doc="$(doc_for "$slug")"
    require_doc "$doc"
    local status_format
    status_format='status: %s\nturn:   %s\nrounds: plan %s/%s, '
    status_format+='implementation %s/%s\nbase:   %s\nmanual: %s\nupdated:%s\n'
    printf "$status_format" \
        "$(frontmatter "$doc" status)" \
        "$(frontmatter "$doc" turn)" \
        "$(frontmatter "$doc" plan_round)" \
        "$(frontmatter "$doc" max_rounds)" \
        "$(frontmatter "$doc" impl_round)" \
        "$(frontmatter "$doc" max_rounds)" \
        "$(frontmatter "$doc" base_commit)" \
        "$(frontmatter "$doc" manual_testing)" \
        "$(frontmatter "$doc" updated)"
}

# A finished task whose acceptance criteria cannot be settled by automated
# tests is not finished from the user's side. Print the plan where they will
# actually see it — at the end of the run, not buried in the document — and
# return a code they can branch on.
announce_done() {
    local slug="$1" doc="$2" manual
    info "task '${slug}' is done"
    manual="$(frontmatter "$doc" manual_testing)"

    case "$manual" in
        none) return "$TURN_DONE" ;;
        required) ;;
        *)
            # Undecided is not the same as "none". Reporting full coverage here
            # would assert something nobody established, so report the gap and
            # use the code that means "your turn".
            info "warning: agent1 did not record whether manual testing is needed"
            info "  (manual_testing is '${manual:-empty}') — decide before "\
"trusting this as verified"
            return "$TURN_DONE_MANUAL"
            ;;
    esac

    # `required` without an actionable plan reports a gap and then withholds
    # what to do about it, so keep the manual result but call the defect out.
    local plan; plan="$(section "$doc" "Manual Testing")"
    if ! manual_plan_is_usable "$doc"; then
        info "warning: manual testing is required but ## Manual Testing is not actionable"
        info "  it needs Setup and numbered Steps with Expected and Covers entries"
        [[ -z "$(printf '%s' "$plan" | tr -d '[:space:]')" ]] \
            || printf '%s\n' "$plan" >&2
        return "$TURN_DONE_MANUAL"
    fi

    printf '\n' >&2
    info "MANUAL TESTING REQUIRED before this change can be considered verified:"
    printf '\n' >&2
    printf '%s\n' "$plan" >&2
    printf '\n' >&2
    return "$TURN_DONE_MANUAL"
}

# Whether ## Manual Testing holds the structure promised to the user. Every
# numbered action needs its own expected result and acceptance-criterion map.
manual_plan_is_usable() {
    section "$1" "Manual Testing" | awk '
        function finish_step() {
            if (step && (!action || !expected || !covers)) bad = 1
        }
        $0 == "### Setup" {
            if (++setup_heading > 1) bad = 1
            area = "setup"
            next
        }
        $0 == "### Steps" {
            finish_step()
            if (++steps_heading > 1) bad = 1
            area = "steps"
            step = 0
            next
        }
        /^### / { finish_step(); area = "other"; next }
        area == "setup" && /[^[:space:]]/ { setup_text = 1; next }
        area == "steps" && /^[[:space:]]*[0-9]+[.)][[:space:]]+/ {
            finish_step()
            step++
            action = $0
            sub(/^[[:space:]]*[0-9]+[.)][[:space:]]+/, "", action)
            action = (action ~ /[^[:space:]]/)
            expected = covers = 0
            next
        }
        area == "steps" && step && /^[[:space:]]*Expected:[[:space:]]*[^[:space:]]/ {
            expected = 1
            next
        }
        area == "steps" && step && /^[[:space:]]*Covers:[[:space:]]*[^[:space:]]/ {
            covers = 1
            next
        }
        END {
            finish_step()
            if (setup_heading != 1 || steps_heading != 1 || !setup_text || !step) bad = 1
            exit bad
        }
    '
}

# Whether the PR body has exact, unique, non-empty Summary and Changes
# subsections. Other optional H3 sections remain allowed.
pr_body_is_usable() {
    section "$1" "Pull Request" | awk '
        function marker(line, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            if (text ~ /^```/) {
                match(text, /^`+/)
                return substr(text, RSTART, RLENGTH)
            }
            if (text ~ /^~~~/) {
                match(text, /^~+/)
                return substr(text, RSTART, RLENGTH)
            }
            return ""
        }
        function closes(line, mark, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            text = substr(text, length(mark) + 1)
            return text ~ /^[[:space:]]*$/
        }
        {
            mark = marker($0)
            if (mark != "") {
                if (fence == "") fence = mark
                else if (substr(fence, 1, 1) == substr(mark, 1, 1) &&
                         length(mark) >= length(fence) && closes($0, mark)) {
                    fence = ""
                }
            }
        }
        fence == "" && $0 == "### Summary" {
            summary++
            area = "summary"
            next
        }
        fence == "" && $0 == "### Changes" {
            changes++
            area = "changes"
            next
        }
        fence == "" && /^### / { area = "other"; next }
        area == "summary" && /[^[:space:]]/ { summary_text = 1 }
        area == "changes" && /[^[:space:]]/ { changes_text = 1 }
        END {
            exit !(summary == 1 && changes == 1 && summary_text && changes_text)
        }
    '
}

# Promotes document-nested PR headings without rewriting Markdown examples in
# fenced code blocks.
promote_pr_headings() {
    awk '
        function marker(line, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            if (text ~ /^```/) {
                match(text, /^`+/)
                return substr(text, RSTART, RLENGTH)
            }
            if (text ~ /^~~~/) {
                match(text, /^~+/)
                return substr(text, RSTART, RLENGTH)
            }
            return ""
        }
        function closes(line, mark, text) {
            text = line
            sub(/^[[:space:]]*/, "", text)
            text = substr(text, length(mark) + 1)
            return text ~ /^[[:space:]]*$/
        }
        {
            mark = marker($0)
            if (mark != "") {
                if (fence == "") fence = mark
                else if (substr(fence, 1, 1) == substr(mark, 1, 1) &&
                         length(mark) >= length(fence) && closes($0, mark)) {
                    fence = ""
                }
            }
            if (fence == "" && /^### /) sub(/^### /, "## ")
            print
        }
    '
}

# chordsVersion out of version.gradle.kts, from a file or from stdin, so the
# working copy and the base commit's copy can be compared.
version_from_stdin() {
    awk -F'"' '/chordsVersion/ && /"/ {print $2; exit}'
}
version_in_file() {
    version_from_stdin < "$1"
}

# Reads the root project version rather than accepting a matching dependency
# version elsewhere in the generated POM.
pom_project_version() {
    awk -F'[<>]' '
        /^[[:space:]]*<dependencies>/ { exit }
        /^[[:space:]]*<version>[^<]+<\/version>[[:space:]]*$/ {
            print $3
            exit
        }
    ' "$1"
}

# Requires every generated dependency-report heading to carry the new project
# version. At least one heading must exist.
dependency_headings_match() {
    local file="$1" version="$2"
    awk -v version="$version" '
        /^# Dependencies of / {
            count++
            suffix = ":" version "`"
            if (length($0) < length(suffix) ||
                substr($0, length($0) - length(suffix) + 1) != suffix) bad = 1
        }
        END { exit !(count > 0 && !bad) }
    ' "$file"
}

# Lists the complete prospective PR changeset, including commits already made
# by an earlier publication attempt and every current index/worktree change.
changeset_files() {
    local base="$1"
    {
        git -C "$REPO_ROOT" diff --name-only "$base" HEAD
        git -C "$REPO_ROOT" diff --name-only
        git -C "$REPO_ROOT" diff --cached --name-only
        git -C "$REPO_ROOT" ls-files --others --exclude-standard
    } | sort -u
}

# Branch name from the issue title: kebab-case, no agent identifiers, per the
# branch-naming rule in AGENTS.md.
branch_name_from() {
    printf '%s' "$1" \
        | tr '[:upper:]' '[:lower:]' \
        | sed -e 's/[^a-z0-9]\{1,\}/-/g' -e 's/^-//' -e 's/-$//' \
        | cut -c1-50 \
        | sed -e 's/-$//'
}

# Branches, commits, pushes, and opens a draft PR. Runs only after the workflow
# reaches `done`, and only with --create-pr. Every step that could surprise the
# user aborts instead of guessing.
create_pr() {
    local doc="$1"
    local number title branch body
    number="$(frontmatter "$doc" issue_number)"
    # The title recorded at setup, not a fresh read: a title edited mid-run
    # would derive a different branch name and strand the branch already
    # created, and the protocol defines the issue copy as a fixed snapshot.
    title="$(frontmatter "$doc" issue_title)"
    [[ -n "$title" ]] || die "issue_title is missing from the document; it is written at setup"

    # Publishing asserts the work is ready for review. An undecided manual
    # testing field, or `required` with no usable plan, means nobody can say
    # that yet — those are warnings on a finished run, not clearance to open a
    # PR someone will read as verified.
    local manual; manual="$(frontmatter "$doc" manual_testing)"
    case "$manual" in
        none) ;;
        required)
            manual_plan_is_usable "$doc" \
                || die "manual testing is required but ## Manual Testing has no "\
"usable plan (Setup; numbered Steps; Expected and Covers lines); nothing was published"
            ;;
        *)
            die "agent1 did not record whether manual testing is needed "\
"(manual_testing is '${manual:-empty}'); decide that before publishing"
            ;;
    esac

    # Anything already dirty when the run began is the user's work, not the
    # agents'. Committing it under this issue's branch would fold unrelated
    # changes into the PR, so refuse rather than sort them out by guessing.
    [[ "$(frontmatter "$doc" dirty_at_start)" == "no" ]] \
        || die "the worktree was already dirty when this run started; "\
"commit or stash your own changes and open the PR yourself"

    branch="$(branch_name_from "$title")"
    [[ -n "$branch" ]] || die "could not derive a branch name from the issue title"

    local base changes dirty
    base="$(frontmatter "$doc" base_commit)"
    git -C "$REPO_ROOT" cat-file -e "${base}^{commit}" 2>/dev/null \
        || die "base_commit '${base}' is not available; cannot validate the PR changeset"
    changes="$(changeset_files "$base")" \
        || die "could not determine the complete changeset since ${base}"
    dirty="$(git -C "$REPO_ROOT" status --porcelain)"
    if [[ -z "$changes" ]]; then
        info "no changes since ${base}; skipping the pull request"
        return 0
    fi

    # Everything below is checked before the first Git write, so a run that
    # cannot produce a policy-compliant PR fails without leaving a branch,
    # a commit, or a push behind.
    # The body is written with `###` headings so it nests inside `## Pull
    # Request` in the document — at `##` they would end the section rather than
    # belong to it. Promote them back on the way out, since AGENTS.md specifies
    # `## Summary` and `## Changes` in the PR description itself.
    body="$(section "$doc" "Pull Request")"
    pr_body_is_usable "$doc" \
        || die "the ## Pull Request section requires exact, non-empty "\
"### Summary and ### Changes sections; nothing was published"
    body="$(printf '%s\n' "$body" | promote_pr_headings)"

    # Every PR must carry a version bump and regenerated reports, enforced by
    # CI. Inspect the whole changeset, not only dirty files: a retry may already
    # have committed the version and reports successfully.
    local f absent=""
    for f in version.gradle.kts pom.xml dependencies.md; do
        printf '%s\n' "$changes" | grep -qx "$f" \
            || absent="${absent:+${absent}, }${f}"
    done
    [[ -z "$absent" ]] \
        || die "not in the changeset: ${absent}. AGENTS.md requires a version "\
"bump and regenerated reports in every PR; nothing was published"

    # "The file was touched" is not "the version went up". Compare against
    # the commit the run started from, require the documented scheme, and
    # validate the generated reports at their exact version-bearing locations.
    local new_v old_v pom_v
    new_v="$(version_in_file "${REPO_ROOT}/version.gradle.kts")"
    old_v="$(git -C "$REPO_ROOT" show "${base}:version.gradle.kts" 2>/dev/null \
             | version_from_stdin || true)"
    [[ "$new_v" =~ ^2\.0\.0-SNAPSHOT\.[0-9]+$ ]] \
        || die "chordsVersion is '${new_v}', which is not the "\
"2.0.0-SNAPSHOT.<N> scheme; nothing was published"
    [[ "$old_v" =~ ^2\.0\.0-SNAPSHOT\.[0-9]+$ ]] \
        || die "could not read a valid chordsVersion at base commit ${base}; nothing was published"
    [[ "${new_v##*.}" -gt "${old_v##*.}" ]] \
        || die "chordsVersion did not increase (${old_v} -> ${new_v}); "\
"the version-increment check would fail, so nothing was published"

    pom_v="$(pom_project_version "${REPO_ROOT}/pom.xml")"
    [[ "$pom_v" == "$new_v" ]] \
        || die "pom.xml root project version is '${pom_v:-missing}', expected "\
"${new_v}; regenerate the reports before publishing"
    dependency_headings_match "${REPO_ROOT}/dependencies.md" "$new_v" \
        || die "not every dependencies.md heading carries ${new_v}; "\
"regenerate the reports before publishing"

    local current; current="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
    if [[ "$current" == "$branch" ]]; then
        info "already on '${branch}'"
    elif [[ "$current" == "master" ]]; then
        [[ -n "$dirty" ]] \
            || die "changes since ${base} are already committed on master; "\
"move them to '${branch}' before publishing"
        git -C "$REPO_ROOT" checkout -b "$branch" >/dev/null 2>&1 \
            || die "could not create branch '${branch}'"
        info "created branch '${branch}'"
    else
        die "on branch '${current}', which does not match this task; "\
"switch to master or to '${branch}' and re-run"
    fi

    # Each step is skipped when already done, so a re-run after a failed push
    # or a failed `gh pr create` resumes instead of concluding there is nothing
    # left to publish. A clean worktree does not mean the work is published.
    if [[ -n "$dirty" ]]; then
        # The version bump and its regenerated reports are their own commit,
        # with the message format AGENTS.md fixes for it. `--only` prevents a
        # staged task file left by an earlier failed commit from leaking into
        # this dedicated commit.
        if ! git -C "$REPO_ROOT" diff --quiet HEAD -- \
            version.gradle.kts pom.xml dependencies.md; then
            git -C "$REPO_ROOT" commit -q --only \
                -m "Bump version —> \`${new_v}\`." -- \
                version.gradle.kts pom.xml dependencies.md \
                || die "the version commit failed"
            info "committed the version bump and regenerated reports"
        else
            info "version bump and reports are already committed"
        fi
        git -C "$REPO_ROOT" add -A || die "git add failed"
        if ! git -C "$REPO_ROOT" diff --cached --quiet; then
            git -C "$REPO_ROOT" commit -q -m "${title}" \
                || die "the task commit failed"
            info "committed onto '${branch}'"
        fi
    else
        info "nothing left to commit; continuing with what is already committed"
    fi

    if ! git -C "$REPO_ROOT" rev-parse --abbrev-ref '@{upstream}' >/dev/null 2>&1 \
       || [[ -n "$(git -C "$REPO_ROOT" log '@{upstream}..HEAD' --oneline)" ]]; then
        git -C "$REPO_ROOT" push -q -u origin "$branch" \
            || die "git push failed; the commits are local and nothing was published"
        info "pushed '${branch}'"
    else
        info "'${branch}' is already pushed"
    fi

    local existing
    existing="$(gh pr view --json url --jq .url 2>/dev/null || true)"
    if [[ -n "$existing" ]]; then
        info "pull request already open: ${existing}"
        return 0
    fi

    # AGENTS.md: draft, assigned to the author, base master, no trailing period
    # in the title, no verification detail and no agent attribution in the body.
    body="${body}"$'\n\n'"Fixes #${number}"
    local url
    url="$(gh pr create --draft --assignee @me --base master \
        --title "${title%.}" --body "$body" 2>&1)" \
        || die "gh pr create failed (the branch is pushed; re-run to retry just this step): ${url}"
    info "draft pull request: ${url}"
}

# Question IDs with no matching answer. A half-answered document would send
# agent1 back to guess at the rest, which is what asking was meant to avoid.
# Duplicate identifiers are reported rather than deduplicated: two distinct
# questions both labelled Q1 would otherwise count as answered by a single A1,
# and the second decision would never be made. Kept separate from
# unanswered_questions because that runs in a command substitution, where a
# `die` would end only the subshell and let the run carry on.
duplicate_question_ids() {
    section "$1" "Questions" | grep -oE '^\*\*[QA][0-9]+' | tr -d '*' \
        | sort | uniq -d | tr '\n' ' '
}

unanswered_questions() {
    local doc="$1" body questions answers q missing=""
    body="$(section "$doc" "Questions")"
    questions="$(printf '%s\n' "$body" | grep -oE '^\*\*Q[0-9]+' | tr -d '*' | sort -u || true)"
    # An answer counts only when something follows the marker. A bare `**A1.**`
    # is a placeholder, and resuming on it would send agent1 back to guess at
    # the very decision it stopped to ask about.
    answers="$(printf '%s\n' "$body" \
        | awk '/^\*\*A[0-9]+\./ {
                 rest = $0
                 sub(/^\*\*A[0-9]+\.\*\*[[:space:]]*/, "", rest)
                 sub(/^\*\*A[0-9]+\.[[:space:]]*/, "", rest)
                 gsub(/[[:space:]]/, "", rest)
                 if (rest != "") { match($0, /A[0-9]+/); print substr($0, RSTART, RLENGTH) }
               }' | sort -u)"
    for q in $questions; do
        printf '%s\n' "$answers" | grep -qx "A${q#Q}" \
            || missing="${missing:+${missing}, }${q}"
    done
    printf '%s' "$missing"
}

# Builds the per-turn prompt. Deliberately thin: the protocol lives in the
# skill, and every invocation is a cold start that reads it fresh.
prompt_for() {
    local role="$1" status="$2" rel_doc="$3"
    cat <<PROMPT
You are ${role} in the Chords pair workflow.

Read these first, in order:
  1. AGENTS.md
  2. .agents/skills/pair-workflow/SKILL.md
  3. ${rel_doc}

The working document is ${rel_doc}. Its status is \`${status}\` and the turn is
yours. Take exactly one turn: do the work the skill assigns to ${role} for this
status, write only the sections ${role} owns, append a line to ## Log, and
update the frontmatter so \`status\` and \`turn\` advance.

Do not take the other agent's turn.

Do not change Git state. No branch creation or switching, no \`git add\`, no
commit, no push, no tag, no rebase/merge/cherry-pick/reset/stash, and no pull
request. Read-only Git (\`status\`, \`diff\`, \`log\`, \`show\`) is expected and
fine. Leave your work as uncommitted changes in the worktree — that is the
deliverable, and this prompt is not authorization to commit it. Refs and the
index are compared before and after your turn, and the run aborts if they
moved.

If you are agent1 and a question must be answered before you can plan honestly,
ask it: write it under ## Questions, set \`resume_status\` to '${status}', set
status to \`questions-pending\` and turn to \`human\`. Ask only what genuinely
changes the work, and give each question a proposed default so the user can
accept them all in one word.

If a decision belongs to the user and no answer would unblock it, set status to
\`blocked\`, set turn to \`human\`, and say why.
PROMPT

    if [[ "$CREATE_PR" -eq 1 ]]; then
        cat <<'PROMPT_PR'

A pull request will be opened from this work once the workflow finishes, so on
your final turn the changeset must be PR-ready. Per AGENTS.md: bump
`chordsVersion` in version.gradle.kts, regenerate pom.xml and dependencies.md
with the focused command in "Versioning and Reports", and write a ## Pull
Request section holding the PR description. Write its two required headings as
`### Summary` and `### Changes` — at `##` they would end the ## Pull Request
section instead of nesting inside it, and the driver promotes them back to `##`
in the published description. No verification or testing detail, and no agent
attribution.

This changes nothing about Git. You still create no branch, stage nothing, and
commit nothing; the driver does all of that after you finish, and the refs and
index are still compared after your turn.
PROMPT_PR
    fi

    [[ "$ACCEPT_DEFAULTS" -eq 1 ]] || return 0
    cat <<'PROMPT_DEFAULTS'

This run was started with --accept-defaults, so nobody is watching it. Do not
set `questions-pending`. Where you would have asked a question, take the
default you would have proposed and carry on — but leave the reasoning visible:
record the question under ## Questions with an **A<n>.** line marked "assumed
default", and list every assumption in ## Plan so the review sees them.

Where the user has already written an answer, that answer wins over your
default. This flag is permission to proceed unattended, not permission to
ignore what you were told.

It is not permission to guess at the task itself. If the issue does not state
what to do or its acceptance criteria, `blocked` is still the correct outcome —
a default cannot substitute for a specification.
PROMPT_DEFAULTS
}

take_turn() {
    local slug="$1"
    local doc; doc="$(doc_for "$slug")"
    require_doc "$doc"

    local status turn rel_doc
    status="$(frontmatter "$doc" status)"
    turn="$(frontmatter "$doc" turn)"
    rel_doc="${doc#"$REPO_ROOT"/}"

    # Answering is the whole action: writing the answers into the document is
    # enough, and the next run picks them up. There is no separate command to
    # remember. --accept-defaults resumes the same way without answers.
    if [[ "$status" == "questions-pending" ]]; then
        local dupes; dupes="$(duplicate_question_ids "$doc")"
        [[ -z "${dupes// /}" ]] \
            || die "duplicate question or answer identifiers in ${rel_doc}: "\
"${dupes}— give each its own number"
        local missing; missing="$(unanswered_questions "$doc")"
        if [[ -z "$missing" || "$ACCEPT_DEFAULTS" -eq 1 ]]; then
            local resume; resume="$(frontmatter "$doc" resume_status)"
            [[ -n "$resume" && "$resume" != "none" ]] \
                || die "resume_status is not set in ${rel_doc}; cannot resume"
            if [[ -z "$missing" ]]; then
                info "answers found; resuming at '${resume}'"
            else
                info "--accept-defaults: resuming at '${resume}' on agent1's own defaults"
            fi
            set_frontmatter "$doc" \
                "status=${resume}" "turn=agent1" "resume_status=none" \
                "updated=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
            status="$resume"
            turn="agent1"
        fi
    fi

    # Terminal states return the TURN_* constants, which exit_for maps to the
    # public exit codes. They are deliberately different numbers so an internal
    # signal can never be mistaken for one.
    local dc=0
    case "$status" in
        done)    announce_done "$slug" "$doc" || dc=$?; return "$dc" ;;
        blocked) info "task '${slug}' is blocked and needs a human"; return "$TURN_NEEDS_YOU" ;;
        questions-pending)
            printf '\n' >&2
            info "agent1 needs answers before it can plan:"
            printf '\n' >&2
            section "$doc" "Questions" >&2
            printf '\n' >&2
            info "write an **A<n>.** line under each question in ${rel_doc},"
            info "then run the same command again."
            return "$TURN_NEEDS_YOU"
            ;;
        plan-requested|plan-review-requested|plan-reviewed|\
        implementation-review-requested|implementation-reviewed) ;;
        *) die "unrecognized status '${status}' in ${doc}" ;;
    esac

    # The round ceiling is the workflow's only defense against two agents
    # disagreeing forever. The skill tells agent1 to set `blocked` on reaching
    # it, but an agent that ignores that instruction is exactly the case the
    # ceiling exists for, so check it here too.
    local plan_round impl_round max_rounds r
    plan_round="$(frontmatter "$doc" plan_round)"
    impl_round="$(frontmatter "$doc" impl_round)"
    max_rounds="$(frontmatter "$doc" max_rounds)"
    for r in "plan_round=$plan_round" "impl_round=$impl_round" "max_rounds=$max_rounds"; do
        [[ "${r#*=}" =~ ^[0-9]+$ ]] \
            || die "${r%%=*} is '${r#*=}' in ${rel_doc}; it must be an integer"
    done
    [[ "$plan_round" -le "$max_rounds" && "$impl_round" -le "$max_rounds" ]] \
        || die "round ceiling exceeded (plan ${plan_round}, implementation "\
"${impl_round}, max ${max_rounds}); read ${rel_doc} and decide"

    # Each phase counts its own rounds against the same ceiling, so the number
    # shown is the one for the phase this turn belongs to.
    local phase_round
    case "$status" in
        plan-requested|plan-review-requested|plan-reviewed) phase_round="plan ${plan_round}" ;;
        *) phase_round="implementation ${impl_round}" ;;
    esac

    # The driver dispatches on `turn`, but `status` is what actually determines
    # whose move it is. An agent that writes a disagreeing pair would hand the
    # turn to the wrong agent, so treat the mismatch as fatal rather than
    # letting the two ping-pong without the state machine advancing.
    local expected
    case "$status" in
        plan-requested|plan-reviewed|implementation-reviewed) expected=agent1 ;;
        plan-review-requested|implementation-review-requested) expected=agent2 ;;
    esac
    [[ "$turn" == "$expected" ]] \
        || die "frontmatter disagrees: status '${status}' is ${expected}'s move, "\
"but turn says '${turn}'"

    local cmd var
    case "$turn" in
        agent1) cmd="$AGENT1_CMD"; var="AGENT1_CMD" ;;
        agent2) cmd="$AGENT2_CMD"; var="AGENT2_CMD" ;;
    esac

    local bin; bin="$(printf '%s' "$cmd" | awk '{print $1}')"
    command -v "$bin" >/dev/null 2>&1 \
        || die "'${bin}' is not on PATH; set ${var} to a command that is"

    info "turn: ${turn} (${bin}) at status '${status}', ${phase_round}/${max_rounds}"

    local before after git_before git_after immutable_before protect_task=1
    # Task is established during plan-requested, including after a question
    # round-trip. Its heading remains unique then, but its contents become
    # immutable only after that planning turn advances.
    [[ "$status" == "plan-requested" ]] && protect_task=0
    before="$(cksum < "$doc")"
    git_before="$(git_state)"
    immutable_before="$(immutable_snapshot "$doc" "$protect_task")"

    # Keep a transcript per turn. An unattended run that goes wrong overnight
    # is otherwise unreconstructable: the document records what an agent chose
    # to write down, not what it actually did.
    local turns_dir="$(dirname "$doc")/turns"
    mkdir -p "$turns_dir"
    local n; n="$(find "$turns_dir" -name '*.log' | wc -l | tr -d ' ')"
    local log; log="$(printf '%s/%02d-%s.log' "$turns_dir" "$((n + 1))" "$turn")"

    # Word splitting on the command is intended: it carries its own flags.
    # PIPESTATUS, not $?, because the pipe through tee would otherwise report
    # tee's exit code and swallow a failed turn.
    local rc
    set +e
    # shellcheck disable=SC2086
    (cd "$REPO_ROOT" && $cmd "$(prompt_for "$turn" "$status" "$rel_doc")") 2>&1 | tee "$log"
    rc=${PIPESTATUS[0]}
    # Restore strict handling for every guard below. An intentional internal
    # non-zero result must disable it immediately before returning to the
    # caller, which maps that result to a public exit code.
    set -e
    # The transcript is named because the CLI's own error is the diagnosis and
    # it goes nowhere else. The two hints cover what actually fails first: a
    # CLI that is on PATH but not signed in, and a model identifier that only
    # the API rejects. Both surface as a turn that dies immediately having
    # written nothing.
    [[ "$rc" -eq 0 ]] \
        || die "${turn} exited ${rc}; document left at status '${status}', "\
"transcript in ${log#"$REPO_ROOT"/}. A turn that fails at once usually means "\
"the CLI is not authenticated or its model identifier was rejected; the "\
"transcript says which"

    # Checked before the document, because a Git write is the more serious
    # violation even on a turn that otherwise did its job.
    #
    # This compares two snapshots of the repository; it cannot see who moved
    # between them. An agent that wrote to Git and a user who switched branches
    # in another window produce the same diff, so the message reports the
    # change and leaves the attribution to whoever reads it. Naming the agent
    # here would accuse it of a violation the driver has no evidence for.
    git_after="$(git_state)"
    if [[ "$git_before" != "$git_after" ]]; then
        info "Git state changed during ${turn}'s turn (- before, + after):"
        diff <(printf '%s\n' "$git_before") <(printf '%s\n' "$git_after") >&2 || true
        die "Git state moved during ${turn}'s turn, which this workflow "\
"forbids the agents to do; inspect the repository before continuing. If you "\
"changed branches or committed while the run was live, that is this diff and "\
"the run can simply be started again"
    fi

    after="$(cksum < "$doc")"
    [[ "$before" != "$after" ]] \
        || die "${turn} did not modify ${rel_doc}; aborting instead of looping"

    local new_status; new_status="$(frontmatter "$doc" status)"
    [[ "$new_status" != "$status" ]] \
        || die "${turn} left status at '${status}'; aborting instead of looping"

    # "Status changed" is not the same as "the state machine advanced". Each
    # legal move is spelled out as status -> next, together with the turn it
    # must hand over to and exactly what each round counter must do. Anything
    # looser lets an agent jump plan-requested straight to `done` — and with
    # --create-pr, publish work that was never reviewed.
    #
    # Fields: next-status | required-turn | plan-delta | impl-delta
    local legal="" rule="" want_turn="" want_plan="" want_impl=""
    case "$status" in
        plan-requested)
            legal="plan-review-requested|agent2|0|0" ;;
        plan-review-requested)
            legal="plan-reviewed|agent1|0|0" ;;
        plan-reviewed)
            # Sending the plan back must spend a round, or the two can trade
            # revisions forever and the ceiling is never reached.
            legal="plan-review-requested|agent2|1|0
implementation-review-requested|agent2|0|0" ;;
        implementation-review-requested)
            legal="implementation-reviewed|agent1|0|0" ;;
        implementation-reviewed)
            legal="implementation-review-requested|agent2|0|1
done|human|0|0" ;;
    esac
    # Either agent may block. Only agent1 asks questions.
    legal="${legal}
blocked|human|0|0"
    [[ "$turn" == "agent1" ]] && legal="${legal}
questions-pending|human|0|0"

    local new_turn new_plan new_impl
    new_turn="$(frontmatter "$doc" turn)"
    new_plan="$(frontmatter "$doc" plan_round)"
    new_impl="$(frontmatter "$doc" impl_round)"

    rule="$(printf '%s\n' "$legal" | grep "^${new_status}|" || true)"
    [[ -n "$rule" ]] \
        || die "${turn} moved '${status}' -> '${new_status}', which the protocol "\
"does not allow here; the document is left as written for inspection"

    IFS='|' read -r _ want_turn want_plan want_impl <<< "$rule"
    [[ "$new_turn" == "$want_turn" ]] \
        || die "${turn} set '${new_status}' but handed the turn to '${new_turn}'; "\
"that state belongs to ${want_turn}"
    local plan_action="stay unchanged" impl_action="stay unchanged"
    [[ "$want_plan" -eq 0 ]] || plan_action="increase by exactly one"
    [[ "$want_impl" -eq 0 ]] || impl_action="increase by exactly one"
    round_delta_ok "$plan_round" "$new_plan" "$want_plan" \
        || die "${turn} moved plan_round ${plan_round} -> ${new_plan}; "\
"this transition requires it to ${plan_action}"
    round_delta_ok "$impl_round" "$new_impl" "$want_impl" \
        || die "${turn} moved impl_round ${impl_round} -> ${new_impl}; "\
"this transition requires it to ${impl_action}"

    # Fields the driver owns must survive every turn untouched: create_pr and
    # the reviewer's diff scope both trust them.
    verify_immutable "$doc" "$immutable_before" "$turn" "$protect_task"

    # An agent that tries to spend a round after the configured ceiling has
    # reached the protocol's human-decision point. Convert that attempted
    # loopback into the documented terminal state instead of accepting it and
    # aborting with an unrelated error on the following turn.
    if [[ "$new_plan" -gt "$max_rounds" || "$new_impl" -gt "$max_rounds" ]]; then
        local blocked_at; blocked_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        set_frontmatter "$doc" \
            "status=blocked" "turn=human" \
            "plan_round=${plan_round}" "impl_round=${impl_round}" \
            "resume_status=none" "updated=${blocked_at}"
        printf '\n%s driver %s -> blocked: review ceiling %s reached; human decision required\n' \
            "$blocked_at" "$status" "$max_rounds" >> "$doc"
        info "${turn} requested another review beyond max_rounds=${max_rounds}; "\
"task is blocked for a human decision"
        set +e
        return "$TURN_NEEDS_YOU"
    fi

    info "advanced: ${status} -> ${new_status}"
    return 0
}

# Names agent commands that remove their CLI's approval or sandbox boundary.
unsafe_agent_roles() {
    local flagged=""
    case " $AGENT1_CMD " in
        *--dangerously-*|*--yolo*|*bypassPermissions*) flagged="agent1" ;;
    esac
    case " $AGENT2_CMD " in
        *--dangerously-*|*--yolo*|*danger-full-access*)
            flagged="${flagged:+${flagged} and }agent2" ;;
    esac
    printf '%s' "$flagged"
}

# Refuses known unsafe execution modes unless the caller explicitly confirms
# that an external isolation boundary exists.
validate_agent_permissions() {
    local flagged; flagged="$(unsafe_agent_roles)"
    if [[ -n "$flagged" && "$ALLOW_UNSAFE_AGENTS" -eq 0 ]]; then
        die "${flagged} command disables approvals or sandboxing; use safe "\
"commands or pass --allow-unsafe-agents only in an isolated environment"
    fi
    if [[ -n "$flagged" ]]; then
        info "warning: --allow-unsafe-agents accepted for ${flagged}; "\
"the driver is not an isolation boundary"
    else
        info "agent commands retain their CLI approval and sandbox boundaries"
    fi
    return 0
}

cmd_step() {
    local slug; slug="$(resolve_slug "${1:-}")" || exit "$EXIT_ERROR"; shift || true
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --accept-defaults|--ad) ACCEPT_DEFAULTS=1; shift ;;
            --allow-unsafe-agents) ALLOW_UNSAFE_AGENTS=1; shift ;;
            *) die "unknown option: $1" ;;
        esac
    done
    require_doc "$(doc_for "$slug")"
    acquire_lock "$slug"
    validate_agent_permissions

    local rc ec=0
    set +e; take_turn "$slug"; rc=$?; set -e
    exit_for "$rc" || ec=$?
    return "$ec"
}

# Maps an internal take_turn result to the script's exit code.
exit_for() {
    case "$1" in
        "$TURN_ADVANCED"|"$TURN_DONE") return "$EXIT_OK" ;;
        "$TURN_DONE_MANUAL")           return "$EXIT_MANUAL" ;;
        "$TURN_NEEDS_YOU")             return "$EXIT_NEEDS_YOU" ;;
        *) die "internal: unmapped turn result '$1'" ;;
    esac
}

cmd_run() {
    local issue_arg="${1:-}"
    local slug; slug="$(resolve_slug "$issue_arg")" || exit "$EXIT_ERROR"; shift || true
    local max_turns="" max_rounds=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --max-turns)
                max_turns="${2:-}"
                require_positive_int --max-turns "$max_turns" || exit "$EXIT_ERROR"
                shift 2 ;;
            --max-rounds|--mr)
                max_rounds="${2:-}"
                require_positive_int --max-rounds "$max_rounds" || exit "$EXIT_ERROR"
                shift 2 ;;
            --accept-defaults|--ad) ACCEPT_DEFAULTS=1; shift ;;
            --create-pr|--cp) CREATE_PR=1; shift ;;
            --allow-dirty) ALLOW_DIRTY=1; shift ;;
            --allow-unsafe-agents) ALLOW_UNSAFE_AGENTS=1; shift ;;
            *) die "unknown option: $1" ;;
        esac
    done

    # Starting is not a separate decision from running — it is the first thing
    # a run needs. Create the document when it is missing so the common path is
    # one command, and leave `start` for when you want to read the issue copy
    # before spending a turn on it.
    # The lock comes before the existence check, not after: otherwise two runs
    # for one issue can both see no document and both create it.
    acquire_lock "$slug"

    local doc; doc="$(doc_for "$slug")"
    if [[ ! -f "$doc" ]]; then
        STARTED_FROM_RUN=1
        if [[ -n "$max_rounds" ]]; then
            cmd_start "$issue_arg" --max-rounds "$max_rounds"
        else
            cmd_start "$issue_arg"
        fi
    elif [[ -n "$max_rounds" ]]; then
        die "--max-rounds only applies when creating the document; '${slug}' already exists"
    fi

    # A full run is 1 planning turn + 2 per plan round + 2 per implementation
    # round + 1 terminal observation. Deriving the ceiling from max_rounds
    # keeps the loop guard from firing before the protocol's own round limit,
    # which would abort instead of handing the disagreement to a person.
    if [[ -z "$max_turns" ]]; then
        local rounds; rounds="$(frontmatter "$doc" max_rounds)"
        [[ "$rounds" =~ ^[0-9]+$ ]] || rounds=2
        max_turns=$(( 4 * rounds + 2 ))
        [[ "$max_turns" -ge "$DEFAULT_MAX_TURNS" ]] || max_turns="$DEFAULT_MAX_TURNS"
    fi

    validate_agent_permissions

    local i rc ec
    for (( i = 1; i <= max_turns; i++ )); do
        set +e; take_turn "$slug"; rc=$?; set -e
        [[ "$rc" -eq "$TURN_ADVANCED" ]] && continue
        cmd_status "$slug"
        ec=0
        exit_for "$rc" || ec=$?
        # Publish only from a finished run. A task that stopped for you, or
        # aborted, has nothing anyone should be reviewing yet.
        if [[ "$CREATE_PR" -eq 1 ]]; then
            case "$rc" in
                "$TURN_DONE"|"$TURN_DONE_MANUAL") create_pr "$doc" ;;
                *) info "not done — no pull request opened" ;;
            esac
        fi
        return "$ec"
    done
    die "hit the ${max_turns}-turn ceiling without reaching a terminal state"
}

main() {
    [[ -f "$SKILL" && -f "$TEMPLATE" ]] || die "run this script from within the repository"
    case "${1:-}" in
        start)  shift; cmd_start  "$@" ;;
        run)    shift; cmd_run    "$@" ;;
        step)   shift; cmd_step   "$@" ;;
        status) shift; cmd_status "$@" ;;
        # No subcommand: an issue on its own means "run it". Running is what
        # this script is for, so it should not need to be asked for by name.
        [0-9]*|'#'*|*://*) cmd_run "$@" ;;
        *) usage; exit 1 ;;
    esac
}

main "$@"
