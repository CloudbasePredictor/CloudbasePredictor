# Release Guide

How to prepare and publish a new release of Cloudbase Predictor.

## Version Numbering

The project uses semantic versioning: `MAJOR.MINOR.PATCH`.

- **MAJOR** — breaking changes or major redesigns
- **MINOR** — new features, backward-compatible
- **PATCH** — bug fixes only

Version is defined in two places:

- `cloudbaseVersionName` in `gradle.properties` — the human-readable version
  string. This is the single source of truth: `:app` reads it for `versionName`
  and `:webApp` bakes it into the web About screen.
- `versionCode` in `app/build.gradle.kts` — a monotonically increasing integer
  (required by Google Play).

```properties
# gradle.properties
cloudbaseVersionName=1.9.1
```

```kotlin
// app/build.gradle.kts
versionCode = 18  // Monotonically increasing integer (required by Google Play)
```

**Both** must be updated for every release. `versionCode` must always increase.

`node scripts/release/verify-version.mjs` checks that the version name, the
version code, the store changelog and the F-Droid metadata describe the same
release. CI runs it on every push and pull request, and the release workflow
runs it again against the pushed tag before anything is built.

Release APKs are split per ABI. The `versionCode` in `app/build.gradle.kts`
is the base release code. The universal APK uses `versionCode * 10`, and
generated ABI APKs use `versionCode * 10 + ABI offset`. The universal APK code
is intentionally lower than the ABI APK codes so stores prefer ABI-specific APKs
when a device is compatible. ABI splits are enabled for APK builds and disabled
for app bundle builds.

## Prerequisites

### Signing Keystore

A release keystore is required for signed builds. Generate one (once):

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -storetype PKCS12 \
  -alias cloudbase \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <PASSWORD> \
  -dname "CN=Cloudbase Predictor, O=CloudbasePredictor"
```

This creates a PKCS12 keystore. PKCS12 uses the same password for the keystore and the key entry, so use the same value for `KEYSTORE_PASSWORD` and `KEY_PASSWORD`.

Store the keystore securely. **Never commit it to the repository.**

### GitHub Secrets

Configure these repository secrets in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore: `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `cloudbase`) |
| `KEY_PASSWORD` | Key password (same as `KEYSTORE_PASSWORD` for the PKCS12 command above) |

For Google Play publishing (optional):

| Secret / Variable | Description |
|-------------------|-------------|
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Google Play API service account JSON |
| `GOOGLE_PLAY_ENABLED` (variable) | Set to `true` to enable publishing |

Recommended (optional) repository variable:

| Variable | Description |
|----------|-------------|
| `RELEASE_CERT_SHA256` | SHA-256 digest of the release signing certificate. When set, the release workflow refuses to publish artifacts signed by any other certificate. Read it with `keytool -list -v -keystore release.keystore -alias cloudbase` (the `SHA256:` line; colons are accepted). |

### Google Play Service Account

