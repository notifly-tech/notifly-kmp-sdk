import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { existsSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";

const tarball = resolve(process.argv[2] ?? "build/packages/notifly-kmp-sdk-0.1.0-alpha.1.tgz");
assert.ok(existsSync(tarball), `missing npm tarball: ${tarball}`);

const consumer = mkdtempSync(join(tmpdir(), "notifly-kmp-js-smoke-"));

try {
  writeFileSync(join(consumer, "package.json"), '{"private":true}\n');
  execFileSync("npm", ["install", "--ignore-scripts", "--no-audit", "--no-fund", tarball], {
    cwd: consumer,
    stdio: "inherit",
  });

  const packageRoot = join(consumer, "node_modules", "notifly-kmp-sdk");
  const files = readdirSync(packageRoot);
  assert.ok(files.includes("LICENSE"), "npm package must contain LICENSE");
  assert.ok(files.some((file) => file.endsWith(".d.ts")), "npm package must contain TypeScript declarations");
  assert.ok(files.some((file) => file.endsWith(".js.map")), "npm package must contain source maps");

  const require = createRequire(join(consumer, "consumer.cjs"));
  const sdk = require("notifly-kmp-sdk");
  const decision = sdk.tech.notifly.kmp.identity.UserIdTransitionPolicy.evaluate(null, "A");
  assert.equal(decision.changed, true);
  assert.equal(decision.shouldSync, true);
  assert.equal(decision.shouldMerge, true);
  assert.equal(decision.shouldClear, false);

  console.log(`verified ${basename(tarball)} from ${dirname(tarball)}`);
} finally {
  rmSync(consumer, { recursive: true, force: true });
}
