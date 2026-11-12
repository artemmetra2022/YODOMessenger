import test from "node:test";
import assert from "node:assert/strict";
import {
  newBoard,
  encodeBoard,
  decodeBoard,
  encodeSharedObject,
  decodeSharedObject,
  applyDelta,
  reverseDelta,
  signedColor,
} from "../../web/board-model.mjs";
import { sharedBoardApi } from "../../web/board-firebase.mjs";
import {
  protectPrivateData,
  parsePublicKey,
  isPrivate,
} from "../../web/private-send.mjs";
const stroke = () => ({
  id: crypto.randomUUID(),
  kind: "PEN",
  color: signedColor("#123456"),
  width: 4,
  fontSize: 32,
  text: "",
  points: [
    [1, 2],
    [10, 20],
  ],
});
test("Android canvas v1 roundtrip and signed ARGB", () => {
  const b = newBoard("Рисунок");
  b.objects = [stroke()];
  assert.equal(b.objects[0].color, -15584170);
  assert.deepEqual(decodeBoard(encodeBoard(b)), b);
});
test("advanced shapes emit v3, rotated objects emit v4", () => {
  const b = newBoard();
  b.objects = [{ ...stroke(), kind: "ELLIPSE" }];
  assert.equal(JSON.parse(encodeBoard(b)).version, 3);
  b.objects[0].rotation = 25;
  assert.equal(JSON.parse(encodeBoard(b)).version, 4);
});
test("shared wrapper matches Android CanvasSharedCodec", () => {
  const o = stroke(),
    raw = encodeSharedObject(o),
    d = decodeBoard(raw);
  assert.equal(d.id, "00000000-0000-0000-0000-000000000000");
  assert.equal(d.width, 1200);
  assert.equal(d.height, 900);
  assert.equal(d.grid, "NONE");
  assert.deepEqual(decodeSharedObject(raw, o.id), o);
  assert.equal(decodeSharedObject(raw, crypto.randomUUID()), null);
});
test("reject duplicate IDs, too many points, coordinates, future versions, deeply nested data", () => {
  const b = newBoard(),
    o = stroke();
  for (const bad of [
    { ...b, objects: [o, o] },
    { ...b, objects: [{ ...o, points: [[Infinity, 0]] }] },
    { ...b, objects: [{ ...o, points: Array(2049).fill([0, 0]) }] },
    { ...b, version: 5 },
    { ...b, title: "" },
  ])
    assert.throws(() => decodeBoard(JSON.stringify(bad)));
  assert.throws(() => decodeBoard("[".repeat(25) + "0" + "]".repeat(25)));
});
test("limit 300 and total 60000 points", () => {
  const b = newBoard();
  b.objects = Array.from({ length: 300 }, stroke);
  assert.doesNotThrow(() => encodeBoard(b));
  assert.throws(() => applyDelta(b, [stroke()]));
  b.objects = Array.from({ length: 30 }, () => ({
    ...stroke(),
    points: Array(2048).fill([1, 2]),
  }));
  assert.throws(() => encodeBoard(b));
});
test("undo leaves concurrent participants untouched", () => {
  const own = stroke(),
    peer = stroke();
  const b = { ...newBoard(), objects: [own, peer] };
  const reverse = reverseDelta(b.objects, { adds: [own], removes: [] });
  assert.deepEqual(
    applyDelta(
      b,
      reverse.adds,
      reverse.removes.map((o) => o.id),
    ).objects,
    [peer],
  );
});
test("private scope excludes support and saved, unknown chat fails closed", async () => {
  assert.ok(isPrivate("a", { type: "PRIVATE" }));
  assert.ok(!isPrivate("saved_x", { type: "PRIVATE" }));
  assert.ok(!isPrivate("support_x", { type: "PRIVATE" }));
  await assert.rejects(
    protectPrivateData(
      "a",
      {},
      "a",
      { text: "secret" },
      () => {},
      () => {},
    ),
  );
});
test("plain groups remain compatible; media-only private needs no key", async () => {
  const data = { text: "группа" };
  assert.equal(
    await protectPrivateData(
      "a",
      { type: "GROUP" },
      "a",
      data,
      () => {
        throw Error();
      },
      () => {},
    ),
    data,
  );
  const media = { imageBase64: "abc" };
  assert.equal(
    await protectPrivateData(
      "a",
      { type: "PRIVATE", participantIds: ["a", "b"] },
      "a",
      media,
      () => {
        throw Error();
      },
      () => {},
    ),
    media,
  );
});
test("no private plaintext fallback for missing, corrupt or changed keys", async () => {
  const data = { text: "secret", replyToText: "quote" };
  await assert.rejects(
    protectPrivateData(
      "a",
      { type: "PRIVATE", participantIds: ["a", "b"] },
      "a",
      data,
      async () => null,
      async () => {},
    ),
  );
  await assert.rejects(
    protectPrivateData(
      "a",
      { type: "PRIVATE", participantIds: ["a", "b"] },
      "a",
      data,
      async () => null,
      async () => {
        throw Error("changed");
      },
    ),
    /changed/,
  );
  assert.deepEqual(data, { text: "secret", replyToText: "quote" });
  assert.throws(() => parsePublicKey("bad"));
});
function mockFirebase() {
  const store = new Map(),
    writes = [];
  const f = {
    doc: (_, ...p) => p.join("/"),
    collection: (_, ...p) => p.join("/"),
    serverTimestamp: () => 123,
    onSnapshot() {
      return () => {};
    },
    async runTransaction(_, fn) {
      const ops = [];
      const tx = {
        get: async (ref) => ({
          exists: () => store.has(ref),
          data: () => store.get(ref),
        }),
        set: (r, d) => ops.push(["set", r, d]),
        update: (r, d) => ops.push(["update", r, d]),
        delete: (r) => ops.push(["delete", r]),
      };
      const value = await fn(tx);
      for (const [op, r, d] of ops) {
        if (op === "delete") store.delete(r);
        else if (op === "set") store.set(r, d);
        else store.set(r, { ...store.get(r), ...d });
        writes.push([op, r, d]);
      }
      return value;
    },
  };
  return {
    store,
    writes,
    api: sharedBoardApi({}, { currentUser: { uid: "a" } }, f),
  };
}
test("shared create, add, erase use Android paths and immutable objects", async () => {
  const { store, api } = mockFirebase();
  await api.createShared("chat");
  const o = stroke();
  await api.changeShared("chat", 0, [o], []);
  const d = store.get("chats/chat/sharedBoardObjects/" + o.id);
  assert.equal(d.authorUid, "a");
  assert.equal(d.generation, 0);
  assert.deepEqual(decodeSharedObject(d.json, o.id), o);
  await api.changeShared("chat", 0, [], [o]);
  assert.ok(!store.has("chats/chat/sharedBoardObjects/" + o.id));
});
test("shared refuses stale generation, clearing, deleted and changed objects", async () => {
  const { store, api, writes } = mockFirebase();
  await api.createShared("chat");
  const path = "chats/chat/sharedBoard/meta",
    m = store.get(path),
    o = stroke();
  for (const state of [
    { ...m, generation: 1 },
    { ...m, clearing: true },
    { ...m, deleted: true },
  ]) {
    store.set(path, state);
    const n = writes.length;
    await assert.rejects(api.changeShared("chat", 0, [o], []));
    assert.equal(writes.length, n);
  }
  store.set(path, m);
  await api.changeShared("chat", 0, [o], []);
  await assert.rejects(api.changeShared("chat", 0, [], [{ ...o, color: 123 }]));
});
test("tombstone is recreated only by explicit create", async () => {
  const { store, api } = mockFirebase();
  await api.createShared("c");
  const path = "chats/c/sharedBoard/meta";
  store.set(path, { ...store.get(path), deleted: true, generation: 3 });
  await api.createShared("c");
  assert.equal(store.get(path).generation, 4);
  assert.equal(store.get(path).deleted, false);
});
