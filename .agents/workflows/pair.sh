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

A new task starts from a GitHub issue. It must describe what to do or what is
wrong, and state its acceptance criteria. The worktree must be clean at the
start unless --allow-dirty is given.

Usage:
  pair.sh <issue> [--ad] [--mr N] [--cp] [--sa] [--max-turns N]
                  [--allow-dirty] [--allow-unsafe-agents]
                  [--claude-model MODEL] [--claude-effort LEVEL]
                  [--codex-model MODEL] [--codex-effort LEVEL]

Runs the issue to completion: sets up on first call, resumes on later ones.
Run the same command again after anything stops it. <issue> is a number (123),
#123, or a full issue URL.

  --ad, --accept-defaults   do not stop to ask questions; take the proposed
                            default and record it, for a run nobody is watching
  --mr, --max-rounds N      review rounds allowed in each phase; N rounds
                            permit at most N - 1 send-backs (default 2)
  --allow-dirty             start even though the worktree has uncommitted
                            changes. They enter the reviewer's scope as if the
                            agents wrote them. This option cannot be combined
                            with --create-pr.
  --allow-unsafe-agents     permit an AGENT1_CMD or AGENT2_CMD that disables
                            approvals or sandboxing. Use only inside an
                            externally isolated, credential-free environment.
  --cp, --create-pr         when the run finishes, branch, commit, push, and
                            open a draft PR. Off by default: the normal result
                            is an uncommitted worktree you review yourself.
                            The agents never touch Git either way — the driver
                            does this afterwards, and only on a finished run
                            from a worktree that was clean at the start.
                            The task branch starts from whatever branch the run
                            started on, and the PR targets the base recorded at
                            setup (master by default).
                            Starting from a branch with commits outside the PR
                            target is supported: those commits may show up in
                            this PR, and the PR body identifies their exact
                            boundary.
  --sa, --swap-agents       swap the planner/implementer and reviewer commands.
                            Repeat this option when resuming the same task.
  --claude-model MODEL      select the Claude Code model for this task.
  --claude-effort LEVEL     select Claude effort: low, medium, high, xhigh, or
                            max.
  --codex-model MODEL       select the Codex model for this task.
  --codex-effort LEVEL      select Codex reasoning effort: minimal, low,
                            medium, high, or xhigh.

Model and effort selections are recorded when the task starts and reused on
resume. A later invocation cannot change them.

Less often:
  pair.sh run    <slug>    resume a task created with a custom --slug
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
                         --add-dir .agents/work --ephemeral
                         --ignore-user-config -m gpt-5.6-sol
                         -c model_reasoning_effort="high"
                         -c service_tier="default")
  PR_BASE_BRANCH  pull request target branch (default: master); its
                  origin/<name> ref must exist when a run starts

Model and effort are pinned so a review is reproducible. Codex's are passed as
flags because --ignore-user-config discards ~/.codex/config.toml by design.
--add-dir is required because that sandbox refuses to write gitignored paths,
and the working document lives in one. Keep it when overriding AGENT2_CMD.

A Codex command that holds the implementer seat — where --swap-agents puts the
default reviewer — is additionally given the Gradle user home and sandbox
network access, because the root build cannot start without either. The grant
is announced when it happens and is never extended to the reviewer.

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
# --add-dir names the work directory because Codex's workspace-write sandbox
# excludes gitignored paths from the writable set, and .agents/work/ is
# gitignored by design — the working document is a scratch artifact that is
# never committed.
# Without it agent2 can read the document but not write its review, and the
# turn ends with the driver aborting on an unmodified document. This widens
# the sandbox by exactly one directory inside the repository; it is not a
# bypass flag, and unsafe_agent_roles() does not treat it as one.
AGENT1_CMD="${AGENT1_CMD:-claude -p --permission-mode acceptEdits --setting-sources project --model claude-opus-5 --effort high}"
AGENT2_CMD="${AGENT2_CMD:-codex exec --sandbox workspace-write --add-dir .agents/work --ephemeral --ignore-user-config -m gpt-5.6-sol -c model_reasoning_effort=\"high\" -c service_tier=\"default\"}"

# Codex's setting for network access inside the workspace-write sandbox. Named
# once because grant_implementer_verification_access() both tests for it and
# appends it, and a typo in either place would be silent.
readonly CODEX_SANDBOX_NETWORK="sandbox_workspace_write.network_access=true"

# Engine-specific selections requested on the command line. They are separate
# from role assignment: --swap-agents exchanges the complete configured
# commands, while these settings continue to identify Claude and Codex.
CLAUDE_MODEL_OPTION=""
CLAUDE_EFFORT_OPTION=""
CODEX_MODEL_OPTION=""
CODEX_EFFORT_OPTION=""

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

# The branch every pull request targets, per "Creating a Pull Request" in
# AGENTS.md. It is also the branch a task is normally cut from — but not the
# only one it may be cut from, see `create_pr`. Overridable for a repository
# whose trunk is named differently; the workflow never infers it from the
# remote, because guessing the target of a PR is not a guess worth making.
PR_BASE_BRANCH="${PR_BASE_BRANCH:-master}"

# Set only when the caller acknowledges that custom agent commands remove the
# CLIs' normal execution boundary.
ALLOW_UNSAFE_AGENTS=0

# Set by --swap-agents. The guard makes repeated aliases idempotent rather than
# swapping the commands back to their defaults.
AGENTS_SWAPPED=0

die() { printf 'pair: %s\n' "$1" >&2; exit 1; }
# Same message, but returns instead of exiting. Helpers that may be called from
# inside a command substitution must use this: there, `die` ends only the
# subshell, so validation would report a problem and let the run continue.
# Callers propagate with `|| exit "$EXIT_ERROR"`.
fail() { printf 'pair: %s\n' "$1" >&2; return 1; }
info() { printf 'pair: %s\n' "$1" >&2; }

doc_for() { printf '%s/%s/plan.md' "$WORK_ROOT" "$1"; }

# Returns the executable recorded in the working document for an agent command.
agent_command_name() { printf '%s' "$1" | awk '{print $1}'; }

# Identifies a directly configured supported CLI. Wrapper commands stay
# customizable through AGENT1_CMD and AGENT2_CMD, but engine-specific options
# cannot be injected into a wrapper without knowing its argument contract.
agent_command_engine() {
    local executable
    executable="$(agent_command_name "$1")"
    case "${executable##*/}" in
        claude|codex) printf '%s' "${executable##*/}" ;;
        *)            printf 'custom' ;;
    esac
}

# Removes quotes used to make Codex configuration values explicit TOML strings.
unquote_setting() {
    local value="$1"
    value="${value#\"}"; value="${value%\"}"
    value="${value#\'}"; value="${value%\'}"
    printf '%s' "$value"
}

# Reads the last model or effort value in one directly configured CLI command.
# Agent command strings already use whitespace-delimited arguments, so this
# mirrors the splitting used when the command runs.
agent_command_setting() {
    local cmd="$1" engine="$2" setting="$3" token value=""
    local -a words
    local i
    read -r -a words <<< "$cmd"
    for (( i = 0; i < ${#words[@]}; i++ )); do
        token="${words[$i]}"
        case "${engine}:${setting}:${token}" in
            claude:model:--model|codex:model:-m|codex:model:--model)
                i=$(( i + 1 )); value="${words[$i]:-}" ;;
            claude:model:--model=*|codex:model:--model=*)
                value="${token#*=}" ;;
            claude:effort:--effort)
                i=$(( i + 1 )); value="${words[$i]:-}" ;;
            claude:effort:--effort=*)
                value="${token#*=}" ;;
            codex:effort:-c|codex:effort:--config)
                if [[ "${words[$(( i + 1 ))]:-}" == model_reasoning_effort=* ]]; then
                    i=$(( i + 1 ))
                    value="${words[$i]#*=}"
                fi ;;
            codex:effort:--config=model_reasoning_effort=*)
                value="${token#--config=model_reasoning_effort=}" ;;
        esac
    done
    unquote_setting "$value"
}

# Reads the sandbox policy named by a directly configured Codex command.
#
# Codex spells one policy several ways: `-s` or `--sandbox` with the value as
# the next word, joined by `=`, or attached to the short option, and
# `-c sandbox_mode=…` selects it from configuration instead. Recognizing a
# single literal spelling would leave grant_implementer_verification_access()
# inert for the rest, and the only symptom would be an implementer that cannot
# build — with nothing in the output pointing at the command that caused it.
#
# The command-line option wins over the configuration override, as it does in
# Codex; within each, the last occurrence wins. Prints nothing when the command
# names no policy, which leaves Codex on its own default.
codex_sandbox_mode() {
    local cmd="$1" token flag="" config=""
    local -a words
    local i
    read -r -a words <<< "$cmd"
    for (( i = 0; i < ${#words[@]}; i++ )); do
        token="${words[$i]}"
        case "$token" in
            -s|--sandbox)
                i=$(( i + 1 )); flag="${words[$i]:-}" ;;
            -s=*|--sandbox=*)
                flag="${token#*=}" ;;
            -s?*)
                flag="${token#-s}" ;;
            -c|--config)
                if [[ "${words[$(( i + 1 ))]:-}" == sandbox_mode=* ]]; then
                    i=$(( i + 1 ))
                    config="${words[$i]#*=}"
                fi ;;
            --config=sandbox_mode=*)
                config="${token#--config=sandbox_mode=}" ;;
        esac
    done
    unquote_setting "${flag:-$config}"
}

