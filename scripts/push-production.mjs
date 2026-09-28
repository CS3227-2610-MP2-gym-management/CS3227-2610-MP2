import { mkdirSync, readFileSync, statSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const productionProjectRef = "ixbhtfqsznxteurqmguw";
const linkedProjectRef = readFileSync("supabase/.temp/project-ref", "utf8").trim();

if (process.argv.length > 2) {
    fail("Production push does not accept command-line overrides.");
}
if (linkedProjectRef !== productionProjectRef) {
    fail(`Refusing push: linked project is ${linkedProjectRef || "missing"}.`);
}

console.log(`Production migration target: GymFlow Production (${productionProjectRef}).`);
runSupabase(["db", "push", "--linked", "--dry-run", "--agent", "no"]);

if (process.env.GYMFLOW_CONFIRM_PRODUCTION_PROJECT !== productionProjectRef) {
    fail(
        "Dry run completed. Set GYMFLOW_CONFIRM_PRODUCTION_PROJECT to the displayed "
        + "project reference before applying migrations."
    );
}

const backupDirectory = resolve("work", "production-backups");
mkdirSync(backupDirectory, { recursive: true });
const timestamp = new Date().toISOString().replaceAll(":", "-").replace(".000Z", "Z");
const backupPath = resolve(backupDirectory, `before-${timestamp}.sql`);

console.log(`Creating encrypted-storage-required logical backup at ${backupPath}`);
runSupabase([
    "db", "dump", "--linked", "--schema", "auth,public", "--data-only", "--use-copy",
    "--file", backupPath, "--agent", "no"
]);
if (statSync(backupPath).size === 0) {
    fail("Production backup file is empty; migrations were not applied.");
}

runSupabase(["db", "push", "--linked", "--yes", "--agent", "no"]);

function runSupabase(args) {
    const cli = resolve("node_modules", "supabase", "dist", "supabase.js");
    const result = spawnSync(process.execPath, [cli, ...args], {
        stdio: "inherit"
    });
    if (result.error) {
        throw result.error;
    }
    if (result.status !== 0) {
        process.exit(result.status ?? 1);
    }
}

function fail(message) {
    console.error(message);
    process.exit(2);
}
