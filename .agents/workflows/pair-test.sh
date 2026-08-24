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
    printf '<project>\n  <version>2.0.0-SNAPSHOT.1</version>\n  <dependencies/>\n</project>\n' \
        > "${repo}/pom.xml"
    printf '# Dependencies of `o:r:2.0.0-SNAPSHOT.1`\n' > "${repo}/dependencies.md"
    printf 'fixture\n' > "${repo}/README.md"
    printf '.agents/work/\n' > "${repo}/.gitignore"

    git -C "$repo" init -q -b master
    git -C "$repo" config user.email t@example.com
    git -C "$repo" config user.name Test
    git -C "$repo" add -A
    git -C "$repo" commit -qm init
    git init -q --bare "${SANDBOX}/origin.git"
    git -C "${SANDBOX}/origin.git" symbolic-ref HEAD refs/heads/master
    git -C "$repo" remote add origin "${SANDBOX}/origin.git"
    git -C "$repo" push -qu origin master
    git clone -q "${SANDBOX}/origin.git" "${SANDBOX}/external"
    git -C "${SANDBOX}/external" config user.email external@example.com
    git -C "${SANDBOX}/external" config user.name External
    export STUB_EXTERNAL_REPO="${SANDBOX}/external"

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
  "pr view")
      [[ -n "${STUB_PR_EXISTS:-}" ]] || exit 1
      if [[ "$*" == *'@tsv'* ]]; then
          printf '%s\t%s\t%s\n' "$STUB_PR_EXISTS" \
              "${STUB_PR_BASE:-master}" "${STUB_PR_HEAD:-a-test-issue}"
      else
          printf '%s\n' "$STUB_PR_EXISTS"
      fi ;;
  "pr create") echo "${STUB_PR_CREATE_FAILS:+pr create refused}" >&2
               [[ -n "${STUB_PR_CREATE_FAILS:-}" ]] && exit 1
               # Record the invocation so tests can assert on the base branch
               # and the body without a real GitHub call.
               if [[ -n "${STUB_PR_RECORD:-}" ]]; then
                   printf '%s\n' "$@" > "$STUB_PR_RECORD"
               fi
               printf 'https://github.com/o/r/pull/1\n' ;;
  *) exit 1 ;;
esac
GH
    # Stub agent: advances one legal state, or misbehaves on demand.
    cat > "${SANDBOX}/bin/stub-agent" <<'AGENT'
#!/usr/bin/env bash
set -uo pipefail
if [[ -n "${STUB_AGENT_ARGS_RECORD:-}" ]]; then
    printf '%s' "${0##*/}" >> "$STUB_AGENT_ARGS_RECORD"
    printf ' <%s>' "$@" >> "$STUB_AGENT_ARGS_RECORD"
    printf '\n' >> "$STUB_AGENT_ARGS_RECORD"
fi
if [[ -n "${STUB_AGENT_ENV_RECORD:-}" ]]; then
    printf 'CHORDS_NO_GRADLE_DAEMON=%s\nGRADLE_OPTS=%s\n' \
        "${CHORDS_NO_GRADLE_DAEMON:-}" "${GRADLE_OPTS:-}" > "$STUB_AGENT_ENV_RECORD"
fi
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
root() { git rev-parse --show-toplevel; }
# Runs Git outside the agent's inherited Trace2 session, standing in for an
# editor refresh, another checkout, or GitHub changing the remote mid-turn.
outside_git() { env -u GIT_TRACE2_EVENT git "$@"; }
# Advances origin/master in the independent checkout and fetches the result
# into the repository under test, all outside the agent process trace.
external_master_advance() {
    local marker="${STUB_EXTERNAL_REPO}/external-$$.txt"
    outside_git -C "$STUB_EXTERNAL_REPO" checkout -q master
    printf 'external\n' > "$marker"
    outside_git -C "$STUB_EXTERNAL_REPO" add "${marker##*/}"
    outside_git -C "$STUB_EXTERNAL_REPO" commit -qm "External change"
    outside_git -C "$STUB_EXTERNAL_REPO" push -q origin master
}
# Fills a section that the template already has, or adds one before ## Log,
# which is how a real agent adds a later round's section.
put_section() {
    local head="## $1"; shift
    local body; body="$(printf '%s\n' "$@")"
    BODY="$body" awk -v head="$head" '
        $0 == head && !written {
            print; print ""; print ENVIRON["BODY"]; written = 1; next
        }
        $0 == "## Log" && !written {
            print head; print ""; print ENVIRON["BODY"]; print ""
            print; written = 1; next
        }
        { print }
    ' "$doc" > "$doc.t" && mv "$doc.t" "$doc"
}
review() { # review <heading> <finding-id>
    local verdict="${STUB_VERDICT_LINE:-Verdict: ${STUB_VERDICT:-APPROVE}}"
    local finding
    case "${STUB_FINDING_LAYOUT:-bullet}" in
        bullet)   finding="- ${2}: something to fix." ;;
        numbered) finding="1. ${2}: something to fix." ;;
        heading)  finding="#### ${2} — something to fix" ;;
        table)    finding="| ${2} | Must fix | Something to fix. |" ;;
        *)        exit 1 ;;
    esac
    if [[ -n "${STUB_PRIOR_REFERENCE:-}" && "$1" == *"Round 2" ]]; then
        put_section "$1" "**Must fix**" "" "$finding" "" \
            "P1-99 was addressed in the previous round." "" "$verdict"
    else
        put_section "$1" "**Must fix**" "" "$finding" "" "$verdict"
    fi
}
dispositions() { # dispositions <heading> <finding-id>
    put_section "$1" "| ID | Disposition | Notes |" "|----|----|----|" \
        "| ${2} | Accepted | Applied. |"
}
# The log is append-only and shared, so every turn adds its own line.
log_line() {
    local role=agent1
    case "$1" in
        plan-review-requested|implementation-review-requested) role=agent2 ;;
    esac
    printf '%s %s %s -> %s: stub turn\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$role" "$1" "$2" >> "$doc"
}
if [[ "$(fm status)" == implementation-review-requested \
      && -n "${STUB_REVIEW_PROMPT_RECORD:-}" ]]; then
    printf '%s\n' "${1:-}" > "$STUB_REVIEW_PROMPT_RECORD"