# Whether a command already passes `option value`, separated or joined by `=`.
# Keeps the verification grant from appending an argument the caller supplied
# themselves, and from announcing a widening that did not happen.
command_passes_option() {
    local cmd="$1" option="$2" value="$3" token
    local -a words
    local i
    read -r -a words <<< "$cmd"
    for (( i = 0; i < ${#words[@]}; i++ )); do
        token="${words[$i]}"
        case "$token" in
            "$option")
                if [[ "${words[$(( i + 1 ))]:-}" == "$value" ]]; then
                    return 0
                fi ;;
            "$option"=*)
                if [[ "${token#*=}" == "$value" ]]; then
                    return 0
                fi ;;
        esac
    done
    return 1
}

# Replaces one engine's model and effort flags without disturbing its safety
# flags. Codex rejects repeated --model arguments, so appending an override is
# not sufficient there.
configure_engine_command() {
    local cmd="$1" engine="$2" model="$3" effort="$4" token
    local -a words kept
    local i
    read -r -a words <<< "$cmd"
    for (( i = 0; i < ${#words[@]}; i++ )); do
        token="${words[$i]}"
        if [[ -n "$model" ]]; then
            case "${engine}:${token}" in
                claude:--model|codex:-m|codex:--model)
                    i=$(( i + 1 )); continue ;;
                claude:--model=*|codex:--model=*) continue ;;
            esac
        fi
        if [[ -n "$effort" ]]; then
            case "${engine}:${token}" in
                claude:--effort)
                    i=$(( i + 1 )); continue ;;
                claude:--effort=*) continue ;;
                codex:-c|codex:--config)
                    if [[ "${words[$(( i + 1 ))]:-}" == model_reasoning_effort=* ]]; then
                        i=$(( i + 1 )); continue
                    fi ;;
                codex:--config=model_reasoning_effort=*) continue ;;
            esac
        fi
        kept+=("$token")
    done
    case "$engine" in
        claude)
            [[ -z "$model" ]] || kept+=(--model "$model")
            [[ -z "$effort" ]] || kept+=(--effort "$effort") ;;
        codex)
            [[ -z "$model" ]] || kept+=(-m "$model")
            [[ -z "$effort" ]] \
                || kept+=(-c "model_reasoning_effort=\"${effort}\"") ;;
    esac
    printf '%s' "${kept[*]}"
}

# Restricts model values to one shell word. The CLIs remain authoritative for
# whether the installed version, account, and provider support that model.
require_model_value() {
    local option="$1" value="$2"
    [[ -n "$value" && "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._:/@+%=-]*(\[1m\])?$ ]] \
        || { fail "${option} needs a model name without spaces, got '${value}'"; return 1; }
}

# Effort names are CLI contracts rather than free-form model identifiers, so a
# typo can be rejected before the workflow creates a document or spends a turn.
require_effort_value() {
    local option="$1" value="$2"
    case "$option:$value" in
        --claude-effort:low|--claude-effort:medium|--claude-effort:high|\
        --claude-effort:xhigh|--claude-effort:max|\
        --codex-effort:minimal|--codex-effort:low|--codex-effort:medium|\
        --codex-effort:high|--codex-effort:xhigh) return 0 ;;
        --claude-effort:*)
            fail "--claude-effort must be low, medium, high, xhigh, or max; got '${value}'" ;;
        *)
            fail "--codex-effort must be minimal, low, medium, high, or xhigh; got '${value}'" ;;
    esac
}

# Applies requested engine settings wherever that CLI currently sits. This is
# deliberately independent of agent1/agent2 so --swap-agents is order-neutral.
apply_engine_settings() {
    local claude_found=0 codex_found=0 engine
    engine="$(agent_command_engine "$AGENT1_CMD")"
    case "$engine" in
        claude)
            claude_found=1
            AGENT1_CMD="$(configure_engine_command "$AGENT1_CMD" claude \
                "$CLAUDE_MODEL_OPTION" "$CLAUDE_EFFORT_OPTION")" ;;
        codex)
            codex_found=1
            AGENT1_CMD="$(configure_engine_command "$AGENT1_CMD" codex \
                "$CODEX_MODEL_OPTION" "$CODEX_EFFORT_OPTION")" ;;
    esac
    engine="$(agent_command_engine "$AGENT2_CMD")"
    case "$engine" in
        claude)
            claude_found=1
            AGENT2_CMD="$(configure_engine_command "$AGENT2_CMD" claude \
                "$CLAUDE_MODEL_OPTION" "$CLAUDE_EFFORT_OPTION")" ;;
        codex)
            codex_found=1
            AGENT2_CMD="$(configure_engine_command "$AGENT2_CMD" codex \
                "$CODEX_MODEL_OPTION" "$CODEX_EFFORT_OPTION")" ;;
    esac
    if [[ -n "$CLAUDE_MODEL_OPTION$CLAUDE_EFFORT_OPTION" \
          && "$claude_found" -eq 0 ]]; then
        die "Claude model or effort options require a direct 'claude' agent command"
    fi
    if [[ -n "$CODEX_MODEL_OPTION$CODEX_EFFORT_OPTION" \
          && "$codex_found" -eq 0 ]]; then
        die "Codex model or effort options require a direct 'codex' agent command"
    fi
}

# Captures the effective setting of a directly configured engine. The markers
# contain characters that model values reject, so valid aliases such as
# Claude's `default` cannot be mistaken for driver metadata.
current_engine_setting() {
    local wanted_engine="$1" setting="$2" command engine value opaque=0
    for command in "$AGENT1_CMD" "$AGENT2_CMD"; do
        engine="$(agent_command_engine "$command")"
        [[ "$engine" != "custom" ]] || opaque=1
        [[ "$engine" == "$wanted_engine" ]] || continue
        value="$(agent_command_setting "$command" "$engine" "$setting")"
        printf '%s' "${value:-(default)}"
        return 0
    done
    [[ "$opaque" -eq 0 ]] && printf '(unconfigured)' || printf '(custom)'
}

# Loads saved selections on resume and rejects an explicit attempt to change
# them. Legacy documents have none of these fields and retain their historical
# AGENT1_CMD/AGENT2_CMD behavior.
prepare_saved_engine_settings() {
    local doc="$1" key value current present=0 requested
    local claude_model claude_effort codex_model codex_effort
    claude_model="$(frontmatter "$doc" claude_model)"
    claude_effort="$(frontmatter "$doc" claude_effort)"
    codex_model="$(frontmatter "$doc" codex_model)"
    codex_effort="$(frontmatter "$doc" codex_effort)"
    for value in "$claude_model" "$claude_effort" "$codex_model" "$codex_effort"; do
        [[ -z "$value" ]] || present=$(( present + 1 ))
    done
    if [[ "$present" -eq 0 ]]; then
        requested="${CLAUDE_MODEL_OPTION}${CLAUDE_EFFORT_OPTION}"
        requested="${requested}${CODEX_MODEL_OPTION}${CODEX_EFFORT_OPTION}"
        [[ -z "$requested" ]] \
            || die "this task predates model options; resume without them or start a new task"
        return 0
    fi
    [[ "$present" -eq 4 ]] \
        || die "model metadata is incomplete in ${doc#"$REPO_ROOT"/}; inspect the document"

    case "$claude_model" in
        '(default)'|'(custom)'|'(unconfigured)') ;;
        *) require_model_value --claude-model "$claude_model" || exit "$EXIT_ERROR" ;;
    esac
    case "$claude_effort" in
        '(default)'|'(custom)'|'(unconfigured)') ;;
        *) require_effort_value --claude-effort "$claude_effort" || exit "$EXIT_ERROR" ;;
    esac
    case "$codex_model" in
        '(default)'|'(custom)'|'(unconfigured)') ;;
        *) require_model_value --codex-model "$codex_model" || exit "$EXIT_ERROR" ;;
    esac
    case "$codex_effort" in
        '(default)'|'(custom)'|'(unconfigured)') ;;
        *) require_effort_value --codex-effort "$codex_effort" || exit "$EXIT_ERROR" ;;
    esac

    for key in claude_model claude_effort codex_model codex_effort; do
        case "$key" in
            claude_model)  value="$claude_model";  current="$CLAUDE_MODEL_OPTION" ;;
            claude_effort) value="$claude_effort"; current="$CLAUDE_EFFORT_OPTION" ;;
            codex_model)   value="$codex_model";   current="$CODEX_MODEL_OPTION" ;;
            codex_effort)  value="$codex_effort";  current="$CODEX_EFFORT_OPTION" ;;
        esac
        [[ -z "$current" || "$current" == "$value" ]] \
            || die "${key//_/-} differs from this task ('${current}' requested, "\
