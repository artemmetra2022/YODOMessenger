import { chromium } from "playwright";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
const base = process.env.YODO_TEST_URL || "http://127.0.0.1:8765";
const browser = await chromium.launch({
  executablePath:
    process.env.CHROMIUM_PATH ||
    execFileSync("which", ["chromium"], { encoding: "utf8" }).trim(),
  headless: true,
  args: ["--no-sandbox"],
});
const errors = [];
try {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    acceptDownloads: true,
  });
  const page = await context.newPage();
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("dialog", (d) =>
    d.accept(d.type() === "prompt" ? "Тестовая заметка" : undefined),
  );
  await page.goto(base + "/tests/web/boards-preview.html");
  await page.waitForFunction(() => window.previewReady);
  assert.equal(
    await page.locator("#board-count").textContent(),
    "7 / 300 объектов",
  );
  const c = page.locator("#board-canvas");
  let box = await c.boundingBox();
  const x = box.x + box.width * 0.2,
    y = box.y + box.height * 0.65;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 100, y + 40, { steps: 10 });
  await page.mouse.up();
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "8 / 300 объектов",
  );
  await page.evaluate(() =>
    boardTest.peer({
      id: crypto.randomUUID(),
      kind: "LINE",
      points: [
        [10, 10],
        [80, 90],
      ],
      color: -16777216,
      width: 4,
      text: "",
      fontSize: 32,
    }),
  );
  await page.locator("#board-undo").click();
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "8 / 300 объектов",
  );
  assert.equal(
    await page.evaluate(() => boardTest.objects.length),
    8,
    "undo must preserve peer stroke",
  );
  await page.locator("#board-redo").click();
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "9 / 300 объектов",
  );
  await page.locator("#board-grid").selectOption("DOTS");
  await page.waitForTimeout(40);
  await page.locator("#board-tool").selectOption("TEXT");
  await page.mouse.click(x + 30, y - 90);
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent ===
      "10 / 300 объектов",
  );
  await page.locator("#board-tool").selectOption("ERASER");
  await page.mouse.click(x + 30, y - 90);
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "9 / 300 объектов",
  );
  await page.locator("#board-send").click();
  assert.equal(await page.evaluate(() => boardTest.sent), 1);
  const download = page.waitForEvent("download");
  await page.locator("#board-json").click();
  const downloaded = await download;
  const fs = await import("node:fs/promises");
  const d = JSON.parse(await fs.readFile(await downloaded.path(), "utf8"));
  assert.equal(d.format, "yodo.canvas");
  assert.equal(d.objects.length, 9);
  await page.evaluate(() => boardTest.meta({ generation: 1, clearing: true }));
  assert.ok(await page.locator("#board-tool").isDisabled());
  assert.ok(await page.locator("#board-undo").isDisabled());
  await page.evaluate(() => boardTest.meta({ clearing: false }));
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "0 / 300 объектов",
  );
  assert.ok(await page.locator("#board-undo").isDisabled());
  await page.evaluate(() => boardTest.meta({ deleted: true }));
  assert.ok(await page.locator("#board-editor").isHidden());
  await page.locator("#board-close").click();
  await page.locator("#btn-boards").click();
  await page
    .getByRole("button", { name: "Идеи для YODO", exact: true })
    .click();
  await page.locator("#board-tool").selectOption("RECTANGLE");
  box = await c.boundingBox();
  await page.mouse.move(box.x + 40, box.y + 100);
  await page.mouse.down();
  await page.mouse.move(box.x + 110, box.y + 170);
  await page.mouse.up();
  await page.waitForFunction(
    () =>
      document.getElementById("board-count").textContent === "8 / 300 объектов",
  );
  await page.locator("#board-library-back").click();
  await page
    .getByRole("button", { name: "Идеи для YODO", exact: true })
    .click();
  assert.equal(
    await page.locator("#board-count").textContent(),
    "8 / 300 объектов",
  );
  const stored = await page.evaluate(
    () =>
      new Promise((res, rej) => {
        const r = indexedDB.open("yodo-web-boards");
        r.onsuccess = () => {
          const q = r.result
            .transaction("boards")
            .objectStore("boards")
            .getAll();
          q.onsuccess = () =>
            res(
              q.result.map((x) => ({
                hasTitle: "title" in x,
                hasObjects: "objects" in x,
                encrypted: x.data instanceof ArrayBuffer,
              })),
            );
        };
        r.onerror = rej;
      }),
  );
  assert.ok(stored.every((x) => !x.hasTitle && !x.hasObjects && x.encrypted));
  await page.evaluate(() =>
    boardTest.boards.setContext("other-account", null, null),
  );
  await page.locator("#btn-boards").click();
  await page.waitForFunction(() =>
    document
      .getElementById("board-list")
      .textContent.includes("Создайте первую"),
  );
  assert.equal(await page.locator(".board-library-row").count(), 0);
  assert.deepEqual(errors, []);
  console.log(
    "PASS: desktop draw, text, eraser, grid, own undo/redo with concurrent peer, export, send, generation fence, tombstone, encrypted persistence, account isolation; no browser errors",
  );
  await context.close();
  const mobile = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  });
  const mp = await mobile.newPage();
  mp.on("pageerror", (e) => errors.push(e.message));
  await mp.goto(base + "/tests/web/boards-preview.html");
  await mp.waitForFunction(() => window.previewReady);
  assert.ok(
    await mp.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  );
  const overflow = await mp.evaluate(
    () =>
      [
        ...document.querySelectorAll(
          ".board-shell button,.board-shell select,.board-header",
        ),
      ].filter(
        (n) =>
          n.getClientRects().length && !n.closest(".board-selection") &&
          n.getBoundingClientRect().right > innerWidth,
      ).length,
  );
  assert.equal(overflow, 0);
  await mp.locator("#board-tool").selectOption("LINE");
  const mb = await mp.locator("#board-canvas").boundingBox();
  await mp.dispatchEvent("#board-canvas", "pointerdown", {
    pointerId: 2,
    pointerType: "touch",
    button: 0,
    clientX: mb.x + 30,
    clientY: mb.y + 30,
    isPrimary: true,
  });
  await mp.dispatchEvent("#board-canvas", "pointercancel", {
    pointerId: 2,
    pointerType: "touch",
  });
  assert.equal(
    await mp.locator("#board-count").textContent(),
    "7 / 300 объектов",
  );
  console.log(
    "PASS: 390px viewport, no horizontal overflow, pointer cancellation",
  );
  await mobile.close();
} finally {
  await browser.close();
}