fi
case "${STUB_MISBEHAVE:-}" in
  illegal-jump) setfm status done; setfm turn human; exit 0 ;;
  git-write)    git branch "stub-rogue-$$"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  stash)        echo x >> version.gradle.kts; git stash -q
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  restage)      echo y >> README.md; git add README.md
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  git-write-exit) git branch "stub-rogue-$$"; exit 19 ;;
  review-edit-exit)
                if [[ "$(fm status)" == plan-review-requested ]]; then
                    printf 'reviewer edit\n' >> "$(root)/README.md"
                    exit 19
                fi ;;
  rewrite-transcript)
                if [[ "$(fm status)" == plan-review-requested ]]; then
                    printf 'rewritten audit\n' \
                        > "$(root)/.agents/work/${PAIR_SLUG}/turns/01-agent1.log"
                fi ;;
  config-write) git config pair.agentMutation true
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  trace-read-only)
                git tag --format '%(refname)' >/dev/null
                git log --oneline -- remote set-url >/dev/null ;;
  combined-drop)
                git branch -dr origin/obsolete
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  unignore-work)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    sed '/[.]agents\/work\//d' "$(root)/.gitignore" \
                        > "$(root)/.gitignore.t"
                    mv "$(root)/.gitignore.t" "$(root)/.gitignore"
                fi ;;
  nonascii-path)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    printf 'reviewed\n' > "$(root)/résumé.txt"
                fi ;;
  codex-ref)    # The Codex CLI's own turn bookkeeping, written by the process
                # rather than the agent. Once, then this turn proceeds normally,
                # so the assertion is that the whole run still finishes.
                [[ "$(fm status)" != plan-requested ]] ||
                    git update-ref "refs/codex/turn-diffs/checkpoints/stub-$$" HEAD ;;
  fetch-lands)  # A fetch arriving from a background process. Once, then this
                # turn proceeds normally so the whole run should finish.
                if [[ "$(fm status)" == plan-requested ]]; then
                    git branch --show-current >/dev/null
                    git remote -v >/dev/null
                    git tag --list >/dev/null
                    external_master_advance
                    outside_git -C "$(root)" fetch -q origin
                fi ;;
  fetch-known)  # The fetched commit already has a local branch, so
                # reachability cannot identify its provenance.
                if [[ "$(fm status)" == plan-requested ]]; then
                    outside_git -C "$STUB_EXTERNAL_REPO" fetch -q origin \
                        known:refs/remotes/origin/known
                    outside_git -C "$STUB_EXTERNAL_REPO" push -q origin \
                        refs/remotes/origin/known:master
                    outside_git -C "$(root)" fetch -q origin
                fi ;;
  fetch-branch) # A newly fetched remote branch is unrelated activity too.
                if [[ "$(fm status)" == plan-requested ]]; then
                    outside_git -C "$STUB_EXTERNAL_REPO" checkout -qb external-branch
                    printf 'branch\n' > "$STUB_EXTERNAL_REPO/branch.txt"
                    outside_git -C "$STUB_EXTERNAL_REPO" add branch.txt
                    outside_git -C "$STUB_EXTERNAL_REPO" commit -qm "External branch"
                    outside_git -C "$STUB_EXTERNAL_REPO" push -q origin \
                        HEAD:external-branch
                    outside_git -C "$(root)" fetch -q origin
                fi ;;
  fetch-tag)    # Tags can arrive through the same background fetch.
                if [[ "$(fm status)" == plan-requested ]]; then
                    outside_git -C "$STUB_EXTERNAL_REPO" tag external-tag
                    outside_git -C "$STUB_EXTERNAL_REPO" push -q origin external-tag
                    outside_git -C "$(root)" fetch -q --tags origin
                fi ;;
  fetch-prune)  # A background prune explains a vanished tracking ref.
                if [[ "$(fm status)" == plan-requested ]]; then
                    outside_git -C "$STUB_EXTERNAL_REPO" push -q origin \
                        --delete obsolete
                    outside_git -C "$(root)" fetch -q --prune origin
                fi ;;
  agent-fetch)  # Even a legitimate remote advance is forbidden when the
                # agent itself fetches it.
                external_master_advance
                git fetch -q origin
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  remote-push)  git push -q origin "HEAD:refs/heads/stub-rogue-$$"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  dangling-push) oid="$(git commit-tree -p HEAD -m rogue 'HEAD^{tree}')"
                git push -q origin "${oid}:refs/heads/stub-rogue-$$"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  push-tag)     git push -q origin existing-tag
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  agent-tag)    git tag "stub-rogue-$$"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  drop-remote)  git update-ref -d refs/remotes/origin/master
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  missing-trace) external_master_advance
                outside_git -C "$(root)" fetch -q origin
                : > "$GIT_TRACE2_EVENT"
                setfm status plan-review-requested; setfm turn agent2; exit 0 ;;
  rewrite-meta) setfm dirty_at_start yes ;;
  rewrite-model) setfm claude_model tampered ;;
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
                    r="$(fm plan_round)"
                    dispositions "Plan Dispositions — Round ${r}" "P${r}-01"
                    log_line plan-reviewed plan-review-requested
                    setfm plan_round "$(( r + 1 ))"
                    setfm status plan-review-requested; setfm turn agent2; exit 0
                fi ;;
  loop-past-impl)
                if [[ "$(fm status)" == implementation-reviewed ]]; then
                    r="$(fm impl_round)"
                    dispositions "Implementation Dispositions — Round ${r}" "I${r}-01"
                    log_line implementation-reviewed implementation-review-requested
                    setfm impl_round "$(( r + 1 ))"
                    setfm status implementation-review-requested; setfm turn agent2; exit 0
                fi ;;
  # A legitimate send-back: dispositions written, one round spent, then the
  # run carries on to `done` through a second review round.
  loop-once|wrong-review-dash)
                if [[ "$(fm status)" == plan-reviewed && "$(fm plan_round)" == 1 ]]; then
                    dispositions "Plan Dispositions — Round 1" "P1-01"
                    log_line plan-reviewed plan-review-requested
                    setfm plan_round 2
                    setfm status plan-review-requested; setfm turn agent2; exit 0
                fi ;;
  # agent2 edits the code it is reviewing.
  agent2-edits) if [[ "$(fm status)" == *review-requested ]]; then
                    printf 'tampered\n' >> "$(root)/README.md"
                fi ;;
  # Retargeting a symlink is a content change even when both targets have the
  # same bytes.
  agent2-retarget-link)
                if [[ "$(fm status)" == *review-requested ]]; then
                    ln -sfn link-target-b "$(root)/review-link"
                fi ;;
  # agent1 rewrites the reviewer's section.
  edit-review)  if [[ "$(fm status)" == plan-reviewed ]]; then
                    awk '/^## Plan Review/ { print; print "AGENT1 WAS HERE"; next } {print}' \
                        "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                fi ;;
  # An earlier log entry is rewritten rather than appended to.
  rewrite-log)  if [[ "$(fm status)" == plan-review-requested ]]; then
                    sed 's/stub turn/rewritten/' "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                fi ;;
  extend-log)   if [[ "$(fm status)" == plan-review-requested ]]; then
                    review "Plan Review — Round 1" "P1-01"
                    perl -0pi -e 's/\n\z//' "$doc"
                    printf ' extended\n' >> "$doc"
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  omit-log)     if [[ "$(fm status)" == plan-requested ]]; then
                    put_section Task "Implement the issue." "- [ ] It works."
                    setfm status plan-review-requested; setfm turn agent2; exit 0
                fi ;;
  rewrite-prior-owned)
                if [[ "$(fm status)" == implementation-reviewed ]]; then
                    sed 's/Applied\./Rewritten later./' "$doc" > "$doc.t" \
                        && mv "$doc.t" "$doc"
                    r="$(fm impl_round)"
                    dispositions "Implementation Dispositions — Round ${r}" "I${r}-01"
                    put_section Outcome "The criterion is met."
                    log_line implementation-reviewed done
                    setfm status done; setfm turn human; setfm manual_testing none; exit 0
                fi ;;
  tamper-snapshot)
                if [[ "$(fm status)" == plan-review-requested ]]; then
                    printf 'tampered\n' >> "$(dirname "$doc")/rounds/plan-1.md"
                    review "Plan Review — Round 1" "P1-01"
                    log_line plan-review-requested plan-reviewed
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  # A question asked while planning that would resume at a finished run.
  bad-resume)   awk '/^## Questions$/ {
                        print; print ""
                        print "**Q1.** Retry? Default if unanswered: no."; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm resume_status done
                log_line "$(fm status)" questions-pending
                setfm status questions-pending; setfm turn human; exit 0 ;;
  verdict-in-prose)
                if [[ "$(fm status)" == plan-review-requested ]]; then
                    put_section "Plan Review — Round 1" "The earlier example says APPROVE."
                    log_line plan-review-requested plan-reviewed
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  no-verdict)   if [[ "$(fm status)" == plan-review-requested ]]; then
                    put_section "Plan Review — Round 1" "**Must fix**" "" "None."
                    log_line plan-review-requested plan-reviewed
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  empty-review) if [[ "$(fm status)" == plan-review-requested ]]; then
                    log_line plan-review-requested plan-reviewed
                    setfm status plan-reviewed; setfm turn agent1; exit 0
                fi ;;
  no-dispositions)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    log_line plan-reviewed implementation-review-requested
                    setfm status implementation-review-requested
                    setfm turn agent2; exit 0
                fi ;;
  prose-disposition)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    put_section "Plan Dispositions — Round 1" \
                        "P1-01 is mentioned, but this is not a disposition row."
                    printf 'work\n' >> "$(root)/src.txt"
                    log_line plan-reviewed implementation-review-requested
                    setfm status implementation-review-requested
                    setfm turn agent2; exit 0
                fi ;;
  duplicate-disposition)
                if [[ "$(fm status)" == plan-reviewed ]]; then
                    put_section "Plan Dispositions — Round 1" \
                        "| ID | Disposition | Notes |" \
                        "|----|-------------|-------|" \
                        "| P1-01 | Accepted | Applied. |" \
                        "| P1-01 | Rejected | Duplicate. |"
                    printf 'work\n' >> "$(root)/src.txt"
                    log_line plan-reviewed implementation-review-requested
                    setfm status implementation-review-requested
                    setfm turn agent2; exit 0
                fi ;;
  no-outcome)   if [[ "$(fm status)" == implementation-reviewed ]]; then
                    r="$(fm impl_round)"
                    dispositions "Implementation Dispositions — Round ${r}" "I${r}-01"
                    log_line implementation-reviewed done
                    setfm status done; setfm turn human
                    setfm manual_testing none; exit 0
                fi ;;
  dup-q)        awk '/^## Questions$/ {
                        print; print ""; print "**Q1.** First?"
                        print "**Q1.** Second?"; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm resume_status "$(fm status)"
                log_line "$(fm status)" questions-pending
                setfm status questions-pending; setfm turn human; exit 0 ;;
  ask)          awk '/^## Questions$/ {
                        print; print ""
                        print "**Q1.** Retry? Default if unanswered: no."; next
                    } {print}' \
                    "$doc" > "$doc.t" && mv "$doc.t" "$doc"
                setfm resume_status "$(fm status)"
                log_line "$(fm status)" questions-pending
                setfm status questions-pending; setfm turn human; exit 0 ;;
  final-edit)   if [[ "$(fm status)" == implementation-reviewed ]]; then
                    r="$(fm impl_round)"
                    dispositions "Implementation Dispositions — Round ${r}" "I${r}-01"
                    put_section Outcome "The criterion is met."
                    printf 'unreviewed\n' >> "$(root)/src.txt"
                    log_line implementation-reviewed done
                    setfm status done; setfm turn human; setfm manual_testing none; exit 0
                fi ;;
  fix-and-rereview)
                if [[ "$(fm status)" == implementation-reviewed \
                      && "$(fm impl_round)" == 1 ]]; then
                    dispositions "Implementation Dispositions — Round 1" "I1-01"
                    put_section "Implementation — Round 2" \
                        "Applied I1-01 and re-ran focused verification."
                    printf 'review fix\n' >> "$(root)/src.txt"
                    log_line implementation-reviewed implementation-review-requested
                    setfm impl_round 2
                    setfm status implementation-review-requested
                    setfm turn agent2; exit 0
                fi ;;
  nothing)      exit 0 ;;
esac
case "$(fm status)" in
  plan-requested)
      awk '/^## Task$/ {
              print; print "Implement the issue."; print "- [ ] It works."; next
           } {print}' "$doc" > "$doc.t" && mv "$doc.t" "$doc"
      log_line plan-requested plan-review-requested
      setfm status plan-review-requested; setfm turn agent2 ;;
  plan-review-requested)
      r="$(fm plan_round)"
      heading="Plan Review — Round ${r}"
      if [[ "${STUB_MISBEHAVE:-}" == wrong-review-dash && "$r" == 2 ]]; then
          heading="Plan Review - Round ${r}"
      fi
      review "$heading" "P${r}-01"
      log_line plan-review-requested plan-reviewed
      setfm status plan-reviewed; setfm turn agent1 ;;
  plan-reviewed)
      r="$(fm plan_round)"
      dispositions "Plan Dispositions — Round ${r}" "P${r}-01"
      printf 'work\n' >> "$(root)/src.txt"
      # A requested PR's version belongs to the implementation handed to the
      # reviewer, not the final disposition-only turn.
      if [[ -n "${STUB_BUMP:-}" ]]; then
          if [[ "$STUB_BUMP" == fake ]]; then
              printf 'b\n' >> "$(root)/version.gradle.kts"
              printf 'b\n' >> "$(root)/pom.xml"
              printf 'b\n' >> "$(root)/dependencies.md"
          else
              printf 'val chordsVersion: String by extra("%s")\n' "$STUB_BUMP" \
                  > "$(root)/version.gradle.kts"
              printf '<project>\n  <version>%s</version>\n  <dependencies/>\n</project>\n' \
                  "$STUB_BUMP" > "$(root)/pom.xml"
              printf '# Dependencies of `o:r:%s`\n' "$STUB_BUMP" \
                  > "$(root)/dependencies.md"
              case "${STUB_BAD_REPORT:-}" in
                  stale-pom)
                      printf 'stale pom\n' > "$(root)/pom.xml" ;;
                  misplaced-pom)
                      printf '%s\n' '<project>' \
                          '  <version>2.0.0-SNAPSHOT.1</version>' \
                          '  <description>2.0.0-SNAPSHOT.2</description>' \
                          '  <dependencies/>' '</project>' > "$(root)/pom.xml" ;;
                  stale-dependencies)
                      printf '%s\n' \
                          '# Dependencies of `o:r:2.0.0-SNAPSHOT.1`' \
                          'new version: 2.0.0-SNAPSHOT.2' \
                          > "$(root)/dependencies.md" ;;
                  partial-dependencies)
                      printf '%s\n' \
                          '# Dependencies of `o:r:2.0.0-SNAPSHOT.2`' \
                          '# Dependencies of `o:s:2.0.0-SNAPSHOT.1`' \
                          > "$(root)/dependencies.md" ;;
              esac
          fi
      fi
      log_line plan-reviewed implementation-review-requested
      setfm status implementation-review-requested; setfm turn agent2 ;;
  implementation-review-requested)
      r="$(fm impl_round)"
      review "Implementation Review — Round ${r}" "I${r}-01"
      log_line implementation-review-requested implementation-reviewed
      setfm status implementation-reviewed; setfm turn agent1 ;;
  implementation-reviewed)
      r="$(fm impl_round)"
      dispositions "Implementation Dispositions — Round ${r}" "I${r}-01"
      put_section Outcome "The criterion is met. Verified by the stub."
      log_line implementation-reviewed done
      setfm status done; setfm turn human
      setfm manual_testing "${STUB_MANUAL:-none}" ;;
  *) exit 1 ;;
esac
AGENT
    chmod +x "${SANDBOX}/bin/gh" "${SANDBOX}/bin/stub-agent"
    ln -s stub-agent "${SANDBOX}/bin/stub-agent1"
    ln -s stub-agent "${SANDBOX}/bin/stub-agent2"
    ln -s stub-agent "${SANDBOX}/bin/claude"
    ln -s stub-agent "${SANDBOX}/bin/codex"
    # Exported here, so sandbox must be called as a plain command: via $( ) the
    # subshell would swallow every one of these.
    export PATH="${SANDBOX}/bin:${ORIGINAL_PATH}"
    export AGENT1_CMD="${SANDBOX}/bin/stub-agent1"
    export AGENT2_CMD="${SANDBOX}/bin/stub-agent2"
    export PAIR_SLUG=issue-7
    export STUB_PR_RECORD="${SANDBOX}/pr-create.args"
    export STUB_REVIEW_PROMPT_RECORD="${SANDBOX}/implementation-review.prompt"
    export STUB_AGENT_ARGS_RECORD="${SANDBOX}/agent-args.log"
    export STUB_AGENT_ENV_RECORD="${SANDBOX}/agent-env.log"
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
check "done status reports no current agent work" \
    "$(printf '%s' "$OUT" | grep -qx 'work: complete' && echo 0 || echo 1)"
