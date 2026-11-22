import { test } from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  ABI_VERSION_CODE_OFFSETS,
  findVersionProblems,
  parseFdroidMetadata,
  readReleaseFacts,
} from "./verify-version.mjs";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

function buildsFor(versionName, versionCode) {
  return Object.entries(ABI_VERSION_CODE_OFFSETS).map(([abi, offset]) => ({
    versionName,
    versionCode: versionCode * 10 + offset,
    commit: `v${versionName}`,
    abi,
  }));
}

/** A consistent set of facts for version 1.9.1 (versionCode 18) with 1.9.0 released before it. */
function consistentFacts(overrides = {}) {
  return {
    versionName: "1.9.1",
    versionCode: 18,
    fallbackVersionName: "1.9.1",
    changelogPath: "fastlane/metadata/android/en-US/changelogs/180.txt",
    changelog: "Fixed wind interpolation.\n",
    metadata: {
      builds: [...buildsFor("1.9.0", 17), ...buildsFor("1.9.1", 18)],
      currentVersion: "1.9.1",
      currentVersionCode: 184,
    },
    ...overrides,
  };
}

function withMetadata(change) {
  const facts = consistentFacts();
  change(facts.metadata);
  return facts;
}

test("consistent files pass with and without a tag", () => {
  assert.deepEqual(findVersionProblems(consistentFacts()), []);
  assert.deepEqual(findVersionProblems(consistentFacts(), { tag: "v1.9.1" }), []);
});

test("the files of this repository agree with each other", async () => {
  const facts = await readReleaseFacts(repositoryRoot);

  assert.match(facts.versionName ?? "", /^\d+\.\d+\.\d+$/u);
  assert.ok(facts.metadata.builds.length >= 4, "the F-Droid metadata must be parsed into build entries");
  assert.deepEqual(findVersionProblems(facts), []);
});

test("a tag that does not match the version name is rejected", () => {
  const problems = findVersionProblems(consistentFacts(), { tag: "v1.9.2" });

  assert.equal(problems.length, 1);
  assert.match(problems[0], /tag is v1\.9\.2, but cloudbaseVersionName is 1\.9\.1/u);
});

test("the unpushed tag of the current version is tolerated only until a tag is released", () => {
  const released = new Set(["v1.9.0"]);

  assert.deepEqual(findVersionProblems(consistentFacts(), { existingTags: released }), []);
  // On a tag push the tag exists by definition, so a missing one means the metadata is wrong.
  const onTagPush = findVersionProblems(consistentFacts(), { tag: "v1.9.1", existingTags: released });
  assert.deepEqual(onTagPush, ["F-Droid metadata builds from v1.9.1, which is not a tag on the remote"]);
  assert.deepEqual(
    findVersionProblems(consistentFacts(), { tag: "v1.9.1", existingTags: new Set(["v1.9.0", "v1.9.1"]) }),
    [],
  );
});

test("an older F-Droid entry that builds from a missing tag is reported once", () => {
  const problems = findVersionProblems(consistentFacts(), { existingTags: new Set() });

  assert.deepEqual(problems, ["F-Droid metadata builds from v1.9.0, which is not a tag on the remote"]);
});

test("a version name bumped without a version code is caught through the F-Droid entries", () => {
  const problems = findVersionProblems(consistentFacts({ versionName: "1.10.0", fallbackVersionName: "1.10.0" }));

  assert.ok(problems.some((problem) => /CurrentVersion is 1\.9\.1, expected 1\.10\.0/u.test(problem)), problems.join("\n"));
  assert.ok(problems.some((problem) => /build 182 has versionName 1\.9\.1/u.test(problem)));
  assert.ok(problems.some((problem) => /build 182 builds from v1\.9\.1, expected v1\.10\.0/u.test(problem)));
});

test("a version code bumped without F-Droid entries or a changelog is caught", () => {
  const problems = findVersionProblems(
    consistentFacts({
      versionCode: 19,
      changelogPath: "fastlane/metadata/android/en-US/changelogs/190.txt",
      changelog: null,
    }),
  );

  assert.ok(problems.includes("Missing store changelog fastlane/metadata/android/en-US/changelogs/190.txt (named after versionCode * 10)"));
  assert.ok(problems.includes("F-Droid CurrentVersionCode is 184, expected 194"));
  for (const code of [191, 192, 193, 194]) {
    assert.ok(problems.some((problem) => problem.startsWith(`F-Droid metadata has no build entry with versionCode ${code} `)));
  }
});

