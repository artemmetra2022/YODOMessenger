import test from "node:test";
import assert from "node:assert/strict";
import {
  objectBounds,
  rotatedBounds,
  rotatePoint,
  moveObject,
  scaleObject,
  rotateObject,
  selectionHit,
  normalizeZoom,
} from "../../web/board-geometry.mjs";
import {
  newBoard,
  encodeBoard,
  decodeBoard,
  reverseDelta,
} from "../../web/board-model.mjs";
import {
  defaultNotifications,
  normalizeNotifications,
  shouldNotify,
  notificationText,
  durationUntil,
  isMuted,
} from "../../web/notification-prefs.mjs";
import { globalShortcut, boardShortcut } from "../../web/shortcuts.mjs";
const object = (kind = "RECTANGLE") => ({
  id: crypto.randomUUID(),
  kind,
  points: [
    [100, 100],
    [200, 200],
  ],
  width: 4,
  fontSize: 32,
  color: -16777216,
  text: "",
});
const doc = () => newBoard();
const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-5, `${a} != ${b}`);
test("geometry uses Android bounds for circle, text, arrow", () => {
  const c = object("CIRCLE");
  c.points = [
    [100, 100],
    [130, 140],
  ];
  assert.deepEqual(objectBounds(c), {
    left: 48,
    top: 48,
    right: 152,
    bottom: 152,
    width: 104,
    height: 104,
    center: [100, 100],
  });
  const t = {
    ...object("TEXT"),
    points: [[100, 100]],
    text: "abc",
    fontSize: 20,
  };
  near(objectBounds(t).top, 80);
  near(objectBounds(t).right, 139);
  near(objectBounds(t).bottom, 106);
  const a = object("ARROW");
  assert.ok(objectBounds(a).right >= 202);
});
test("rotation center, normalized angle, selection uses inverse rotation", () => {
  const o = object();
  const n = rotateObject(o, -270, doc());
  assert.equal(n.rotation, 90);
  near(rotatedBounds(n).width, 104);
  assert.ok(selectionHit(n, [150, 150]));
  assert.ok(!selectionHit(n, [10, 10]));
  const p = rotatePoint([2, 0], [0, 0], 90);
  near(p[0], 0);
  near(p[1], 2);
});
test("move clamps the whole shape, not each point independently", () => {
  const o = object(),
    n = moveObject(o, -1000, -1000, doc());
  near(n.points[1][0] - n.points[0][0], 100);
  near(objectBounds(n).left, 0);
  near(objectBounds(n).top, 0);
  assert.deepEqual(o.points, [
    [100, 100],
    [200, 200],
  ]);
});
test("scale respects stroke and text limits, rejects oversize shapes", () => {
  const o = object(),
    n = scaleObject(o, 2, doc());
  assert.equal(n.width, 8);
  near(n.points[1][0] - n.points[0][0], 200);
  const t = { ...object("TEXT"), points: [[100, 100]], text: "abc" };
  assert.ok(scaleObject(t, 100, doc()).fontSize <= 96);
  assert.throws(() => scaleObject(o, 0, doc()));
  assert.throws(() =>
    moveObject(
      {
        ...o,
        points: [
          [0, 0],
          [2000, 2000],
        ],
      },
      0,
      0,
      doc(),
    ),
  );
  assert.throws(() => rotateObject(o, Infinity, doc()));
});
test("transformed objects retain Android v4 format and immutable replacement compatibility", () => {
  const b = doc(),
    original = object();
  const n = {
    ...rotateObject(scaleObject(original, 1.2, b), 45, b),
    id: crypto.randomUUID(),
  };
  b.objects = [n];
  const decoded = decodeBoard(encodeBoard(b));
  assert.equal(decoded.version, 4);
  assert.equal(decoded.objects[0].rotation, 45);
  assert.notEqual(n.id, original.id);
});
test("undo never resurrects replaced source after peer removes replacement", () => {
  const old = object(),
    n = { ...old, id: crypto.randomUUID() };
  assert.deepEqual(reverseDelta([], { adds: [n], removes: [old] }), {
    adds: [],
    removes: [],
  });
});
test("zoom clamped between 50% and 600%", () => {
  assert.equal(normalizeZoom(0.01), 0.5);
  assert.equal(normalizeZoom(99), 6);
  assert.equal(normalizeZoom(NaN), 1);
});
const event = {
  chatId: "c",
  senderId: "b",
  owner: "a",
  visible: false,
  activeChatId: "other",
};
test("notifications default to off; own, active, silent are suppressed", () => {
  const p = defaultNotifications();
  assert.equal(shouldNotify(p, event), false);
  p.enabled = true;
  assert.ok(shouldNotify(p, event));
  for (const extra of [
    { senderId: "a" },
    { silent: true },
    { visible: true, activeChatId: "c" },
  ])
    assert.equal(shouldNotify(p, { ...event, ...extra }), false);
});
test("per-chat mute and global DND expire without rewriting storage", () => {
  const p = { ...defaultNotifications(), enabled: true, quietUntil: 200 };
  assert.equal(shouldNotify(p, event, 100), false);
  assert.equal(shouldNotify(p, event, 201), true);
  p.chats.c = { muteUntil: -1 };
  assert.equal(shouldNotify(p, event, 999999), false);
  p.chats.c = { muteUntil: 500 };
  assert.equal(shouldNotify(p, event, 501), true);
});
test("preview never exposes protected messages; plain preview is opt-in and bounded", () => {
  const p = defaultNotifications();
  assert.equal(notificationText(p, { body: "secret" }), "Новое сообщение");
  p.previews = true;
  assert.equal(
    notificationText(p, { body: "secret", protectedText: true }),
    "Новое сообщение",
  );
  assert.equal(notificationText(p, { body: "hello" }), "hello");
  assert.equal(notificationText(p, { body: "x".repeat(500) }).length, 240);
});
test("normalize corrupt preferences; duration whitelist and permanent mute", () => {
  assert.deepEqual(normalizeNotifications(null), defaultNotifications());
  assert.equal(
    normalizeNotifications({ enabled: "true", quietUntil: NaN }).enabled,
    false,
  );
  assert.equal(durationUntil("3600000", 100), 3600100);
  assert.equal(durationUntil("forever"), -1);
  assert.equal(durationUntil("evil"), 0);
  assert.equal(isMuted(-1), true);
  assert.equal(isMuted(1, 2), false);
});
const key = (code, extra = {}) => ({
  code,
  key: "",
  ctrlKey: false,
  metaKey: false,
  altKey: false,
  shiftKey: false,
  isComposing: false,
  target: { closest: () => null },
  ...extra,
});
test("global shortcuts use physical keys across Russian layout and Mac", () => {
  assert.equal(
    globalShortcut(key("KeyK", { ctrlKey: true, key: "л" })),
    "search",
  );
  assert.equal(
    globalShortcut(key("KeyB", { metaKey: true, shiftKey: true, key: "и" })),
    "boards",
  );
  assert.equal(globalShortcut(key("Enter", { ctrlKey: true })), "send");
  assert.equal(globalShortcut(key("ArrowDown", { altKey: true })), "next");
});
test("composition and AltGraph never trigger shortcuts", () => {
  assert.equal(
    globalShortcut(key("KeyK", { ctrlKey: true, isComposing: true })),
    null,
  );
  assert.equal(
    globalShortcut(
      key("KeyK", { ctrlKey: true, getModifierState: () => true }),
    ),
    null,
  );
  assert.equal(boardShortcut(key("KeyV", { isComposing: true })), null);
});
test("board shortcuts do not steal native text editing", () => {
  const input = key("KeyZ", { ctrlKey: true, target: { closest: () => ({}) } });
  assert.equal(boardShortcut(input), null);
  assert.equal(boardShortcut(key("KeyZ", { ctrlKey: true })), "undo");
  assert.equal(
    boardShortcut(key("KeyZ", { ctrlKey: true, shiftKey: true })),
    "redo",
  );
  assert.equal(boardShortcut(key("KeyV")), "SELECT");
  assert.equal(boardShortcut(key("Delete")), "delete");
});