check "done status explains manual testing" \
    "$(printf '%s' "$OUT" | grep -qx 'manual testing: not required' && echo 0 || echo 1)"
transcript_count="$(find "$R/.agents/work/issue-7/turns" -name '*.log' | wc -l)"
check "five transcripts kept" "$([[ "$transcript_count" -eq 5 ]] && echo 0 || echo 1)"
check "first turn writes the required Task" \
    "$(grep -q 'Implement the issue' "$R/.agents/work/issue-7/plan.md" && echo 0 || echo 1)"
cleanup

sandbox
run "$R" 7 >/dev/null
printf 'after done\n' >> "$R/README.md"
run "$R" status 7
want "status does not report changed completed work as intact" 0 \
    "work: completion invalidated"
cleanup

# --- user-facing status ---------------------------------------------------
sandbox
export AGENT1_CMD="${SANDBOX}/bin/claude"
export AGENT2_CMD="${SANDBOX}/bin/codex"
run "$R" start 7
want "status fixture starts" 0
run "$R" status 7
want "status reports both agent engines" 0 "agents: agent1=claude, agent2=codex"
check "status describes the planning work" \
    "$(printf '%s' "$OUT" | grep -qx 'work: agent1 — planning' \
        && echo 0 || echo 1)"
check "status explains an undecided manual check" \
    "$(printf '%s' "$OUT" | grep -qx \
        'manual testing: undecided until completion' && echo 0 || echo 1)"
check "status hides the internal base commit" \
    "$(! printf '%s' "$OUT" | grep -q '^base:' && echo 0 || echo 1)"
TZ=America/New_York run "$R" status 7
check "status renders the update time in the client timezone" \
    "$(printf '%s' "$OUT" | grep -Eq \
        '^updated: [A-Z][a-z]{2} [0-9]{2}, [0-9]{4} at [0-9]{2}:[0-9]{2} E[DS]T$' \
        && echo 0 || echo 1)"
run "$R" step 7
want "planner advances status fixture" 0
run "$R" status 7
want "status describes plan review" 0 "work: agent2 — reviewing the plan"
run "$R" step 7
want "reviewer advances status fixture" 0
run "$R" status 7
want "status describes implementation work" 0 \
    "work: agent1 — addressing plan review, then implementing"
cleanup

# --- agent selection -----------------------------------------------------
sandbox
run "$R" start 7 --sa
want "short swap option starts a task" 0
D="$R/.agents/work/issue-7/plan.md"
check "swapped task records agent2 as planner" \
    "$(grep -q "^agent1: ${SANDBOX}/bin/stub-agent2$" "$D" && echo 0 || echo 1)"
check "swapped task records agent1 as reviewer" \
    "$(grep -q "^agent2: ${SANDBOX}/bin/stub-agent1$" "$D" && echo 0 || echo 1)"
run "$R" step 7 --sa
want "short swap option advances one step" 0
run "$R" 7
want "swapped task rejects an unswapped resume" 1 "agent selection differs"
run "$R" 7 --sa
want "short swap option resumes the task" 0 "is done"
cleanup

sandbox
run "$R" 7 --swap-agents
want "long swap option runs a task" 0 "is done"
cleanup

# --- model and effort selection ------------------------------------------
sandbox
export AGENT1_CMD="${SANDBOX}/bin/claude --model claude-opus-5 --effort high"
export AGENT2_CMD="${SANDBOX}/bin/codex exec -m gpt-5.6-sol "\
"-c model_reasoning_effort=\"high\""
run "$R" start 7 --claude-model default --claude-effort xhigh \
    --codex-model gpt-5.6-terra --codex-effort medium
want "model options start a task" 0
D="$R/.agents/work/issue-7/plan.md"
check "Claude model selection is recorded" \
    "$(grep -qx 'claude_model: default' "$D" && echo 0 || echo 1)"
check "Claude effort selection is recorded" \
    "$(grep -qx 'claude_effort: xhigh' "$D" && echo 0 || echo 1)"
check "Codex model selection is recorded" \
    "$(grep -qx 'codex_model: gpt-5.6-terra' "$D" && echo 0 || echo 1)"
check "Codex effort selection is recorded" \
    "$(grep -qx 'codex_effort: medium' "$D" && echo 0 || echo 1)"
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7
want "saved Claude settings resume without repeated options" 0
check "saved Claude settings reach the CLI" \
    "$(grep -q '^claude .*<--model> <default>.*<--effort> <xhigh>' \
        "$STUB_AGENT_ARGS_RECORD" && echo 0 || echo 1)"
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7
want "saved Codex settings resume without repeated options" 0
check "saved Codex settings reach the CLI" \
    "$(grep -q '^codex .*<-m> <gpt-5.6-terra>.*<model_reasoning_effort="medium">' \
        "$STUB_AGENT_ARGS_RECORD" && echo 0 || echo 1)"
run "$R" step 7 --claude-model sonnet
want "a resumed task rejects a different model" 1 "claude-model differs"
cleanup

sandbox
export AGENT1_CMD="${SANDBOX}/bin/claude"
export AGENT2_CMD="${SANDBOX}/bin/codex exec"
run "$R" start 7 --claude-model sonnet
want "direct engines start a model-configured task" 0
export AGENT1_CMD="${SANDBOX}/bin/stub-agent1"
run "$R" 7
want "a changed engine wrapper reports the agent selection mismatch" 1 \
    "agent selection differs"
cleanup

sandbox
export AGENT1_CMD="${SANDBOX}/bin/claude"
export AGENT2_CMD="${SANDBOX}/bin/claude"
run "$R" start 7
want "a task may use the same direct engine for both roles" 0
D="$R/.agents/work/issue-7/plan.md"
check "an absent Codex engine is recorded as unconfigured" \
    "$(grep -qx 'codex_model: (unconfigured)' "$D" \
        && grep -qx 'codex_effort: (unconfigured)' "$D" && echo 0 || echo 1)"
run "$R" step 7
want "unconfigured engine metadata is accepted on resume" 0
cleanup

sandbox
export AGENT1_CMD="${SANDBOX}/bin/claude --model claude-opus-5 --effort high"
export AGENT2_CMD="${SANDBOX}/bin/codex exec -m gpt-5.6-sol "\
"-c model_reasoning_effort=\"high\""
run "$R" start 7 --sa --claude-model 'opus[1m]' --codex-model gpt-5.6-luna
want "model settings combine with swapped agents" 0
D="$R/.agents/work/issue-7/plan.md"
check "bracketed Claude model alias stays literal" \
    "$(grep -qxF 'claude_model: opus[1m]' "$D" && echo 0 || echo 1)"
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7 --sa
want "swapped task resumes its engine settings" 0
check "Codex settings follow Codex into the planner role" \
    "$(grep -q '^codex .*<-m> <gpt-5.6-luna>' "$STUB_AGENT_ARGS_RECORD" \
        && echo 0 || echo 1)"
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7 --sa
want "swapped Claude reviewer restores its model" 0
check "bracketed Claude model reaches the CLI literally" \
    "$(grep -qF '<--model> <opus[1m]>' "$STUB_AGENT_ARGS_RECORD" \
        && echo 0 || echo 1)"
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
sandbox
printf 'private scratch\n' > "$R/preexisting.txt"
git -C "$R" config status.showUntrackedFiles no
run "$R" start 7
want "dirty-start detection cannot hide untracked files through config" 1 \
    "worktree has uncommitted changes"
cleanup

# Different slugs still share one worktree. A task that started from the clean
# state must not adopt edits another task produced before its next turn.
sandbox
PAIR_SLUG=issue-7 run "$R" start 7 >/dev/null
PAIR_SLUG=issue-8 run "$R" start 8 >/dev/null
PAIR_SLUG=issue-7 run "$R" step 7 >/dev/null
PAIR_SLUG=issue-7 run "$R" step 7 >/dev/null
PAIR_SLUG=issue-7 run "$R" step 7 >/dev/null
PAIR_SLUG=issue-8 run "$R" step 8
want "one slug cannot adopt another slug's worktree edits" 1 \
    "worktree content changed between pair-workflow turns"
cleanup

sandbox; STUB_MISBEHAVE=git-write run "$R" 7
want "branch creation caught" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=stash run "$R" 7
want "git stash caught" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=restage run "$R" 7
want "restaged blob caught" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=git-write-exit run "$R" 7
want "a nonzero agent cannot bypass the Git guard" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=review-edit-exit run "$R" 7
want "a nonzero reviewer cannot bypass the worktree guard" 1 \
    "worktree content changed"; cleanup
sandbox; STUB_MISBEHAVE=rewrite-transcript run "$R" 7
want "an agent cannot rewrite an earlier transcript" 1 \
    "rewrote an earlier transcript"; cleanup
sandbox; STUB_MISBEHAVE=config-write run "$R" 7
want "repository configuration changes are Git-state changes" 1 "Git state moved"; cleanup
sandbox; STUB_MISBEHAVE=trace-read-only run "$R" 7
want "read-only commands containing ref-writing words stay allowed" 0; cleanup
sandbox
git -C "$R" push -qu origin HEAD:obsolete
git -C "$R" fetch -q origin
STUB_MISBEHAVE=combined-drop run "$R" 7
want "combined branch flags cannot hide a tracking-ref deletion" 1 \
    "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=codex-ref run "$R" 7
want "Codex turn-diff ref does not trip the guard" 0; cleanup
sandbox; STUB_MISBEHAVE=fetch-lands run "$R" 7
want "a fetch landing mid-turn does not trip the guard" 0; cleanup
sandbox; STUB_MISBEHAVE=fetch-lands run "$R" 7
want "a landed fetch is still reported" 0 "outside agent1's process"; cleanup
sandbox
git -C "$R" checkout -qb known
printf 'known\n' > "$R/known.txt"
git -C "$R" add known.txt
git -C "$R" commit -qm "Known locally"
git -C "$R" push -qu origin known
git -C "$R" checkout -q master
STUB_MISBEHAVE=fetch-known run "$R" 7
want "a fetch to an already reachable commit is allowed" 0; cleanup
sandbox; STUB_MISBEHAVE=fetch-branch run "$R" 7
want "a newly fetched remote branch is allowed" 0; cleanup
sandbox; STUB_MISBEHAVE=fetch-tag run "$R" 7
want "a newly fetched tag is allowed" 0; cleanup
sandbox
git -C "$R" push -qu origin HEAD:obsolete
STUB_MISBEHAVE=fetch-prune run "$R" 7
want "a background fetch prune is allowed" 0; cleanup
sandbox; STUB_MISBEHAVE=agent-fetch run "$R" 7
want "an agent fetch that moves a ref is caught" 1 "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=remote-push run "$R" 7
want "a push to origin caught" 1 "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=dangling-push run "$R" 7
want "a push of an unreachable commit is caught" 1 "ref-writing Git command"; cleanup
sandbox
git -C "$R" tag existing-tag
git -C "$R" push -q origin existing-tag
STUB_MISBEHAVE=push-tag run "$R" 7
want "a push with no local ref movement is caught" 1 \
    "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=agent-tag run "$R" 7
