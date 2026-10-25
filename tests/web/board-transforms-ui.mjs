import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
const base = process.env.YODO_TEST_URL || "http://127.0.0.1:8765";
const browser = await chromium.launch({
  executablePath:
    process.env.CHROMIUM_PATH ||
    execFileSync("which", ["chromium"], { encoding: "utf8" }).trim(),
  headless: true,
  args: ["--no-sandbox"],
});
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1100 },
    acceptDownloads: true,
  });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base + "/tests/web/boards-preview.html");
  await page.waitForFunction(() => window.previewReady);
  const canvas = page.locator("#board-canvas");
  await canvas.focus();
  await page.keyboard.press("v");
  assert.equal(await page.locator("#board-tool").inputValue(), "SELECT");
  const getRect = () =>
    page.evaluate(() =>
      boardTest.objects
        .map((x) => JSON.parse(x.json).objects[0])
        .find((x) => x.kind === "ROUNDED_RECT"),
    );
  const old = await getRect();
  let box = await canvas.boundingBox();
  const coord = (p, b = box) => [
    b.x + (p[0] / 1200) * b.width,
    b.y + (p[1] / 900) * b.height,
  ];
  let [x, y] = coord([300, 310]);
  await page.mouse.click(x, y);
  assert.equal(await page.locator("#board-object-copy").isEnabled(), true);
  box = await canvas.boundingBox();
  [x, y] = coord([300, 310]);
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 35, y + 25, { steps: 5 });
  await page.mouse.up();
  await page.waitForFunction(
    (id) => !boardTest.objects.some((o) => o.id === id),
    old.id,
  );
  let moved = await getRect();
  assert.notEqual(moved.id, old.id);
  assert.ok(moved.points[0][0] > old.points[0][0]);
  assert.equal(
    await page.locator("#board-count").textContent(),
    "7 / 300 объектов",
  );
  await page.locator("#board-object-angle").fill("45");
  await page.locator("#board-object-angle").press("Tab");
  await page.waitForFunction(() =>
    boardTest.objects.some(
      (x) => JSON.parse(x.json).objects[0].rotation === 45,
    ),
  );
  let rotated = await getRect();
  assert.equal(rotated.rotation, 45);
  assert.equal(
    JSON.parse(
      await page.evaluate(
        () =>
          boardTest.objects.find(
            (x) => JSON.parse(x.json).objects[0].kind === "ROUNDED_RECT",
          ).json,
      ),
    ).version,
    4,
  );
  await page.locator("#board-object-larger").click();
  await page.waitForFunction(
    (w) =>
      boardTest.objects.some((x) => {
        const o = JSON.parse(x.json).objects[0];
        return o.kind === "ROUNDED_RECT" && o.width > w;
      }),
    rotated.width,
  );
  await canvas.focus();
  await page.keyboard.press("Control+z");
  await page.waitForFunction(
    (w) =>
      boardTest.objects.some((x) => {
        const o = JSON.parse(x.json).objects[0];
        return o.kind === "ROUNDED_RECT" && Math.abs(o.width - w) < 1e-5;
      }),
    rotated.width,
  );
  await page.keyboard.press("Control+Shift+z");
  await page.waitForFunction(
    (w) =>
      boardTest.objects.some((x) => {
        const o = JSON.parse(x.json).objects[0];
        return o.kind === "ROUNDED_RECT" && o.width > w;
      }),
    rotated.width,
  );
  await page.locator("#board-object-copy").click();
  await page.waitForFunction(() => boardTest.objects.length === 8);
  await canvas.focus();
  await page.keyboard.press("Delete");
  await page.waitForFunction(() => boardTest.objects.length === 7);
  await page.keyboard.press("Control+z");
  await page.waitForFunction(() => boardTest.objects.length === 8);
  await page.locator("#board-zoom-in").click();
  assert.equal(await page.locator("#board-zoom-label").textContent(), "125%");
  await canvas.focus();
  await page.keyboard.press("Equal");
  assert.equal(await page.locator("#board-zoom-label").textContent(), "156%");
  await page.keyboard.press("Digit0");
  assert.equal(await page.locator("#board-zoom-label").textContent(), "100%");
  for (let i = 0; i < 6; i++) await page.locator("#board-zoom-in").click();
  await canvas.focus();
  await page.keyboard.press("h");
  assert.equal(await page.locator("#board-tool").inputValue(), "HAND");
  const vp = await page.locator("#board-viewport").boundingBox();
  const before = await page
    .locator("#board-viewport")
    .evaluate((n) => [n.scrollLeft, n.scrollTop]);
  await page.mouse.move(vp.x + vp.width * 0.6, vp.y + vp.height * 0.5);
  await page.mouse.down();
  await page.mouse.move(vp.x + vp.width * 0.5, vp.y + vp.height * 0.4);
  await page.mouse.up();
  const after = await page
    .locator("#board-viewport")
    .evaluate((n) => [n.scrollLeft, n.scrollTop]);
  assert.ok(after[0] > before[0] || after[1] > before[1]);
  await canvas.focus();
  await page.keyboard.press("Digit0");
  await page.keyboard.press("v");
  box = await canvas.boundingBox();
  const text = await page.evaluate(() =>
    boardTest.objects
      .map((o) => JSON.parse(o.json).objects[0])
      .find((o) => o.kind === "TEXT"),
  );
  [x, y] = coord([text.points[0][0] + 40, text.points[0][1] - 8]);
  await page.mouse.click(x, y);
  const download = page.waitForEvent("download");
  await page.locator("#board-png").click();
  const file = await download,
    raw = await fs.readFile(await file.path());
  const match = await page.evaluate(async (b64) => {
    const { drawBoard, newBoard } = await import("../../web/board-model.mjs");
    const b = newBoard();
    b.objects = boardTest.objects.map((x) => JSON.parse(x.json).objects[0]);
    const expected = document.createElement("canvas");
    expected.width = 1200;
    expected.height = 900;
    drawBoard(expected, b);
    const img = new Image();
    await new Promise((r, j) => {
      img.onload = r;
      img.onerror = j;
      img.src = "data:image/png;base64," + b64;
    });
    const result = document.createElement("canvas");
    result.width = 1200;
    result.height = 900;
    result.getContext("2d").drawImage(img, 0, 0);
    return result.toDataURL() === expected.toDataURL();
  }, raw.toString("base64"));
  assert.ok(match, "PNG must not contain selection frame or zoom");
  assert.deepEqual(errors, []);
  console.log(
    "PASS: selection, move, rotate v4, resize, conflict-safe replacement IDs, keyboard undo/redo, copy/delete, zoom/pan, clean PNG export",
  );
  const mobile = await browser.newPage({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  });
  await mobile.goto(base + "/tests/web/boards-preview.html");
  await mobile.waitForFunction(() => window.previewReady);
  const touchBox = await mobile.locator("#board-canvas").boundingBox(),
    cx = touchBox.x + touchBox.width / 2,
    cy = touchBox.y + touchBox.height / 2;
  const cdp = await mobile.context().newCDPSession(mobile);
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchStart",
    touchPoints: [
      { x: cx - 25, y: cy, id: 1 },
      { x: cx + 25, y: cy, id: 2 },
    ],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchMove",
    touchPoints: [
      { x: cx - 55, y: cy, id: 1 },
      { x: cx + 55, y: cy, id: 2 },
    ],
  });
  await cdp.send("Input.dispatchTouchEvent", {
    type: "touchEnd",
    touchPoints: [],
  });
  assert.ok(
    parseInt(await mobile.locator("#board-zoom-label").textContent()) > 100,
  );
  assert.equal(
    await mobile.locator("#board-count").textContent(),
    "7 / 300 объектов",
  );
  console.log(
    "PASS: real two-finger pinch zoom cancels the provisional stroke without adding objects",
  );
  await mobile.close();
  await page.close();
} finally {
  await browser.close();
}