"'${value}' recorded); start a new task to change model settings"
    done

    [[ -n "$CLAUDE_MODEL_OPTION" || "$claude_model" == '(default)' \
        || "$claude_model" == '(custom)' || "$claude_model" == '(unconfigured)' ]] \
        || CLAUDE_MODEL_OPTION="$claude_model"
    [[ -n "$CLAUDE_EFFORT_OPTION" || "$claude_effort" == '(default)' \
        || "$claude_effort" == '(custom)' || "$claude_effort" == '(unconfigured)' ]] \
        || CLAUDE_EFFORT_OPTION="$claude_effort"
    [[ -n "$CODEX_MODEL_OPTION" || "$codex_model" == '(default)' \
        || "$codex_model" == '(custom)' || "$codex_model" == '(unconfigured)' ]] \
        || CODEX_MODEL_OPTION="$codex_model"
    [[ -n "$CODEX_EFFORT_OPTION" || "$codex_effort" == '(default)' \
        || "$codex_effort" == '(custom)' || "$codex_effort" == '(unconfigured)' ]] \
        || CODEX_EFFORT_OPTION="$codex_effort"
    apply_engine_settings
}

# Exchanges the complete commands so their models, permissions, and flags move
# with their agents. Calling this more than once in one invocation has no effect.
swap_agent_commands() {
    [[ "$AGENTS_SWAPPED" -eq 0 ]] || return 0
    local command="$AGENT1_CMD"
    AGENT1_CMD="$AGENT2_CMD"
    AGENT2_CMD="$command"
    AGENTS_SWAPPED=1
}

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

# The remote-tracking ref to measure and publish against. A missing ref is a
# setup error: falling back to a local branch would let an entire run finish
# before publication discovers that its recorded target is unavailable.
pr_base_ref() {
    local ref="refs/remotes/origin/${PR_BASE_BRANCH}"
    git -C "$REPO_ROOT" show-ref --verify --quiet "$ref" || return 1
    printf '%s' "$ref"
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

# Content fingerprint of everything an agent could edit: tracked files as the
# worktree holds them, plus every untracked file Git does not ignore. Compared
# around `agent2`'s turns, which are read-only with respect to the codebase —
# the Git snapshot above cannot see an unstaged edit, and an unstaged edit is
# exactly what a reviewer that "just fixed it" would leave behind.
#
# `.agents/work/` is gitignored, so the working document, the transcripts, and
# the round snapshots — all of which change during a turn by design — are
# excluded by construction rather than by a list that could drift.
worktree_state() {
    git -C "$REPO_ROOT" diff HEAD --
    (
        cd "$REPO_ROOT" || exit 1
        git ls-files --others --exclude-standard -z \
            | while IFS= read -r -d '' file; do
                  local mode object target
                  if [[ -L "$file" ]]; then
                      mode=120000
                      target="$(readlink "$file")"
                      object="$(printf '%s' "$target" | git hash-object --stdin)"
                  else
                      [[ -x "$file" ]] && mode=100755 || mode=100644
                      object="$(git hash-object -- "$file")"
                  fi
                  printf '%s %s %s\n' "$mode" "$object" "$file"
              done
    )
}

# Every `## ` heading outside fenced code, in document order. Fenced headings
# are content, exactly as they are for section_raw().
section_names() {
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
            if (fence == "" && /^## /) {
                name = $0
                sub(/^## /, "", name)
                print name
            }
        }
    ' "$1"
}

# Whether the active turn may edit a section. Ownership alone is insufficient:
# it would let an agent rewrite its own completed rounds and alter the audit
# record later. The current state therefore opens only the sections that turn
# actually needs; every other section, including earlier ones by the same role,
# stays byte-identical.
section_is_mutable() {
    local status="$1" plan_round="$2" impl_round="$3" name="$4"
    local next_impl_round="$(( impl_round + 1 ))"
    case "${status}|${name}" in
        plan-requested\|Task|plan-requested\|Questions|plan-requested\|Plan)
            return 0 ;;
        plan-review-requested\|"Plan Review — Round ${plan_round}")
            return 0 ;;
        plan-reviewed\|Questions|plan-reviewed\|Plan|\
        plan-reviewed\|"Plan Dispositions — Round ${plan_round}"|\
        plan-reviewed\|"Implementation — Round ${impl_round}"|\
        plan-reviewed\|"Pull Request")
            return 0 ;;
        implementation-review-requested\|"Implementation Review — Round ${impl_round}")
            return 0 ;;
        implementation-reviewed\|Questions|\
        implementation-reviewed\|"Implementation — Round ${next_impl_round}"|\
        implementation-reviewed\|"Implementation Dispositions — Round ${impl_round}"|\
        implementation-reviewed\|Outcome|implementation-reviewed\|"Pull Request"|\
        implementation-reviewed\|"Manual Testing")
            return 0 ;;
    esac
    return 1
}

# Digest of every section closed during this turn. Section names are included,
# so adding or deleting an unauthorized section is caught alongside editing it.
protected_sections() {
    local doc="$1" status="$2" plan_round="$3" impl_round="$4" name
    section_names "$doc" | while IFS= read -r name; do
        if [[ "$name" == "Log" ]]; then continue; fi
        if section_is_mutable "$status" "$plan_round" "$impl_round" "$name"; then
            continue
        fi
        printf '%s=%s\n' "$name" "$(section_raw "$doc" "$name" | cksum)"
    done
}

# The non-blank entries in `## Log`, the one section both roles write. Comments
# are dropped so removing the template's placeholder does not read as rewriting
# another agent's entry.
log_entries() {
    section "$1" "Log" | awk 'NF'
}

verify_sections() {
    local doc="$1" before="$2" who="$3" status="$4" plan_round="$5"
    local impl_round="$6" now
    now="$(protected_sections "$doc" "$status" "$plan_round" "$impl_round")"
    [[ "$before" == "$now" ]] && return 0
    info "closed sections changed during ${who}'s turn (- before, + after):"
    diff <(printf '%s\n' "$before") <(printf '%s\n' "$now") >&2 || true
    die "${who} rewrote a section that is closed during '${status}'; "\
"the document is left as written for you to inspect"
}

verify_log_appended() {
    local doc="$1" before="$2" who="$3" now added
    now="$(log_entries "$doc")"
    if [[ -z "$before" ]]; then
        added="$now"
    elif [[ "$now" == "$before"$'\n'* ]]; then
        added="${now#"$before"$'\n'}"
    else
        added=""
    fi
    # Exactly one non-blank line is required. This rejects no-op turns and text
    # appended to the end of the previous entry as well as ordinary rewrites.
    [[ -n "$added" && "$added" != *$'\n'* ]] && return 0
    info "## Log before ${who}'s turn (- before, + after):"
    diff <(printf '%s\n' "$before") <(printf '%s\n' "$now") >&2 || true
    die "${who} must append exactly one new ## Log line without changing "\
"earlier entries"
}

require_doc() {
    local doc="$1"
    [[ -f "$doc" ]] || die "no working document at ${doc} — run 'pair.sh start' first"
}

# Adds the default PR target to a document created before that field existed.
# Unlike the starting branch and commit, this value is known: the workflow had
# only one target before the field was introduced.
backfill_pr_base_branch() {
    local doc="$1"
    [[ -n "$(frontmatter "$doc" pr_base_branch)" ]] && return 0

    local tmp; tmp="$(mktemp "${doc}.XXXXXX")"
    awk '
        NR == 1 && $0 == "---" { inside = 1; print; next }
        inside && $0 == "---" {
            print "pr_base_branch: master"
            inside = 0
        }
        { print }
    ' "$doc" > "$tmp" && mv "$tmp" "$doc" \
        || { rm -f "$tmp"; die "could not backfill pr_base_branch in ${doc}"; }
    info "backfilled pr_base_branch: master in ${doc#"$REPO_ROOT"/}"
}

# Adds question provenance to a document created before that field existed.
# A document already paused on a question carries its origin in the legacy
# resume field; every other state starts with no active question.
backfill_question_origin() {
    local doc="$1"
    [[ -n "$(frontmatter "$doc" question_origin)" ]] && return 0

    local origin=none status resume legacy=1 key
    status="$(frontmatter "$doc" status)"
    resume="$(frontmatter "$doc" resume_status)"
    if [[ "$status" == "questions-pending" ]]; then
        for key in claude_model claude_effort codex_model codex_effort; do
            [[ -z "$(frontmatter "$doc" "$key")" ]] || legacy=0
        done
        [[ "$legacy" -eq 1 ]] \
            || die "question_origin is missing from current-format "\
"${doc#"$REPO_ROOT"/}; restore its recorded value before resuming"
        case "$resume" in
            plan-requested|plan-reviewed|implementation-reviewed) origin="$resume" ;;
            *) die "cannot recover question_origin in ${doc#"$REPO_ROOT"/}: "\
"legacy resume_status is '${resume:-empty}'; inspect the document before resuming" ;;
        esac
    fi

    local tmp; tmp="$(mktemp "${doc}.XXXXXX")"
    awk -v origin="$origin" '
        NR == 1 && $0 == "---" { inside = 1; print; next }
        inside && $0 == "---" {
            print "question_origin: " origin
            inside = 0
        }
        { print }
    ' "$doc" > "$tmp" && mv "$tmp" "$doc" \
        || { rm -f "$tmp"; die "could not backfill question_origin in ${doc}"; }
    info "backfilled question_origin: ${origin} in ${doc#"$REPO_ROOT"/}"
}