1. Go to [Google Play Console](https://play.google.com/console) → Setup → API access
2. Create or link a Google Cloud project
3. Create a service account with **Editor** role
4. Download the JSON key
5. Add the full JSON as the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret

## Release Process

### 1. Update version

Set the new version string in `gradle.properties`:

```properties
cloudbaseVersionName=<newVersion>   # new version, for example "1.4.0"
```

Increment the base version code in `app/build.gradle.kts`:

```kotlin
versionCode = <nextBaseCode>        // increment
```

### 2. Add the store changelog

Create `fastlane/metadata/android/en-US/changelogs/<versionCode * 10>.txt`
(for example `180.txt` for `versionCode = 18`) with the user-visible changes,
at most 500 characters. F-Droid and Google Play both read this file.

### 3. Update F-Droid metadata

Edit `metadata/com.cloudbasepredictor.yml`:

```yaml
CurrentVersion: <newVersion>
CurrentVersionCode: <highestAbiVersionCode>  # highest F-Droid ABI APK code
```

Add new build entries under `Builds:` for each F-Droid ABI APK:
`armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. Do not include the universal
APK in F-Droid metadata; it is only published with the GitHub release. Use
F-Droid's `$$VERSION$$` placeholder in `output` paths instead of hardcoding
`versionName`.

If a fdroiddata merge-request checkout is being maintained separately, mirror
the same build entries, current version fields, and anti-feature text in that
checkout's `metadata/com.cloudbasepredictor.yml`.

### 4. Check the release

Every push and pull request already runs a release rehearsal in CI (the
`Release Rehearsal` job of `Android Tests`): it builds the minified APK and app
bundle, signs them with a throwaway key and verifies the signatures, so an R8
or packaging problem shows up before a tag exists. The version files are
checked by `Repository Checks`. Locally, the quickest confirmation is:

```bash
node scripts/release/verify-version.mjs --remote-tags
```

The full set of gates, if you want to run them yourself:

```bash
# Android release gates
./gradlew :app:testDebugUnitTest
./gradlew :engine:allTests
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:detekt :engine:detekt

# Shared and web release gates
CHROME_BIN=/path/to/chrome ./gradlew :shared:allTests
CHROME_BIN=/path/to/chrome ./gradlew :webApp:wasmJsBrowserTest
./gradlew :shared:detekt :webApp:detekt
npm test
node scripts/launch-sites/build-snapshot.mjs \
  --input scripts/launch-sites/fixtures/sample.geojson \
  --output webApp/src/wasmJsMain/resources/data/launch-sites
node scripts/launch-sites/verify-snapshot.mjs \
  webApp/src/wasmJsMain/resources/data/launch-sites \
  --min-sites 1
BINARYEN_CORES=1 ./gradlew :webApp:wasmJsBrowserDistribution
node scripts/web/check-bundle.mjs
CHROME_BIN=/path/to/chrome node scripts/web/check-browser-release.mjs
npm ci
npx playwright install webkit
npm run check:web:webkit-input

# Release artifact
./gradlew :app:assembleRelease  # verify release build works
```

Do not create the release tag until CI is green on the release commit.

### 5. Commit and tag

```bash
git add -A
git commit -m "Release v<newVersion>"
git tag v<newVersion>
git push origin master --tags
```

### 6. Automated pipeline

Pushing the tag triggers the **Release** workflow (`.github/workflows/release.yml`):

1. Checks that the tag matches `cloudbaseVersionName` and that the version code,
   changelog and F-Droid metadata agree with it
2. Runs Android unit/engine tests, instrumentation compilation, and static analysis
3. Builds signed ABI APKs and AAB, and verifies that all of them are signed by
   one certificate (the expected one, when `RELEASE_CERT_SHA256` is set)
4. Creates a GitHub Release with the APKs, the AAB and the R8 mapping file attached
5. Optionally publishes the AAB to Google Play internal track

The workflow also runs the shared web release gates on the tagged commit. They
report on that commit but do not hold back the Android artifacts: a tag
publishes the APKs and the app bundle, while the web site deploys from the
default branch.

#### If the release run fails

A failing run opens an issue titled `Release v<version> failed` with a link to
the run. Fix the cause and use **Re-run failed jobs** on that run; the tag does
not need to be moved or recreated. The issue closes when the GitHub Release has
been published. The R8 mapping file attached to each release
(`CloudbasePredictor_v<version>_mapping.txt`) is what turns a stack trace from
that build back into readable names.

### 7. Promote on Google Play

After verifying the internal build:

1. Go to Google Play Console → Release → Testing → Internal testing
2. Promote the release to Production (or Open testing first)

## F-Droid

F-Droid builds the app from source using the metadata in `metadata/com.cloudbasepredictor.yml`.

To submit the app to F-Droid:

1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
2. Copy `metadata/com.cloudbasepredictor.yml` to the fork
3. Submit a merge request
4. F-Droid will verify the build is reproducible

After initial acceptance, new versions are picked up automatically via `AutoUpdateMode: Version`.

## Local Signed Build

To build a signed release locally:

```bash
export KEYSTORE_PATH=/path/to/release.keystore
export KEYSTORE_PASSWORD=<password>
export KEY_ALIAS=cloudbase
export KEY_PASSWORD=<password> # same as KEYSTORE_PASSWORD for the PKCS12 command above

./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

The signed universal and ABI APKs will be at `app/build/outputs/apk/release/`.
The signed AAB will be at `app/build/outputs/bundle/release/CloudbasePredictor_v<versionName>.aab`.