want "tag creation caught" 1 "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=drop-remote run "$R" 7
want "an agent deleting a remote-tracking ref is caught" 1 \
    "ref-writing Git command"; cleanup
sandbox; STUB_MISBEHAVE=missing-trace run "$R" 7
want "an external-ref change without provenance is caught" 1 \
    "Git trace is missing or unreadable"; cleanup

# --- immutable metadata (RF-05) -------------------------------------------
sandbox; STUB_MISBEHAVE=rewrite-meta run "$R" 7
want "driver-owned field edit caught" 1 "protected fields or sections"
cleanup
sandbox; STUB_MISBEHAVE=rewrite-model run "$R" 7
want "model metadata edit caught" 1 "protected fields or sections"
cleanup

sandbox; run "$R" start 7 >/dev/null
D="$R/.agents/work/issue-7/plan.md"
sed 's/^claude_model: .*/claude_model: bad model/' "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" step 7
want "malformed saved model metadata is rejected" 1 "without spaces"
check "malformed saved model starts no agent turn" \
    "$([[ ! -d "$R/.agents/work/issue-7/turns" ]] && echo 0 || echo 1)"
cleanup

# A legacy document may continue through agent turns. Its known historical
# target is backfilled, while unknown starting metadata is required only for
# publication.
sandbox; run "$R" start 7 >/dev/null
D="$R/.agents/work/issue-7/plan.md"
awk '!/^(base_branch|start_commit|pr_base_branch): / &&
     !/^(claude_model|claude_effort|codex_model|codex_effort): /' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" step 7
want "legacy working document may take another non-publishing turn" 0
check "legacy document gets the historical master target" \
    "$(grep -qx 'pr_base_branch: master' "$D" && echo 0 || echo 1)"
check "legacy non-publishing turn writes a transcript" \
    "$([[ -f "$R/.agents/work/issue-7/turns/01-agent1.log" ]] && echo 0 || echo 1)"
cleanup

sandbox; run "$R" start 7 >/dev/null
D="$R/.agents/work/issue-7/plan.md"
awk '!/^question_origin: / &&
     !/^(claude_model|claude_effort|codex_model|codex_effort): /' \
    "$D" > "$D.t" && mv "$D.t" "$D"
STUB_MISBEHAVE=ask run "$R" 7
want "a legacy document can record a newly raised question" 3 "needs answers"
check "the driver records the origin after legacy metadata backfill" \
    "$(grep -qx 'question_origin: plan-requested' "$D" && echo 0 || echo 1)"
cleanup

sandbox
STUB_MISBEHAVE=ask run "$R" 7
D="$R/.agents/work/issue-7/plan.md"
awk '!/^question_origin: / &&
     !/^(claude_model|claude_effort|codex_model|codex_effort): /' \
    "$D" > "$D.t" && mv "$D.t" "$D"
awk '/^\*\*Q1\./ { print; print ""; print "**A1.** No retry."; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" 7
want "a legacy questions-pending document resumes from its saved status" 0 \
    "answers found"
cleanup

sandbox
STUB_MISBEHAVE=ask run "$R" 7
D="$R/.agents/work/issue-7/plan.md"
awk '!/^question_origin: /' "$D" > "$D.t" && mv "$D.t" "$D"
awk '/^\*\*Q1\./ { print; print ""; print "**A1.** No retry."; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" 7
want "missing question provenance in a current document is refused" 1 \
    "question_origin is missing from current-format"
cleanup

sandbox; run "$R" start 7 >/dev/null
D="$R/.agents/work/issue-7/plan.md"
awk '!/^(base_branch|start_commit|pr_base_branch): / &&
     !/^(claude_model|claude_effort|codex_model|codex_effort): /' \
    "$D" > "$D.t" && mv "$D.t" "$D"
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 --cp
want "legacy publication metadata is refused before an agent turn" 1 \
    "predates metadata required by --create-pr"
check "legacy publication refusal writes no transcript" \
    "$([[ ! -d "$R/.agents/work/issue-7/turns" ]] && echo 0 || echo 1)"
cleanup

sandbox; run "$R" start 7 >/dev/null
D="$R/.agents/work/issue-7/plan.md"
awk '!/^(claude_model|claude_effort|codex_model|codex_effort): /' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" step 7 --claude-model opus
want "legacy model metadata rejects new model options" 1 "predates model options"
check "legacy model refusal writes no transcript" \
    "$([[ ! -d "$R/.agents/work/issue-7/turns" ]] && echo 0 || echo 1)"
cleanup

# --- reviewer stays out of the worktree (RR3-01) --------------------------
sandbox; STUB_MISBEHAVE=agent2-edits run "$R" 7
want "agent2 editing a source file caught" 1 \
    "worktree content changed during agent2's review"
cleanup

sandbox
ln -s missing-target "$R/dangling-link"
run "$R" 7 --allow-dirty
want "a dangling untracked symlink survives reviewer snapshots" 0 "is done"
cleanup

sandbox
printf 'same content\n' > "$R/link-target-a"
printf 'same content\n' > "$R/link-target-b"
ln -s link-target-a "$R/review-link"
STUB_MISBEHAVE=agent2-retarget-link run "$R" 7 --allow-dirty
want "agent2 retargeting an untracked symlink is caught" 1 \
    "worktree content changed during agent2's review"
cleanup

# --- section ownership (RR3-02) -------------------------------------------
sandbox; STUB_MISBEHAVE=edit-review run "$R" 7
want "agent1 rewriting the review caught" 1 "section that is closed"
cleanup
sandbox; STUB_MISBEHAVE=rewrite-log run "$R" 7
want "rewritten log entry caught" 1 "append exactly one"
cleanup
sandbox; STUB_MISBEHAVE=extend-log run "$R" 7
want "extending the previous log line is refused" 1 "append exactly one"
cleanup
sandbox; STUB_MISBEHAVE=omit-log run "$R" 7
want "a turn without a new log line is refused" 1 "append exactly one"
cleanup
sandbox; STUB_MISBEHAVE=rewrite-prior-owned run "$R" 7
want "agent1 cannot rewrite its completed prior-round section" 1 "closed during"
cleanup

# --- resume target (RR3-03) -----------------------------------------------
sandbox; STUB_MISBEHAVE=bad-resume run "$R" 7
want "resume_status past the asking status refused" 1 "resumes where it was asked"
cleanup

# A value written before this guard existed, or edited in by hand, is still
# refused when the answers come back rather than acted on.
sandbox
STUB_MISBEHAVE=ask run "$R" 7
D="$R/.agents/work/issue-7/plan.md"
awk '/^resume_status: / { print "resume_status: done"; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
awk '/^\*\*Q1\./ { print; print ""; print "**A1.** No retry."; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" 7; want "resume into a terminal status refused" 1 "question was raised from"
cleanup

sandbox
STUB_MISBEHAVE=ask run "$R" 7
D="$R/.agents/work/issue-7/plan.md"
awk '/^resume_status: / { print "resume_status: implementation-reviewed"; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
awk '/^\*\*Q1\./ { print; print ""; print "**A1.** No retry."; next } { print }' \
    "$D" > "$D.t" && mv "$D.t" "$D"
run "$R" 7
want "resume into another allowed but later status refused" 1 "question was raised from"
cleanup

# --- review results gate advancement (RR3-04) -----------------------------
sandbox; STUB_MISBEHAVE=empty-review run "$R" 7
want "advancing on an empty review refused" 1 "has to say what was reviewed"
cleanup
sandbox; STUB_MISBEHAVE=no-verdict run "$R" 7
want "review without a verdict refused" 1 "states no verdict"
cleanup
sandbox; STUB_MISBEHAVE=verdict-in-prose run "$R" 7
want "a verdict token in prose does not satisfy the review" 1 "states no verdict"
cleanup
for verdict in 'Verdict: **APPROVE**' '**Verdict:** APPROVE' \
    'Verdict: `APPROVE`' '**Verdict: APPROVE**' 'Verdict: approve.'; do
    sandbox; STUB_VERDICT_LINE="$verdict" run "$R" 7
    want "formatted verdict is accepted: ${verdict}" 0 "is done"
    cleanup
done
sandbox; STUB_VERDICT_LINE=$'Verdict: APPROVE\n\n---' run "$R" 7
want "a horizontal rule may follow the verdict" 0 "is done"
cleanup
for layout in bullet numbered heading table; do
    sandbox
    STUB_FINDING_LAYOUT="$layout" STUB_MISBEHAVE=no-dispositions run "$R" 7
    want "${layout} findings must be dispositioned" 1 "undispositioned"
    cleanup
done
sandbox; STUB_MISBEHAVE=prose-disposition run "$R" 7
want "mentioning a finding outside a valid table row is refused" 1 "undispositioned"
cleanup
sandbox; STUB_MISBEHAVE=duplicate-disposition run "$R" 7
want "duplicate disposition rows are refused" 1 "undispositioned"
cleanup
sandbox; STUB_VERDICT="REQUEST CHANGES" run "$R" 7
want "advancing past REQUEST CHANGES refused" 1 "is not available from here"
cleanup
sandbox; STUB_MISBEHAVE=no-outcome run "$R" 7
want "done with an empty Outcome refused" 1 "empty ## Outcome"
cleanup

# --- per-round snapshots (RR3-05) -----------------------------------------
sandbox; STUB_MISBEHAVE=loop-once run "$R" 7 --mr 2
want "a second review round reaches done" 0 "is done"
check "the previous plan round was saved for comparison" \
    "$([[ -f "$R/.agents/work/issue-7/rounds/plan-1.md" ]] && echo 0 || echo 1)"
check "the implementation round patch was saved" \
    "$([[ -f "$R/.agents/work/issue-7/rounds/impl-1.patch" ]] && echo 0 || echo 1)"
check "the saved patch carries the untracked implementation file" \
    "$(grep -q 'src.txt' "$R/.agents/work/issue-7/rounds/impl-1.patch" && echo 0 || echo 1)"
cleanup
sandbox; STUB_MISBEHAVE=loop-once STUB_PRIOR_REFERENCE=1 run "$R" 7 --mr 2
want "a previous-round finding reference is not a new finding" 0 "is done"
cleanup
sandbox; STUB_MISBEHAVE=wrong-review-dash run "$R" 7 --mr 2
want "a mistyped review heading gets a precise error" 1 \
    "must write exactly one '## Plan Review — Round 2'"
cleanup
sandbox; STUB_MISBEHAVE=tamper-snapshot run "$R" 7
want "rewriting a driver-owned round snapshot is refused" 1 "review snapshot"
cleanup

# --- the final state must still be what agent2 reviewed -------------------
sandbox; STUB_MISBEHAVE=final-edit run "$R" 7
want "agent1 source edits after review require another round" 1 "another implementation review"
cleanup
sandbox; STUB_MISBEHAVE=fix-and-rereview run "$R" 7 --mr 2
want "agent1 source fixes finish after another review" 0 "is done"
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
run "$R" 7 --claude-model 'bad model'
reject "model names with spaces are rejected" "without spaces"
run "$R" 7 --claude-effort ultra
reject "unknown Claude effort is rejected" "must be low, medium, high, xhigh, or max"
run "$R" 7 --codex-effort max
reject "unknown Codex effort is rejected" "must be minimal, low, medium, high, or xhigh"
run "$R" https://github.com/other/proj/issues/7
reject "foreign issue URL refused" "but this repository"
run "$R" https://example.invalid/o/r/issues/7
want "foreign issue host refused" 1 "but this repository"
STUB_REPO_URL=unavailable run "$R" https://github.com/o/r/issues/7
want "URL validation fails closed" 1 "cannot resolve"
cleanup

