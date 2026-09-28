import { spawnSync } from "node:child_process";
import { resolve } from "node:path";

const loopbackHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);
const selectedEnvironment = process.env.GYMFLOW_ENV?.trim().toLowerCase();
const configuredUrl = process.env.GYMFLOW_SUPABASE_URL?.trim();

if (process.argv.length > 2) {
    fail("Local reset does not accept command-line overrides.");
}
if (selectedEnvironment && selectedEnvironment !== "local") {
    fail("Refusing local reset while GYMFLOW_ENV is not local.");
}
if (configuredUrl) {
    let parsed;
    try {
        parsed = new URL(configuredUrl);
    } catch {
        fail("Refusing local reset because GYMFLOW_SUPABASE_URL is invalid.");
    }
    if (!loopbackHosts.has(parsed.hostname)) {
        fail("Refusing local reset because GYMFLOW_SUPABASE_URL is not loopback.");
    }
}

console.log("Reset target: local Supabase (127.0.0.1). Production is not eligible.");
runSupabase(["db", "reset", "--local"]);

function runSupabase(args) {
    const cli = resolve("node_modules", "supabase", "dist", "supabase.js");
    const result = spawnSync(process.execPath, [cli, ...args], {
        stdio: "inherit"
    });
    if (result.error) {
        throw result.error;
    }
    process.exit(result.status ?? 1);
}

function fail(message) {
    console.error(message);
    process.exit(2);
}
