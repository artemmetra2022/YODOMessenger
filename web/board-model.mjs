import { objectBounds, rotatePoint } from "./board-geometry.mjs";
/** Android CanvasCodec / CanvasSharedCodec wire format. No Firebase or DOM dependencies. */
export const LIMITS = {
  objects: 300,
  points: 2048,
  total: 60000,
  bytes: 5000000,
};
export const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const KINDS = [
  "PEN",
  "LINE",
  "ARROW",
  "RECTANGLE",
  "CIRCLE",
  "TEXT",
  "FORMULA",
  "ROUNDED_RECT",
  "ELLIPSE",
  "TRIANGLE",
  "DIAMOND",
];
const advanced = ["ROUNDED_RECT", "ELLIPSE", "TRIANGLE", "DIAMOND"];
const finite = (x, a, b) =>
  typeof x === "number" && Number.isFinite(x) && x >= a && x <= b;
function require(ok, text = "Некорректный файл доски") {
  if (!ok) throw new Error(text);
}
export function newBoard(title = "Новая доска") {
  return {
    format: "yodo.canvas",
    version: 1,
    id: crypto.randomUUID(),
    title,
    width: 1200,
    height: 900,
    grid: "SQUARES",
    template: "NONE",
    objects: [],
  };
}
export function validateBoard(d) {
  require(d && d.format === "yodo.canvas" && [1, 2, 3, 4].includes(d.version));
  require(
    UUID.test(d.id) &&
      typeof d.title === "string" &&
      d.title.trim().length > 0 &&
      d.title.length <= 100,
  );
  require(
    Number.isInteger(d.width) &&
      Number.isInteger(d.height) &&
      finite(d.width, 256, 4096) &&
      finite(d.height, 256, 4096),
  );
  require(
    ["NONE", "SQUARES", "DOTS"].includes(d.grid) &&
      ["NONE", "AXES", "TRIANGLE", "RIGHT_TRIANGLE"].includes(d.template),
  );
  require(
    d.background == null ||
      (typeof d.background === "string" && d.background.length <= 900000),
  );
  require(Array.isArray(d.objects) && d.objects.length <= LIMITS.objects);
  let total = 0,
    formulas = 0;
  const ids = new Set();
  for (const o of d.objects) {
    require(o && UUID.test(o.id) && !ids.has(o.id) && KINDS.includes(o.kind));
    ids.add(o.id);
    require(
      Number.isInteger(o.color) && finite(o.color, -2147483648, 2147483647),
    );
    require(
      finite(o.width, 1, 32) &&
        finite(o.fontSize ?? 32, 16, 96) &&
        finite(o.rotation ?? 0, 0, 359.999999) &&
        finite(o.textBoxWidth ?? 0, 0, 4096),
    );
    require(
      Array.isArray(o.points) &&
        o.points.length >= 1 &&
        o.points.length <= LIMITS.points,
    );
    total += o.points.length;
    require(total <= LIMITS.total);
    require(["PEN", "TEXT"].includes(o.kind) || o.points.length === 2);
    for (const p of o.points)
      require(
        Array.isArray(p) &&
          p.length === 2 &&
          finite(p[0], 0, d.width) &&
          finite(p[1], 0, d.height),
      );
    require(o.text == null || typeof o.text === "string");
    if (o.kind === "TEXT")
      require(
        o.text?.trim() &&
          o.text.length <= 500 &&
          o.text.split("\n").length <= 8,
      );
    if (o.kind === "FORMULA") {
      formulas++;
      require(
        d.version >= 2 &&
          typeof o.text === "string" &&
          o.text.length <= 500 &&
          o.points[1][0] > o.points[0][0] &&
          o.points[1][1] > o.points[0][1],
      );
    }
    require(!advanced.includes(o.kind) || d.version >= 3);
    require(!(o.rotation || o.textBoxWidth) || d.version === 4);
  }
  require(formulas <= 30);
  return d;
}
export function encodeBoard(d) {
  const version = d.objects.some((o) => o.rotation || o.textBoxWidth)
    ? 4
    : d.objects.some((o) => advanced.includes(o.kind))
      ? 3
      : d.objects.some((o) => o.kind === "FORMULA")
        ? 2
        : 1;
  const result = JSON.stringify(validateBoard({ ...d, version }));
  require(new TextEncoder().encode(result).length <=
    LIMITS.bytes, "Доска слишком большая");
  return result;
}
export function decodeBoard(raw) {
  require(
    typeof raw === "string" &&
      new TextEncoder().encode(raw).length <= LIMITS.bytes,
  );
  let depth = 0,
    quoted = false,
    escaped = false;
  for (const c of raw) {
    if (quoted) {
      if (escaped) escaped = false;
      else if (c === "\\") escaped = true;
      else if (c === '"') quoted = false;
    } else if (c === '"') quoted = true;
    else if (c === "{" || c === "[") {
      depth++;
      require(depth <= 24);
    } else if (c === "}" || c === "]") {
      depth--;
      require(depth >= 0);
    }
  }
  require(depth === 0 && !quoted);
  return validateBoard(JSON.parse(raw));
}
export function encodeSharedObject(o) {
  const raw = encodeBoard({
    ...newBoard("o"),
    id: "00000000-0000-0000-0000-000000000000",
    grid: "NONE",
    objects: [o],
  });
  require(raw.length <= 120000, "Линия слишком большая");
  return raw;
}
export function decodeSharedObject(raw, id) {
  try {
    require(raw.length <= 120000);
    const d = decodeBoard(raw);
    require(
      d.width === 1200 &&
        d.height === 900 &&
        !d.background &&
        d.objects.length === 1 &&
        d.objects[0].id === id,
    );
    return d.objects[0];
  } catch {
    return null;
  }
}
export const signedColor = (hex, alpha = 255) =>
  (alpha << 24) | parseInt(hex.slice(1), 16) | 0;