# Starting branch metadata matters only to publication. A legacy run may keep
# taking agent turns without it, but --create-pr must fail before the next turn:
# its original HEAD cannot be reconstructed safely after the fact.
validate_run_metadata() {
    local doc="$1"
    backfill_pr_base_branch "$doc"
    backfill_question_origin "$doc"
    [[ "$CREATE_PR" -eq 1 ]] || return 0

    local key missing=""
    for key in base_branch start_commit; do
        [[ -n "$(frontmatter "$doc" "$key")" ]] \
            || missing="${missing:+${missing}, }${key}"
    done
    [[ -z "$missing" ]] && return 0

    local number; number="$(frontmatter "$doc" issue_number)"
    die "${doc#"$REPO_ROOT"/} predates metadata required by --create-pr "\
"(missing: ${missing}); continue without --create-pr, or start a replacement "\
"with '.agents/workflows/pair.sh start ${number:-<issue>} --slug <new-name>'"
}

# Frontmatter the driver owns. An agent that rewrote these could retarget the
# issue, move the review's diff baseline, or clear `dirty_at_start` and make a
# worktree that was already dirty publishable.
readonly IMMUTABLE_KEYS="issue issue_number issue_title agent1 agent2 claude_model "\
"claude_effort codex_model codex_effort base_commit base_branch start_commit "\
"pr_base_branch dirty_at_start max_rounds changeset_digest "\
"reviewed_changeset_digest question_origin"

# Prevents a resumed task from silently assigning its existing plan or review
# to different agents. The selected executables are fixed when `start` creates
# the document, and the caller repeats --swap-agents when that selection was
# swapped.
validate_agent_selection() {
    local doc="$1" expected1 expected2 selected1 selected2
    expected1="$(frontmatter "$doc" agent1)"
    expected2="$(frontmatter "$doc" agent2)"
    selected1="$(agent_command_name "$AGENT1_CMD")"
    selected2="$(agent_command_name "$AGENT2_CMD")"
    if [[ "$expected1" != "$selected1" || "$expected2" != "$selected2" ]]; then
        die "agent selection differs from this task (agent1=${expected1}, "\
"agent2=${expected2}); use the same agent executables and --swap-agents choice "\
"that created it"
    fi
}

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

# Releases the lock, restores the signal's default action, and terminates with
# that signal instead of letting the driver resume without mutual exclusion.
handle_signal() {
    local signal="$1"
    release_lock
    trap - "$signal"
    kill -s "$signal" "$$"
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
    trap release_lock EXIT
    trap 'handle_signal INT' INT
    trap 'handle_signal TERM' TERM
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
                fail "that URL is for ${host}/${want}, but this repository is "\
"${here_host}/${here}"
                return 1
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
        || { fail "--slug must be a plain name (letters, digits, dot, dash, "\
"underscore), got '$1'"; return 1; }
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
        || die "usage: pair.sh start <issue-number|issue-url> [--slug <name>] "\
"[--max-rounds N] [--swap-agents] [model and effort options]"

    local slug="" max_rounds="2"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --slug)       slug="${2:-}"; require_safe_slug "$slug" || exit "$EXIT_ERROR"; shift 2 ;;
            --allow-dirty) ALLOW_DIRTY=1; shift ;;
            --swap-agents|--sa) swap_agent_commands; shift ;;
            --claude-model)
                CLAUDE_MODEL_OPTION="${2:-}"
                require_model_value --claude-model "$CLAUDE_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --claude-effort)
                CLAUDE_EFFORT_OPTION="${2:-}"
                require_effort_value --claude-effort "$CLAUDE_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-model)
                CODEX_MODEL_OPTION="${2:-}"
                require_model_value --codex-model "$CODEX_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-effort)
                CODEX_EFFORT_OPTION="${2:-}"
                require_effort_value --codex-effort "$CODEX_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --max-rounds|--mr)
                max_rounds="${2:-}"
                require_positive_int --max-rounds "$max_rounds" || exit "$EXIT_ERROR"
                shift 2 ;;
            --task) die "a GitHub issue is the only input; pass an issue number or URL" ;;
            *) die "unknown option: $1" ;;
        esac
    done

    apply_engine_settings

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

    # Freeze a real remote PR target before spending an agent turn. Local
    # branches may lag, carry unpushed commits, or merely hide a typo in
    # PR_BASE_BRANCH; none describes what GitHub will compare the PR against.
    local base_commit target_ref
    target_ref="$(pr_base_ref)" \
        || die "origin/${PR_BASE_BRANCH} is unavailable; fetch it or correct "\