# A missing recorded PR target is a setup error, before an agent turn can be
# spent against a baseline that cannot be published.
sandbox
export PR_BASE_BRANCH=missing
run "$R" 7 --cp
want "missing PR target is refused during setup" 1 "could not refresh origin/missing"
check "missing PR target starts no agent turn" \
    "$([[ ! -d "$R/.agents/work/issue-7/turns" ]] && echo 0 || echo 1)"
unset PR_BASE_BRANCH
cleanup

# --- dirty worktree (RS-01) -----------------------------------------------
sandbox; echo scratch > "$R/untracked.txt"
run "$R" 7;                want "dirty start refused"       1 "uncommitted changes"
run "$R" 7 --allow-dirty;  want "dirty start with override" 0 "allow-dirty"
cleanup
sandbox; echo scratch > "$R/untracked.txt"
run "$R" 7 --allow-dirty --cp
reject "dirty publication flags fail before setup" "cannot be used together"
cleanup
sandbox; echo scratch > "$R/untracked.txt"
run "$R" start 7 --allow-dirty
run "$R" 7 --cp
want "publication of an existing dirty-start run fails before a turn" 1 \
    "publication is disabled"
check "dirty publication refusal writes no transcript" \
    "$([[ ! -d "$R/.agents/work/issue-7/turns" ]] && echo 0 || echo 1)"
cleanup

# A missing earlier transcript must not make the next turn reuse an existing
# sequence number and overwrite the record that survived.
sandbox; run "$R" start 7
mkdir -p "$R/.agents/work/issue-7/turns"
printf 'surviving transcript\n' > "$R/.agents/work/issue-7/turns/02-agent1.log"
run "$R" step 7
want "a transcript gap does not prevent the next turn" 0
check "a transcript gap allocates after the highest sequence" \
    "$([[ -f "$R/.agents/work/issue-7/turns/03-agent1.log" ]] && echo 0 || echo 1)"
check "the surviving transcript is not overwritten" \
    "$(grep -q '^surviving transcript$' \
        "$R/.agents/work/issue-7/turns/02-agent1.log" && echo 0 || echo 1)"
cleanup

# A surviving Trace2 sidecar also owns its sequence number. Reusing it would
# append stale commands and let an old marker stand in for the current turn.
sandbox; run "$R" start 7
mkdir -p "$R/.agents/work/issue-7/turns"
printf '{"event":"cmd_name","name":"version"}\n' \
    > "$R/.agents/work/issue-7/turns/04-agent1.git-trace.json"
run "$R" step 7
want "an orphan Git trace does not get reused" 0
check "an orphan Git trace advances the shared sequence" \
    "$([[ -f "$R/.agents/work/issue-7/turns/05-agent1.git-trace.json" ]] \
        && echo 0 || echo 1)"
cleanup

# --- execution boundary (RR2-06) ------------------------------------------
sandbox
AGENT1_CMD="${SANDBOX}/bin/stub-agent --dangerously-skip-permissions" run "$R" 7
want "unsafe agent command refused by default" 1 "allow-unsafe-agents"
AGENT1_CMD="${SANDBOX}/bin/stub-agent --dangerously-skip-permissions" \
    run "$R" 7 --allow-unsafe-agents
want "unsafe agent command needs explicit opt-in" 0 "is done"
cleanup

sandbox
AGENT2_CMD="${SANDBOX}/bin/stub-agent2 --sandbox danger-full-access" \
    run "$R" 7 --sa
want "swapped unsafe agent command is still refused" 1 "allow-unsafe-agents"
cleanup

# Every agent turn disables reusable Gradle and Kotlin daemons. The Kotlin
# strategy is a Gradle project property, so GRADLE_OPTS needs its
# `org.gradle.project.` system-property form.
sandbox; run "$R" start 7; run "$R" step 7
want "an agent turn advances with daemon isolation" 0
check "an agent turn disables reusable Gradle daemons" \
    "$(grep -q '^CHORDS_NO_GRADLE_DAEMON=1$' "$STUB_AGENT_ENV_RECORD" \
        && grep -q -- '-Dorg.gradle.daemon=false' "$STUB_AGENT_ENV_RECORD" \
        && echo 0 || echo 1)"
check "an agent turn selects in-process Kotlin compilation" \
    "$(grep -q -- \
        '-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process' \
        "$STUB_AGENT_ENV_RECORD" && echo 0 || echo 1)"
cleanup

# --- sandboxed implementer verification access ----------------------------
# Codex under workspace-write cannot start the root Gradle build: the wrapper
# locks inside the Gradle user home, and gradle.properties forces a forked
# daemon that binds a loopback port. An implementer that cannot build reaches
# review having compiled nothing, and the run ends `blocked` rather than done.
sandbox
export GRADLE_USER_HOME="${SANDBOX}/gradle-home"
mkdir -p "$GRADLE_USER_HOME"
export AGENT1_CMD="${SANDBOX}/bin/codex exec --sandbox workspace-write"
export AGENT2_CMD="${SANDBOX}/bin/claude"
run "$R" start 7
want "a sandboxed Codex implementer starts a task" 0
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7
want "the implementer grant is announced, not silent" 0 "implementer sandbox widened"
check "the implementer can reach the Gradle user home" \
    "$(grep -qF -- "<--add-dir> <${GRADLE_USER_HOME}>" \
        "$STUB_AGENT_ARGS_RECORD" && echo 0 || echo 1)"
check "the implementer sandbox admits the Gradle daemon socket" \
    "$(grep -qF -- '<-c> <sandbox_workspace_write.network_access=true>' \
        "$STUB_AGENT_ARGS_RECORD" && echo 0 || echo 1)"
unset GRADLE_USER_HOME
cleanup

# Codex accepts one sandbox policy under several spellings. A customized
# command using any of them must be recognized: matching only the long
# separated form leaves the implementer unwidened, and the run then fails the
# root build with nothing in the output naming the cause.
for spelling in "-s workspace-write" "-s=workspace-write" \
    "-sworkspace-write" "--sandbox=workspace-write"; do
    sandbox
    export GRADLE_USER_HOME="${SANDBOX}/gradle-home"
    mkdir -p "$GRADLE_USER_HOME"
    export AGENT1_CMD="${SANDBOX}/bin/codex exec ${spelling}"
    export AGENT2_CMD="${SANDBOX}/bin/claude"
    run "$R" start 7
    : > "$STUB_AGENT_ARGS_RECORD"
    run "$R" step 7
    want "'${spelling}' is recognized as workspace-write" 0 \
        "implementer sandbox widened"
    check "'${spelling}' reaches the Gradle user home" \
        "$(grep -qF -- "<--add-dir> <${GRADLE_USER_HOME}>" \
            "$STUB_AGENT_ARGS_RECORD" && echo 0 || echo 1)"
    unset GRADLE_USER_HOME
    cleanup
done

# The mirror of the above: recognizing spellings must not decay into matching
# the words anywhere in the command. A policy that is not workspace-write has
# no claim on the widening, whether or not the phrase appears elsewhere.
for spelling in "-s read-only" "--sandbox read-only" \
    "-s read-only -c sandbox_workspace_write.network_access=false"; do
    sandbox
    export GRADLE_USER_HOME="${SANDBOX}/gradle-home"
    mkdir -p "$GRADLE_USER_HOME"
    export AGENT1_CMD="${SANDBOX}/bin/codex exec ${spelling}"
    export AGENT2_CMD="${SANDBOX}/bin/claude"
    run "$R" start 7
    : > "$STUB_AGENT_ARGS_RECORD"
    run "$R" step 7
    check "'${spelling}' is not widened" \
        "$(grep -qF -- "<--add-dir> <${GRADLE_USER_HOME}>" \
            "$STUB_AGENT_ARGS_RECORD" && echo 1 || echo 0)"
    unset GRADLE_USER_HOME
    cleanup
done

# A caller who already granted the access keeps their own spelling: the driver
# must neither duplicate the argument nor claim a widening it did not make.
sandbox
export GRADLE_USER_HOME="${SANDBOX}/gradle-home"
mkdir -p "$GRADLE_USER_HOME"
export AGENT1_CMD="${SANDBOX}/bin/codex exec --sandbox workspace-write \
--add-dir=${GRADLE_USER_HOME} -c=sandbox_workspace_write.network_access=true"
export AGENT2_CMD="${SANDBOX}/bin/claude"
run "$R" start 7
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7
want "an already-granted implementer is not widened again" 0
check "the caller's own grant is not announced as a widening" \
    "$(printf '%s' "$OUT" | grep -q 'implementer sandbox widened' \
        && echo 1 || echo 0)"
check "the Gradle user home is not passed twice" \
    "$([[ "$(grep -c -- "--add-dir" "$STUB_AGENT_ARGS_RECORD")" -eq 1 ]] \
        && echo 0 || echo 1)"
unset GRADLE_USER_HOME
cleanup

# The reviewer never builds, so the widening must not follow Codex into that
# seat — this is what keeps the default configuration unchanged.
sandbox
export GRADLE_USER_HOME="${SANDBOX}/gradle-home"
mkdir -p "$GRADLE_USER_HOME"
export AGENT1_CMD="${SANDBOX}/bin/claude"
export AGENT2_CMD="${SANDBOX}/bin/codex exec --sandbox workspace-write"
run "$R" start 7
want "a Codex reviewer starts a task" 0
run "$R" step 7
want "the Claude implementer takes its turn" 0
: > "$STUB_AGENT_ARGS_RECORD"
run "$R" step 7
want "the Codex reviewer takes its turn" 0
check "a Codex reviewer keeps the narrower sandbox" \
    "$(grep -q 'network_access' "$STUB_AGENT_ARGS_RECORD" && echo 1 || echo 0)"
check "a Codex reviewer is not given the Gradle user home" \
    "$(grep -qF -- "<--add-dir> <${GRADLE_USER_HOME}>" \
        "$STUB_AGENT_ARGS_RECORD" && echo 1 || echo 0)"
unset GRADLE_USER_HOME
cleanup

# A Gradle user home that does not exist yet is a warning, not an abort: the
# run is still worth taking, it just cannot verify at the end.
sandbox
export GRADLE_USER_HOME="${SANDBOX}/never-populated"
export AGENT1_CMD="${SANDBOX}/bin/codex exec --sandbox workspace-write"
export AGENT2_CMD="${SANDBOX}/bin/claude"
run "$R" 7
want "a missing Gradle user home warns without failing the run" 0 \
    "no Gradle user home"
unset GRADLE_USER_HOME
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

sandbox
STUB_ISSUE_TITLE='Keep ISSUE_URL literal' run "$R" start 7
check "placeholder text inside an issue title stays literal" \
    "$(grep -q 'issue_title: Keep ISSUE_URL literal' \
        "$R/.agents/work/issue-7/plan.md" && echo 0 || echo 1)"
cleanup