export function cssColor(argb) {
  return `rgba(${(argb >>> 16) & 255},${(argb >>> 8) & 255},${argb & 255},${(argb >>> 24) / 255})`;
}
export function applyDelta(board, adds = [], removes = []) {
  const gone = new Set(removes);
  return validateBoard({
    ...board,
    version: 4,
    objects: [...board.objects.filter((o) => !gone.has(o.id)), ...adds],
  });
}
export function reverseDelta(objects, step) {
  const ids = new Set(objects.map((o) => o.id));
  if (
    step.adds.length === 1 &&
    step.removes.length === 1 &&
    !ids.has(step.adds[0].id)
  )
    return { adds: [], removes: [] };
  return {
    adds: step.removes.filter((o) => !ids.has(o.id)),
    removes: step.adds.filter((o) => ids.has(o.id)),
  };
}
export function drawBoard(canvas, board, draft = null, background = null) {
  const ctx = canvas.getContext("2d");
  const sx = canvas.width / board.width,
    sy = canvas.height / board.height;
  ctx.setTransform(sx, 0, 0, sy, 0, 0);
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, board.width, board.height);
  if (background) ctx.drawImage(background, 0, 0, board.width, board.height);
  ctx.strokeStyle = "#e5e9ef";
  ctx.fillStyle = "#d5dbe4";
  ctx.lineWidth = 1;
  if (board.grid !== "NONE")
    for (let x = 0; x < board.width; x += 24) {
      if (board.grid === "SQUARES") {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, board.height);
        ctx.stroke();
      } else
        for (let y = 0; y < board.height; y += 24) ctx.fillRect(x, y, 2, 2);
    }
  if (board.grid === "SQUARES")
    for (let y = 0; y < board.height; y += 24) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(board.width, y);
      ctx.stroke();
    }
  if (board.template === "AXES") {
    ctx.strokeStyle = "#94a3b8";
    ctx.beginPath();
    ctx.moveTo(0, board.height / 2);
    ctx.lineTo(board.width, board.height / 2);
    ctx.moveTo(board.width / 2, 0);
    ctx.lineTo(board.width / 2, board.height);
    ctx.stroke();
  } else if (board.template !== "NONE") {
    ctx.strokeStyle = "#94a3b8";
    ctx.beginPath();
    ctx.moveTo(board.width * 0.25, board.height * 0.75);
    ctx.lineTo(board.width * 0.75, board.height * 0.75);
    ctx.lineTo(
      board.template === "TRIANGLE" ? board.width * 0.5 : board.width * 0.25,
      board.height * 0.25,
    );
    ctx.closePath();
    ctx.stroke();
  }
  for (const o of [...board.objects, ...(draft ? [draft] : [])])
    drawObject(ctx, o);
  ctx.setTransform(1, 0, 0, 1, 0, 0);
}
export function drawObject(ctx, o) {
  const [a, b = a] = o.points;
  const [x, y] = a,
    [u, v] = b;
  const left = Math.min(x, u),
    top = Math.min(y, v),
    w = Math.abs(u - x),
    h = Math.abs(v - y);
  ctx.save();
  ctx.strokeStyle = cssColor(o.color);
  ctx.fillStyle = cssColor(o.color);
  ctx.lineWidth = o.width;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  if (o.rotation) {
    const [cx, cy] = objectBounds(o).center;
    ctx.translate(cx, cy);
    ctx.rotate((o.rotation * Math.PI) / 180);
    ctx.translate(-cx, -cy);
  }
  ctx.beginPath();
  switch (o.kind) {
    case "PEN":
      ctx.moveTo(x, y);
      for (const p of o.points.slice(1)) ctx.lineTo(...p);
      if (o.points.length === 1) {
        ctx.arc(x, y, o.width / 2, 0, Math.PI * 2);
        ctx.fill();
      } else ctx.stroke();
      break;
    case "LINE":
    case "ARROW":
      ctx.moveTo(x, y);
      ctx.lineTo(u, v);
      if (o.kind === "ARROW") {
        const angle = Math.atan2(v - y, u - x),
          len = 14 + o.width * 2;
        for (const d of [-0.5, 0.5]) {
          ctx.moveTo(u, v);
          ctx.lineTo(
            u - Math.cos(angle + d) * len,
            v - Math.sin(angle + d) * len,
          );
        }
      }
      ctx.stroke();
      break;
    case "RECTANGLE":
      ctx.strokeRect(left, top, w, h);
      break;
    case "ROUNDED_RECT":
      ctx.roundRect(left, top, w, h, Math.min(16, w / 2, h / 2));
      ctx.stroke();
      break;
    case "CIRCLE":
      ctx.arc(x, y, Math.hypot(u - x, v - y), 0, Math.PI * 2);
      ctx.stroke();
      break;
    case "ELLIPSE":
      ctx.ellipse(left + w / 2, top + h / 2, w / 2, h / 2, 0, 0, Math.PI * 2);
      ctx.stroke();
      break;
    case "TRIANGLE":
    case "DIAMOND": {
      const p =
        o.kind === "TRIANGLE"
          ? [
              [left + w / 2, top],
              [left + w, top + h],
              [left, top + h],
            ]
          : [
              [left + w / 2, top],
              [left + w, top + h / 2],
              [left + w / 2, top + h],
              [left, top + h / 2],
            ];
      ctx.moveTo(...p[0]);
      p.slice(1).forEach((q) => ctx.lineTo(...q));
      ctx.closePath();
      ctx.stroke();
      break;
    }
    case "TEXT":
    case "FORMULA":
      ctx.font = `${o.fontSize || 32}px system-ui`;
      ctx.textBaseline = "alphabetic";
      (o.text || "")
        .split("\n")
        .forEach((line, i) =>
          ctx.fillText(line, x, y + i * (o.fontSize || 32) * 1.3),
        );
      break;
  }
  ctx.restore();
}
export function hitObject(o, p, r = 14) {
  // Uses the same paths as the renderer; accounts for rotations, shape edges, text baseline.
  const c =
      typeof OffscreenCanvas === "function"
        ? new OffscreenCanvas(1, 1)
        : document.createElement("canvas"),
    ctx = c.getContext("2d");
  // Geometry hit testing for strokes, bounding box for text/formula.
  let q = p;
  if (o.rotation) {
    q = rotatePoint(p, objectBounds(o).center, -(o.rotation || 0));
  }
  const [a, b = a] = o.points;
  const minX = Math.min(a[0], b[0]),
    maxX = Math.max(a[0], b[0]),
    minY = Math.min(a[1], b[1]),
    maxY = Math.max(a[1], b[1]);
  if (["TEXT", "FORMULA"].includes(o.kind))
    return (
      q[0] >= minX - r &&
      q[0] <=
        Math.max(
          maxX,
          a[0] +
            (o.textBoxWidth ||
              Math.max(...o.text.split("\n").map((s) => s.length)) *
                (o.fontSize ?? 32) *
                0.65),
        ) +
          r &&
      q[1] >= minY - (o.fontSize ?? 32) - r &&
      q[1] <=
        Math.max(
          maxY,
          a[1] + o.text.split("\n").length * (o.fontSize ?? 32) * 1.3,
        ) +
          r
    );
  // Capture the actual stroked path without changing the drawing code.
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  const copy = { ...o, rotation: 0 };
  drawObject(ctx, copy);
  ctx.lineWidth = o.width + r * 2;
  if (o.kind === "RECTANGLE") {
    ctx.beginPath();
    ctx.rect(minX, minY, maxX - minX, maxY - minY);
  }
  return (
    ctx.isPointInStroke(q[0], q[1]) ||
    (o.kind === "PEN" &&
      o.points.length === 1 &&
      Math.hypot(q[0] - a[0], q[1] - a[1]) <= r + o.width / 2)
  );
}