"PR_BASE_BRANCH before starting the workflow"
    base_commit="$(git -C "$REPO_ROOT" merge-base "$target_ref" HEAD)" \
        || die "cannot find a merge-base between HEAD and origin/${PR_BASE_BRANCH}"
    base_commit="$(git -C "$REPO_ROOT" rev-parse --short "$base_commit")"
    # The branch the run is cut from. `base_commit` cannot stand in for it here:
    # it is deliberately the merge-base, so it says nothing about whether the
    # starting point carried unmerged work. Recorded now because `create_pr`
    # cannot recover it later, once HEAD has moved to the task branch. A
    # detached HEAD records the commit, which reads correctly where it is used.
    local base_branch; base_branch="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
    [[ "$base_branch" != "HEAD" ]] \
        || base_branch="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
    # Unlike `base_commit`, this is the exact immutable point from which the
    # task starts. Publication uses it to distinguish the task's own version
    # bump and ancestry from commits inherited from a parent branch.
    local start_commit; start_commit="$(git -C "$REPO_ROOT" rev-parse HEAD)"
    local carried
    carried="$(git -C "$REPO_ROOT" rev-list --count \
        "${target_ref}..${start_commit}")"
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
"review scope; publication is disabled for this task, so a later "\
"run --create-pr will be refused"

    mkdir -p "$(dirname "$doc")"
    # Record executables for role assignment and engine settings independently,
    # so swapping roles does not change what --claude-* or --codex-* means.
    local a1 a2 claude_model claude_effort codex_model codex_effort
    a1="$(agent_command_name "$AGENT1_CMD")"
    a2="$(agent_command_name "$AGENT2_CMD")"
    claude_model="$(current_engine_setting claude model)"
    claude_effort="$(current_engine_setting claude effort)"
    codex_model="$(current_engine_setting codex model)"
    codex_effort="$(current_engine_setting codex effort)"

    # The body goes in verbatim from a file rather than through a substitution:
    # issue text routinely contains backslashes and ampersands, which awk's
    # gsub would silently reinterpret.
    local body_file; body_file="$(dirname "$doc")/.issue-body"
    printf '%s\n' "$body" > "$body_file"

    NUMBER="$number" TITLE="$title" ISSUE="$url" ROUNDS="$max_rounds" \
    SLUG="$slug" BASE="$base_commit" NOW="$now" A1="$a1" A2="$a2" DIRTY="$dirty" \
    CLAUDEMODEL="$claude_model" CLAUDEEFFORT="$claude_effort" \
    CODEXMODEL="$codex_model" CODEXEFFORT="$codex_effort" \
    BASEBRANCH="$base_branch" STARTCOMMIT="$start_commit" \
    PRBASE="$PR_BASE_BRANCH" \
    awk -v bodyfile="$body_file" '
        /ISSUE_BODY/ {
            # Demote headings from the issue by one level so they nest under
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
                if (fence == "" && line ~ /^#/) {
                    line = (line ~ /^##/ ? "#" line : "##" line)
                }
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
          $0 = put($0, "PR_BASE_BRANCH", ENVIRON["PRBASE"])
          $0 = put($0, "BASE_BRANCH",  ENVIRON["BASEBRANCH"])
          $0 = put($0, "START_COMMIT", ENVIRON["STARTCOMMIT"])
          $0 = put($0, "CREATED_AT",   ENVIRON["NOW"])
          $0 = put($0, "TASK_SLUG",    ENVIRON["SLUG"])
          $0 = put($0, "AGENT1_NAME",  ENVIRON["A1"])
          $0 = put($0, "AGENT2_NAME",  ENVIRON["A2"])
          $0 = put($0, "CLAUDE_MODEL", ENVIRON["CLAUDEMODEL"])
          $0 = put($0, "CLAUDE_EFFORT", ENVIRON["CLAUDEEFFORT"])
          $0 = put($0, "CODEX_MODEL", ENVIRON["CODEXMODEL"])
          $0 = put($0, "CODEX_EFFORT", ENVIRON["CODEXEFFORT"])
          if ($0 ~ /^max_rounds: /)     $0 = "max_rounds: "     ENVIRON["ROUNDS"]
          if ($0 ~ /^dirty_at_start: /) $0 = "dirty_at_start: " ENVIRON["DIRTY"]
          print }
    ' "$TEMPLATE" > "$doc"

    rm -f "$body_file"

    info "created ${doc#"$REPO_ROOT"/} from issue #${number} at base ${base_commit}"
    info "  ${title}"
    [[ "$carried" -eq 0 ]] \
        || info "starting point '${base_branch}' at "\
"$(git -C "$REPO_ROOT" rev-parse --short "$start_commit") carries ${carried} "\
"commit(s) not in ${PR_BASE_BRANCH}; they are context, not this task's review scope"
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
        fence == "" && $0 == "### Reviewer notes" {
            reviewer_notes++
            area = "other"
            next
        }
        fence == "" && /^### / { area = "other"; next }
        area == "summary" && /[^[:space:]]/ { summary_text = 1 }
        area == "changes" && /[^[:space:]]/ { changes_text = 1 }
        END {
            exit !(summary == 1 && changes == 1 && summary_text && changes_text &&
                   reviewer_notes <= 1)
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

# Adds the driver's stacking paragraph to an agent-written Reviewer notes
# section, or creates that section when the agent did not provide one. Heading
# recognition ignores fenced Markdown examples, matching promote_pr_headings.
merge_reviewer_note() {
    local note="$1"
    NOTE="$note" awk '
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
        function emit(line) {
            print line
            last_blank = (line == "")
        }
        function add_note() {
            if (!last_blank) emit("")
            emit(ENVIRON["NOTE"])
            emit("")
            inserted = 1
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
            if (fence == "" && in_notes && /^## / &&
                $0 != "## Reviewer notes") {
                add_note()
                in_notes = 0
            }
            emit($0)
            if (fence == "" && $0 == "## Reviewer notes") in_notes = 1
        }
        END {
            if (in_notes) {
                add_note()
            } else if (!inserted) {
                if (!last_blank) emit("")
                emit("## Reviewer notes")
                add_note()
            }
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
            if (substr($0, length($0) - length(suffix) + 1) != suffix) {
                bad = 1
            }
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

# Content, type, and mode digest of the complete prospective changeset.
# Deliberately computed from the working files rather than from a patch, so
# committing does not change it: the digest recorded when the review finished
# must still match on a retry that has already committed part of the work.
changeset_digest() {
    local base="$1" file path mode object target
    changeset_files "$base" | while IFS= read -r file; do
        path="${REPO_ROOT}/${file}"
        if [[ -L "$path" ]]; then
            mode=120000
            target="$(readlink "$path")"
            object="$(printf '%s' "$target" | git -C "$REPO_ROOT" hash-object --stdin)"
            printf '%s %s %s\n' "$mode" "$object" "$file"
        elif [[ -f "$path" ]]; then
            [[ -x "$path" ]] && mode=100755 || mode=100644
            object="$(git -C "$REPO_ROOT" hash-object -- "$file")"
            printf '%s %s %s\n' "$mode" "$object" "$file"
        else
            printf 'deleted %s\n' "$file"
        fi
    done | cksum | tr -s ' ' '-'
}

# Fingerprint of driver-owned per-round snapshots. They live in the writable,
# gitignored work directory, so the ordinary worktree guard cannot see them.
# Comparing this around every turn keeps an agent from rewriting the baseline
# that a later reviewer is told to trust.
rounds_state() {
    local dir="$1" file
    [[ -d "$dir" ]] || return 0
    for file in "$dir"/*; do
        if [[ -L "$file" ]]; then
            printf '%s=symlink:%s\n' "${file##*/}" "$(readlink "$file")"
        elif [[ -f "$file" ]]; then
            printf '%s=file:%s\n' "${file##*/}" "$(cksum < "$file")"
        fi
    done
}

# The whole changeset as one patch, including files Git does not track yet —
# a new test file is exactly the kind of thing a later round must be able to
# see. `--no-index` exits 1 when it finds a difference, which is the normal
# case here.
changeset_patch() {
    local base="$1" file
    # A baseline that no longer resolves is create_pr's problem to report; this
    # snapshot is a reviewing aid, and failing the handoff over it would abort
    # a run that is otherwise fine.
    git -C "$REPO_ROOT" cat-file -e "${base}^{commit}" 2>/dev/null || base="HEAD"
    git -C "$REPO_ROOT" diff "$base" --
    (
        cd "$REPO_ROOT" || exit 1
        git ls-files --others --exclude-standard -z \
            | while IFS= read -r -d '' file; do
                  git diff --no-index --binary -- /dev/null "$file" || true
              done
    )
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
    command -v gh >/dev/null 2>&1 \
        || die "'gh' is not on PATH; it is required to open the pull request"
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

    local base start target_branch changes task_changes dirty
    base="$(frontmatter "$doc" base_commit)"
    git -C "$REPO_ROOT" cat-file -e "${base}^{commit}" 2>/dev/null \
        || die "base_commit '${base}' is not available; cannot validate the PR changeset"
    start="$(frontmatter "$doc" start_commit)"
    git -C "$REPO_ROOT" cat-file -e "${start}^{commit}" 2>/dev/null \
        || die "start_commit '${start}' is not available; cannot validate where the task began"
    target_branch="$(frontmatter "$doc" pr_base_branch)"
    local base_ref="refs/remotes/origin/${target_branch}"
    git -C "$REPO_ROOT" show-ref --verify --quiet "$base_ref" \
        || die "origin/${target_branch} is unavailable; fetch the pull request target and "\
"start a new workflow run before publishing"
    local target_base
    target_base="$(git -C "$REPO_ROOT" merge-base "$base_ref" HEAD)" \
        || die "cannot find a merge-base between HEAD and origin/${target_branch}"
    [[ "$(git -C "$REPO_ROOT" rev-parse "$base")" == "$target_base" ]] \
        || die "the recorded PR base ${base} is not the pull request's merge-base "\
"${target_base}; start a new workflow run so the task and PR scopes are recalculated"

    # The first publication attempt must still stand exactly where the run
    # started. Once the driver has created the task branch, retries may be ahead
    # of that point only on that branch, because earlier publication steps may
    # already have committed the reviewed files.
    local current current_head
    current="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
    current_head="$(git -C "$REPO_ROOT" rev-parse HEAD)"
    if [[ "$current" == "$branch" ]]; then
        git -C "$REPO_ROOT" merge-base --is-ancestor "$start" HEAD \
            || die "task branch '${branch}' no longer descends from the recorded "\
"starting commit ${start}; nothing was published"
    else
        [[ "$current_head" == "$(git -C "$REPO_ROOT" rev-parse "$start")" ]] \
            || die "HEAD moved from the recorded starting commit ${start} to "\
"${current_head}; return to the starting point or start a new workflow run"
    fi

    changes="$(changeset_files "$base")" \
        || die "could not determine the complete changeset since ${base}"
    task_changes="$(changeset_files "$start")" \
        || die "could not determine the task changeset since ${start}"
    dirty="$(git -C "$REPO_ROOT" status --porcelain)"
    if [[ -z "$changes" ]]; then
        info "no changes since ${base}; skipping the pull request"
        return 0
    fi

    # Publication is bound to the changeset the review actually finished on.
    # `git add -A` below stages whatever is in the worktree, so without this a
    # retry after a failed push — or any edit made once the run reported
    # `done` — would be committed and published as reviewed work.
    local reviewed current_digest
    reviewed="$(frontmatter "$doc" changeset_digest)"
    current_digest="$(changeset_digest "$start")"
    [[ -n "$reviewed" && "$reviewed" != "none" ]] \
        || die "no reviewed changeset is recorded in the document; the driver "\
"writes it when the run reaches 'done', so this run has nothing to publish"
    [[ "$reviewed" == "$current_digest" ]] \
        || die "the changeset has changed since the review finished "\
"(${reviewed} -> ${current_digest}); nothing was published. Re-run the "\
"workflow so the new state is reviewed, or open the pull request yourself"

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
"### Summary and ### Changes sections and at most one ### Reviewer notes "\
"section; nothing was published"
    body="$(printf '%s\n' "$body" | promote_pr_headings)"

    # Every task must contribute its own version bump and regenerated reports.
    # A stacked parent's copies are part of the PR changeset too, so compare
    # against the exact starting commit rather than the merge-base with the PR
    # target. A retry may already have committed these files successfully.
    local f absent=""
    for f in version.gradle.kts pom.xml dependencies.md; do
        printf '%s\n' "$task_changes" | grep -qx "$f" \
            || absent="${absent:+${absent}, }${f}"
    done
    [[ -z "$absent" ]] \
        || die "not in the changeset after start_commit: ${absent}. AGENTS.md requires a "\
"version bump and regenerated reports in every PR; nothing was published"

    # "The files were touched" does not prove that they are valid. Compare the
    # version against the commit where the task started, require the documented
    # scheme, and validate both generated reports at their version-bearing
    # locations.
    local new_v old_v pom_v
    new_v="$(version_in_file "${REPO_ROOT}/version.gradle.kts")"
    old_v="$(git -C "$REPO_ROOT" show "${start}:version.gradle.kts" 2>/dev/null \
             | version_from_stdin || true)"
    [[ "$new_v" =~ ^2\.0\.0-SNAPSHOT\.[0-9]+$ ]] \
        || die "chordsVersion is '${new_v}', which is not the "\
"2.0.0-SNAPSHOT.<N> scheme; nothing was published"
    [[ "$old_v" =~ ^2\.0\.0-SNAPSHOT\.[0-9]+$ ]] \
        || die "could not read a valid chordsVersion at start commit ${start}; "\
"nothing was published"
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

    # Stacking is determined from the immutable starting commit, never from the
    # current value of a branch ref. The recorded branch remains a useful label
    # if it is later moved, renamed, or deleted. The branch label does not
    # decide whether earlier commits need a reviewer boundary; ancestry does,
    # including when the run continues on an existing task branch.
    local start_label stacked_on="" carried=0 start_short
    start_label="$(frontmatter "$doc" base_branch)"
    stacked_on="$start_label"
    start_short="$(git -C "$REPO_ROOT" rev-parse --short "$start")"
    if [[ -n "$stacked_on" ]]; then
        carried="$(git -C "$REPO_ROOT" rev-list --count \
            "${base_ref}..${start}" 2>/dev/null || printf '0')"
        [[ "$carried" -gt 0 ]] || stacked_on=""
    else
        stacked_on=""
    fi

    # Where the task branch starts. Work stacked on earlier commits is the
    # ordinary case, not an error: those commits ride along until the PR target
    # contains them. A new task branch is cut from the exact HEAD recorded at
    # setup; an existing task branch continues there. The PR still targets
    # ${target_branch}.
    #
    # What is still refused is committing onto a branch that is not this
    # task's. The new branch always starts at the recorded commit and every
    # publication commit lands on it, so the branch the run was started from is
    # never written to, whatever it is called.
    if [[ "$current" == "$branch" ]]; then
        info "already on '${branch}'"
    else
        # A clean worktree here means the changeset since ${base} is already
        # committed — onto the branch the run started from, which is not this
        # task's branch. Moving commits between branches is history rewriting
        # by another name, so it is the user's call, not the driver's.
        [[ -n "$dirty" ]] \
            || die "changes since ${base} are already committed on "\
"'${current}'; move them to '${branch}' before publishing"
        local checkout_error
        if ! checkout_error="$(git -C "$REPO_ROOT" checkout -b \
            "$branch" "$start" 2>&1)"; then
            die "could not create branch '${branch}': ${checkout_error}"
        fi
        info "created branch '${branch}' from '${start_label}' at ${start_short}"
    fi

    # Say plainly what the PR will contain. A reviewer opening a stacked PR
    # sees commits nobody in this run wrote, and the person who started the run
    # should hear that from the driver rather than discover it on GitHub.
    [[ -z "$stacked_on" ]] \
        || info "stacked on '${stacked_on}' at ${start_short}: the pull request "\
"targets ${target_branch} and carries ${carried} earlier commit(s) that are not "\
"ancestors of that target"

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

    # A stacked PR shows commits from its parent branch, and a reviewer has no
    # way to tell those from this task's work. AGENTS.md allows ## Reviewer
    # notes for exactly this: material information the reviewer needs. It says
    # nothing about verification and attributes nothing to an agent, so the
    # rules on both stay intact.
    if [[ -n "$stacked_on" ]]; then
        local stacking_note
        stacking_note="The workflow started from \`${stacked_on}\` at \
\`${start_short}\`. That starting point contains ${carried} commit(s) that are \
not ancestors of \`${target_branch}\`, so this pull request may also show them. \
Review the task commits after \`${start_short}\`."
        body="$(printf '%s\n' "$body" | merge_reviewer_note "$stacking_note")"
    fi

    # AGENTS.md: draft, assigned to the author, base ${target_branch}, no
    # trailing period in the title, no verification detail and no agent
    # attribution in the body.
    body="${body}"$'\n\n'"Fixes #${number}"
    local url
    url="$(gh pr create --draft --assignee @me --base "$target_branch" \
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

# Review sections for one phase, in document order — `Plan Review …` or
# `Implementation Review …`, whatever round suffix the reviewer wrote.
phase_sections() {
    section_names "$1" | awk -v want="$2" 'index($0, want) == 1'
}

# The verdict a review section carries. Only the final non-blank line is
# machine-readable; mentions in findings or discussion cannot advance a round.
review_verdict() {
    section "$1" "$2" | awk '
        NF {
            semantic = $0
            gsub(/[[:space:]]/, "", semantic)
            if (semantic !~ /^---+$/ && semantic !~ /^___+$/ &&
                semantic !~ /^\*\*\*+$/) last = $0
        }
        END {
            gsub(/[*_`]/, "", last)
            sub(/^[[:space:]]+/, "", last)
            sub(/[[:space:]]+$/, "", last)
            last = toupper(last)
            sub(/^VERDICT:[[:space:]]*/, "", last)
            sub(/\.[[:space:]]*$/, "", last)
            sub(/[[:space:]]+$/, "", last)
            if (last == "APPROVE" || last == "APPROVE WITH CHANGES" ||
                last == "REQUEST CHANGES") print last
        }
    '
}

# Finding IDs declared at the start of review lines: P<round>-<n> for the plan,
# I<round>-<n> for the implementation. Prose and other rounds are ignored.
finding_ids() {
    local doc="$1" review="$2" phase="$3" round="$4"
    section "$doc" "$review" | awk -v want="${phase}${round}-" '
        {
            line = $0
            sub(/^[[:space:]]*/, "", line)
            sub(/^[-*][[:space:]]+/, "", line)
            sub(/^#+[[:space:]]+/, "", line)
            sub(/^[0-9]+[.)][[:space:]]+/, "", line)
            sub(/^\|[[:space:]]*/, "", line)
            gsub(/^[*_`]+/, "", line)
            if (index(line, want) == 1) {
                rest = substr(line, length(want) + 1)
                if (match(rest, /^[0-9]+/)) {
                    print want substr(rest, RSTART, RLENGTH)
                }
            }
        }
    ' | sort -u
}

# Valid finding IDs in one exact disposition table. A row is valid only when it
# has an allowed disposition and a non-empty note, and duplicate rows answer
# nothing. Prose mentions do not count.
valid_disposition_ids() {
    section "$1" "$2" | awk -F'|' '
        function trim(value) {
            sub(/^[[:space:]]+/, "", value)
            sub(/[[:space:]]+$/, "", value)
            return value
        }
        /^\|/ {
            id = trim($2)
            disposition = trim($3)
            notes = trim($4)
            if (id ~ /^[PI][0-9]+-[0-9]+$/) {
                rows[id]++
                if (disposition ~ /^(Accepted|Rejected|Deferred)$/ && notes != "") {
                    valid[id]++
                }
            }
        }
        END {
            for (id in rows) if (rows[id] == 1 && valid[id] == 1) print id
        }
    '
}

# Findings from the round's review that its exact disposition table does not
# answer with one valid row.
undispositioned_findings() {
    local doc="$1" review="$2" dispositions="$3" phase="$4" round="$5"
    local id valid missing=""
    valid="$(valid_disposition_ids "$doc" "$dispositions")"
    for id in $(finding_ids "$doc" "$review" "$phase" "$round" || true); do
        printf '%s\n' "$valid" | grep -qx "$id" \
            || missing="${missing:+${missing}, }${id}"
    done
    printf '%s' "$missing"
}

# A review turn must leave a review. Each round writes its own section, so a
# reviewer cannot advance on the previous round's findings, and that section
# must carry content and one of the three verdicts. Without this the state
# machine accepts an empty review and the second opinion becomes a formality.
require_review() {
    local doc="$1" prefix="$2" round="$3" rel_doc="$4"
    local names count last="${prefix} — Round ${round}"
    names="$(phase_sections "$doc" "$prefix" | awk 'NF')"
    count="$(printf '%s' "$names" | grep -c . || true)"
    [[ "$count" -eq "$round" ]] \
        || die "agent2 left ${count} '## ${prefix}' section(s) in ${rel_doc} at "\
"round ${round}; every round records its own review"
    [[ "$(section_heading_count "$doc" "$last")" -eq 1 ]] \
        || die "agent2 must write exactly one '## ${last}' section in ${rel_doc}"
    [[ -n "$(section "$doc" "$last" | tr -d '[:space:]')" ]] \
        || die "'## ${last}' in ${rel_doc} is empty; a review that advances the "\
"workflow has to say what was reviewed"
    [[ -n "$(review_verdict "$doc" "$last")" ]] \
        || die "'## ${last}' in ${rel_doc} states no verdict; it must end with "\
"APPROVE, APPROVE WITH CHANGES, or REQUEST CHANGES"
}

# agent1's side of the same gate. Every finding needs a disposition before its
# phase can be left, and `REQUEST CHANGES` closes the forward move outright:
# the ways on from there are another round, `blocked`, or a question. A
# disposition table cannot overrule the verdict.
require_dispositions() {
    local doc="$1" prefix="$2" disp="$3" forward="$4" new_status="$5"
    local rel_doc="$6" round="$7" review dispositions missing verdict phase
    review="${prefix} — Round ${round}"
    dispositions="${disp} — Round ${round}"
    [[ "$(section_heading_count "$doc" "$review")" -eq 1 ]] \
        || die "expected exactly one '## ${review}' section in ${rel_doc}"
    [[ "$(section_heading_count "$doc" "$dispositions")" -eq 1 ]] \
        || die "agent1 must write exactly one '## ${dispositions}' section in ${rel_doc}"
    case "$prefix" in
        "Plan Review")           phase=P ;;
        "Implementation Review") phase=I ;;
        *) die "internal: unknown review phase '${prefix}'" ;;
    esac
    missing="$(undispositioned_findings \
        "$doc" "$review" "$dispositions" "$phase" "$round")"
    [[ -z "$missing" ]] \
        || die "agent1 left findings from '## ${review}' undispositioned in "\
"${rel_doc}: ${missing}; every finding ID needs one valid row in '## ${dispositions}'"
    verdict="$(review_verdict "$doc" "$review")"
    if [[ "$verdict" == "REQUEST CHANGES" && "$new_status" == "$forward" ]]; then
        die "'## ${review}' ends with REQUEST CHANGES, so '${forward}' is not "\
"available from here; revise and spend a round, or set blocked"
    fi
    return 0
}

# Builds the per-turn prompt. Deliberately thin: the protocol lives in the
# skill, and every invocation is a cold start that reads it fresh.
prompt_for() {
    local role="$1" status="$2" rel_doc="$3" previous="${4:-}"
    cat <<PROMPT
You are ${role} in the Chords pair workflow.

Read these first, in order:
  1. AGENTS.md
  2. .agents/skills/pair-workflow/SKILL.md
  3. ${rel_doc}

The working document is ${rel_doc}. Its status is \`${status}\` and the turn is
yours. Take exactly one turn: do the work the skill assigns to ${role} for this
status, write only the sections that procedure opens, append one line to ## Log,
and update the frontmatter so \`status\` and \`turn\` advance.

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

    if [[ "$status" == "implementation-review-requested" ]]; then
        local review_base
        review_base="$(frontmatter "${REPO_ROOT}/${rel_doc}" start_commit)"
        [[ -n "$review_base" ]] || review_base=HEAD
        cat <<PROMPT_SCOPE

Review only this task's changes from \`${review_base}\` through the current
worktree. The commits between \`base_commit\` and \`start_commit\`, if any, were
inherited from the branch where the run started. They may be read as context,
but they are outside this task and must not produce findings or edits.
PROMPT_SCOPE
    fi

    if [[ -n "$previous" ]]; then
        cat <<PROMPT_PREVIOUS

This is not the first round. The previous round's state is saved at
${previous} — the driver wrote it at that handoff, because nothing else keeps
it: the plan is revised in place and the implementation is never committed.
Read it to isolate what changed since, and review that delta together with the
dispositions for the previous round.
PROMPT_PREVIOUS
    fi

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
            # question_origin is written by the driver when the question is
            # raised and immutable during agent turns. The editable resume
            # value must still name that exact status when answers return.
            local resume origin
            resume="$(frontmatter "$doc" resume_status)"
            origin="$(frontmatter "$doc" question_origin)"
            case "$origin" in
                plan-requested|plan-reviewed|implementation-reviewed) ;;
                *) die "question_origin is '${origin:-empty}' in ${rel_doc}; the "\
"driver cannot verify where this question was raised" ;;
            esac
            [[ "$resume" == "$origin" ]] \
                || die "resume_status is '${resume:-empty}' in ${rel_doc}, but "\
"the question was raised from '${origin}'; refusing to skip workflow phases"
            if [[ -z "$missing" ]]; then
                info "answers found; resuming at '${resume}'"
            else
                info "--accept-defaults: resuming at '${resume}' on agent1's own defaults"
            fi
            set_frontmatter "$doc" \
                "status=${resume}" "turn=agent1" "resume_status=none" \
                "question_origin=none" \
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

    # A later round is told to review the delta, which needs the previous
    # round's state to still exist. Nothing else keeps it: `## Plan` is revised
    # in place and the implementation is never committed. So the driver saves
    # it at each handoff to agent2 and names the previous one in the prompt.
    # The implementation digest saved here is the exact state agent2 receives;
    # a final agent1 turn may finish only while the worktree still matches it.
    local rounds_dir previous="" review_base
    rounds_dir="$(dirname "$doc")/rounds"
    mkdir -p "$rounds_dir"
    review_base="$(frontmatter "$doc" start_commit)"
    [[ -n "$review_base" ]] || review_base=HEAD
    case "$status" in
        plan-review-requested)
            section "$doc" "Plan" > "${rounds_dir}/plan-${plan_round}.md"
            [[ "$plan_round" -le 1 ]] \
                || previous="${rounds_dir}/plan-$(( plan_round - 1 )).md" ;;
        implementation-review-requested)
            changeset_patch "$review_base" \
                > "${rounds_dir}/impl-${impl_round}.patch"
            set_frontmatter "$doc" \
                "reviewed_changeset_digest=$(changeset_digest "$review_base")"
            [[ "$impl_round" -le 1 ]] \
                || previous="${rounds_dir}/impl-$(( impl_round - 1 )).patch" ;;
    esac
    [[ -z "$previous" || -f "$previous" ]] || previous=""
    previous="${previous#"$REPO_ROOT"/}"

    local before after git_before git_after immutable_before protect_task=1
    # Task is established during plan-requested, including after a question
    # round-trip. Its heading remains unique then, but its contents become
    # immutable only after that planning turn advances.
    [[ "$status" == "plan-requested" ]] && protect_task=0
    before="$(cksum < "$doc")"
    git_before="$(git_state)"
    immutable_before="$(immutable_snapshot "$doc" "$protect_task")"
    local sections_before log_before worktree_before="" rounds_before
    sections_before="$(protected_sections \
        "$doc" "$status" "$plan_round" "$impl_round")"
    log_before="$(log_entries "$doc")"
    rounds_before="$(rounds_state "$rounds_dir")"
    [[ "$turn" != "agent2" ]] || worktree_before="$(worktree_state)"

    # Keep a transcript per turn. An unattended run that goes wrong overnight
    # is otherwise unreconstructable: the document records what an agent chose
    # to write down, not what it actually did.
    local turns_dir="$(dirname "$doc")/turns"
    mkdir -p "$turns_dir"
    local n=0 candidate base sequence
    for candidate in "$turns_dir"/*.log; do
        [[ -e "$candidate" ]] || continue
        base="${candidate##*/}"
        sequence="${base%%-*}"
        [[ "$sequence" =~ ^[0-9]+$ ]] || continue
        if [[ "$sequence" -gt "$n" ]]; then
            n="$sequence"
        fi
    done
    n=$(( n + 1 ))
    local log; log="$(printf '%s/%02d-%s.log' "$turns_dir" "$n" "$turn")"

    # Agent commands carry their own whitespace-delimited flags. Split them
    # once into an array so model names such as `opus[1m]` stay literal instead
    # of undergoing pathname expansion. PIPESTATUS, not $?, reports the agent
    # rather than tee.
    local rc
    local -a command_parts
    read -r -a command_parts <<< "$cmd"
    set +e
    (cd "$REPO_ROOT" && "${command_parts[@]}" \
        "$(prompt_for "$turn" "$status" "$rel_doc" "$previous")") 2>&1 | tee "$log"
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

    # agent2 reviews the code; it never edits it. Checked only on its turns,
    # since changing the worktree is the whole point of agent1's. Like the Git
    # comparison above this detects rather than prevents, and it cannot say who
    # moved — but a reviewer that edits what it is reviewing has ended the
    # independence the second opinion is for, so the run stops either way.
    if [[ "$turn" == "agent2" ]]; then
        local worktree_after; worktree_after="$(worktree_state)"
        if [[ "$worktree_before" != "$worktree_after" ]]; then
            info "worktree content changed during ${turn}'s turn (- before, + after):"
            diff <(printf '%s\n' "$worktree_before") \
                 <(printf '%s\n' "$worktree_after") 2>&1 | head -n 40 >&2 || true
            die "${turn} changed the code it was reviewing, which this workflow "\