sandbox
STUB_ISSUE_TITLE='Поліпшити пошук' run "$R" start 7
check "a non-ASCII issue title gets a safe branch fallback" \
    "$(grep -qx 'task_branch: issue-7' \
        "$R/.agents/work/issue-7/plan.md" && echo 0 || echo 1)"
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
pr_section_with_notes() {
    local d="$1/.agents/work/issue-7/plan.md"
    awk '/^## Pull Request$/ {
            print; print ""
            print "### Summary"; print "Does the thing."; print ""
            print "### Changes"; print "- Added src.txt"; print ""
            print "### Reviewer notes"; print "Agent context."; print ""
            print "### Important notes"; print "Keep this last."
            next
         } {print}' "$d" > "$d.t" && mv "$d.t" "$d"
}
# Reads the argument immediately following an option from the recorded gh
# invocation. PR bodies contain newlines, so whole-file grep is too loose.
recorded_arg_after() {
    awk -v option="$2" 'found { print; exit } $0 == option { found = 1 }' "$1"
}
# A real bump moves the project version and both generated reports together.
bump() {
    local v="${2:-2.0.0-SNAPSHOT.2}"
    printf 'val chordsVersion: String by extra("%s")\n' "$v" > "$1/version.gradle.kts"
    printf '<project>\n  <version>%s</version>\n  <dependencies/>\n</project>\n' \
        "$v" > "$1/pom.xml"
    printf '# Dependencies of `o:r:%s`\n' "$v" > "$1/dependencies.md"
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

# The scratch root is an explicit driver exclusion, not a mutable .gitignore
# convention. A task may change that rule without reviewing or publishing its
# own plans, transcripts, and traces.
sandbox
STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_MISBEHAVE=unignore-work run "$R" 7 >/dev/null
pr_section "$R"
run "$R" 7 --cp
want "removing the work-root ignore rule does not publish scratch artifacts" 0 \
    "draft pull request"
check "the published tree excludes pair-workflow scratch files" \
    "$([[ -z "$(git -C "$R" ls-tree -r --name-only HEAD .agents/work)" ]] \
        && echo 0 || echo 1)"
cleanup

sandbox
STUB_ISSUE_TITLE='Fix the title...' STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null
pr_section "$R"
run "$R" 7 --cp
want "all trailing periods are removed from a PR title" 0 "draft pull request"
check "the recorded PR title has no trailing period" \
    "$([[ "$(recorded_arg_after "$STUB_PR_RECORD" --title)" == 'Fix the title' ]] \
        && echo 0 || echo 1)"
cleanup

sandbox
git -C "$R" tag -a unrelated-release -m "Unrelated release"
git -C "$R" config push.followTags true
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null
pr_section "$R"
run "$R" 7 --cp
want "publication neutralizes automatic tag following" 0 "draft pull request"
check "an unrelated annotated tag was not published" \
    "$([[ -z "$(git -C "$R" ls-remote --tags origin)" ]] && echo 0 || echo 1)"
cleanup

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
run "$R" 7 --cp; want "PR refused without a version bump" 1 "not in the changeset"
current_branch="$(git -C "$R" rev-parse --abbrev-ref HEAD)"
check "nothing was committed on refusal" \
    "$([[ "$current_branch" == master ]] && echo 0 || echo 1)"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" config pair.externalMutation true
run "$R" 7 --cp
want "publication refuses changed Git transport configuration" 1 \
    "effective Git configuration changed"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "PR published from a compliant changeset" 0 "draft pull request"
check "version commit uses the required message" \
    "$(git -C "$R" log --format=%s | grep -q '^Bump version' && echo 0 || echo 1)"
check "branch was pushed to origin" \
    "$(git -C "$R" ls-remote --heads origin | grep -q . && echo 0 || echo 1)"
STUB_PR_EXISTS=https://github.com/o/r/pull/1 run "$R" 7 --cp
want "re-run finds the existing PR" 0 "already open"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_EXISTS=https://github.com/o/r/pull/1 STUB_PR_BASE=staging run "$R" 7 --cp
want "an existing PR on the wrong base is not accepted as published" 1 \
    "different head or base"
cleanup

sandbox
git -C "$STUB_EXTERNAL_REPO" checkout -qb a-test-issue
printf 'collision\n' > "$STUB_EXTERNAL_REPO/collision.txt"
git -C "$STUB_EXTERNAL_REPO" add collision.txt
git -C "$STUB_EXTERNAL_REPO" commit -qm "Colliding task branch"
git -C "$STUB_EXTERNAL_REPO" push -q origin a-test-issue
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null
pr_section "$R"
run "$R" 7 --cp
want "a remote task-branch collision fails before local publication commits" 1 \
    "contains history outside this task's starting point"
check "remote collision leaves the starting branch untouched" \
    "$([[ "$(git -C "$R" rev-parse --abbrev-ref HEAD)" == master ]] \
        && echo 0 || echo 1)"
cleanup

# A failed `gh pr create` must leave the push intact and stay retryable.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp
want "failed PR create reports and stops" 1 "gh pr create failed"
run "$R" 7 --cp; want "retry after a failed PR create" 0 "draft pull request"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp >/dev/null
git -C "$R" commit -qm "Unreviewed empty commit" --allow-empty
run "$R" 7 --cp
want "a content-equivalent commit cannot enter a publication retry" 1 \
    "moved beyond the driver's recorded publication head"
cleanup

# A retry trusts the exact server head, not a potentially stale or unrelated
# local upstream. External commits on the task branch must never enter the PR.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp >/dev/null
git -C "$STUB_EXTERNAL_REPO" fetch -q origin
git -C "$STUB_EXTERNAL_REPO" checkout -qB a-test-issue origin/a-test-issue
printf 'remote-only\n' > "$STUB_EXTERNAL_REPO/remote-only.txt"
git -C "$STUB_EXTERNAL_REPO" add remote-only.txt
git -C "$STUB_EXTERNAL_REPO" commit -qm "External task-branch commit"
git -C "$STUB_EXTERNAL_REPO" push -q origin a-test-issue
run "$R" 7 --cp
want "an externally advanced task branch is not treated as already pushed" 1 \
    "contains commits outside this reviewed run"
cleanup

# --- stacked work ---------------------------------------------------------
# Starting from a branch whose own PR is still open is the ordinary case, not a
# refusal: the task branch is cut from it and the PR still targets master.
stack_on() { # stack_on <repo> <branch> [version] — creates one commit outside master
    git -C "$1" checkout -q -b "$2"
    printf 'earlier\n' > "$1/earlier.txt"
    if [[ -n "${3:-}" ]]; then
        bump "$1" "$3"
    fi
    git -C "$1" add -A
    git -C "$1" commit -qm "Earlier work under review"
}

# Advances the PR target from another checkout, as an unrelated GitHub change
# would, then refreshes the repository under test.
advance_origin_master() { # advance_origin_master <repo>
    printf 'unrelated\n' > "$STUB_EXTERNAL_REPO/unrelated.txt"
    git -C "$STUB_EXTERNAL_REPO" add unrelated.txt
    git -C "$STUB_EXTERNAL_REPO" commit -qm "Unrelated target change"
    git -C "$STUB_EXTERNAL_REPO" push -q origin master
    git -C "$1" fetch -q origin
}

sandbox; stack_on "$R" open-pr-branch
parent_head="$(git -C "$R" rev-parse HEAD)"
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7
want "stacking is reported during setup" 0 "starting point 'open-pr-branch'"
check "review prompt construction emits no command errors" \
    "$(printf '%s' "$OUT" | grep -q 'command not found' && echo 1 || echo 0)"
pr_section "$R"
run "$R" 7 --cp; want "PR published from a stacked branch" 0 "draft pull request"
want "stacking is reported to the user" 0 "stacked on 'open-pr-branch'"
recorded_start="$(awk '/^start_commit: / {print $2}' \
    "$R/.agents/work/issue-7/plan.md")"
check "the exact starting commit is recorded" \
    "$([[ "$recorded_start" == "$parent_head" ]] && echo 0 || echo 1)"
check "task branch was cut from the stacked branch" \
    "$(git -C "$R" rev-parse --abbrev-ref HEAD | grep -qx 'a-test-issue' && echo 0 || echo 1)"
check "the stacked branch was not committed onto" \
    "$([[ "$(git -C "$R" rev-list --count open-pr-branch)" -eq 2 ]] && echo 0 || echo 1)"
check "the PR still targets master" \
    "$([[ "$(recorded_arg_after "$STUB_PR_RECORD" --base)" == master ]] \
        && echo 0 || echo 1)"
check "the PR body names the branch it is stacked on" \
    "$(grep -q 'Reviewer notes' "$STUB_PR_RECORD" &&
       grep -q 'open-pr-branch' "$STUB_PR_RECORD" &&
       grep -q "${parent_head:0:7}" "$STUB_PR_RECORD" && echo 0 || echo 1)"
check "the agent review patch excludes inherited parent files" \
    "$(grep -q 'earlier.txt' \
        "$R/.agents/work/issue-7/rounds/impl-1.patch" && echo 1 || echo 0)"
check "the reviewer prompt excludes inherited commits from findings" \
    "$(grep -q 'outside this task and must not produce findings' \
        "$STUB_REVIEW_PROMPT_RECORD" && echo 0 || echo 1)"
check "the reviewer prompt names the recorded task boundary" \
    "$(grep -qF "Review only this task's changes from \`${recorded_start}\`" \
        "$STUB_REVIEW_PROMPT_RECORD" && echo 0 || echo 1)"
cleanup

# An agent may add reviewer context of its own. The driver merges the stacking
# paragraph into that section before any later optional section.
sandbox; stack_on "$R" open-pr-branch
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section_with_notes "$R"
run "$R" 7 --cp; want "agent and driver reviewer notes publish" 0 "draft pull request"
check "the PR body has one Reviewer notes heading" \
    "$([[ "$(grep -c '^## Reviewer notes$' "$STUB_PR_RECORD")" -eq 1 ]] \
        && echo 0 || echo 1)"
check "the stacking paragraph stays inside Reviewer notes" \
    "$(awk '
        $0 == "## Reviewer notes" { notes++; note_line = NR }
        /^The workflow started from / { driver_line = NR }
        $0 == "## Important notes" { important_line = NR }
        END {
            exit !(notes == 1 && note_line < driver_line &&
                   driver_line < important_line)
        }
    ' "$STUB_PR_RECORD" && echo 0 || echo 1)"
check "the agent reviewer context is preserved" \
    "$(grep -q '^Agent context\.$' "$STUB_PR_RECORD" && echo 0 || echo 1)"
check "the driver reviewer note has one blank line on each side" \
    "$(awk '
        {
            if (/^The workflow started from /) {
                found = 1
                before = (previous == "" && before_previous != "")
                getline
                after = ($0 == "")
                getline
                next_heading = ($0 == "## Important notes")
                exit !(before && after && next_heading)
            }
            before_previous = previous
            previous = $0
        }
        END { if (!found) exit 1 }
    ' "$STUB_PR_RECORD" && echo 0 || echo 1)"
cleanup

# A version inherited from the parent is not this task's required bump. It may
# be in the PR diff against master, but it is absent from the task changeset
# after the recorded starting commit.
sandbox; stack_on "$R" open-pr-branch 2.0.0-SNAPSHOT.2
run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp
want "a parent version bump does not satisfy the task" 1 \
    "not in the changeset after start_commit"
cleanup

# A run that starts on its existing task branch keeps using that branch, but
# earlier commits outside the PR target still need a reviewer boundary.
sandbox; stack_on "$R" a-test-issue
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "an existing task branch publishes" 0 "draft pull request"
want "existing task branch reports inherited history" 0 "stacked on 'a-test-issue'"
check "existing task branch gets a reviewer boundary" \
    "$(grep -q 'Reviewer notes' "$STUB_PR_RECORD" && echo 0 || echo 1)"
cleanup

