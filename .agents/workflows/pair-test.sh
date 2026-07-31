#!/usr/bin/env bash
#
# Regression tests for pair.sh.
#
# Runs against a throwaway repository with stub agents, so nothing here touches
# the real worktree, the real remote, or GitHub. Stubs replace `gh` too, which
# is what keeps the publish path testable without opening a pull request.
#
#   .agents/workflows/pair-test.sh [name-filter]

set -uo pipefail

readonly SUITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DRIVER="${SUITE_DIR}/pair.sh"
FILTER="${1:-}"
readonly ORIGINAL_PATH="$PATH"
R=""
PASS=0
FAIL=0
SANDBOX=""

cleanup() { [[ -n "$SANDBOX" && -d "$SANDBOX" ]] && rm -rf "$SANDBOX"; }
trap cleanup EXIT

ok()   { printf '  ok    %s\n' "$1"; PASS=$(( PASS + 1 )); }
bad()  { printf '  FAIL  %s\n     %s\n' "$1" "$2"; FAIL=$(( FAIL + 1 )); }

# Builds a self-contained repository: a copy of the driver and its skill, a
# fake origin to push to, and stub `gh`/agent commands on PATH.
sandbox() {
    SANDBOX="$(mktemp -d)"
    local repo="${SANDBOX}/repo"
    mkdir -p "${repo}/.agents/workflows" "${repo}/.agents/skills/pair-workflow" "${SANDBOX}/bin"

    cp "$DRIVER" "${repo}/.agents/workflows/pair.sh"
    cp "${SUITE_DIR}/../skills/pair-workflow/template.md" "${repo}/.agents/skills/pair-workflow/"
    printf 'protocol stub\n' > "${repo}/.agents/skills/pair-workflow/SKILL.md"
    printf 'val chordsVersion: String by extra("2.0.0-SNAPSHOT.1")\n' > "${repo}/version.gradle.kts"
    printf 'pom\n' > "${repo}/pom.xml"
    printf 'deps\n' > "${repo}/dependencies.md"
    printf '.agents/work/\n' > "${repo}/.gitignore"

    git -C "$repo" init -q -b master
    git -C "$repo" config user.email t@example.com
    git -C "$repo" config user.name Test
    git -C "$repo" add -A
    git -C "$repo" commit -qm init
    git init -q --bare "${SANDBOX}/origin.git"
    git -C "$repo" remote add origin "${SANDBOX}/origin.git"

    # Stub gh: issue metadata from files, and a recorded no-op for pr create.
    cat > "${SANDBOX}/bin/gh" <<'GH'
#!/usr/bin/env bash
case "$1 $2" in
  "issue view")
      if [[ "$*" == *"--jq .title"* ]]; then
          printf '%s\n' "${STUB_ISSUE_TITLE:-A test issue}"
      else
          issue_body="${STUB_ISSUE_BODY:-}"
          if [[ -z "$issue_body" ]]; then
              issue_body='Problem stated here at some length. Acceptance criteria: '
              issue_body+='it works and a test covers it.'
          fi
          printf '{"number":%s,"title":"%s","body":"%s",'\
'"url":"https://github.com/o/r/issues/%s","state":"OPEN"}\n' \
              "$3" "${STUB_ISSUE_TITLE:-A test issue}" "$issue_body" "$3"
      fi ;;
  "repo view")
      if [[ "$*" == *"--jq .url"* ]]; then
          [[ "${STUB_REPO_URL:-}" != unavailable ]] || exit 1
          printf '%s\n' "${STUB_REPO_URL:-https://github.com/o/r}"
      else
          printf 'o/r\n'
      fi ;;
  "pr view")   [[ -n "${STUB_PR_EXISTS:-}" ]] && printf '%s\n' "$STUB_PR_EXISTS" || exit 1 ;;
  "pr create") echo "${STUB_PR_CREATE_FAILS:+pr create refused}" >&2
               [[ -n "${STUB_PR_CREATE_FAILS:-}" ]] && exit 1
               printf 'https://github.com/o/r/pull/1\n' ;;
  *) exit 1 ;;
