import { chromium } from "playwright";
import fs from "node:fs/promises";
import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../../", import.meta.url));
const base = process.env.YODO_TEST_URL || "http://127.0.0.1:8765";
const keys = JSON.parse(
  await fs.readFile(
    root + "/tests/web/fixtures/synthetic-hpke-public.json",
    "utf8",
  ),
);
const source = await fs.readFile(root + "/web/app.js", "utf8");
const browser = await chromium.launch({
  executablePath:
    process.env.CHROMIUM_PATH ||
    execFileSync("which", ["chromium"], { encoding: "utf8" }).trim(),
  headless: true,
  args: ["--no-sandbox"],
});
const errors = [],
  alerts = [];
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  });
  await page.addInitScript(() => {
    window.__notices = [];
    window.__noticeObjects = [];
    window.__permissionRequests = 0;
    class TestNotification {
      static permission = "granted";
      static async requestPermission() {
        window.__permissionRequests++;
        return this.permission;
      }
      constructor(title, options) {
        window.__notices.push({ title, options });
        window.__noticeObjects.push(this);
      }
      close() {
        this.closed = true;
      }
    }
    Object.defineProperty(window, "Notification", {
      value: TestNotification,
      configurable: true,
    });
  });
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("dialog", (d) => {
    alerts.push(d.message());
    d.accept();
  });
  await page.route("https://cdn.jsdelivr.net/**", (r) =>
    r.fulfill({
      contentType: "text/javascript",
      body: "window.QRCode=function(){};",
    }),
  );
  await page.route("https://www.gstatic.com/firebasejs/**", async (route) => {
    const url = route.request().url();
    let body = "";
    if (url.includes("firebase-app"))
      body = "export const initializeApp=()=>({});";
    if (url.includes("firebase-auth")) {
      const match = source.match(
        /import \{([^}]+)\} from "https:[^"]+firebase-auth.js"/s,
      );
      const names = match[1]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      body = `const user={uid:'a',email:'test@example.test',emailVerified:true};let listener;const auth={currentUser:user};\n`;
      for (const name of names) {
        if (name === "getAuth") body += "export const getAuth=()=>auth;";
        else if (name === "onAuthStateChanged")
          body +=
            "export const onAuthStateChanged=(a,cb)=>{listener=cb;queueMicrotask(()=>cb(user));};";
        else if (name === "signOut")
          body +=
            "export const signOut=async()=>{auth.currentUser=null;await listener(null);};";
        else if (name === "GoogleAuthProvider")
          body += "export class GoogleAuthProvider{};";
        else body += `export const ${name}=()=>({});`;
      }
    }
    if (url.includes("firebase-firestore")) {
      const match = source.match(
        /import \{([^}]+)\} from "https:[^"]+firebase-firestore.js"/s,
      );
      const names = match[1]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      body = `
const records=new Map([['users/a',{displayName:'Тест',username:'test',publicKey:${JSON.stringify(keys.a)}}],['users/b',{displayName:'Собеседник',publicKey:${JSON.stringify(keys.b)}}],['chats/test',{type:'PRIVATE',participantIds:['a','b'],participantNames:{a:'Тест',b:'Собеседник'},title:'Тестовый чат'}]]);let count=0;const listeners=[];
const path=x=>typeof x==='string'?x:x?.path||'';
const snap=(p)=>({id:p.split('/').at(-1),exists:()=>records.has(p),data:()=>records.get(path(p)),metadata:{hasPendingWrites:false}});
function result(p){const docs=[...records.keys()].filter(k=>k.startsWith(p+'/')&&k.split('/').length===p.split('/').length+1).map(snap);return {docs,size:docs.length,empty:!docs.length,forEach:f=>docs.forEach(f),docChanges:()=>docs.map(doc=>({type:'added',doc}))};}
const ping=()=>{for(const [p,cb]of [...listeners].filter(([p])=>p==='chats'||p.endsWith('/messages')||p.endsWith('sharedBoardObjects')||p.endsWith('sharedBoard/meta')))cb((p==='chats'||p.endsWith('/messages')||p.endsWith('sharedBoardObjects'))?result(p):snap(p));};
window.mockFirebase={records,listeners,ping};
`;
      const known = {
        getFirestore: "()=>({})",
        doc: "(x,...p)=>{if(typeof x==='string'){const z=x+'/'+(p.join('/')||'message-'+(++count));return {path:z,id:z.split('/').at(-1)};}const z=p.join('/');return {path:z,id:z.split('/').at(-1)};}",
        collection: "(x,...p)=>p.join('/')",
        query: "x=>x",
        getDoc: "async p=>snap(path(p))",
        getDocFromServer: "async p=>snap(path(p))",
        getDocs: "async p=>result(path(p))",
        onSnapshot:
          "(p,cb)=>{p=path(p);listeners.push([p,cb]);queueMicrotask(()=>cb((p==='chats'||p.endsWith('/messages')||p.endsWith('sharedBoardObjects'))?result(p):snap(p)));return()=>{const i=listeners.findIndex(x=>x[0]===p&&x[1]===cb);if(i>=0)listeners.splice(i,1);};}",
        writeBatch:
          "()=>{const ops=[];return{set:(p,d)=>ops.push([p,d]),update:(p,d)=>ops.push([p,{...records.get(path(p)),...d}]),commit:async()=>{for(const [p,d]of ops)records.set(path(p),d);ping();}};}",
        setDoc:
          "async(p,d,options)=>{records.set(path(p),options?.merge?{...records.get(path(p)),...d}:d);}",
        updateDoc:
          "async(p,d)=>{records.set(path(p),{...records.get(path(p)),...d});}",
        serverTimestamp: "()=>({seconds:123})",
        increment: "n=>n",
        deleteField: "()=>null",
        arrayUnion: "(...x)=>x",
        arrayRemove: "(...x)=>x",
        runTransaction:
          "async(db,fn)=>{const tx={get:async p=>snap(path(p)),set:(p,d)=>records.set(path(p),d),update:(p,d)=>records.set(path(p),{...records.get(path(p)),...d}),delete:p=>records.delete(path(p))};const r=await fn(tx);ping();return r;}",
      };
      for (const name of names)
        body += `\nexport const ${name}=${known[name] || "(...x)=>x"};`;
    }
    await route.fulfill({ contentType: "text/javascript", body });
  });
  await page.goto(base + "/web/");
  await page.waitForSelector("#screen-app.active");
  await page.waitForTimeout(100);
  await page
    .locator(".chat-item")
    .filter({ hasText: "Тестовый чат" })
    .first()
    .click();
  await page.locator("#message-input").fill("Тест: сообщение из веба");
  await page.locator(".btn-send").click();
  await page.waitForTimeout(500);
  await page.waitForFunction(() =>
    [...mockFirebase.records.keys()].some((x) =>
      x.startsWith("chats/test/messages/"),
    ),
  );
  const message = await page.evaluate(
    () =>
      [...mockFirebase.records].find(([p]) =>
        p.startsWith("chats/test/messages/"),
      )[1],
  );
  assert.equal(message.text, "");
  assert.equal(message.encryptedPayloadVersion, 2);
  assert.equal(message.encrypted, true);
  assert.deepEqual(Object.keys(message.encByUid), ["a", "b"]);
  assert.deepEqual(message.recipientIds, ["b"]);
  assert.deepEqual(message.readBy, []);
  assert.ok(message.sentAt);
  assert.equal(await page.locator("#message-input").inputValue(), "");
  assert.ok(
    (await page.locator("#messages").textContent()).includes(
      "Тест: сообщение из веба",
    ),
  );
  assert.equal(
    (await page.evaluate(() => mockFirebase.records.get("chats/test")))
      .lastMessage,
    "🔒 Сообщение",
  );
  await page.locator("#btn-shared-board").click();
  await page.waitForSelector("#board-modal:not(.hidden)");
  assert.equal(
    await page.locator("#board-heading").textContent(),
    "Общая доска чата",
  );
  await page.locator("#board-close").click();
  // Global shortcuts, persisted account preferences, and messages from inactive chats.
  await page.keyboard.press("Control+k");
  await page.waitForSelector("#shortcut-search:not(.hidden)");
  await page.locator("#shortcut-query").fill("Тестовый");
  await page.keyboard.press("Enter");
  assert.ok(await page.locator("#shortcut-search").isHidden());
  await page.keyboard.press("F1");
  assert.ok(await page.locator("#shortcut-help").isVisible());
  await page.keyboard.press("Escape");
  await page.keyboard.press("Control+,");
  await page.waitForSelector("#modal-settings:not(.hidden)");
  await page.locator("label:has(#settings-notifications-toggle)").click();
  await page.locator("#settings-notifications-previews").check();
  assert.equal(
    await page.evaluate(() => __permissionRequests),
    0,
    "permission must never be requested automatically",
  );
  await page.locator("#btn-close-settings-modal").click();
  await page.evaluate(() => {
    mockFirebase.records.set("chats/other", {
      type: "GROUP",
      participantIds: ["a", "b"],
      title: "Другой чат",
      lastMessage: "",
      lastMessageTimestamp: 0,
    });
    mockFirebase.ping();
  });
  async function incoming(id, mid, text, extra = {}) {
    await page.evaluate(
      ({ id, mid, text, extra }) => {
        mockFirebase.records.set("chats/" + id + "/messages/" + mid, {
          senderId: "b",
          text,
          timestamp: Date.now(),
          status: "SENT",
          ...extra,
        });
        const c = mockFirebase.records.get("chats/" + id);
        mockFirebase.records.set("chats/" + id, {
          ...c,
          lastMessageId: mid,
          lastMessage: text,
          lastMessageTimestamp: Date.now(),
          lastMessageSenderId: "b",
        });
        mockFirebase.ping();
      },
      { id, mid, text, extra },
    );
    await page.waitForTimeout(100);
  }
  await incoming("other", "incoming-1", "Текст уведомления");
  assert.equal(await page.evaluate(() => __notices.length), 1);
  assert.equal(
    await page.evaluate(() => __notices[0].options.body),
    "Текст уведомления",
  );
  await page.evaluate(() => mockFirebase.ping());
  await page.waitForTimeout(50);
  assert.equal(
    await page.evaluate(() => __notices.length),
    1,
    "duplicate heads must not notify twice",
  );
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Другой");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Control+Shift+m");
  assert.equal(
    await page.locator("#btn-chat-notifications").textContent(),
    "Тихо",
  );
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Тестовый");
  await page.keyboard.press("Enter");
  await incoming("other", "incoming-2", "Не показывать");
  assert.equal(await page.evaluate(() => __notices.length), 1);
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Другой");
  await page.keyboard.press("Enter");
  await page.locator("#btn-chat-notifications").click();
  await page.locator("#notification-chat-mute").selectOption("3600000");
  assert.equal(
    await page.locator("#notification-chat-mute").inputValue(),
    "current",
  );
  await page.locator("#notification-chat-close").click();
  const prefs = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("yodo-notifications:v1:a")),
  );
  assert.ok(prefs.chats.other.muteUntil > Date.now());
  await page.keyboard.press("Control+Shift+m");
  await page.keyboard.press("Control+,");
  await page.locator("#settings-notifications-quiet").selectOption("3600000");
  await page.locator("#btn-close-settings-modal").click();
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Тестовый");
  await page.keyboard.press("Enter");
  await incoming("other", "incoming-3", "Тишина");
  assert.equal(await page.evaluate(() => __notices.length), 1);
  await page.keyboard.press("Control+,");
  await page.locator("#settings-notifications-quiet").selectOption("off");
  await page.locator("#settings-notifications-previews").uncheck();
  await page.locator("#btn-close-settings-modal").click();
  await incoming("other", "incoming-4", "Скрытое превью");
  assert.equal(await page.evaluate(() => __notices.length), 2);
  assert.equal(
    await page.evaluate(() => __notices.at(-1).options.body),
    "Новое сообщение",
  );
  await incoming("other", "incoming-5", "Тихое сообщение", { silent: true });
  assert.equal(await page.evaluate(() => __notices.length), 2);
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Другой");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Control+,");
  await page.locator("#settings-notifications-previews").check();
  await page.locator("#btn-close-settings-modal").click();
  await incoming("test", "protected-incoming", "Нельзя раскрывать", {
    encrypted: true,
  });
  assert.equal(await page.evaluate(() => __notices.length), 3);
  assert.equal(
    await page.evaluate(() => __notices.at(-1).options.body),
    "Новое сообщение",
  );
  assert.equal(
    await page.evaluate(
      () => JSON.parse(localStorage.getItem("yodo-notifications:v1:a")).enabled,
    ),
    true,
  );
  await page.keyboard.press("Control+Shift+b");
  await page.waitForSelector("#board-modal:not(.hidden)");
  assert.equal(await page.locator("#board-heading").textContent(), "Мои доски");
  await page.locator("#board-close").click();
  await page.keyboard.press("Control+k");
  await page.locator("#shortcut-query").fill("Тестовый");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Control+Shift+s");
  assert.ok(await page.locator("#board-editor").isVisible());
  await page.locator("#board-close").click();
  await page.locator("#message-input").fill("Отправка сочетанием");
  await page.keyboard.press("Control+Enter");
  await page.waitForFunction(
    () => document.getElementById("message-input").value === "",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  const outside = await page
    .locator("#chat-header button")
    .evaluateAll(
      (ns) =>
        ns.filter(
          (n) =>
            n.getClientRects().length &&
            n.getBoundingClientRect().right > innerWidth,
        ).length,
    );
  assert.equal(outside, 0);
  if (process.env.YODO_CAPTURE_MOBILE)
    await page.screenshot({ path: process.env.YODO_CAPTURE_MOBILE });
  await page.locator("#btn-back").click();
  await page.locator("#btn-logout").click();
  await page.evaluate(() => __noticeObjects.at(-1).onclick());

  await page.waitForSelector("#screen-login.active");
  assert.ok(await page.locator("#board-modal").isHidden());
  assert.ok(await page.evaluate(() => __noticeObjects.every((x) => x.closed)));
  assert.deepEqual(errors, []);
  assert.equal(alerts.length, 1); // only shared board creation confirmation
  console.log(
    "PASS: full app bootstrap, private send with v2 ciphertext/receipts, shared board, global shortcuts, notification mute/DND/previews/dedup/silent, privacy, logout cleanup; SDK mocked, no live Firebase writes",
  );
  await page.close();
} finally {
  await browser.close();
}