# The immutable starting commit, not the later value of its branch ref, owns
# the stack boundary and carried-commit count.
sandbox; stack_on "$R" open-pr-branch
parent_head="$(git -C "$R" rev-parse HEAD)"
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" checkout -q --detach
git -C "$R" branch -f open-pr-branch master
run "$R" 7 --cp
want "a moved parent ref keeps the recorded stack boundary" 0 \
    "stacked on 'open-pr-branch' at ${parent_head:0:7}"
check "a moved parent ref does not remove the reviewer note" \
    "$(grep -q "${parent_head:0:7}" "$STUB_PR_RECORD" && echo 0 || echo 1)"
cleanup

# Switching to another commit with the same tree cannot retarget the task
# branch: content digests do not encode ancestry.
sandbox
git -C "$R" checkout -q -b alternate
git -C "$R" commit -qm "Equivalent alternate history" --allow-empty
git -C "$R" checkout -q master
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" checkout -q alternate
run "$R" 7 --cp
want "publication refuses a different HEAD with the same tree" 1 \
    "HEAD moved from the recorded starting commit"
cleanup

# A detached start is recorded as a commit label; publication should not expose
# Git's raw "HEAD" sentinel in its branch-creation message.
sandbox
git -C "$R" checkout -q --detach
detached_start="$(git -C "$R" rev-parse --short HEAD)"
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "publication from a detached start succeeds" 0 "draft pull request"
want "detached publication names its recorded commit" 0 \
    "created branch 'a-test-issue' from '${detached_start}'"
check "detached publication does not report raw HEAD" \
    "$(printf '%s' "$OUT" | grep -q "from 'HEAD'" && echo 1 || echo 0)"
cleanup

# The stacked conclusion comes from the recorded base branch, not from the
# branch that happens to be checked out, so a retry reaches it again.
sandbox; stack_on "$R" open-pr-branch
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp >/dev/null
run "$R" 7 --cp; want "retry from the task branch still reports stacking" 0 \
    "stacked on 'open-pr-branch'"
cleanup

# An unrelated target advance shares only the recorded start with the task.
# It changes GitHub state without changing the task or the PR changeset.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
advance_origin_master "$R"
run "$R" 7 --cp; want "an unrelated target advance still publishes" 0 \
    "draft pull request"
cleanup

# Publication refreshes the target itself; correctness cannot depend on an IDE
# or the caller fetching between the external update and --create-pr.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
printf 'not fetched locally\n' > "$STUB_EXTERNAL_REPO/not-fetched.txt"
git -C "$STUB_EXTERNAL_REPO" add not-fetched.txt
git -C "$STUB_EXTERNAL_REPO" commit -qm "Unfetched target change"
git -C "$STUB_EXTERNAL_REPO" push -q origin master
run "$R" 7 --cp
want "publication refreshes a stale local target ref" 0 "draft pull request"
cleanup

# A parent that merges mid-run moves the merge-base forward. That subtracts
# from the pull request rather than adding to it: the carried commits are in
# the target now, and the task's own reviewed changeset is measured from
# `start_commit` either way. The baseline is recomputed and the run publishes,
# because the merge was nobody-in-the-run's doing and the review still stands.
sandbox; stack_on "$R" open-pr-branch
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" push -q origin open-pr-branch:master
git -C "$R" fetch -q origin
run "$R" 7 --cp; want "a parent merging mid-run publishes from the new base" 0 \
    "draft pull request"
want "the moved baseline is reported" 0 "baseline moves from"
check "the merged parent is no longer carried as stacking" \
    "$(printf '%s' "$OUT" | grep -q 'stacked on' && echo 1 || echo 0)"
cleanup

# A publication retry may see the target absorb one of the task commits after
# the failed push/create sequence. That is beyond the immutable task boundary,
# so advancing the baseline would silently omit reviewed work from the PR.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
STUB_PR_CREATE_FAILS=1 run "$R" 7 --cp >/dev/null
recorded_start="$(awk '/^start_commit: / {print $2}' \
    "$R/.agents/work/issue-7/plan.md")"
first_task_commit="$(git -C "$R" rev-list --reverse \
    "${recorded_start}..HEAD" | head -n 1)"
git -C "$R" push -q origin "${first_task_commit}:master"
git -C "$R" fetch -q origin
run "$R" 7 --cp
want "a target that absorbed task commits is refused" 1 \
    "absorbed commits produced by this run"
check "no PR was created after task absorption" \
    "$([[ -f "$STUB_PR_RECORD" ]] && echo 1 || echo 0)"
cleanup

# Advancing is not the same as being rewritten. A target force-pushed back
# behind the recorded base leaves a scope the driver cannot reconstruct, and
# that still refuses rather than guessing.
sandbox
printf 'later\n' > "$R/later.txt"
git -C "$R" add -A
git -C "$R" commit -qm "Later work on master"
git -C "$R" push -q origin master
rewound="$(git -C "$R" rev-parse --short 'HEAD^')"
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" push -qf origin "${rewound}:master"
run "$R" 7 --cp; want "a rewritten target is refused, not published" 1 \
    "was rewritten after setup"
check "nothing was published when the target was rewritten" \
    "$([[ -f "$STUB_PR_RECORD" ]] && echo 1 || echo 0)"
cleanup

# Local master moving is not a merge. Only origin/master is, since that is what
# the pull request is opened against.
sandbox; stack_on "$R" open-pr-branch
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" branch -f master open-pr-branch
run "$R" 7 --cp; want "an unpushed local master does not clear stacking" 0 \
    "stacked on 'open-pr-branch'"
cleanup

# An unstacked run must not gain a reviewer note it does not need.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "unstacked run publishes" 0 "draft pull request"
check "no stacking reported when cut from master" \
    "$(printf '%s' "$OUT" | grep -q 'stacked on' && echo 1 || echo 0)"
check "no reviewer note when cut from master" \
    "$([[ -f "$STUB_PR_RECORD" ]] &&
       ! grep -q 'Reviewer notes' "$STUB_PR_RECORD" && echo 0 || echo 1)"
cleanup

# The configurable PR target is captured at setup, so a later invocation cannot
# silently retarget a finished run by omitting or changing the environment.
sandbox
git -C "$R" branch trunk
git -C "$R" push -qu origin trunk
export PR_BASE_BRANCH=trunk
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
unset PR_BASE_BRANCH
run "$R" 7 --cp; want "a configured PR base publishes" 0 "draft pull request"
check "the configured PR base is recorded in the document" \
    "$(grep -qx 'pr_base_branch: trunk' \
        "$R/.agents/work/issue-7/plan.md" && echo 0 || echo 1)"
check "the recorded PR base reaches gh after the environment is cleared" \
    "$([[ "$(recorded_arg_after "$STUB_PR_RECORD" --base)" == trunk ]] \
        && echo 0 || echo 1)"
cleanup

# Preserve Git's useful diagnosis when the derived task branch already exists.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" branch a-test-issue
run "$R" 7 --cp
want "existing derived branch reports the Git error" 1 "already exists"
cleanup

# Committing the reviewed work onto the parent branch moves HEAD away from the
# recorded starting point, so publication refuses it before creating a branch.
sandbox; stack_on "$R" open-pr-branch
STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
git -C "$R" add -A; git -C "$R" commit -qm "premature"
run "$R" 7 --cp; want "committed work on a foreign branch refused" 1 \
    "HEAD moved from the recorded starting commit"
cleanup

# --- publication is bound to the reviewed changeset (RR3-06) --------------
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
bump "$R" 2.0.0-SNAPSHOT.3
run "$R" 7 --cp; want "post-review edits are not published" 1 \
    "worktree changed after the task reached done"
check "nothing was committed on a digest mismatch" \
    "$([[ "$(git -C "$R" rev-parse --abbrev-ref HEAD)" == master ]] && echo 0 || echo 1)"
cleanup

sandbox
STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_MISBEHAVE=nonascii-path run "$R" 7 >/dev/null
pr_section "$R"
printf 'changed after review\n' > "$R/résumé.txt"
run "$R" 7 --cp
want "non-ASCII path content remains bound to the completed review" 1 \
    "worktree changed after the task reached done"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
chmod +x "$R/src.txt"
run "$R" 7 --cp
want "post-review executable-bit changes are not published" 1 \
    "worktree changed after the task reached done"
cleanup

# The prospective PR baseline is the merge-base with master, not whatever HEAD
# happens to be, so publication can account for every inherited commit.
sandbox
printf 'local master only\n' > "$R/local-master.txt"
git -C "$R" add -A && git -C "$R" commit -qm "Local master commit."
git -C "$R" checkout -q -b earlier-work
printf 'earlier\n' > "$R/earlier.txt"
git -C "$R" add -A && git -C "$R" commit -qm "Earlier commit."
run "$R" start 7 >/dev/null
recorded="$(awk '/^base_commit: / {print $2}' "$R/.agents/work/issue-7/plan.md")"
expected="$(git -C "$R" rev-parse \
    "$(git -C "$R" merge-base refs/remotes/origin/master HEAD)")"
check "baseline is the merge-base with origin/master" \
    "$([[ "$recorded" == "$expected" ]] && echo 0 || echo 1)"
check "a local-only master commit remains in the PR changeset" \
    "$([[ "$recorded" != "$(git -C "$R" rev-parse --short master)" ]] && echo 0 || echo 1)"
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

sandbox
bare_headings='Problem stated here.\n# Task\nIssue task details.\n# Log\n'
bare_headings+='Acceptance criteria: issue headings stay nested and the run completes.'
STUB_ISSUE_BODY="$bare_headings" run "$R" 7
want "bare issue headings remain nested under the Issue section" 0 "is done"
D="$R/.agents/work/issue-7/plan.md"
check "issue Task and Log headings do not duplicate workflow sections" \
    "$([[ "$(grep -c '^## Task$' "$D")" -eq 1 \
          && "$(grep -c '^## Log$' "$D")" -eq 1 \
          && "$(grep -c '^### Task$' "$D")" -eq 1 \
          && "$(grep -c '^### Log$' "$D")" -eq 1 ]] && echo 0 || echo 1)"
cleanup

sandbox
bare_hidden='Problem stated here.\n# Task\nHIDDEN_CRITERION\n'
bare_hidden+='Acceptance criteria: content after a bare heading remains protected.'
STUB_ISSUE_BODY="$bare_hidden" STUB_MISBEHAVE=edit-hidden run "$R" 7
want "rewriting after a bare issue heading is refused" 1 \
    "protected fields or sections"
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
sandbox; STUB_BUMP=fake run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "touched but unbumped version refused" 1 "did not increase"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.0 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "lower version refused" 1 "did not increase"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_BAD_REPORT=stale-pom \
    run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "stale POM refused" 1 "root project version"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_BAD_REPORT=misplaced-pom \
    run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp
want "new version elsewhere in a stale POM is refused" 1 "root project version"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_BAD_REPORT=stale-dependencies \
    run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp
want "new token with a stale dependency heading is refused" 1 "not every"
cleanup

sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 STUB_BAD_REPORT=partial-dependencies \
    run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp
want "partially stale dependency headings are refused" 1 "not every"
cleanup

sandbox; STUB_MANUAL=required STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
run "$R" 7 --cp; want "required manual testing without a plan is not published" 1 "no usable plan"
cleanup

sandbox; STUB_MANUAL=required STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null
pr_section "$R"; manual_plan "$R"
run "$R" 7 --cp; want "required manual testing with a plan publishes" 2 "draft pull request"
cleanup