esac
GH
    # Stub agent: advances one legal state, or misbehaves on demand.
    cat > "${SANDBOX}/bin/stub-agent" <<'AGENT'
#!/usr/bin/env bash
set -uo pipefail
doc="$(git rev-parse --show-toplevel)/.agents/work/${PAIR_SLUG}/plan.md"
fm() {
    awk -v k="$1" '
        NR == 1 && $0 == "---" { i = 1; next }
        i && $0 == "---" { exit }
        i && index($0, k ":") == 1 {
            sub(/^[^:]*:[ ]*/, ""); print; exit
        }
    ' "$doc"
}
setfm() {
    awk -v k="$1" -v v="$2" '
        NR == 1 && $0 == "---" { i = 1; print; next }
        i && $0 == "---" { i = 0; print; next }
        i && index($0, k ":") == 1 { print k ": " v; next }
        { print }
    ' "$doc" > "$doc.t" && mv "$doc.t" "$doc"
}
case "${STUB_MISBEHAVE:-}" in
  illegal-jump) setfm status done; setfm turn human; exit 0 ;;
  git-write)    git branch "stub-rogue-$$"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  stash)        echo x >> version.gradle.kts; git stash -q
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  restage)      echo y >> pom.xml; git add pom.xml
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  rewrite-meta) setfm dirty_at_start yes ;;
  round-jump)   setfm plan_round 9; setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  loop-no-round) # plan loopback without spending a round
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    setfm status plan-review-requested; setfm turn agent2; exit 0
                fi ;;
  wrong-phase)  # increment the other phase's counter
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    setfm impl_round 2
                    setfm status implementation-review-requested; setfm turn agent2; exit 0
                fi ;;
  wrong-turn)   setfm status plan-review-requested; setfm turn agent1; exit 0 ;;
  agent2-asks)  if [[ "$(fm status)" == plan-review-requested ]]; then
                    setfm resume_status plan-review-requested
                    setfm status questions-pending; setfm turn human; exit 0
                fi ;;
  edit-issue)   awk '/^## Issue$/{print; print "SNEAKY EXTRA CRITERION"; next} {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc" ;;
  edit-hidden)  sed 's/HIDDEN_CRITERION/REWRITTEN_CRITERION/' "$doc" > "$doc.t" \
                    && mv "$doc.t" "$doc"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  duplicate-task) awk '/^## Questions$/ {
                        print "## Task"; print "Second task"; print; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  edit-task)    if [[ "$(fm status)" == plan-review-requested ]]; then
                    awk '/^## Task$/{print; print "CHANGED TASK"; next} {print}' \
                        "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  index-flag)   git update-index --assume-unchanged version.gradle.kts
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  loop-past-plan)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    setfm plan_round "$(( $(fm plan_round) + 1 ))"
                    setfm status plan-review-requested; setfm turn agent2; exit 0
                fi ;;
  loop-past-impl)
                if [[ "$(fm status)" == implementation-reviewed ]]; then
                    setfm impl_round "$(( $(fm impl_round) + 1 ))"
                    setfm status implementation-review-requested; setfm turn agent2; exit 0
                fi ;;
  dup-q)        awk '/^## Questions$/ {
                        print; print ""; print "**Q1.** First?"
                        print "**Q1.** Second?"; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm resume_status "$(fm status)"
                setfm status questions-pending; setfm turn human; exit 0 ;;
  ask)          awk '/^## Questions$/ {
                        print; print ""
                        print "**Q1.** Retry? Default if unanswered: no."; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm resume_status "$(fm status)"
                setfm status questions-pending; setfm turn human; exit 0 ;;
  nothing)      exit 0 ;;
