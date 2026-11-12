import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
const base = process.env.YODO_TEST_URL || "http://127.0.0.1:8765",
  browser = await chromium.launch({
    executablePath:
      process.env.CHROMIUM_PATH ||
      execFileSync("which", ["chromium"], { encoding: "utf8" }).trim(),
    headless: true,
    args: ["--no-sandbox"],
  });
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base + "/tests/web/preferences-preview-general.html");
  await page.waitForFunction(() => window.previewReady);
  await page.locator("#settings-notifications-previews").check();
  await page.locator("#settings-notifications-quiet").selectOption("forever");
  assert.ok(await page.locator("#settings-notifications-toggle").isChecked());
  await page.evaluate(() =>
    preferencesTest.notifications.setOwner("other-account"),
  );
  assert.equal(
    await page.locator("#settings-notifications-toggle").isChecked(),
    false,
  );
  assert.equal(
    await page.locator("#settings-notifications-previews").isChecked(),
    false,
  );
  assert.equal(
    await page.locator("#settings-notifications-quiet").inputValue(),
    "off",
  );
  await page.evaluate(() =>
    preferencesTest.notifications.setOwner("preview-account"),
  );
  assert.equal(
    await page.locator("#settings-notifications-toggle").isChecked(),
    true,
  );
  assert.equal(
    await page.locator("#settings-notifications-previews").isChecked(),
    true,
  );
  assert.equal(
    await page.locator("#settings-notifications-quiet").inputValue(),
    "forever",
  );
  await page.reload();
  await page.waitForFunction(() => window.previewReady);
  assert.equal(
    await page.locator("#settings-notifications-previews").isChecked(),
    true,
  );
  assert.equal(
    await page.locator("#settings-notifications-quiet").inputValue(),
    "forever",
  );
  await page.evaluate(() => {
    Notification.permission = "denied";
    preferencesTest.notifications.refresh();
  });
  assert.ok(
    await page.locator("#settings-notifications-permission").isDisabled(),
  );
  assert.match(
    await page.locator("#settings-notifications-hint").textContent(),
    /заблокированы/,
  );
  await page.evaluate(() => {
    delete window.Notification;
    preferencesTest.notifications.refresh();
  });
  assert.ok(
    await page.locator("#settings-notifications-permission").isDisabled(),
  );
  assert.match(
    await page.locator("#settings-notifications-hint").textContent(),
    /не поддерживает/,
  );
  await page.evaluate(() => {
    window.originalSetItem = Storage.prototype.setItem;
    Storage.prototype.setItem = () => {
      throw new DOMException("blocked", "QuotaExceededError");
    };
  });
  await page.locator("#settings-notifications-previews").uncheck();
  assert.match(
    await page.locator("#settings-notifications-hint").textContent(),
    /до закрытия вкладки/,
  );
  await page.evaluate(() => (Storage.prototype.setItem = originalSetItem));
  await page.evaluate(() => preferencesTest.shortcuts.showHelp());
  assert.equal(
    await page
      .locator("#shortcut-help .web-preferences-card")
      .evaluate((n) => n.scrollTop),
    0,
  );
  await page.keyboard.press("Shift+Tab");
  assert.equal(
    await page.evaluate(() => document.activeElement.id),
    "shortcut-help-close",
  );
  await page.keyboard.press("Escape");
  assert.ok(await page.locator("#shortcut-help").isHidden());
  assert.deepEqual(errors, []);
  console.log(
    "PASS: notification preference persistence/reload, account isolation, denied/unsupported permission, storage failure, help starts at top and traps focus",
  );
  await page.close();
} finally {
  await browser.close();
}