for missing in long-bullet no-setup no-expected no-covers; do
    sandbox
    STUB_MANUAL=required STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null
    pr_section "$R"; manual_plan_variant "$R" "$missing"
    run "$R" 7 --cp
    want "manual plan rejects ${missing}" 1 "no usable plan"
    cleanup
done

# Workstation hooks are outside the reviewed changeset and must not run inside
# the driver's publication transaction.
sandbox; STUB_BUMP=2.0.0-SNAPSHOT.2 run "$R" 7 >/dev/null; pr_section "$R"
cat > "$R/.git/hooks/commit-msg" <<'HOOK'
#!/usr/bin/env bash
touch "$(git rev-parse --show-toplevel)/hook-ran"
exit 1
HOOK
chmod +x "$R/.git/hooks/commit-msg"
run "$R" 7 --cp
want "publication ignores workstation commit hooks" 0 "draft pull request"
check "the workstation hook did not execute" \
    "$([[ ! -e "$R/hook-ran" ]] && echo 0 || echo 1)"
cleanup

# --- agent execution environment (RF-11) ----------------------------------
#
# These assert the shipped configuration rather than the driver's logic,
# because both defects they cover already happened and neither is visible
# until a turn is already running. An agent that cannot write the document or
# cannot build produces a run that dies several minutes in, with a message
# about the symptom rather than the cause.
readonly REPO="${SUITE_DIR}/../.."

for option in --claude-model --claude-effort --codex-model --codex-effort; do
    check "the /pair command advertises ${option}" \
        "$(grep -qF -- "$option" "${REPO}/.claude/commands/pair.md" \
            && echo 0 || echo 1)"
done

# Codex's workspace-write sandbox excludes gitignored paths, and .agents/work/
# is gitignored by design. Without --add-dir the reviewer reads the document,
# forms its findings, and cannot write them down.
#
# Read the assignment out of the driver rather than its --help output: the
# usage text describes the flag in prose, so matching that would pass while
# the default that actually runs had lost it.
agent2_default="$(grep -m1 '^AGENT2_CMD=' "$DRIVER")"
check "AGENT2_CMD default makes the work root writable" \
    "$(printf '%s' "$agent2_default" | grep -q -- '--add-dir .agents/work' \
        && echo 0 || echo 1)"
check "the work root is gitignored, which is why --add-dir is needed" \
    "$(git -C "$REPO" check-ignore -q .agents/work/ && echo 0 || echo 1)"
check "Claude Code local permissions are gitignored" \
    "$(git -C "$REPO" check-ignore -q .claude/settings.local.json \
        && echo 0 || echo 1)"
check "lock signal traps terminate the driver after releasing the lock" \
    "$(grep -qF -- 'kill -s "$signal" "$$"' "$DRIVER" \
        && grep -qF -- "trap 'handle_signal TERM' TERM" "$DRIVER" \
        && echo 0 || echo 1)"

# agent1 runs with --setting-sources project, which loads .claude/settings.json
# and nothing else. A verification command missing from it is refused before
# the process starts, and the run reviews code that was never compiled.
check "project settings exist for agent1 to load" \
    "$([[ -f "${REPO}/.claude/settings.json" ]] && echo 0 || echo 1)"
check "project settings do not bypass the root Gradle wrapper" \
    "$(grep -qF -- 'Bash(./gradlew' "${REPO}/.claude/settings.json" \
        2>/dev/null && echo 1 || echo 0)"
check "project settings allow the codegen Gradle wrapper" \
    "$(grep -qF -- 'Bash(.agents/workflows/gradle-codegen.sh:*)' \
        "${REPO}/.claude/settings.json" 2>/dev/null && echo 0 || echo 1)"
for command in 'Bash(git status:*)' 'Bash(git diff:*)' \
    'Bash(git log:*)' 'Bash(git show:*)'; do
    check "project settings allow ${command}" \
        "$(grep -qF -- "$command" "${REPO}/.claude/settings.json" \
            2>/dev/null && echo 0 || echo 1)"
done

for skill in build-engineer codegen-engineer component-engineer tester kotlin-engineer; do
    check "${skill} routes root Gradle through the wrapper" \
        "$(grep -qF -- '.agents/workflows/gradle-root.sh' \
            "${REPO}/.agents/skills/${skill}/SKILL.md" && echo 0 || echo 1)"
done
for file in AGENTS.md \
    .agents/skills/build-engineer/SKILL.md \
    .agents/skills/ci-engineer/SKILL.md \
    .agents/skills/codegen-engineer/SKILL.md \
    .agents/skills/tester/SKILL.md; do
    check "${file} routes codegen Gradle through the wrapper" \
        "$(grep -qF -- '.agents/workflows/gradle-codegen.sh' \
            "${REPO}/${file}" && echo 0 || echo 1)"
done

# The root project's JDK 11 selection cannot be expressed as a permission rule:
# `JAVA_HOME="$(jenv prefix)" ./gradlew …` is an environment assignment with a
# command substitution, and `jenv shell 11` does not survive into the next
# command. The wrapper is that flow behind one allowlistable path, so an
# unattended implementation can compile before it is reviewed.
check "the root Gradle wrapper script is executable" \
    "$([[ -x "${SUITE_DIR}/gradle-root.sh" ]] && echo 0 || echo 1)"
check "project settings allow the root Gradle wrapper" \
    "$(grep -qF -- '.agents/workflows/gradle-root.sh' \
        "${REPO}/.claude/settings.json" 2>/dev/null && echo 0 || echo 1)"

wrapper="${SUITE_DIR}/gradle-root.sh"
wrapper_output="$($wrapper publish 2>&1)"; wrapper_rc=$?
check "the root wrapper refuses external publication" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"not permitted"* ]] \
        && echo 0 || echo 1)"
wrapper_output="$($wrapper --init-script /tmp/untrusted.gradle test 2>&1)"; wrapper_rc=$?
check "the root wrapper refuses arbitrary init scripts" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"not permitted"* ]] \
        && echo 0 || echo 1)"

# The codegen wrapper gives the separate build the same allowlistable JDK
# selection boundary as the root wrapper.
codegen_wrapper="${SUITE_DIR}/gradle-codegen.sh"
check "the codegen Gradle wrapper script is executable" \
    "$([[ -x "$codegen_wrapper" ]] && echo 0 || echo 1)"
wrapper_output="$($codegen_wrapper publish 2>&1)"; wrapper_rc=$?
check "the codegen wrapper refuses external publication" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"not permitted"* ]] \
        && echo 0 || echo 1)"
wrapper_output="$($codegen_wrapper --init-script /tmp/untrusted.gradle build 2>&1)"
wrapper_rc=$?
check "the codegen wrapper refuses arbitrary init scripts" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"not permitted"* ]] \
        && echo 0 || echo 1)"

fake_root="$(mktemp -d)"
mkdir -p "${fake_root}/.agents/workflows" "${fake_root}/codegen/plugins" \
    "${fake_root}/jdk/bin"
cp "$codegen_wrapper" "${fake_root}/.agents/workflows/gradle-codegen.sh"
cat > "${fake_root}/jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
printf '    java.specification.version = 17\n'
JAVA
cat > "${fake_root}/codegen/plugins/gradlew" <<'GRADLE'
#!/usr/bin/env bash
pwd > "$GRADLE_PWD_RECORD"
printf '<%s>\n' "$@" > "$GRADLE_ARGS_RECORD"
GRADLE
chmod +x "${fake_root}/jdk/bin/java" "${fake_root}/codegen/plugins/gradlew"
GRADLE_PWD_RECORD="${fake_root}/gradle.pwd" \
    GRADLE_ARGS_RECORD="${fake_root}/gradle.args" \
    CHORDS_JDK17_HOME="${fake_root}/jdk" \
    "${fake_root}/.agents/workflows/gradle-codegen.sh" build >/dev/null 2>&1
wrapper_rc=$?
check "the codegen wrapper accepts JDK 17" "$wrapper_rc"
check "the codegen wrapper enters the plugin build" \
    "$(grep -Fqx "${fake_root}/codegen/plugins" "${fake_root}/gradle.pwd" \
        && echo 0 || echo 1)"
check "the codegen wrapper isolates Gradle and Kotlin daemons" \
    "$(grep -qx '<--no-daemon>' "${fake_root}/gradle.args" \
        && grep -qx '<-Pkotlin.compiler.execution.strategy=in-process>' \
            "${fake_root}/gradle.args" \
        && grep -qx '<build>' "${fake_root}/gradle.args" \
        && echo 0 || echo 1)"
cat > "${fake_root}/jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
printf '    java.specification.version = 11\n'
JAVA
wrapper_output="$(CHORDS_JDK17_HOME="${fake_root}/jdk" \
    "${fake_root}/.agents/workflows/gradle-codegen.sh" build 2>&1)"
wrapper_rc=$?
check "the codegen wrapper refuses the wrong JDK" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"need JDK 17"* ]] \
        && echo 0 || echo 1)"
rm -rf "$fake_root"

# A forced daemonless run must pass both controls to Gradle.
fake_root="$(mktemp -d)"
mkdir -p "${fake_root}/.agents/workflows" "${fake_root}/jdk/bin"
cp "$wrapper" "${fake_root}/.agents/workflows/gradle-root.sh"
cat > "${fake_root}/jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
printf '    java.specification.version = 11\n'
printf '    os.arch = x86_64\n'
JAVA
cat > "${fake_root}/gradlew" <<'GRADLE'
#!/usr/bin/env bash
printf '<%s>\n' "$@" > "$GRADLE_ARGS_RECORD"
GRADLE
chmod +x "${fake_root}/jdk/bin/java" "${fake_root}/gradlew"
GRADLE_ARGS_RECORD="${fake_root}/gradle.args" \
    CHORDS_JDK11_HOME="${fake_root}/jdk" CHORDS_NO_GRADLE_DAEMON=1 \
    "${fake_root}/.agents/workflows/gradle-root.sh" :core:test >/dev/null 2>&1
wrapper_rc=$?
check "the root wrapper accepts forced daemon isolation" "$wrapper_rc"
check "the root wrapper passes both daemon controls" \
    "$(grep -qx '<--no-daemon>' "${fake_root}/gradle.args" \
        && grep -qx '<-Pkotlin.compiler.execution.strategy=in-process>' \
            "${fake_root}/gradle.args" \
        && echo 0 || echo 1)"
rm -rf "$fake_root"

# A fake Java 11 proves that the architecture guard fires before the real
# Gradle wrapper starts. The fake uname confines the macOS branch to this test.
fake_root="$(mktemp -d)"
mkdir -p "${fake_root}/jdk/bin" "${fake_root}/bin"
cat > "${fake_root}/jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
printf '    java.specification.version = 11\n'
printf '    os.arch = aarch64\n'
JAVA
cat > "${fake_root}/bin/uname" <<'UNAME'
#!/usr/bin/env bash
printf 'Darwin\n'
UNAME
chmod +x "${fake_root}/jdk/bin/java" "${fake_root}/bin/uname"
wrapper_output="$(PATH="${fake_root}/bin:${ORIGINAL_PATH}" \
    CHORDS_JDK11_HOME="${fake_root}/jdk" "$wrapper" :core:test 2>&1)"
wrapper_rc=$?
check "the root wrapper refuses a macOS ARM JDK" \
    "$([[ "$wrapper_rc" -eq 1 && "$wrapper_output" == *"must use an x86_64"* ]] \
        && echo 0 || echo 1)"
rm -rf "$fake_root"

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