esac
case "$(fm status)" in
  plan-requested)
      awk '/^## Task$/ {
              print; print "Implement the issue."; print "- [ ] It works."; next
           } {print}' "$doc" > "$doc.t" && mv "$doc.t" "$doc"
      setfm status plan-review-requested; setfm turn agent2 ;;
  plan-review-requested)
      setfm status plan-reviewed; setfm turn agent1 ;;
  plan-reviewed)
      setfm status implementation-review-requested; setfm turn agent2
      printf 'work\n' >> "$(git rev-parse --show-toplevel)/src.txt" ;;
  implementation-review-requested)
      setfm status implementation-reviewed; setfm turn agent1 ;;
  implementation-reviewed)
      setfm status done; setfm turn human
      setfm manual_testing "${STUB_MANUAL:-none}" ;;
  *) exit 1 ;;
esac
AGENT
    chmod +x "${SANDBOX}/bin/gh" "${SANDBOX}/bin/stub-agent"
    # Exported here, so sandbox must be called as a plain command: via $( ) the
    # subshell would swallow every one of these.
    export PATH="${SANDBOX}/bin:${ORIGINAL_PATH}"
    export AGENT1_CMD="${SANDBOX}/bin/stub-agent" AGENT2_CMD="${SANDBOX}/bin/stub-agent"
    export PAIR_SLUG=issue-7
    R="$repo"
}

# run <repo> <args...> — sets RC and OUT. Never call inside $( ): the
# assignment would happen in a subshell and never reach the caller.
RC=0
OUT=""
run() {
    local repo="$1"; shift
    OUT="$(cd "$repo" && ./.agents/workflows/pair.sh "$@" 2>&1)"
    RC=$?
    return 0
}

# Asserts a clean rejection: the expected code, exactly one `pair:` line, and no
# working directory left behind. `want` alone cannot see a run that reports a
# problem and then keeps going.
reject() { # reject <name> <substring>
    local name="$1" sub="$2" msgs created
    [[ -n "$FILTER" && "$name" != *"$FILTER"* ]] && return 0
    msgs="$(printf '%s\n' "$OUT" | grep -c '^pair:')"
    created="$(ls "$R/.agents/work" 2>/dev/null | tr '\n' ' ')"
    if [[ "$RC" != 1 ]]; then
        bad "$name" "expected exit 1, got ${RC}"
    elif ! printf '%s' "$OUT" | grep -q "$sub"; then
        bad "$name" "expected output matching '${sub}'"
    elif [[ "$msgs" != 1 ]]; then
        bad "$name" "expected one message, got ${msgs}: $(printf '%s' "$OUT" | tr '\n' ' ')"
    elif [[ -n "$created" ]]; then
        bad "$name" "left work directories behind: ${created}"
    else
        ok "$name"
    fi
    return 0
}

want() { # want <name> <expected-code> [substring] — reads RC/OUT from the last run
    local name="$1" exp="$2" got="$RC" sub="${3:-}"
    [[ -n "$FILTER" && "$name" != *"$FILTER"* ]] && return 0
    if [[ "$got" != "$exp" ]]; then
        local tail_output; tail_output="$(printf '%s' "$OUT" | tail -2 | tr '\n' ' ')"
        bad "$name" "expected exit ${exp}, got ${got}: ${tail_output}"
        return 0
    fi
    if [[ -n "$sub" ]] && ! printf '%s' "$OUT" | grep -q "$sub"; then
        local tail_output; tail_output="$(printf '%s' "$OUT" | tail -2 | tr '\n' ' ')"
        bad "$name" "expected output matching '${sub}': ${tail_output}"
        return 0
    fi
    ok "$name"
}

printf 'pair.sh regression suite\n\n'

check() { # check <name> <0|1 condition-result>
    [[ -n "$FILTER" && "$1" != *"$FILTER"* ]] && return 0
    [[ "$2" -eq 0 ]] && ok "$1" || bad "$1" "condition not met"
    return 0
}

