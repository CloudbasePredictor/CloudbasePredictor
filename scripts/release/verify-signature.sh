#!/usr/bin/env bash
# Verifies that release artifacts are signed, and signed by one and the same certificate.
#
# Usage: verify-signature.sh [--expected-sha256 <digest>] <file.apk|file.aab>...
#
# The build applies the release signing configuration only when its inputs are present, so a missing
# or wrong secret would otherwise produce unsigned or wrongly signed files that still get published.
# An APK is checked with apksigner, an app bundle with jarsigner. When --expected-sha256 is given
# (colons, spaces and letter case are ignored), the signing certificate must have that SHA-256 digest;
# an empty value skips the comparison and only reports the digest.
set -euo pipefail

expected=""
files=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --expected-sha256)
      [ "$#" -ge 2 ] || { echo "error: --expected-sha256 needs a value (it may be empty)" >&2; exit 2; }
      expected="$2"
      shift 2
      ;;
    --*)
      echo "error: unknown option $1" >&2
      exit 2
      ;;
    *)
      files+=("$1")
      shift
      ;;
  esac
done

if [ "${#files[@]}" -eq 0 ]; then
  echo "error: no artifacts given (did the build produce any?)" >&2
  exit 2
fi

normalize_digest() {
  printf '%s' "$1" | tr -d ': \n\r' | tr 'A-F' 'a-f'
}

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -n "$sdk" ] || { echo "error: apksigner not on PATH and ANDROID_HOME is not set" >&2; return 1; }
  local newest
  newest="$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name apksigner 2>/dev/null | sort -V | tail -n 1)"
  [ -n "$newest" ] || { echo "error: no apksigner under $sdk/build-tools" >&2; return 1; }
  printf '%s\n' "$newest"
}

# Prints the SHA-256 digest of the certificate that signed an APK. Fails when the APK does not verify
# or has more than one signer.
apk_digest() {
  local apksigner output signers
  apksigner="$(find_apksigner)"
  output="$("$apksigner" verify --print-certs "$1")" || { echo "error: $1 does not verify as a signed APK" >&2; return 1; }
  signers="$(printf '%s\n' "$output" | grep -c 'certificate SHA-256 digest:' || true)"
  if [ "$signers" -ne 1 ]; then
    echo "error: $1 has $signers signers, expected exactly 1" >&2
    return 1
  fi
  printf '%s\n' "$output" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
}

# Prints the SHA-256 digest of the certificate that signed an app bundle (JAR signature).
bundle_digest() {
  local output
  output="$(jarsigner -verify "$1" 2>&1)" || { echo "error: jarsigner could not verify $1" >&2; return 1; }
  if ! printf '%s\n' "$output" | grep -q 'jar verified'; then
    echo "error: $1 is not signed" >&2
    return 1
  fi
  keytool -printcert -jarfile "$1" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1
}

expected="$(normalize_digest "$expected")"
reference=""
status=0

for file in "${files[@]}"; do
  if [ ! -f "$file" ]; then
    echo "error: $file does not exist" >&2
    status=1
    continue
  fi
  case "$file" in
    *.apk) digest="$(apk_digest "$file")" || { status=1; continue; } ;;
    *.aab) digest="$(bundle_digest "$file")" || { status=1; continue; } ;;
    *)
      echo "error: $file is neither an .apk nor an .aab" >&2
      status=1
      continue
      ;;
  esac
  digest="$(normalize_digest "$digest")"
  if [ -z "$digest" ]; then
    echo "error: could not read the signing certificate of $file" >&2
    status=1
    continue
  fi
  echo "signed  ${digest}  $(basename "$file")"
  if [ -z "$reference" ]; then
    reference="$digest"
  elif [ "$digest" != "$reference" ]; then
    echo "error: $(basename "$file") is signed by a different certificate than the first artifact" >&2
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  exit "$status"
fi

if [ -n "$expected" ]; then
  if [ "$reference" != "$expected" ]; then
    echo "error: artifacts are signed by ${reference}, expected ${expected}" >&2
    exit 1
  fi
  echo "All ${#files[@]} artifact(s) are signed by the expected certificate."
else
  echo "All ${#files[@]} artifact(s) are signed by one certificate. No expected digest was given, so it was not compared."
fi
