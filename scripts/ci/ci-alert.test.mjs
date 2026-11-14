// Tests .github/actions/ci-alert/alert.sh against a recording stand-in for the GitHub CLI, so the
// decision logic (open, refresh, close, do nothing) is checked without touching a real repository.

import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtemp, readFile, rm, symlink } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ALERT_SCRIPT = path.resolve(__dirname, "../../.github/actions/ci-alert/alert.sh");
const FAKE_GH = path.join(__dirname, "fixtures", "fake-gh.sh");
const TITLE = "Web deploy is failing";

/** Runs alert.sh with a stand-in `gh` that logs its arguments and answers `issue list` with `openIssues`. */
async function runAlert({ status, openIssues = [], details = "", title = TITLE }) {
  const dir = await mkdtemp(path.join(os.tmpdir(), "ci-alert-"));
  const logPath = path.join(dir, "gh.log");
  // alert.sh finds this stand-in as `gh` because its directory comes first on PATH.
  await symlink(FAKE_GH, path.join(dir, "gh"));
  try {
    const result = spawnSync("bash", [ALERT_SCRIPT], {
      encoding: "utf8",
      env: {
        PATH: `${dir}${path.delimiter}${process.env.PATH}`,
        GH_LOG: logPath,
        GH_OPEN_ISSUES: JSON.stringify(openIssues),
        GITHUB_REPOSITORY: "example/repo",
        ALERT_TITLE: title,
        ALERT_STATUS: status,
        ALERT_DETAILS: details,
        ALERT_WORKFLOW: "Deploy Web to GitHub Pages",
        ALERT_EVENT: "schedule",
        ALERT_REF: "master",
        ALERT_RUN_URL: "https://github.com/example/repo/actions/runs/42",
      },
    });
    const calls = (await readFile(logPath, "utf8").catch(() => "")).split("\n").filter(Boolean);
    return { exitCode: result.status, stdout: result.stdout, stderr: result.stderr, calls };
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

const startsWith = (prefix) => (call) => call.startsWith(prefix);

test("the first failure opens a labelled issue that links to the failing run", async () => {
  const { exitCode, calls, stderr } = await runAlert({ status: "failure", details: "Failed jobs: verify" });

  assert.equal(exitCode, 0, stderr);
  assert.ok(calls.some(startsWith("label create ci-alert")), "the label must exist before it is used");
  const create = calls.find(startsWith("issue create"));
  assert.ok(create, `expected an issue to be created, got: ${calls.join(" | ")}`);
  assert.match(create, /--title Web deploy is failing/u);
  assert.match(create, /--label ci-alert/u);
  assert.match(create, /actions\/runs\/42/u);
  assert.match(create, /Failed jobs: verify/u);
  assert.ok(!calls.some(startsWith("issue edit")));
});

test("a repeated failure refreshes the existing issue instead of opening or commenting", async () => {
  const { exitCode, calls, stderr } = await runAlert({
    status: "failure",
    openIssues: [
      { number: 7, title: "Something else is failing" },
      { number: 12, title: TITLE },
    ],
  });

  assert.equal(exitCode, 0, stderr);
  assert.ok(calls.some(startsWith("issue edit 12 ")), calls.join(" | "));
  assert.ok(!calls.some(startsWith("issue create")));
  assert.ok(!calls.some(startsWith("issue comment")));
});

test("a success closes the open issue for the same title only", async () => {
  const { exitCode, calls, stderr } = await runAlert({
    status: "success",
    openIssues: [
      { number: 7, title: "Something else is failing" },
      { number: 12, title: TITLE },
    ],
  });

  assert.equal(exitCode, 0, stderr);
  const closes = calls.filter(startsWith("issue close"));
  assert.equal(closes.length, 1, calls.join(" | "));
  assert.match(closes[0], /^issue close 12 /u);
  assert.match(closes[0], /Recovered in https:\/\/github\.com\/example\/repo\/actions\/runs\/42/u);
});

test("a success without an open issue changes nothing", async () => {
  const { exitCode, calls, stderr } = await runAlert({
    status: "success",
    openIssues: [{ number: 7, title: "Something else is failing" }],
  });

  assert.equal(exitCode, 0, stderr);
  assert.deepEqual(calls.filter((call) => !call.startsWith("issue list")), []);
});

test("any other status, such as a skipped or cancelled run, does not call GitHub at all", async () => {
  for (const status of ["skip", "cancelled", ""]) {
    const { exitCode, calls } = await runAlert({ status: status || "none" });
    assert.equal(exitCode, 0);
    assert.deepEqual(calls, [], `status ${status}`);
  }
});

test("a title with quotes and shell characters is matched literally", async () => {
  const title = 'Release v1.9.1 "failed" $(whoami) `id`';
  const { exitCode, calls, stderr } = await runAlert({
    status: "failure",
    title,
    openIssues: [{ number: 3, title }],
  });

  assert.equal(exitCode, 0, stderr);
  assert.ok(calls.some(startsWith("issue edit 3 ")), calls.join(" | "));
});