# --- happy path -----------------------------------------------------------
sandbox
run "$R" 7;         want "full run reaches done" 0 "is done"
run "$R" status 7;  want "status reports done"   0 "status: done"
transcript_count="$(find "$R/.agents/work/issue-7/turns" -name '*.log' | wc -l)"
check "five transcripts kept" "$([[ "$transcript_count" -eq 5 ]] && echo 0 || echo 1)"
check "first turn writes the required Task" \
    "$(grep -q 'Implement the issue' "$R/.agents/work/issue-7/plan.md" && echo 0 || echo 1)"
cleanup

# --- exit codes -----------------------------------------------------------
sandbox; STUB_MANUAL=required run "$R" 7; want "manual testing exits 2" 2
cleanup
sandbox; STUB_MISBEHAVE=ask run "$R" 7;   want "questions exit 3" 3 "needs answers"
cleanup

# --- state machine (RF-04, round guard) -----------------------------------
sandbox; STUB_MISBEHAVE=illegal-jump run "$R" 7
want "illegal transition refused" 1 "does not allow"
cleanup
sandbox; STUB_MISBEHAVE=round-jump run "$R" 7
want "round jump refused" 1 "requires it to stay unchanged"
cleanup

# --- git guard (RF-02) ----------------------------------------------------
sandbox; STUB_MISBEHAVE=git-write run "$R" 7
want "branch creation caught" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=stash run "$R" 7
want "git stash caught" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=restage run "$R" 7
want "restaged blob caught" 1 "Git state moved"; cleanup

# --- immutable metadata (RF-05) -------------------------------------------
sandbox; STUB_MISBEHAVE=rewrite-meta run "$R" 7
want "driver-owned field edit caught" 1 "protected fields or sections"
cleanup

# --- stalled turn ---------------------------------------------------------
sandbox; STUB_MISBEHAVE=nothing run "$R" 7
want "turn that does nothing aborts" 1 "did not modify"
cleanup

# --- input validation (RS-05, RS-02) --------------------------------------
sandbox
run "$R" start 7 --slug ../escape;  reject "slug traversal rejected" "plain name"
run "$R" 7 --mr 0;                  reject "zero rounds rejected"    "positive whole number"
run "$R" 7 --max-turns 0;           reject "zero turns rejected"     "positive whole number"
run "$R" https://github.com/other/proj/issues/7
reject "foreign issue URL refused" "but this repository"
run "$R" https://example.invalid/o/r/issues/7
want "foreign issue host refused" 1 "but this repository"
STUB_REPO_URL=unavailable run "$R" https://github.com/o/r/issues/7
want "URL validation fails closed" 1 "cannot resolve"
cleanup

# --- dirty worktree (RS-01) -----------------------------------------------
sandbox; echo scratch > "$R/untracked.txt"
run "$R" 7;                want "dirty start refused"       1 "uncommitted changes"
run "$R" 7 --allow-dirty;  want "dirty start with override" 0 "allow-dirty"
cleanup

# --- execution boundary (RR2-06) ------------------------------------------
sandbox
AGENT1_CMD="${SANDBOX}/bin/stub-agent --dangerously-skip-permissions" run "$R" 7
want "unsafe agent command refused by default" 1 "allow-unsafe-agents"
AGENT1_CMD="${SANDBOX}/bin/stub-agent --dangerously-skip-permissions" \
    run "$R" 7 --allow-unsafe-agents
want "unsafe agent command needs explicit opt-in" 0 "is done"
cleanup

# --- answers (RS-06) ------------------------------------------------------
sandbox
STUB_MISBEHAVE=ask run "$R" 7
answer_with() {
    local plan="$1/.agents/work/issue-7/plan.md"
    awk -v a="$2" '/^\*\*Q1\./ {print; print ""; print a; next} {print}' \
        "$plan" > "$1/tmp.md" && mv "$1/tmp.md" "$plan"
}
answer_with "$R" '**A1.**'
run "$R" 7; want "empty answer not accepted" 3 "needs answers"
cleanup

# A fresh sandbox: leaving the empty marker in place and adding a second one
# would now trip the duplicate-id check rather than exercise the resume path.
sandbox
STUB_MISBEHAVE=ask run "$R" 7
answer_with "$R" '**A1.** No retry.'
run "$R" 7; want "real answer resumes" 0 "answers found"
cleanup

