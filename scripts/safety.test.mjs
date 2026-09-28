import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

const reset = resolve("scripts/reset-local-supabase.mjs");
const push = resolve("scripts/push-production.mjs");

test("local reset rejects a production environment and hosted URL", () => {
  const production = spawnSync(process.execPath, [reset], {
    encoding: "utf8",
    env: { ...process.env, GYMFLOW_ENV: "production" },
  });
  assert.equal(production.status, 2);
  assert.match(production.stderr, /not local/);

  const hosted = spawnSync(process.execPath, [reset], {
    encoding: "utf8",
    env: {
      ...process.env,
      GYMFLOW_ENV: "local",
      GYMFLOW_SUPABASE_URL: "https://hosted.example.com",
    },
  });
  assert.equal(hosted.status, 2);
  assert.match(hosted.stderr, /not loopback/);
});

test("local reset rejects command-line overrides before spawning the CLI", () => {
  const result = spawnSync(process.execPath, [reset, "--linked"], {
    encoding: "utf8",
    env: { ...process.env, GYMFLOW_ENV: "local" },
  });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /does not accept command-line overrides/);
});

test("production push rejects an unrelated linked project before dry run", () => {
  const directory = mkdtempSync(join(tmpdir(), "gymflow-push-test-"));
  try {
    mkdirSync(join(directory, "supabase", ".temp"), { recursive: true });
    writeFileSync(join(directory, "supabase", ".temp", "project-ref"), "wrong-project");
    const result = spawnSync(process.execPath, [push], {
      cwd: directory,
      encoding: "utf8",
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Refusing push: linked project is wrong-project/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("production push stops after dry run without explicit confirmation", () => {
  withFakeProductionCli((directory) => {
    const result = spawnSync(process.execPath, [push], {
      cwd: directory,
      encoding: "utf8",
      env: { ...process.env, GYMFLOW_CONFIRM_PRODUCTION_PROJECT: "" },
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Set GYMFLOW_CONFIRM_PRODUCTION_PROJECT/);
    assert.match(result.stdout, /--dry-run/);
    assert.doesNotMatch(result.stdout, /--yes/);
  });
});

test("production push refuses an empty backup before applying migrations", () => {
  withFakeProductionCli((directory) => {
    const result = spawnSync(process.execPath, [push], {
      cwd: directory,
      encoding: "utf8",
      env: {
        ...process.env,
        GYMFLOW_CONFIRM_PRODUCTION_PROJECT: "ixbhtfqsznxteurqmguw",
      },
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /backup file is empty/);
    assert.match(result.stdout, /db dump/);
    assert.doesNotMatch(result.stdout, /--yes/);
  });
});

test("production push applies migrations only after a nonempty backup", () => {
  withFakeProductionCli((directory) => {
    const result = spawnSync(process.execPath, [push], {
      cwd: directory,
      encoding: "utf8",
      env: {
        ...process.env,
        GYMFLOW_CONFIRM_PRODUCTION_PROJECT: "ixbhtfqsznxteurqmguw",
      },
    });
    assert.equal(result.status, 0);
    const dryRun = result.stdout.indexOf("--dry-run");
    const backup = result.stdout.indexOf("db dump");
    const apply = result.stdout.indexOf("--yes");
    assert.ok(dryRun >= 0 && backup > dryRun && apply > backup);
  }, true);
});

function withFakeProductionCli(action, nonemptyBackup = false) {
  const directory = mkdtempSync(join(tmpdir(), "gymflow-push-test-"));
  try {
    mkdirSync(join(directory, "supabase", ".temp"), { recursive: true });
    writeFileSync(join(directory, "supabase", ".temp", "project-ref"),
      "ixbhtfqsznxteurqmguw");
    const cliDirectory = join(directory, "node_modules", "supabase", "dist");
    mkdirSync(cliDirectory, { recursive: true });
    writeFileSync(join(cliDirectory, "supabase.js"), `
      const fs = require("node:fs");
      const args = process.argv.slice(2);
      console.log(args.join(" "));
      const file = args.indexOf("--file");
      if (file >= 0) fs.writeFileSync(args[file + 1],
        ${JSON.stringify(nonemptyBackup ? "backup" : "")});
    `);
    action(directory);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}
