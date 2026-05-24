# Release Guide

How to prepare and publish a new release of Cloudbase Predictor.

## Version Numbering

The project uses semantic versioning: `MAJOR.MINOR.PATCH`.

- **MAJOR** — breaking changes or major redesigns
- **MINOR** — new features, backward-compatible
- **PATCH** — bug fixes only

Version is defined in two places in `app/build.gradle.kts`:

```kotlin
versionCode = 1        // Monotonically increasing integer (required by Google Play)
versionName = "0.0.1"  // Human-readable version string
```

**Both** must be updated for every release. `versionCode` must always increase.

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

### Google Play Service Account

1. Go to [Google Play Console](https://play.google.com/console) → Setup → API access
2. Create or link a Google Cloud project
3. Create a service account with **Editor** role
4. Download the JSON key
5. Add the full JSON as the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret

## Release Process

### 1. Update version

Edit `app/build.gradle.kts`:

```kotlin
versionCode = <nextBaseCode>     // increment
versionName = "<newVersion>"     // new version, for example "1.4.0"
```

### 2. Update F-Droid metadata

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

### 3. Run tests locally

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease  # verify release build works
```

### 4. Commit and tag

```bash
git add -A
git commit -m "Release v<newVersion>"
git tag v<newVersion>
git push origin master --tags
```

### 5. Automated pipeline

Pushing the tag triggers the **Release** workflow (`.github/workflows/release.yml`):

1. Runs unit tests
2. Builds signed ABI APKs and AAB
3. Creates a GitHub Release with the APKs and AAB attached
4. Optionally publishes the AAB to Google Play internal track

### 6. Promote on Google Play

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