"forbids; inspect the worktree before continuing"
        fi
    fi

    local rounds_after; rounds_after="$(rounds_state "$rounds_dir")"
    if [[ "$rounds_before" != "$rounds_after" ]]; then
        info "round snapshots changed during ${turn}'s turn (- before, + after):"
        diff <(printf '%s\n' "$rounds_before") \
             <(printf '%s\n' "$rounds_after") >&2 || true
        die "${turn} rewrote a driver-owned review snapshot; inspect ${rounds_dir}"
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

    # Diagnose a mistyped review heading before section ownership reports it
    # as an unexpected protected section. This gate applies only when the
    # reviewer claims to have completed the requested review.
    case "${status}|${new_status}" in
        plan-review-requested\|plan-reviewed)
            require_review "$doc" "Plan Review" "$plan_round" "$rel_doc" ;;
        implementation-review-requested\|implementation-reviewed)
            require_review "$doc" "Implementation Review" \
                "$impl_round" "$rel_doc" ;;
    esac

    # Section ownership is what makes the document an audit record rather than
    # a shared scratchpad. A turn writes its own sections; the other role's
    # must come back byte-identical, and the shared log must only have grown.
    verify_sections "$doc" "$sections_before" "$turn" \
        "$status" "$plan_round" "$impl_round"
    verify_log_appended "$doc" "$log_before" "$turn"

    # Where a question resumes is decided when it is asked, not when it is
    # answered, and it can only be the status the asking turn started from.
    if [[ "$new_status" == "questions-pending" ]]; then
        local new_resume; new_resume="$(frontmatter "$doc" resume_status)"
        [[ "$new_resume" == "$status" ]] \
            || die "${turn} set resume_status to '${new_resume:-empty}' while "\
