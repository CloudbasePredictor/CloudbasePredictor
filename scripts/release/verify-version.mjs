#!/usr/bin/env node

// Checks that every place a release version is written by hand agrees with the others.
//
// A release touches five things: `cloudbaseVersionName` in gradle.properties, `versionCode` (and a
// fallback version name) in app/build.gradle.kts, the F-Droid build entries, the store changelog,
// and the git tag. Nothing tied them together: version 1.9.1 was committed with F-Droid entries that
// build from the tag `v1.9.1`, the tag was never pushed, and the metadata pointed at a commit that
// did not exist for months.
//
// Usage:
//   node scripts/release/verify-version.mjs                    # files agree with each other
//   node scripts/release/verify-version.mjs --remote-tags      # ...and F-Droid entries use real tags
//   node scripts/release/verify-version.mjs --tag v1.9.1 --remote-tags   # ...and the pushed tag fits

import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath, pathToFileURL } from "node:url";

// Must match abiVersionCodeOffsets in app/build.gradle.kts: an ABI APK has versionCode * 10 + offset.
export const ABI_VERSION_CODE_OFFSETS = Object.freeze({
  "armeabi-v7a": 1,
  "arm64-v8a": 2,
  x86: 3,
  x86_64: 4,
});
// Google Play rejects release notes longer than this.
const MAX_CHANGELOG_CHARACTERS = 500;
const VERSION_NAME_PATTERN = /^\d+\.\d+\.\d+$/u;

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