# --- template safety (RS-07) ----------------------------------------------
sandbox
STUB_ISSUE_TITLE='Fix A & B' run "$R" start 7
D="$R/.agents/work/issue-7/plan.md"
check "title with & does not leak the placeholder" \
    "$(grep -q 'ISSUE_TITLE' "$D" && echo 1 || echo 0)"
check "title with & is written literally" \
    "$(grep -q 'Fix A & B' "$D" && echo 0 || echo 1)"
cleanup

# --- publish path (RF-06, RF-07, RF-08) -----------------------------------
# Fills the template's existing ## Pull Request section. Appending a second one
# would not work: section() reads the first heading it finds.
pr_section() {
    local d="$1/.agents/work/issue-7/plan.md"
    awk '/^## Pull Request$/{
            print; print ""
            print "### Summary"; print "Does the thing."; print ""
            print "### Changes"; print "- Added src.txt"
            next
         } {print}' "$d" > "$d.t" && mv "$d.t" "$d"
}
pr_section_custom() {
    local d="$1/.agents/work/issue-7/plan.md"
    awk -v sh="$2" -v sb="$3" -v ch="$4" -v cb="$5" \
        '/^## Pull Request$/{
            print; print ""; print sh; print sb; print ""; print ch; print cb
            next
         } {print}' "$d" > "$d.t" && mv "$d.t" "$d"
}
# A real bump: the version moves, and both generated reports carry the new one.
# The previous fixture only appended a byte to each file, which is exactly the
# stale-report case the publisher is supposed to refuse.
bump() {
    local v="${2:-2.0.0-SNAPSHOT.2}"
    printf 'val chordsVersion: String by extra("%s")\n' "$v" > "$1/version.gradle.kts"
    printf '<project>\n  <version>%s</version>\n  <dependencies/>\n</project>\n' \
        "$v" > "$1/pom.xml"
    printf '# Dependencies of `o:r:%s`\n' "$v" > "$1/dependencies.md"
}
# Touched but not actually bumped, for the negative case.
fake_bump() {
    printf 'b\n' >> "$1/version.gradle.kts"
    printf 'b\n' >> "$1/pom.xml"
    printf 'b\n' >> "$1/dependencies.md"
}
manual_plan() {
    awk '/^## Manual Testing$/{print; print ""
         print "### Setup"
         print "Launch the client against a slow endpoint."
         print ""
         print "### Steps"
         print "1. Open the form."
         print "   Expected: the window stays responsive."
         print "   Covers: does not block the calling thread."
         next} {print}' \
      "$1/.agents/work/issue-7/plan.md" > "$1/t.md" \
        && mv "$1/t.md" "$1/.agents/work/issue-7/plan.md"
}
manual_plan_variant() {
    local d="$1/.agents/work/issue-7/plan.md"
    awk -v variant="$2" '/^## Manual Testing$/ {
         print; print ""
         if (variant == "long-bullet") {
             print "- Long enough to pass the former length check, but unusable."
             next
         }
         if (variant != "no-setup") {
             print "### Setup"; print "Launch the client."; print ""
         }
         print "### Steps"; print "1. Open the form."
         if (variant != "no-expected") print "   Expected: the form remains responsive."
         if (variant != "no-covers") print "   Covers: the responsiveness criterion."
         next
       } {print}' "$d" > "$d.t" && mv "$d.t" "$d"
}

sandbox; run "$R" 7 >/dev/null
run "$R" 7 --cp; want "PR refused without Summary/Changes" 1 "requires exact"
cleanup

sandbox; run "$R" 7 >/dev/null
pr_section_custom "$R" "### Summary typo" "Does the thing." \
    "### Changes" "- Added src.txt"
run "$R" 7 --cp; want "PR refused with an inexact heading" 1 "requires exact"
cleanup