"asking from '${status}'; a question resumes where it was asked"
        set_frontmatter "$doc" "question_origin=${status}"
    fi

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

    # What the review said gates the move. The driver reads only the three
    # fixed verdict tokens and the finding IDs the skill defines — judging the
    # findings stays with the agents — but "the reviewer replied" and "agent1
    # answered every finding" are structural, and until now neither was
    # required to advance. Skipped when a turn ends at `blocked` or a question:
    # those are the states for a review that could not be completed.
    case "${new_status}" in
        blocked|questions-pending) ;;
        *)
            case "$status" in
                plan-reviewed)
                    require_dispositions "$doc" "Plan Review" "Plan Dispositions" \
                        "implementation-review-requested" "$new_status" "$rel_doc" \
                        "$plan_round" ;;
                implementation-reviewed)
                    require_dispositions "$doc" "Implementation Review" \
                        "Implementation Dispositions" "done" "$new_status" "$rel_doc" \
                        "$impl_round"
                    # `done` is the claim that the task is finished. An empty
                    # outcome leaves nobody able to say what shipped.
                    [[ "$new_status" != "done" \
                       || -n "$(section "$doc" "Outcome" | tr -d '[:space:]')" ]] \
                        || die "agent1 set 'done' with an empty ## Outcome in "\
