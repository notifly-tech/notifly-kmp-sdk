import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
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
  const declarations = files.find((file) => file.endsWith(".d.ts"));
  assert.ok(files.includes("LICENSE"), "npm package must contain LICENSE");
  assert.ok(declarations, "npm package must contain TypeScript declarations");
  assert.ok(files.some((file) => file.endsWith(".js.map")), "npm package must contain source maps");
  const declarationText = readFileSync(join(packageRoot, declarations), "utf8");
  for (const publicType of [
    "PopupFactory",
    "PopupRenderer",
    "PopupRenderTask",
    "PopupRendererConfig",
    "PopupRenderInput",
    "PopupRenderOutput",
  ]) {
    assert.match(declarationText, new RegExp(`(?:abstract )?class ${publicType}\\b`));
  }
  assert.match(declarationText, /render\(input: .*PopupRenderInput, onComplete: .*PopupRenderOutput.*\): .*PopupRenderTask;/);

  const require = createRequire(join(consumer, "consumer.cjs"));
  const sdk = require("notifly-kmp-sdk");
  const decision = sdk.tech.notifly.kmp.identity.UserIdTransitionPolicy.evaluate(null, "A");
  assert.equal(decision.changed, true);
  assert.equal(decision.shouldSync, true);
  assert.equal(decision.shouldMerge, true);
  assert.equal(decision.shouldClear, false);

  const popup = sdk.tech.notifly.kmp.popup;
  const model = popup.model;
  const renderAndAwait = async (renderer, input, afterRender = () => {}) => {
    let callbackCount = 0;
    let resolveOutput;
    const outputPromise = new Promise((resolve) => {
      resolveOutput = resolve;
    });
    const task = renderer.render(input, (output) => {
      callbackCount += 1;
      resolveOutput(output);
    });
    assert.equal(typeof task.cancel, "function");
    afterRender(task);
    const output = await Promise.race([
      outputPromise,
      new Promise((_, reject) => setTimeout(() => reject(new Error("popup callback timed out")), 5000)),
    ]);
    await new Promise((resolve) => setTimeout(resolve, 50));
    assert.equal(callbackCount, 1, "popup callback must run exactly once");
    return output;
  };

  const staticRenderer = popup.PopupFactory.create(new model.PopupRendererConfig("", "not-https", ""));
  const staticOutput = await renderAndAwait(
    staticRenderer,
    new model.PopupRenderInput("static", null, null, null, null, null),
  );
  assert.equal(staticOutput.outcome, "static");
  assert.equal(staticOutput.html, null);
  assert.equal(staticOutput.errorCode, null);
  assert.equal(staticOutput.httpStatus, null);
  staticRenderer.close();

  const closedOutput = await renderAndAwait(
    staticRenderer,
    new model.PopupRenderInput("static", null, null, null, null, null),
  );
  assert.equal(closedOutput.outcome, "failed");
  assert.equal(closedOutput.errorCode, "renderer_closed");

  const cancellableRenderer = popup.PopupFactory.create(new model.PopupRendererConfig("", "not-https", ""));
  const cancelledOrCompleted = await renderAndAwait(
    cancellableRenderer,
    new model.PopupRenderInput("ssr", "campaign", "user", "device", "open", "{}"),
    (task) => {
      task.cancel();
      task.cancel();
    },
  );
  assert.ok(
    cancelledOrCompleted.outcome === "cancelled" ||
      (cancelledOrCompleted.outcome === "failed" &&
        cancelledOrCompleted.errorCode === "invalid_configuration"),
  );
  cancellableRenderer.close();

  console.log(`verified ${basename(tarball)} from ${dirname(tarball)}`);
} finally {
  rmSync(consumer, { recursive: true, force: true });
}
