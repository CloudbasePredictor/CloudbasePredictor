#!/usr/bin/env bash
# Opens, refreshes or closes the tracking issue for a failing workflow. See action.yml.
#
# A failed scheduled run is easy to miss: GitHub shows it only on the Actions tab. An open issue is
# visible on the repository front page and notifies watchers once, when it is opened or closed.
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${ALERT_TITLE:?}" "${ALERT_STATUS:?}" "${ALERT_RUN_URL:?}"
label="ci-alert"

# Lists by label instead of searching: the search index lags behind by minutes, which would open a
# duplicate when two runs fail close together.
find_open_issue() {
  gh issue list --repo "$GITHUB_REPOSITORY" --state open --label "$label" --limit 100 --json number,title |
    jq -r --arg title "$ALERT_TITLE" 'map(select(.title == $title)) | .[0].number // empty'
}

case "$ALERT_STATUS" in
  failure)
    gh label create "$label" --repo "$GITHUB_REPOSITORY" --force --color B60205 \
      --description "Opened automatically while a workflow is failing" >/dev/null
    body="The **${ALERT_WORKFLOW:-workflow}** workflow is failing.

- Latest failing run: ${ALERT_RUN_URL}
- Trigger: \`${ALERT_EVENT:-unknown}\` on \`${ALERT_REF:-unknown}\`
- Reported: $(date -u '+%Y-%m-%d %H:%M UTC')"
    if [ -n "${ALERT_DETAILS:-}" ]; then
      body="${body}

${ALERT_DETAILS}"
    fi
    body="${body}

This issue is maintained automatically: each failing run refreshes this text, and the next successful run closes the issue."
    number="$(find_open_issue)"
    if [ -n "$number" ]; then
      gh issue edit "$number" --repo "$GITHUB_REPOSITORY" --body "$body" >/dev/null
      echo "Refreshed alert issue #${number}"
    else
      gh issue create --repo "$GITHUB_REPOSITORY" --title "$ALERT_TITLE" --label "$label" --body "$body"
    fi
    ;;
  success)
    number="$(find_open_issue)"
    if [ -n "$number" ]; then
      gh issue close "$number" --repo "$GITHUB_REPOSITORY" --comment "Recovered in ${ALERT_RUN_URL}"
    else
      echo "No open alert issue titled '${ALERT_TITLE}'"
    fi
    ;;
  *)
    echo "Nothing to report (status: ${ALERT_STATUS})"
    ;;
esac
