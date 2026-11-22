#!/usr/bin/env bash
# Stand-in for the GitHub CLI used by ci-alert.test.mjs. It records every call as one line in
# $GH_LOG (newlines inside an argument, such as an issue body, are flattened) and answers
# `gh issue list` with the JSON in $GH_OPEN_ISSUES.
set -euo pipefail

args="$*"
printf '%s\n' "${args//$'\n'/ }" >> "$GH_LOG"
if [ "${1:-} ${2:-}" = "issue list" ]; then
  printf '%s' "$GH_OPEN_ISSUES"
fi