"${rel_doc}; it states what shipped, what was rejected, and the final "\
"verification result" ;;
            esac ;;
    esac

    # Bind completion and publication to what agent2 actually reviewed. Agent1
    # may update dispositions and outcome on its final turn, but a source,
    # mode, or type change requires another implementation-review round.
    if [[ "$new_status" == "done" ]]; then
        local reviewed_digest current_digest
        reviewed_digest="$(frontmatter "$doc" reviewed_changeset_digest)"
        current_digest="$(changeset_digest "$review_base")"
        [[ -n "$reviewed_digest" && "$reviewed_digest" != "none" ]] \
            || die "agent1 set 'done' without a driver-recorded implementation review"
        [[ "$reviewed_digest" == "$current_digest" ]] \
            || die "agent1 changed the implementation after agent2 reviewed it "\
"(${reviewed_digest} -> ${current_digest}); increment impl_round and request "\
"another implementation review instead of setting done"
        set_frontmatter "$doc" "changeset_digest=${reviewed_digest}"
    fi

    info "advanced: ${status} -> ${new_status}"
    return 0
}

# Names agent commands that remove their CLI's approval or sandbox boundary.
unsafe_agent_roles() {
    local flagged=""
    case " $AGENT1_CMD " in
        *--dangerously-*|*--yolo*|*bypassPermissions*|*danger-full-access*)
            flagged="agent1" ;;
    esac
    case " $AGENT2_CMD " in
        *--dangerously-*|*--yolo*|*bypassPermissions*|*danger-full-access*)
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

# Where the root build keeps its wrapper distributions, caches, and daemon
# registry. Honors GRADLE_USER_HOME so a machine that relocates it is still
# described accurately.
gradle_user_home() { printf '%s' "${GRADLE_USER_HOME:-${HOME}/.gradle}"; }

# Gives a sandboxed Codex implementer the two things the root build needs and
# the `workspace-write` sandbox withholds. Both are outside the workspace:
#
#   * The wrapper takes a lock inside the Gradle user home
#     (wrapper/dists/…/gradle-<version>-bin.zip.lck) before it starts anything,
#     and the sandbox denies that write.
#   * gradle.properties sets `org.gradle.jvmargs`, so Gradle 6.9.4 always runs
#     the build in a forked daemon — `--no-daemon` only makes that daemon
#     single-use — and the daemon binds a loopback TCP port the sandbox denies.
#     Matching the JVM arguments from the client does not avoid the fork, so
#     there is no socket-free way to run this build.
#
# Without both, the implementer reaches `## Implementation` having compiled
# nothing and the run ends `blocked` on the verification rule instead of
# `done`. The default configuration never hits this because Claude holds the
# implementer seat; --swap-agents is what moves Codex into it.
#
# Scoped to agent1 deliberately. Network access inside the sandbox is a real
# widening, and the reviewer does not build, so it has no claim on it.
#
# Only a command that names `workspace-write` itself is widened, in any of the
# spellings codex_sandbox_mode() understands. A command that names no policy is
# left alone rather than assumed: extending a sandbox the caller never asked
# for would be a worse failure than the one this repairs.
grant_implementer_verification_access() {
    [[ "$(agent_command_engine "$AGENT1_CMD")" == codex ]] || return 0
    [[ "$(codex_sandbox_mode "$AGENT1_CMD")" == workspace-write ]] || return 0

    local home; home="$(gradle_user_home)"
    # Agent commands are whitespace-delimited when they are split for
    # execution, so a path with a space cannot be passed through as one word.
    # Say so rather than appending an argument that would silently truncate.
    if [[ "$home" == *[[:space:]]* ]]; then
        info "warning: Gradle user home '${home}' contains whitespace; the "\
"sandboxed implementer cannot be given access to it and will not be able to "\
"verify"
        return 0
    fi
    if [[ ! -d "$home" ]]; then
        info "warning: no Gradle user home at '${home}'; the sandboxed "\
"implementer cannot verify until the root build has populated it once"
        return 0
    fi

    local granted=""
    if ! command_passes_option "$AGENT1_CMD" --add-dir "$home"; then
        AGENT1_CMD+=" --add-dir ${home}"
        granted="the Gradle user home"
    fi
    if ! command_passes_option "$AGENT1_CMD" -c "$CODEX_SANDBOX_NETWORK" \
        && ! command_passes_option "$AGENT1_CMD" --config "$CODEX_SANDBOX_NETWORK"
    then
        AGENT1_CMD+=" -c ${CODEX_SANDBOX_NETWORK}"
        granted="${granted:+${granted} and }sandbox network access"
    fi
    [[ -z "$granted" ]] \
        || info "implementer sandbox widened so the root build can start: "\
"${granted}"
    return 0
}

cmd_step() {
    local slug; slug="$(resolve_slug "${1:-}")" || exit "$EXIT_ERROR"; shift || true
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --accept-defaults|--ad) ACCEPT_DEFAULTS=1; shift ;;
            --allow-unsafe-agents) ALLOW_UNSAFE_AGENTS=1; shift ;;
            --swap-agents|--sa) swap_agent_commands; shift ;;
            --claude-model)
                CLAUDE_MODEL_OPTION="${2:-}"
                require_model_value --claude-model "$CLAUDE_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --claude-effort)
                CLAUDE_EFFORT_OPTION="${2:-}"
                require_effort_value --claude-effort "$CLAUDE_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-model)
                CODEX_MODEL_OPTION="${2:-}"
                require_model_value --codex-model "$CODEX_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-effort)
                CODEX_EFFORT_OPTION="${2:-}"
                require_effort_value --codex-effort "$CODEX_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            *) die "unknown option: $1" ;;
        esac
    done

    require_doc "$(doc_for "$slug")"
    acquire_lock "$slug"
    validate_run_metadata "$(doc_for "$slug")"
    validate_agent_selection "$(doc_for "$slug")"
    prepare_saved_engine_settings "$(doc_for "$slug")"
    validate_agent_permissions
    grant_implementer_verification_access

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
            --swap-agents|--sa) swap_agent_commands; shift ;;
            --claude-model)
                CLAUDE_MODEL_OPTION="${2:-}"
                require_model_value --claude-model "$CLAUDE_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --claude-effort)
                CLAUDE_EFFORT_OPTION="${2:-}"
                require_effort_value --claude-effort "$CLAUDE_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-model)
                CODEX_MODEL_OPTION="${2:-}"
                require_model_value --codex-model "$CODEX_MODEL_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            --codex-effort)
                CODEX_EFFORT_OPTION="${2:-}"
                require_effort_value --codex-effort "$CODEX_EFFORT_OPTION" \
                    || exit "$EXIT_ERROR"
                shift 2 ;;
            *) die "unknown option: $1" ;;
        esac
    done

    [[ "$CREATE_PR" -eq 0 || "$ALLOW_DIRTY" -eq 0 ]] \
        || die "--allow-dirty and --create-pr cannot be used together; a run "\
"that includes pre-existing changes cannot publish them safely"

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

    validate_run_metadata "$doc"
    validate_agent_selection "$doc"
    prepare_saved_engine_settings "$doc"

    if [[ "$CREATE_PR" -eq 1 \
          && "$(frontmatter "$doc" dirty_at_start)" != "no" ]]; then
        die "the worktree was already dirty when this run started; publication "\
"is disabled for this run, so review the result and publish it manually"
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
    grant_implementer_verification_access

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