sandbox; run "$R" 7 >/dev/null
pr_section_custom "$R" "### Summary" "Does the thing." "### Changes" ""
run "$R" 7 --cp; want "PR refused with an empty Changes body" 1 "requires exact"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "PR refused without version and reports" 1 "not in the changeset"
current_branch="$(git -C "$R" rev-parse --abbrev-ref HEAD)"
check "nothing was committed on refusal" \
    "$([[ "$current_branch" == master ]] && echo 0 || echo 1)"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
run "$R" 7 --cp; want "PR published from a compliant changeset" 0 "draft pull request"
check "version commit uses the required message" \
    "$(git -C "$R" log --format=%s | grep -q '^Bump version' && echo 0 || echo 1)"
check "branch was pushed to origin" \
    "$(git -C "$R" ls-remote --heads origin | grep -q . && echo 0 || echo 1)"
STUB_PR_EXISTS=https://github.com/o/r/pull/1 run "$R" 7 --cp
want "re-run finds the existing PR" 0 "already open"
cleanup

# A failed `gh pr create` must leave the push intact and stay retryable.
sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp
want "failed PR create reports and stops" 1 "gh pr create failed"
run "$R" 7 --cp; want "retry after a failed PR create" 0 "draft pull request"
cleanup

# --- re-review: round accounting (RR2-01, RR2-07) -------------------------
sandbox; STUB_MISBEHAVE=loop-no-round run "$R" 7
want "loopback without spending a round refused" 1 "increase by exactly one"
cleanup
sandbox; STUB_MISBEHAVE=wrong-phase run "$R" 7
want "wrong phase counter refused" 1 "stay unchanged"
cleanup
sandbox; STUB_MISBEHAVE=wrong-turn run "$R" 7
want "wrong next turn refused" 1 "belongs to"
cleanup
sandbox; STUB_MISBEHAVE=agent2-asks run "$R" 7
want "agent2 may not ask questions" 1 "does not allow"
cleanup
sandbox; STUB_MISBEHAVE=loop-past-plan run "$R" 7 --mr 1
want "plan loopback at the ceiling blocks" 3 "blocked for a human"
cleanup
sandbox; STUB_MISBEHAVE=loop-past-impl run "$R" 7 --mr 1
want "implementation loopback at the ceiling blocks" 3 "blocked for a human"
cleanup

# --- re-review: issue immutability (RR2-02) -------------------------------
sandbox; STUB_MISBEHAVE=edit-issue run "$R" 7
want "rewriting ## Issue refused" 1 "protected fields or sections"
cleanup

sandbox; STUB_MISBEHAVE=edit-task run "$R" 7
want "rewriting established ## Task refused" 1 "protected fields or sections"
cleanup

sandbox
hidden_comment='Problem stated here at some length. <!-- HIDDEN_CRITERION --> '
hidden_comment+='Acceptance criteria: preserve comments exactly and cover it.'
STUB_ISSUE_BODY="$hidden_comment" \
STUB_MISBEHAVE=edit-hidden run "$R" 7
want "rewriting an issue HTML comment refused" 1 "protected fields or sections"
cleanup

sandbox
fenced_heading='Problem stated here.\n```markdown\n## Example\n'
fenced_heading+='HIDDEN_CRITERION\n```\nAcceptance criteria: preserve the fenced example.'
STUB_ISSUE_BODY="$fenced_heading" \
STUB_MISBEHAVE=edit-hidden run "$R" 7
want "rewriting after a fenced H2 refused" 1 "protected fields or sections"
cleanup

sandbox
long_fence='Problem stated here.\n````markdown\n```\n## Example\n'
long_fence+='HIDDEN_CRITERION\n````\nAcceptance criteria: preserve long fences.'
STUB_ISSUE_BODY="$long_fence" \
STUB_MISBEHAVE=edit-hidden run "$R" 7
want "short fence inside a long fence does not truncate protection" \
    1 "protected fields or sections"
cleanup

sandbox; STUB_MISBEHAVE=duplicate-task run "$R" 7
want "duplicate protected Task section refused" 1 "protected fields or sections"
cleanup

