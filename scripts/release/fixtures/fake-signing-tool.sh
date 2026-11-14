#!/usr/bin/env bash
# Stand-in for apksigner, jarsigner and keytool in verify-signature.test.mjs. The test links it under
# each of those names; it prints the file named by FAKE_<TOOL>_OUTPUT and exits with FAKE_<TOOL>_EXIT.
set -euo pipefail

tool="$(basename "$0" | tr '[:lower:]' '[:upper:]')"
output_variable="FAKE_${tool}_OUTPUT"
exit_variable="FAKE_${tool}_EXIT"

if [ -n "${!output_variable:-}" ]; then
  cat "${!output_variable}"
fi
exit "${!exit_variable:-0}"