function unquote(value) {
  return value.trim().replace(/^(['"])(.*)\1$/u, "$2");
}

/** Extracts the build entries and the current-version fields from the F-Droid metadata file. */
export function parseFdroidMetadata(text) {
  const builds = [];
  let currentVersion = null;
  let currentVersionCode = null;
  let build = null;

  for (const line of text.split(/\r?\n/u)) {
    const buildStart = line.match(/^ {2}- versionName:\s*(.+)$/u);
    if (buildStart) {
      build = { versionName: unquote(buildStart[1]), versionCode: null, commit: null, abi: null };
      builds.push(build);
      continue;
    }
    if (/^\S/u.test(line)) build = null; // a top-level key ends the Builds list

    const versionCode = line.match(/^ {4}versionCode:\s*(\d+)\s*$/u);
    const commit = line.match(/^ {4}commit:\s*(\S+)\s*$/u);
    const abi = line.match(/ABI_FILTERS=([\w-]+)/u);
    if (build && versionCode) build.versionCode = Number(versionCode[1]);
    if (build && commit) build.commit = unquote(commit[1]);
    if (build && abi) build.abi = abi[1];

    const current = line.match(/^CurrentVersion:\s*(.+)$/u);
    const currentCode = line.match(/^CurrentVersionCode:\s*(\d+)\s*$/u);
    if (current) currentVersion = unquote(current[1]);
    if (currentCode) currentVersionCode = Number(currentCode[1]);
  }
  return { builds, currentVersion, currentVersionCode };
}

async function readOptional(filePath) {
  try {
    return await readFile(filePath, "utf8");
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }
}

/** Reads every hand-maintained version fact from a checkout. Missing files become null values. */
export async function readReleaseFacts(root = repositoryRoot) {
  const gradleProperties = (await readOptional(path.join(root, "gradle.properties"))) ?? "";
  const appBuildFile = (await readOptional(path.join(root, "app", "build.gradle.kts"))) ?? "";
  const metadataText = await readOptional(path.join(root, "metadata", "com.cloudbasepredictor.yml"));

  const versionName = gradleProperties.match(/^cloudbaseVersionName=(.*)$/mu)?.[1]?.trim() ?? null;
  const versionCodeText = appBuildFile.match(/^\s*versionCode\s*=\s*(\d+)\s*$/mu)?.[1];
  const versionCode = versionCodeText === undefined ? null : Number(versionCodeText);
  const fallbackVersionName =
    appBuildFile.match(/gradleProperty\("cloudbaseVersionName"\)\s*\.getOrElse\("([^"]*)"\)/u)?.[1] ?? null;

  const changelogPath =
    versionCode === null ? null : `fastlane/metadata/android/en-US/changelogs/${versionCode * 10}.txt`;
  const changelog = changelogPath === null ? null : await readOptional(path.join(root, ...changelogPath.split("/")));

  return {
    versionName,
    versionCode,
    fallbackVersionName,
    changelogPath,
    changelog,
    metadata: metadataText === null ? null : parseFdroidMetadata(metadataText),
  };
}

/**
 * Returns one message per disagreement, or an empty list.
 *
 * `tag` is the tag being released, when the check runs for a tag push. `existingTags` is the set of
 * tags known to the remote; when given, every F-Droid `commit:` must be one of them. The tag of the
 * version in the working tree is allowed to be missing while no tag is being released, because the
 * release commit necessarily exists before its tag.
 */
export function findVersionProblems(facts, { tag = null, existingTags = null } = {}) {
  const problems = [];
  const { versionName, versionCode, fallbackVersionName, changelogPath, changelog, metadata } = facts;

  if (versionName === null) {
    problems.push("gradle.properties does not define cloudbaseVersionName");
  } else if (!VERSION_NAME_PATTERN.test(versionName)) {
    problems.push(`cloudbaseVersionName must look like 1.2.3, got "${versionName}"`);
  }
  if (versionCode === null || versionCode <= 0) {
    problems.push("app/build.gradle.kts does not define a positive versionCode");
  }
  // Everything below compares against these two values, so without them it would only add noise.
  if (problems.length > 0) return problems;

  const expectedTag = `v${versionName}`;
  const highestAbiCode = versionCode * 10 + Math.max(...Object.values(ABI_VERSION_CODE_OFFSETS));

  if (tag !== null && tag !== expectedTag) {
    problems.push(`The tag is ${tag}, but cloudbaseVersionName is ${versionName} (expected tag ${expectedTag})`);
  }
  if (fallbackVersionName !== null && fallbackVersionName !== versionName) {
    problems.push(
      `app/build.gradle.kts falls back to versionName "${fallbackVersionName}", but gradle.properties says "${versionName}"`,
    );
  }

  if (changelog === null) {
    problems.push(`Missing store changelog ${changelogPath} (named after versionCode * 10)`);
  } else if (changelog.trim().length === 0) {
    problems.push(`Store changelog ${changelogPath} is empty`);
  } else if ([...changelog.trim()].length > MAX_CHANGELOG_CHARACTERS) {
    problems.push(
      `Store changelog ${changelogPath} has ${[...changelog.trim()].length} characters; the limit is ${MAX_CHANGELOG_CHARACTERS}`,
    );
  }

  if (metadata === null) {
    problems.push("Missing F-Droid metadata file metadata/com.cloudbasepredictor.yml");
    return problems;
  }
  if (metadata.currentVersion !== versionName) {
    problems.push(`F-Droid CurrentVersion is ${metadata.currentVersion}, expected ${versionName}`);
  }
  if (metadata.currentVersionCode !== highestAbiCode) {
    problems.push(`F-Droid CurrentVersionCode is ${metadata.currentVersionCode}, expected ${highestAbiCode}`);
  }

  for (const [abi, offset] of Object.entries(ABI_VERSION_CODE_OFFSETS)) {
    const code = versionCode * 10 + offset;
    const build = metadata.builds.find((candidate) => candidate.versionCode === code);
    if (!build) {
      problems.push(`F-Droid metadata has no build entry with versionCode ${code} (${abi})`);
      continue;
    }
    if (build.versionName !== versionName) {
      problems.push(`F-Droid build ${code} has versionName ${build.versionName}, expected ${versionName}`);
    }
    if (build.commit !== expectedTag) {
      problems.push(`F-Droid build ${code} builds from ${build.commit}, expected ${expectedTag}`);
    }
    if (build.abi !== abi) {
      problems.push(`F-Droid build ${code} has ABI_FILTERS=${build.abi}, expected ${abi}`);
    }
  }

  const newerBuilds = metadata.builds.filter((build) => build.versionCode > highestAbiCode);
  if (newerBuilds.length > 0) {
    problems.push(
      `F-Droid metadata lists versionCode ${Math.max(...newerBuilds.map((build) => build.versionCode))}, ` +
        `above the current ${highestAbiCode}; versionCode must only increase`,
    );
  }

  if (existingTags !== null) {
    const missing = new Set();
    for (const build of metadata.builds) {
      if (build.commit === null || existingTags.has(build.commit)) continue;
      const isUnreleasedCurrentVersion = build.commit === expectedTag && tag === null;
      if (!isUnreleasedCurrentVersion) missing.add(build.commit);
    }
    for (const commit of missing) {
      problems.push(`F-Droid metadata builds from ${commit}, which is not a tag on the remote`);
    }
  }

  return problems;
}

function readRemoteTags(root) {
  const output = execFileSync("git", ["ls-remote", "--tags", "origin"], { cwd: root, encoding: "utf8" });
  const tags = new Set();
  for (const line of output.split("\n")) {
    const ref = line.split("\t")[1];
    if (ref?.startsWith("refs/tags/")) tags.add(ref.slice("refs/tags/".length).replace(/\^\{\}$/u, ""));
  }
  return tags;
}

function parseArgs(argv) {
  const args = { tag: null, remoteTags: false, root: repositoryRoot };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--tag") {
      args.tag = argv[(i += 1)] ?? null;
    } else if (arg === "--remote-tags") {
      args.remoteTags = true;
    } else if (arg === "--root") {
      args.root = path.resolve(argv[(i += 1)]);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (argv.includes("--tag") && !args.tag) throw new Error("--tag needs a value");
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const facts = await readReleaseFacts(args.root);
  const existingTags = args.remoteTags ? readRemoteTags(args.root) : null;
  const problems = findVersionProblems(facts, { tag: args.tag, existingTags });

  console.log(`versionName:  ${facts.versionName}`);
  console.log(`versionCode:  ${facts.versionCode} (AAB ${facts.versionCode * 10}, ABI APKs up to ${facts.versionCode * 10 + 4})`);
  console.log(`changelog:    ${facts.changelogPath}`);
  if (args.tag) console.log(`tag:          ${args.tag}`);
  if (existingTags) {
    const released = existingTags.has(`v${facts.versionName}`);
    console.log(`remote tags:  ${existingTags.size} (v${facts.versionName} ${released ? "is tagged" : "is not tagged yet"})`);
  }

  if (problems.length > 0) {
    console.error(`\nFAIL  ${problems.length} release version problem(s):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exitCode = 1;
    return;
  }
  console.log("\nPASS  Release version, version code, changelog and F-Droid metadata agree.");
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
  main().catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}