test("a stale fallback version name in the build file is reported", () => {
  const problems = findVersionProblems(consistentFacts({ fallbackVersionName: "1.9.0" }));

  assert.deepEqual(problems, [
    'app/build.gradle.kts falls back to versionName "1.9.0", but gradle.properties says "1.9.1"',
  ]);
  assert.deepEqual(findVersionProblems(consistentFacts({ fallbackVersionName: null })), []);
});

test("changelog problems: empty and longer than the store limit", () => {
  assert.match(findVersionProblems(consistentFacts({ changelog: "  \n" }))[0], /is empty/u);
  assert.match(findVersionProblems(consistentFacts({ changelog: "x".repeat(501) }))[0], /501 characters; the limit is 500/u);
  assert.deepEqual(findVersionProblems(consistentFacts({ changelog: `${"я".repeat(500)}\n` })), []);
});

test("F-Droid entry mistakes: wrong ABI, wrong commit, and a version code above the current one", () => {
  const wrongAbi = withMetadata((metadata) => {
    metadata.builds.find((build) => build.versionCode === 183).abi = "x86_64";
  });
  assert.deepEqual(findVersionProblems(wrongAbi), ["F-Droid build 183 has ABI_FILTERS=x86_64, expected x86"]);

  const wrongCommit = withMetadata((metadata) => {
    metadata.builds.find((build) => build.versionCode === 181).commit = "v1.9.0";
  });
  assert.deepEqual(findVersionProblems(wrongCommit), ["F-Droid build 181 builds from v1.9.0, expected v1.9.1"]);

  const future = withMetadata((metadata) => {
    metadata.builds.push({ versionName: "2.0.0", versionCode: 201, commit: "v2.0.0", abi: "armeabi-v7a" });
  });
  assert.match(findVersionProblems(future)[0], /lists versionCode 201, above the current 184/u);
});

test("missing or malformed basics stop the check with a clear message", () => {
  assert.deepEqual(findVersionProblems(consistentFacts({ versionName: null })), [
    "gradle.properties does not define cloudbaseVersionName",
  ]);
  assert.deepEqual(findVersionProblems(consistentFacts({ versionName: "1.9" })), [
    'cloudbaseVersionName must look like 1.2.3, got "1.9"',
  ]);
  assert.deepEqual(findVersionProblems(consistentFacts({ versionCode: null })), [
    "app/build.gradle.kts does not define a positive versionCode",
  ]);
  assert.deepEqual(findVersionProblems(consistentFacts({ metadata: null })), [
    "Missing F-Droid metadata file metadata/com.cloudbasepredictor.yml",
  ]);
});

test("parseFdroidMetadata reads build entries and the current version fields", () => {
  const parsed = parseFdroidMetadata(
    [
      "Categories:",
      "  - Weather",
      "Builds:",
      "  - versionName: 1.9.1",
      "    versionCode: 181",
      "    commit: v1.9.1",
      "    subdir: app",
      "    gradle:",
      "      - yes",
      "    output: build/outputs/apk/release/CloudbasePredictor_v$$VERSION$$_armeabi-v7a.apk",
      "    gradleprops:",
      "      - ABI_FILTERS=armeabi-v7a",
      "",
      "  - versionName: '1.9.1'",
      "    versionCode: 182",
      "    commit: v1.9.1",
      "    gradleprops:",
      "      - ABI_FILTERS=arm64-v8a",
      "",
      "AutoUpdateMode: Version",
      "VercodeOperation:",
      "  - 10 * %c + 1",
      "CurrentVersion: 1.9.1",
      "CurrentVersionCode: 184",
      "",
    ].join("\n"),
  );

  assert.deepEqual(parsed, {
    builds: [
      { versionName: "1.9.1", versionCode: 181, commit: "v1.9.1", abi: "armeabi-v7a" },
      { versionName: "1.9.1", versionCode: 182, commit: "v1.9.1", abi: "arm64-v8a" },
    ],
    currentVersion: "1.9.1",
    currentVersionCode: 184,
  });
});
