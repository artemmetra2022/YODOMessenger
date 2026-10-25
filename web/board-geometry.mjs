/** Geometry mirrors Android CanvasObjectTransforms. Transformations never change the stored object ID. */
export function rotatePoint([x, y], [cx, cy], degrees) {
  const t = (degrees * Math.PI) / 180,
    dx = x - cx,
    dy = y - cy;
  return [
    cx + dx * Math.cos(t) - dy * Math.sin(t),
    cy + dx * Math.sin(t) + dy * Math.cos(t),
  ];
}
export function objectBounds(o) {
  const a = o.points[0],
    b = o.points.at(-1),
    size = o.fontSize ?? 32;
  let pts = o.points;
  if (o.kind === "CIRCLE") {
    const r = Math.hypot(b[0] - a[0], b[1] - a[1]);
    pts = [
      [a[0] - r, a[1] - r],
      [a[0] + r, a[1] + r],
    ];
  }
  if (o.kind === "TEXT") {
    const lines = o.text.split("\n"),
      w =
        o.textBoxWidth > 0
          ? o.textBoxWidth
          : Math.max(1, ...lines.map((s) => s.length)) * size * 0.65;
    pts = [
      [a[0], a[1] - size],
      [a[0] + w, a[1] + size * (lines.length - 1) * 1.25 + size * 0.3],
    ];
  }
  if (o.kind === "ARROW") {
    const angle = Math.atan2(b[1] - a[1], b[0] - a[0]),
      len = 14 + o.width * 2;
    pts = [
      ...pts,
      ...[-0.5, 0.5].map((d) => [
        b[0] - Math.cos(angle + d) * len,
        b[1] - Math.sin(angle + d) * len,
      ]),
    ];
  }
  const pad = ["TEXT", "FORMULA"].includes(o.kind) ? 0 : o.width / 2;
  const xs = pts.map((p) => p[0]),
    ys = pts.map((p) => p[1]);
  const left = Math.min(...xs) - pad,
    top = Math.min(...ys) - pad,
    right = Math.max(...xs) + pad,
    bottom = Math.max(...ys) + pad;
  return {
    left,
    top,
    right,
    bottom,
    width: right - left,
    height: bottom - top,
    center: [(left + right) / 2, (top + bottom) / 2],
  };
}
export function rotatedBounds(o) {
  const b = objectBounds(o),
    ps = [
      [b.left, b.top],
      [b.right, b.top],
      [b.right, b.bottom],
      [b.left, b.bottom],
    ].map((p) => rotatePoint(p, b.center, o.rotation || 0)),
    xs = ps.map((p) => p[0]),
    ys = ps.map((p) => p[1]);
  return {
    left: Math.min(...xs),
    top: Math.min(...ys),
    right: Math.max(...xs),
    bottom: Math.max(...ys),
    width: Math.max(...xs) - Math.min(...xs),
    height: Math.max(...ys) - Math.min(...ys),
  };
}
export function fitObject(o, doc) {
  const b = rotatedBounds(o);
  if (b.width > doc.width + 0.01 || b.height > doc.height + 0.01)
    throw new Error("Объект не помещается на доске");
  const dx =
      b.left < 0 ? -b.left : b.right > doc.width ? doc.width - b.right : 0,
    dy = b.top < 0 ? -b.top : b.bottom > doc.height ? doc.height - b.bottom : 0;
  const pts = o.points.map(([x, y]) => [x + dx, y + dy]);
  if (
    pts.some(
      ([x, y]) =>
        x < -0.001 ||
        x > doc.width + 0.001 ||
        y < -0.001 ||
        y > doc.height + 0.001,
    )
  )
    throw new Error("Контрольные точки выходят за границы доски");
  return {
    ...o,
    points: pts.map(([x, y]) => [
      Math.max(0, Math.min(doc.width, x)),
      Math.max(0, Math.min(doc.height, y)),
    ]),
  };
}
export function moveObject(o, dx, dy, doc) {
  if (!Number.isFinite(dx) || !Number.isFinite(dy))
    throw new Error("Некорректное перемещение");
  return fitObject(
    { ...o, points: o.points.map(([x, y]) => [x + dx, y + dy]) },
    doc,
  );
}
export function scaleObject(o, factor, doc) {
  if (!Number.isFinite(factor) || factor <= 0)
    throw new Error("Некорректный масштаб");
  const font = ["TEXT", "FORMULA"].includes(o.kind),
    size = o.fontSize ?? 32,
    lo = Math.max(1 / o.width, font ? 16 / size : 0.01),
    hi = Math.min(32 / o.width, font ? 96 / size : 100),
    k = Math.max(lo, Math.min(hi, factor)),
    c = objectBounds(o).center;
  return fitObject(
    {
      ...o,
      points: o.points.map(([x, y]) => [
        c[0] + (x - c[0]) * k,
        c[1] + (y - c[1]) * k,
      ]),
      width: Math.max(1, Math.min(32, o.width * k)),
      fontSize: font ? Math.max(16, Math.min(96, size * k)) : size,
      ...(o.textBoxWidth > 0 ? { textBoxWidth: o.textBoxWidth * k } : {}),
    },
    doc,
  );
}
export function rotateObject(o, degrees, doc) {
  if (!Number.isFinite(degrees)) throw new Error("Некорректный угол");
  return fitObject({ ...o, rotation: ((degrees % 360) + 360) % 360 }, doc);
}
export function selectionHit(o, p, margin = 0) {
  const b = objectBounds(o),
    q = rotatePoint(p, b.center, -(o.rotation || 0));
  return (
    q[0] >= b.left - margin &&
    q[0] <= b.right + margin &&
    q[1] >= b.top - margin &&
    q[1] <= b.bottom + margin
  );
}
export const normalizeZoom = (z) =>
  Math.max(0.5, Math.min(6, Number.isFinite(z) ? z : 1));
