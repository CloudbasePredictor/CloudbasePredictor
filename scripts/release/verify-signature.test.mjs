// Tests verify-signature.sh with stand-ins for apksigner, jarsigner and keytool, so its parsing and
// decisions are checked on every run without an Android SDK or a signed build.

import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SCRIPT = path.join(__dirname, "verify-signature.sh");
const FAKE_TOOL = path.join(__dirname, "fixtures", "fake-signing-tool.sh");

const DIGEST_A = "dcb39cb1e4243c52f55caef4deb6af399a28a70c61e14ad66f7116185e80587d";
const DIGEST_B = "1c57dce39b0e7e83def56028a6a53d743c21e29d156620c8115bb9b83d959885";

/** `dcb3…` as keytool prints it: `DC:B3:…`. */
function colonUpper(digest) {
  return digest.toUpperCase().match(/.{2}/gu).join(":");
}

function apksignerOutput(digest) {
  return [
    "Signer #1 certificate DN: CN=Cloudbase Predictor",
    `Signer #1 certificate SHA-256 digest: ${digest}`,
    "Signer #1 certificate SHA-1 digest: 0b1cb33901dfad1823ca2c8b65bdc167cfc5756a",
    "Signer #1 certificate MD5 digest: cae04ed9995aa5804e5b3627271314b9",
    "",
  ].join("\n");
}

function keytoolOutput(digest) {
  return [
    "Signer #1:",
    "",
    "Certificate #1:",
    "Owner: CN=Cloudbase Predictor",
    "Certificate fingerprints:",
    "\t SHA1: 0B:1C:B3:39:01:DF:AD:18:23:CA:2C:8B:65:BD:C1:67:CF:C5:75:6A",
    `\t SHA256: ${colonUpper(digest)}`,
    "Signature algorithm name: SHA256withRSA",
    "Subject Public Key Algorithm: 2048-bit RSA key",
    "",
  ].join("\n");
}

// jarsigner prints its verdict first and then a long list of warnings for a self-signed bundle.
const JARSIGNER_VERIFIED = `\njar verified.\n\nWarning: \n${"This jar contains entries whose certificate chain is invalid.\n".repeat(20_000)}`;
const JARSIGNER_UNSIGNED = "\njar is unsigned.\n";

/**
 * Runs verify-signature.sh on `files` (created empty in a temp directory) with the given canned
 * tool outputs. Returns the exit code and the combined output.
 */
async function runVerify({ files, expected, apksigner, apksignerExit = 0, jarsigner, keytool }) {
  const dir = await mkdtemp(path.join(os.tmpdir(), "verify-signature-"));
  try {
    const env = { PATH: `${dir}${path.delimiter}${process.env.PATH}` };
    for (const [tool, output] of Object.entries({ apksigner, jarsigner, keytool })) {
      await symlink(FAKE_TOOL, path.join(dir, tool));
      if (output !== undefined) {
        const outputPath = path.join(dir, `${tool}.out`);
        await writeFile(outputPath, output);
        env[`FAKE_${tool.toUpperCase()}_OUTPUT`] = outputPath;
      }
    }
    env.FAKE_APKSIGNER_EXIT = String(apksignerExit);

    const paths = [];
    for (const file of files) {
      const filePath = path.join(dir, file);
      if (!file.startsWith("missing")) await writeFile(filePath, "");
      paths.push(filePath);
    }
    const args = expected === undefined ? paths : ["--expected-sha256", expected, ...paths];
    const result = spawnSync("bash", [SCRIPT, ...args], { encoding: "utf8", env });
    return { exitCode: result.status, output: `${result.stdout}${result.stderr}` };
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test("an APK and a bundle signed by the expected certificate pass", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app_arm64-v8a.apk", "app.aab"],
    expected: colonUpper(DIGEST_A),
    apksigner: apksignerOutput(DIGEST_A),
    jarsigner: JARSIGNER_VERIFIED,
    keytool: keytoolOutput(DIGEST_A),
  });

  assert.equal(exitCode, 0, output);
  assert.match(output, new RegExp(`signed {2}${DIGEST_A} {2}app_arm64-v8a\\.apk`, "u"));
  assert.match(output, new RegExp(`signed {2}${DIGEST_A} {2}app\\.aab`, "u"));
  assert.match(output, /All 2 artifact\(s\) are signed by the expected certificate\./u);
});