# --- re-review: index flags (RR2-10) --------------------------------------
sandbox; STUB_MISBEHAVE=index-flag run "$R" 7
want "assume-unchanged caught" 1 "Git state moved"
cleanup

# --- re-review: duplicate ids (RR2-09) ------------------------------------
sandbox; STUB_MISBEHAVE=dup-q run "$R" 7
run "$R" 7; want "duplicate question ids refused" 1 "duplicate question or answer"
cleanup

# --- re-review: slug traversal on every command (RR2-08) ------------------
sandbox
run "$R" run ../../escape;    reject "run rejects a traversal slug"    "plain name"
run "$R" step ../../escape;   reject "step rejects a traversal slug"   "plain name"
run "$R" status ../../escape; reject "status rejects a traversal slug" "plain name"
run "$R" '#notanissue';       reject "malformed #issue rejected"       "not a GitHub issue"
cleanup

# --- re-review: turn ceiling follows max-rounds (RR2-11) ------------------
sandbox
run "$R" 7 --mr 3
want "three rounds fit within the derived ceiling" 0 "is done"
cleanup

# --- re-review: publish gates (RR2-03, RR2-05) ----------------------------
sandbox; run "$R" 7 >/dev/null; pr_section "$R"; fake_bump "$R"
run "$R" 7 --cp; want "touched but unbumped version refused" 1 "did not increase"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
printf 'stale pom\n' > "$R/pom.xml"
run "$R" 7 --cp; want "stale report refused" 1 "root project version"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
printf '%s\n' '<project>' '  <version>2.0.0-SNAPSHOT.1</version>' \
    '  <description>2.0.0-SNAPSHOT.2</description>' \
    '  <dependencies/>' '</project>' \
    > "$R/pom.xml"
run "$R" 7 --cp
want "new version elsewhere in a stale POM is refused" 1 "root project version"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
printf '# Dependencies of `o:r:2.0.0-SNAPSHOT.1`\nnew version: 2.0.0-SNAPSHOT.2\n' \
    > "$R/dependencies.md"
run "$R" 7 --cp
want "new token with a stale dependency heading is refused" 1 "not every"
cleanup

sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
printf '# Dependencies of `o:r:2.0.0-SNAPSHOT.2`\n# Dependencies of `o:s:2.0.0-SNAPSHOT.1`\n' \
    > "$R/dependencies.md"
run "$R" 7 --cp
want "partially stale dependency headings are refused" 1 "not every"
cleanup

sandbox; STUB_MANUAL=required run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
run "$R" 7 --cp; want "required manual testing without a plan is not published" 1 "no usable plan"
cleanup

sandbox; STUB_MANUAL=required run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"; manual_plan "$R"
run "$R" 7 --cp; want "required manual testing with a plan publishes" 2 "draft pull request"
cleanup

for missing in long-bullet no-setup no-expected no-covers; do
    sandbox
    STUB_MANUAL=required run "$R" 7 >/dev/null
    pr_section "$R"; bump "$R"; manual_plan_variant "$R" "$missing"
    run "$R" 7 --cp
    want "manual plan rejects ${missing}" 1 "no usable plan"
    cleanup
done

# The first publication commit may succeed before the task commit fails. A
# retry must recognize the committed reports and finish the remaining steps.
sandbox; run "$R" 7 >/dev/null; pr_section "$R"; bump "$R"
cat > "$R/.git/hooks/commit-msg" <<'HOOK'
#!/usr/bin/env bash
grep -q '^Bump version' "$1"
HOOK
chmod +x "$R/.git/hooks/commit-msg"
run "$R" 7 --cp
want "task commit failure stops after version commit" 1 "task commit failed"
check "version commit survives task commit failure" \
    "$(git -C "$R" log --format=%s | grep -q '^Bump version' && echo 0 || echo 1)"
rm -f "$R/.git/hooks/commit-msg"
run "$R" 7 --cp
want "retry after task commit failure publishes" 0 "draft pull request"
cleanup

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
