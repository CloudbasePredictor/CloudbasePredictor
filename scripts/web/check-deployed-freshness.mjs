#!/usr/bin/env node

// Fails when the live site is not serving the default branch.
//
// Every other gate in this repository answers "is this build good?". None of them answer "did that
// build actually reach production?" -- and that gap is not theoretical: when the WebKit gate failed
// inside web-deploy.yml, the Deploy job was *skipped*, GitHub Pages kept serving the previous build,
// and the same commit still showed a green Web CI check. The site silently fell behind twice before
// anyone noticed.
//
// This check is deliberately outcome-based. It does not care why the site is stale -- a failed gate,
// a skipped deploy, a broken cron, a Pages outage -- it only compares the commit the site reports
// against the commit the default branch is on. Anything that stops a deploy shows up here.
//
// Requires the deployed build to expose build-info.json (emitted by :webApp generateWebBuildInfo).

import { execFileSync } from "node:child_process";

const siteUrl = (process.env.SITE_URL ?? "https://cloudbasepredictor.github.io/CloudbasePredictor")
  .replace(/\/+$/u, "");
const buildInfoUrl = `${siteUrl}/build-info.json`;
// A deploy takes a few minutes. Don't cry stale while one is plausibly still in flight.
const graceMinutes = Number(process.env.GRACE_MINUTES ?? 90);
const fetchTimeoutMillis = Number(process.env.FETCH_TIMEOUT_MILLIS ?? 20000);

const expectedCommit = git("rev-parse", "HEAD");
const expectedShort = expectedCommit.slice(0, 8);
const headCommittedAt = Number(git("show", "-s", "--format=%ct", "HEAD")) * 1000;
const headAgeMinutes = (Date.now() - headCommittedAt) / 60000;

console.log(`Site:            ${siteUrl}`);
console.log(`Default branch:  ${expectedShort} (committed ${headAgeMinutes.toFixed(0)} min ago)`);

const buildInfo = await fetchBuildInfo();

if (buildInfo === null) {
  fail(
    "The live site does not expose build-info.json.",
    "The deployed build predates the freshness instrumentation, or the deploy is not publishing it.",
    "Expected: " + buildInfoUrl,
    "",
    "If this is the first run after adding generateWebBuildInfo, push to the default branch and let",
    "the deploy publish once; this check goes green on the next run.",
  );
}

const deployedCommit = typeof buildInfo.commit === "string" ? buildInfo.commit : "";
const deployedShort = deployedCommit.slice(0, 8) || "unknown";
console.log(`Deployed:        ${deployedShort} (version ${buildInfo.version ?? "?"})`);

if (deployedCommit === expectedCommit) {
  console.log(`\nPASS  The live site is serving the default branch (${expectedShort}).`);
  process.exit(0);
}

// The site is behind. Say by how much, and in which direction.
const behind = countCommitsBetween(deployedCommit, expectedCommit);
let drift;
if (behind !== null) {
  drift = `The live site is ${behind} commit(s) behind the default branch.`;
} else if (isShallowRepository()) {
  // On a shallow clone the deployed commit may simply be a normal ancestor beyond the fetch depth,
  // so don't accuse a force-push we cannot actually see.
  drift =
    `Cannot measure the drift: the deployed commit ${deployedShort} is not in this shallow clone's ` +
    "history. It may be an ordinary ancestor beyond the fetch depth, a force-push, or a build from elsewhere.";
} else {
  drift = `The deployed commit ${deployedShort} is not in this branch's history (force-push, or a build from elsewhere).`;
}

if (headAgeMinutes < graceMinutes) {
  console.log(`\nPASS  ${drift}`);
  console.log(
    `      HEAD is only ${headAgeMinutes.toFixed(0)} min old and the grace window is ${graceMinutes} min — ` +
      "a deploy is plausibly still in flight.",
  );
  process.exit(0);
}

fail(
  `The live site is stale: serving ${deployedShort}, but the default branch is at ${expectedShort}.`,
  drift,
  `HEAD has been on the default branch for ${headAgeMinutes.toFixed(0)} min, well past the ${graceMinutes} min grace window,`,
  "so the deploy did not merely lag — it did not happen, or it failed.",
  "",
  "Check the most recent 'Deploy Web to GitHub Pages' run. A failed release gate SKIPS the deploy",
  "job rather than turning the deploy red, which is exactly the silence this check exists to break.",
);

async function fetchBuildInfo() {
  const response = await fetch(buildInfoUrl, {
    cache: "no-store",
    headers: { "Cache-Control": "no-cache" },
    signal: AbortSignal.timeout(fetchTimeoutMillis),
  }).catch((error) => {
    fail(`Could not reach ${buildInfoUrl}`, String(error));
  });
  if (response.status === 404) return null;
  if (!response.ok) fail(`${buildInfoUrl} returned HTTP ${response.status}`);
  return response.json().catch(() => {
    fail(`${buildInfoUrl} is not valid JSON — the deploy published something unexpected.`);
  });
}

/** Number of commits on the default branch that the deployed build is missing, or null if unknown. */
function countCommitsBetween(fromCommit, toCommit) {
  if (!/^[0-9a-f]{7,40}$/iu.test(fromCommit)) return null;
  try {
    return Number(git("rev-list", "--count", `${fromCommit}..${toCommit}`));
  } catch {
    return null;
  }
}

/** True when running in a shallow clone, where a missing commit may just be beyond the fetch depth. */
function isShallowRepository() {
  try {
    return git("rev-parse", "--is-shallow-repository") === "true";
  } catch {
    return false;
  }
}

function git(...args) {
  return execFileSync("git", args, { encoding: "utf8" }).trim();
}

function fail(...lines) {
  console.error(`\nFAIL  ${lines[0]}`);
  for (const line of lines.slice(1)) console.error(line ? `      ${line}` : "");
  process.exit(1);
}