// Regression: the verdict used to come from `printf … | grep -q`, which fails under pipefail once
// the output is long enough for the writer to be killed by SIGPIPE.
test("a signed bundle is recognized even when jarsigner prints a very long report", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.aab"],
    jarsigner: JARSIGNER_VERIFIED,
    keytool: keytoolOutput(DIGEST_A),
  });

  assert.equal(exitCode, 0, output);
  assert.match(output, /No expected digest was given/u);
});

test("signer lines with another prefix for the same certificate are accepted", async () => {
  const rotated = [
    `Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: ${DIGEST_A}`,
    `Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: ${DIGEST_A}`,
    "",
  ].join("\n");
  const { exitCode, output } = await runVerify({ files: ["app.apk"], expected: DIGEST_A, apksigner: rotated });

  assert.equal(exitCode, 0, output);
});

test("an APK signed by two different certificates is rejected and the tool output is shown", async () => {
  const twoSigners = `${apksignerOutput(DIGEST_A)}Signer #2 certificate SHA-256 digest: ${DIGEST_B}\n`;
  const { exitCode, output } = await runVerify({ files: ["app.apk"], apksigner: twoSigners });

  assert.equal(exitCode, 1);
  assert.match(output, /expected one signing certificate in .*app\.apk, apksigner reported:/u);
  assert.match(output, /Signer #2 certificate SHA-256 digest/u);
});

test("an APK that apksigner does not verify is rejected", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.apk"],
    apksigner: "DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\n",
    apksignerExit: 1,
  });

  assert.equal(exitCode, 1);
  assert.match(output, /app\.apk does not verify as a signed APK/u);
});

test("an unsigned bundle is rejected", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.aab"],
    jarsigner: JARSIGNER_UNSIGNED,
    keytool: "Not a signed jar file\n",
  });

  assert.equal(exitCode, 1);
  assert.match(output, /app\.aab is not signed/u);
});

test("a bundle signed by another certificate than the APK is rejected", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.apk", "app.aab"],
    apksigner: apksignerOutput(DIGEST_A),
    jarsigner: JARSIGNER_VERIFIED,
    keytool: keytoolOutput(DIGEST_B),
  });

  assert.equal(exitCode, 1);
  assert.match(output, /app\.aab is signed by a different certificate than the first artifact/u);
});

test("a certificate other than the expected one is rejected", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.apk"],
    expected: DIGEST_B,
    apksigner: apksignerOutput(DIGEST_A),
  });

  assert.equal(exitCode, 1);
  assert.match(output, new RegExp(`signed by ${DIGEST_A}, expected ${DIGEST_B}`, "u"));
});

test("an empty expected digest only reports the certificate", async () => {
  const { exitCode, output } = await runVerify({
    files: ["app.apk"],
    expected: "",
    apksigner: apksignerOutput(DIGEST_A),
  });

  assert.equal(exitCode, 0, output);
  assert.match(output, /No expected digest was given, so it was not compared/u);
});

test("missing artifacts and an empty artifact list fail", async () => {
  const missing = await runVerify({ files: ["app.apk", "missing.apk"], apksigner: apksignerOutput(DIGEST_A) });
  assert.equal(missing.exitCode, 1);
  assert.match(missing.output, /missing\.apk does not exist/u);

  const none = await runVerify({ files: [], expected: DIGEST_A });
  assert.equal(none.exitCode, 2);
  assert.match(none.output, /no artifacts given/u);
});
