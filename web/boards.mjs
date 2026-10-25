import {
  objectBounds,
  rotatedBounds,
  moveObject,
  scaleObject,
  rotateObject,
  selectionHit,
  normalizeZoom,
} from "./board-geometry.mjs";
import { boardShortcut } from "./shortcuts.mjs";
import {
  newBoard,
  decodeBoard,
  encodeBoard,
  validateBoard,
  encodeSharedObject,
  decodeSharedObject,
  drawBoard,
  hitObject,
  signedColor,
  applyDelta,
  reverseDelta,
} from "./board-model.mjs";
import {
  saveLocalBoard,
  listLocalBoards,
  deleteLocalBoard,
} from "./board-store.mjs";
export const boardMarkup = `
<div id="board-modal" class="board-overlay hidden" role="dialog" aria-modal="true" aria-labelledby="board-heading">
 <section class="board-shell">
  <header class="board-header"><div><h2 id="board-heading">Мои доски</h2><p id="board-subtitle">Сохранены только в этом браузере</p></div><button type="button" id="board-close" aria-label="Закрыть доски">×</button></header>
  <div id="board-library"><div class="board-library-actions"><button type="button" id="board-new">＋ Новая доска</button><button type="button" id="board-import">Импорт JSON</button><input type="file" id="board-import-file" accept=".json,application/json" hidden></div><div id="board-list"></div></div>
  <div id="board-editor" class="hidden">
   <div class="board-toolbar" aria-label="Инструменты рисования">
    <label>Инструмент<select id="board-tool"><option value="SELECT">Выделение</option><option value="HAND">Перемещение холста</option><option value="PEN" selected>Карандаш</option><option value="HIGHLIGHTER">Маркер</option><option value="LINE">Линия</option><option value="ARROW">Стрелка</option><option value="RECTANGLE">Прямоугольник</option><option value="ROUNDED_RECT">Скруглённый</option><option value="CIRCLE">Круг</option><option value="ELLIPSE">Эллипс</option><option value="TRIANGLE">Треугольник</option><option value="DIAMOND">Ромб</option><option value="TEXT">Текст</option><option value="ERASER">Ластик</option></select></label>
    <label>Цвет<input type="color" id="board-color" value="#1e293b"></label>
    <label>Толщина<select id="board-width"><option>2</option><option selected>4</option><option>8</option><option>12</option><option>24</option></select></label>
    <label>Сетка<select id="board-grid"><option value="SQUARES">Клетка</option><option value="DOTS">Точки</option><option value="NONE">Без сетки</option></select></label>
    <button type="button" id="board-undo" aria-label="Отменить">Отмена</button><button type="button" id="board-redo" aria-label="Повторить">Повтор</button>
   </div>
   <div class="board-view-tools"><button type="button" id="board-zoom-out" aria-label="Отдалить">−</button><output id="board-zoom-label">100%</output><button type="button" id="board-zoom-in" aria-label="Приблизить">+</button><button type="button" id="board-zoom-reset">Вписать</button><button type="button" id="board-help">Клавиши</button><span>H — перемещение · Ctrl/⌘ + колесо — масштаб</span></div>
   <div id="board-selection" class="board-selection hidden" aria-label="Выделенный объект"><span id="board-selection-label">Объект выделен</span><button type="button" id="board-object-smaller">Уменьшить</button><button type="button" id="board-object-larger">Увеличить</button><label>Угол<input id="board-object-angle" type="number" min="-360" max="360" step="15" value="0" aria-label="Угол поворота в градусах"></label><button type="button" id="board-object-edit">Изменить текст</button><button type="button" id="board-object-copy">Копия</button><button type="button" id="board-object-delete">Удалить</button></div>
   <div class="board-paper-wrap" id="board-viewport"><div id="board-stage"><canvas id="board-canvas" width="1200" height="900" tabindex="0" aria-label="Доска для рисования мышью, пером или касанием"></canvas></div></div>
   <div class="board-footer"><span id="board-count"></span><div><button type="button" id="board-png">Скачать PNG</button><button type="button" id="board-json">Скачать JSON</button><button type="button" id="board-send">Отправить в чат</button><button type="button" id="board-library-back">Мои доски</button></div></div>
  </div>
  <p id="board-status" role="status" aria-live="polite"></p>
 </section>
</div>`;
const $ = (id) => document.getElementById(id);
function download(data, type, name) {
  const url = URL.createObjectURL(new Blob([data], { type })),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function initBoards(api) {
  document.body.insertAdjacentHTML("beforeend", boardMarkup);
  let owner = null,
    chatId = null,
    chat = null,
    board = null,
    mode = "local",
    meta = null,
    remote = [],
    stop = null,
    epoch = 0,
    busy = false,
    history = [],
    future = [],
    gesture = null,
    draft = null,
    background = null,
    previousFocus = null,
    opening = 0,
    selectedId = null,
    transformPreview = null,
    zoom = 1,
    pointers = new Map(),
    pinch = null;
  const modal = $("board-modal"),
    canvas = $("board-canvas"),
    viewport = $("board-viewport"),
    stage = $("board-stage");
  const say = (s, error = false) => {
    $("board-status").textContent = s;
    $("board-status").classList.toggle("board-error", error);
  };
  function resetView() {
    selectedId = null;
    transformPreview = null;
    zoom = 1;
    pointers.clear();
    pinch = null;
    viewport.scrollLeft = 0;
    viewport.scrollTop = 0;
  }
  function layoutView() {
    if (!board || !viewport.clientWidth) return;
    const w = Math.max(1, viewport.clientWidth - 24),
      h = Math.max(1, viewport.clientHeight - 24),
      fit = Math.min(w / board.width, h / board.height),
      cw = board.width * fit * zoom,
      ch = board.height * fit * zoom;
    stage.style.width = Math.max(w, cw) + "px";
    stage.style.height = Math.max(h, ch) + "px";
    canvas.style.width = cw + "px";
    canvas.style.height = ch + "px";
    canvas.style.left = Math.max(0, (w - cw) / 2) + "px";
    canvas.style.top = Math.max(0, (h - ch) / 2) + "px";
    $("board-zoom-label").textContent = Math.round(zoom * 100) + "%";
    $("board-zoom-out").disabled = zoom <= 0.5;
    $("board-zoom-in").disabled = zoom >= 6;
  }
  function setZoom(next, anchor) {
    if (!board) return;
    const r = canvas.getBoundingClientRect(),
      v = viewport.getBoundingClientRect(),
      a = anchor || [
        v.left + viewport.clientWidth / 2,
        v.top + viewport.clientHeight / 2,
      ],
      px = (a[0] - r.left) / r.width,
      py = (a[1] - r.top) / r.height;
    zoom = normalizeZoom(next);
    layoutView();
    const after = canvas.getBoundingClientRect();
    viewport.scrollLeft += after.left + px * after.width - a[0];
    viewport.scrollTop += after.top + py * after.height - a[1];
    paint();
  }
  $("board-zoom-out").onclick = () => setZoom(zoom / 1.25);
  $("board-zoom-in").onclick = () => setZoom(zoom * 1.25);
  $("board-zoom-reset").onclick = () => {
    zoom = 1;
    layoutView();
    viewport.scrollLeft = 0;
    viewport.scrollTop = 0;
    paint();
  };
  $("board-help").onclick = () => api.showShortcuts?.();
  viewport.addEventListener(
    "wheel",
    (e) => {
      if ((e.ctrlKey || e.metaKey) && board) {
        e.preventDefault();
        if (!gesture)
          setZoom(zoom * Math.exp(-e.deltaY * 0.002), [e.clientX, e.clientY]);
      }
    },
    { passive: false },
  );
  new ResizeObserver(() => {
    if (!modal.classList.contains("hidden")) layoutView();
  }).observe(viewport);
  function handleBoardKey(e) {
    return safe(async (e) => {
      if (
        e.defaultPrevented ||
        !board ||
        $("board-editor").classList.contains("hidden")
      )
        return;
      const action = boardShortcut(e);
      if (!action) return;
      if (gesture || pinch) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      e.preventDefault();
      e.stopPropagation();
      if (action === "zoomIn") setZoom(zoom * 1.25);
      else if (action === "zoomOut") setZoom(zoom / 1.25);
      else if (action === "zoomReset") $("board-zoom-reset").click();
      else if (action === "undo") $("board-undo").click();
      else if (action === "redo") $("board-redo").click();
      else if (action === "delete") $("board-object-delete").click();
      else if (["left", "right", "up", "down"].includes(action)) {
        const step = e.shiftKey ? 10 : 1;
        await transformSelection((o) =>
          moveObject(
            o,
            action === "left" ? -step : action === "right" ? step : 0,
            action === "up" ? -step : action === "down" ? step : 0,
            board,
          ),
        );
      } else if (writable() && !gesture) {
        $("board-tool").value = action;
        canvas.focus({ preventScroll: true });
      }
    })(e);
  }
  modal.addEventListener("keydown", handleBoardKey);
  function paint() {
    if (!board) return;
    if (canvas.width !== board.width || canvas.height !== board.height) {
      canvas.width = board.width;
      canvas.height = board.height;
    }
    canvas.style.aspectRatio = board.width + " / " + board.height;
    canvas.style.setProperty("--board-aspect", board.width / board.height);
    const displayed =
      transformPreview && selectedId
        ? {
            ...board,
            objects: board.objects.map((o) =>
              o.id === selectedId ? transformPreview : o,
            ),
          }
        : board;
    drawBoard(canvas, displayed, draft, background);
    const selected = displayed.objects.find((o) => o.id === selectedId);
    if (!selected) {
      selectedId = null;
      transformPreview = null;
    }
    $("board-selection").classList.remove("hidden");
    $("board-selection-label").textContent = selected
      ? "Объект выделен"
      : "Выделите объект (V)";
    for (const id of [
      "board-object-smaller",
      "board-object-larger",
      "board-object-angle",
      "board-object-copy",
      "board-object-delete",
      "board-object-edit",
    ])
      $(id).disabled = busy || !!gesture || !selected || !writable();
    $("board-object-edit").classList.toggle(
      "hidden",
      selected?.kind !== "TEXT",
    );
    if (selected) {
      const b = rotatedBounds(selected),
        ctx = canvas.getContext("2d"),
        unit =
          board.width / (canvas.getBoundingClientRect().width || board.width);
      ctx.save();
      ctx.strokeStyle = "#1473e6";
      ctx.fillStyle = "#ffffff";
      ctx.lineWidth = 2 * unit;
      ctx.setLineDash([6 * unit, 4 * unit]);
      ctx.strokeRect(b.left, b.top, b.width, b.height);
      ctx.setLineDash([]);
      ctx.fillRect(
        b.right - 6 * unit,
        b.bottom - 6 * unit,
        12 * unit,
        12 * unit,
      );
      ctx.strokeRect(
        b.right - 6 * unit,
        b.bottom - 6 * unit,
        12 * unit,
        12 * unit,
      );
      ctx.restore();
      if (document.activeElement !== $("board-object-angle"))
        $("board-object-angle").value = Math.round(selected.rotation || 0);
      $("board-object-edit").classList.toggle(
        "hidden",
        selected.kind !== "TEXT",
      );
    }
    layoutView();
    $("board-count").textContent = `${board.objects.length} / 300 объектов`;
    $("board-grid").value = board.grid;
    $("board-undo").disabled = busy || !!gesture || !history.length;
    $("board-redo").disabled = busy || !!gesture || !future.length;
  }
  function controls() {
    const disabled =
      busy || (mode === "shared" && (!meta || meta.deleted || meta.clearing));
    for (const id of [
      "board-tool",
      "board-color",
      "board-width",
      "board-grid",
      "board-object-smaller",
      "board-object-larger",
      "board-object-angle",
      "board-object-copy",
      "board-object-delete",
      "board-object-edit",
    ])
      $(id).disabled = disabled;
    paint();
  }
  function show() {
    previousFocus = document.activeElement;
    modal.classList.remove("hidden");
    $("board-close").focus();
  }
  function close() {
    if (busy) {
      say("Дождитесь завершения сохранения.");
      return;
    }
    opening++;
    pointers.clear();
    pinch = null;
    gesture = null;
    draft = null;
    modal.classList.add("hidden");
    previousFocus?.focus();
  }
  $("board-close").onclick = close;
  modal.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      e.preventDefault();
      if (gesture || draft || transformPreview) {
        gesture = null;
        draft = null;
        transformPreview = null;
        pointers.clear();
        pinch = null;
        paint();
      } else if (selectedId) {
        selectedId = null;
        paint();
      } else close();
      e.stopPropagation();
    }
    if (e.key === "Tab") {
      const nodes = [...modal.querySelectorAll("button,select,input")].filter(
        (n) => !n.disabled && n.getClientRects().length,
      );
      if (!nodes.length) return;
      const a = nodes[0],
        b = nodes.at(-1);
      if (e.shiftKey && document.activeElement === a) {
        e.preventDefault();
        b.focus();
      } else if (!e.shiftKey && document.activeElement === b) {
        e.preventDefault();
        a.focus();
      }
    }
  });
  const safe =
    (fn) =>
    async (...args) => {
      try {
        await fn(...args);
      } catch (e) {
        say(e.message || "Не удалось выполнить действие", true);
      }
    };
  async function loadBackground() {
    background = null;
    if (!board?.background) return;
    const current = board;
    const img = new Image();
    await new Promise((resolve, reject) => {
      img.onload = resolve;
      img.onerror = () => reject(new Error("Не удалось открыть фон доски"));
      img.src = "data:image/png;base64," + board.background;
    });
    if (board === current) {
      background = img;
      paint();
    }
  }
  async function editLocal(d) {
    mode = "local";
    resetView();
    meta = null;
    board = decodeBoard(encodeBoard(d));
    history = [];
    future = [];
    gesture = null;
    draft = null;
    $("board-heading").textContent = board.title;
    $("board-subtitle").textContent =
      "Личная доска · автосохранение в этом браузере";
    $("board-library").classList.add("hidden");
    $("board-editor").classList.remove("hidden");
    $("board-send").disabled = !chatId || !api.canSend();
    $("board-library-back").classList.remove("hidden");
    say(
      "Доски не синхронизируются с телефоном автоматически. Для переноса используйте JSON.",
    );
    controls();
    await loadBackground();
  }
  async function library() {
    if (busy) return;
    mode = "local";
    resetView();
    board = null;
    background = null;
    gesture = null;
    draft = null;
    history = [];
    future = [];
    $("board-heading").textContent = "Мои доски";
    $("board-subtitle").textContent =
      "Сохранены только в этом браузере · отдельно для аккаунта";
    $("board-editor").classList.add("hidden");
    $("board-library").classList.remove("hidden");
    say("Загрузка…");
    const token = ++opening,
      uid = owner;
    const rows = await listLocalBoards(uid);
    if (token !== opening || uid !== owner) return;
    const list = $("board-list");
    list.replaceChildren();
    for (const item of rows) {
      const row = document.createElement("div");
      row.className = "board-library-row";
      const open = document.createElement("button");
      open.textContent = item.error ? "Повреждённая доска" : item.board.title;
      open.disabled = !!item.error;
      open.onclick = safe(() => editLocal(item.board));
      const info = document.createElement("span");
      info.textContent = item.error
        ? "Файл недоступен"
        : `${item.board.objects.length} объектов`;
      const remove = document.createElement("button");
      remove.textContent = "Удалить";
      remove.onclick = safe(async () => {
        if (confirm("Удалить доску из этого браузера?")) {
          await deleteLocalBoard(owner, item.board?.id || item.id);
          await library();
        }
      });
      row.append(open, info, remove);
      list.append(row);
    }
    if (!rows.length) {
      const empty = document.createElement("p");
      empty.className = "board-empty";
      empty.textContent = "Здесь будут ваши рисунки. Создайте первую доску.";
      list.append(empty);
    }
    say(
      "Личные доски хранятся зашифрованно в браузере. Очистка данных сайта удалит их; сохраняйте JSON-копии.",
    );
  }
  $("board-new").onclick = safe(async () => {
    const title = prompt("Название доски", "Новая доска");
    if (!title?.trim()) return;
    const d = newBoard(title.trim().slice(0, 100));
    await saveLocalBoard(owner, d);
    await editLocal(d);
  });
  $("board-import").onclick = () => $("board-import-file").click();
  $("board-import-file").onchange = safe(async (e) => {
    const file = e.target.files[0];
    e.target.value = "";
    if (!file) return;
    if (file.size > 5000000) throw new Error("Файл больше 5 МБ");
    const d = decodeBoard(await file.text());
    d.id = crypto.randomUUID();
    await saveLocalBoard(owner, d);
    await editLocal(d);
  });
  $("board-library-back").onclick = safe(library);
  async function commit(adds, removed, record = true) {
    if (busy || !board) return;
    const before = board,
      uid = owner,
      session = epoch,
      generation = meta?.generation || 0;
    const next = applyDelta(
      before,
      adds,
      removed.map((o) => o.id),
    );
    busy = true;
    controls();
    try {
      if (mode === "shared") {
        if (!meta || meta.deleted || meta.clearing)
          throw new Error("Общая доска недоступна");
        await api.changeShared(chatId, meta.generation || 0, adds, removed);
      } else {
        await saveLocalBoard(uid, next);
        if (uid === owner && session === epoch) board = next;
      }
      if (
        uid !== owner ||
        session !== epoch ||
        (mode === "shared" && (meta?.generation || 0) !== generation)
      )
        return false;
      if (record) {
        history.push({ adds, removes: removed });
        if (history.length > 60) history.shift();
        future = [];
      }
      say(
        mode === "shared"
          ? "Изменение сохранено в общей доске. Содержимое не защищено сквозным шифрованием."
          : "Сохранено в этом браузере",
      );
      return true;
    } finally {
      busy = false;
      controls();
    }
  }
  $("board-undo").onclick = safe(async () => {
    if (busy || !history.length) return;
    const step = history.at(-1),
      reverse = reverseDelta(board.objects, step);
    if (await commit(reverse.adds, reverse.removes, false)) {
      history.pop();
      future.push(step);
      selectedId = reverse.adds.at(-1)?.id || null;
      paint();
    }
  });
  $("board-redo").onclick = safe(async () => {
    if (busy || !future.length) return;
    const step = future.at(-1);
    if (
      step.adds.length === 1 &&
      step.removes.length === 1 &&
      !board.objects.some((o) => o.id === step.removes[0].id)
    ) {
      future.pop();
      say("Объект уже изменён другим участником. Повтор пропущен.", true);
      paint();
      return;
    }
    const ids = new Set(board.objects.map((o) => o.id)),
      adds = step.adds.filter((o) => !ids.has(o.id)),
      removes = step.removes.filter((o) => ids.has(o.id));
    if (await commit(adds, removes, false)) {
      future.pop();
      history.push(step);
      selectedId = adds.at(-1)?.id || null;
      paint();
    }
  });
  $("board-grid").onchange = safe(async (e) => {
    const grid = e.target.value;
    if (busy) return;
    const session = epoch,
      uid = owner;
    busy = true;
    controls();
    try {
      if (mode === "shared") await api.setSharedGrid(chatId, grid);
      else {
        const next = { ...board, grid };
        await saveLocalBoard(uid, next);
        if (session === epoch && uid === owner) board = next;
      }
    } finally {
      busy = false;
      controls();
    }
  });
  function point(e) {
    const r = canvas.getBoundingClientRect();
    return [
      Math.max(
        0,
        Math.min(board.width, ((e.clientX - r.left) / r.width) * board.width),
      ),
      Math.max(
        0,
        Math.min(board.height, ((e.clientY - r.top) / r.height) * board.height),
      ),
    ];
  }
  function writable() {
    return (
      !busy &&
      board &&
      !(mode === "shared" && (!meta || meta.deleted || meta.clearing))
    );
  }
  canvas.onpointerdown = safe(async (e) => {
    if (e.button !== 0 || !board) return;
    canvas.focus({ preventScroll: true });
    pointers.set(e.pointerId, [e.clientX, e.clientY]);
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      gesture = null;
      draft = null;
      transformPreview = null;
      pinch = { distance: Math.hypot(a[0] - b[0], a[1] - b[1]), zoom };
      paint();
      return;
    }
    if (gesture) return;
    const tool = $("board-tool").value;
    if (tool === "HAND") {
      e.preventDefault();
      gesture = {
        type: "pan",
        pointer: e.pointerId,
        x: e.clientX,
        y: e.clientY,
        left: viewport.scrollLeft,
        top: viewport.scrollTop,
      };
      canvas.setPointerCapture(e.pointerId);
      return;
    }
    if (!writable()) return;
    e.preventDefault();
    const p = point(e);
    if (tool === "SELECT") {
      const current = board.objects.find((o) => o.id === selectedId),
        tol = (12 * board.width) / canvas.getBoundingClientRect().width;
      const bounds = current && rotatedBounds(current);
      if (
        current &&
        Math.hypot(p[0] - bounds.right, p[1] - bounds.bottom) < tol
      ) {
        gesture = {
          type: "resize",
          pointer: e.pointerId,
          start: p,
          original: current,
          center: objectBounds(current).center,
        };
      } else {
        const hit = [...board.objects]
          .reverse()
          .find((o) => selectionHit(o, p, tol / 3));
        selectedId = hit?.id || null;
        if (hit)
          gesture = {
            type: "move",
            pointer: e.pointerId,
            start: p,
            original: hit,
          };
      }
      if (gesture) canvas.setPointerCapture(e.pointerId);
      paint();
      return;
    }
    selectedId = null;
    if (tool === "TEXT") {
      const text = prompt("Текст (до 500 символов)");
      if (text?.trim())
        await commit(
          [
            {
              id: crypto.randomUUID(),
              kind: "TEXT",
              points: [p],
              color: signedColor($("board-color").value),
              width: 4,
              text: text.trim(),
              fontSize: 32,
            },
          ],
          [],
        );
      return;
    }
    if (tool === "ERASER") {
      const hit = [...board.objects].reverse().find((o) => hitObject(o, p));
      if (hit) await commit([], [hit]);
      return;
    }
    if (board.objects.length >= 300)
      throw new Error("На доске уже 300 объектов");
    gesture = { type: "draw", pointer: e.pointerId, tool };
    canvas.setPointerCapture(e.pointerId);
    draft = {
      id: crypto.randomUUID(),
      kind: tool === "HIGHLIGHTER" ? "PEN" : tool,
      points: ["PEN", "HIGHLIGHTER"].includes(tool) ? [p] : [p, p],
      color: signedColor(
        $("board-color").value,
        tool === "HIGHLIGHTER" ? 80 : 255,
      ),
      width: Number($("board-width").value),
      text: "",
      fontSize: 32,
    };
    paint();
  });
  canvas.onpointermove = (e) => {
    if (pointers.has(e.pointerId))
      pointers.set(e.pointerId, [e.clientX, e.clientY]);
    if (pinch && pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      if (pinch.distance > 0)
        setZoom(
          (pinch.zoom * Math.hypot(a[0] - b[0], a[1] - b[1])) / pinch.distance,
          [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2],
        );
      return;
    }
    if (!gesture || gesture.pointer !== e.pointerId) return;
    if (gesture.type === "pan") {
      viewport.scrollLeft = gesture.left - (e.clientX - gesture.x);
      viewport.scrollTop = gesture.top - (e.clientY - gesture.y);
      return;
    }
    const p = point(e);
    if (["move", "resize"].includes(gesture.type)) {
      try {
        transformPreview =
          gesture.type === "move"
            ? moveObject(
                gesture.original,
                p[0] - gesture.start[0],
                p[1] - gesture.start[1],
                board,
              )
            : scaleObject(
                gesture.original,
                Math.hypot(p[0] - gesture.center[0], p[1] - gesture.center[1]) /
                  Math.max(
                    0.01,
                    Math.hypot(
                      gesture.start[0] - gesture.center[0],
                      gesture.start[1] - gesture.center[1],
                    ),
                  ),
                board,
              );
        paint();
      } catch {
        /* Retain the last in-bounds preview. */
      }
      return;
    }
    if (!draft) return;
    if (draft.kind === "PEN") {
      if (
        draft.points.length < 2048 &&
        Math.hypot(
          p[0] - draft.points.at(-1)[0],
          p[1] - draft.points.at(-1)[1],
        ) >= 1
      )
        draft.points.push(p);
    } else draft.points[1] = p;
    paint();
  };
  canvas.onpointerup = safe(async (e) => {
    pointers.delete(e.pointerId);
    if (pinch) {
      if (pointers.size < 2) pinch = null;
      return;
    }
    if (!gesture || gesture.pointer !== e.pointerId) return;
    const g = gesture,
      object = draft,
      transformed = transformPreview;
    gesture = null;
    draft = null;
    transformPreview = null;
    if (canvas.hasPointerCapture(e.pointerId))
      canvas.releasePointerCapture(e.pointerId);
    paint();
    if (g.type === "draw" && object) await commit([object], []);
    else if (
      ["move", "resize"].includes(g.type) &&
      transformed &&
      JSON.stringify(transformed) !== JSON.stringify(g.original)
    )
      await replaceSelected(g.original, transformed);
  });
  const cancelPointer = (e) => {
    pointers.delete(e.pointerId);
    pinch = null;
    gesture = null;
    draft = null;
    transformPreview = null;
    paint();
  };
  canvas.onpointercancel = cancelPointer;
  canvas.onlostpointercapture = (e) => {
    if (gesture?.pointer === e.pointerId) cancelPointer(e);
  };
  async function replaceSelected(original, changed, copy = false) {
    const next = { ...changed, id: crypto.randomUUID() };
    if (await commit([next], copy ? [] : [original])) {
      selectedId = next.id;
      paint();
    }
  }
  async function transformSelection(fn) {
    if (!writable() || gesture) return;
    const o = board.objects.find((o) => o.id === selectedId);
    if (o) await replaceSelected(o, fn(o));
  }
  $("board-object-smaller").onclick = safe(() =>
    transformSelection((o) => scaleObject(o, 0.9, board)),
  );
  $("board-object-larger").onclick = safe(() =>
    transformSelection((o) => scaleObject(o, 1.1, board)),
  );
  $("board-object-angle").onchange = safe((e) =>
    transformSelection((o) => rotateObject(o, Number(e.target.value), board)),
  );
  $("board-object-copy").onclick = safe(async () => {
    if (!writable() || gesture) return;
    const o = board.objects.find((o) => o.id === selectedId);
    if (o) await replaceSelected(o, moveObject(o, 16, 16, board), true);
  });
  $("board-object-delete").onclick = safe(async () => {
    if (!writable() || gesture) return;
    const o = board.objects.find((o) => o.id === selectedId);
    if (o && (await commit([], [o]))) {
      selectedId = null;
      paint();
    }
  });
  $("board-object-edit").onclick = safe(async () => {
    if (!writable() || gesture) return;
    const o = board.objects.find((o) => o.id === selectedId);
    if (o?.kind !== "TEXT") return;
    const text = prompt("Текст (до 500 символов)", o.text);
    if (text?.trim())
      await replaceSelected(
        o,
        moveObject({ ...o, text: text.trim() }, 0, 0, board),
      );
  });
  function exportCanvas() {
    const c = document.createElement("canvas");
    c.width = board.width;
    c.height = board.height;
    drawBoard(c, board, null, background);
    return c;
  }
  $("board-png").onclick = safe(async () => {
    paint();
    const blob = await new Promise((r) =>
      exportCanvas().toBlob(r, "image/png"),
    );
    if (!blob) throw new Error("Не удалось создать PNG");
    download(blob, "image/png", board.title + ".png");
  });
  $("board-json").onclick = safe(() =>
    download(encodeBoard(board), "application/json", board.title + ".json"),
  );
  $("board-send").onclick = safe(async () => {
    if (!board || !chatId || !api.canSend())
      throw new Error("Откройте чат, в который можно отправлять сообщения");
    busy = true;
    controls();
    try {
      await api.sendImage(exportCanvas());
      say("Рисунок отправлен в чат как изображение.");
    } finally {
      busy = false;
      controls();
    }
  });
  function sharedDocument() {
    const g = meta?.generation || 0;
    return {
      ...newBoard(meta?.title || "Общая доска чата"),
      grid: meta?.grid || "SQUARES",
      objects: meta?.clearing
        ? []
        : remote
            .filter((r) => (r.generation || 0) === g)
            .map((r) => decodeSharedObject(r.json, r.id))
            .filter(Boolean)
            .slice(0, 300),
    };
  }
  function updateShared() {
    const preview = $("shared-board-preview"),
      exists = meta && !meta.deleted;
    preview.classList.toggle("hidden", !exists);
    if (exists) {
      const d = sharedDocument();
      drawBoard($("shared-board-thumb"), d);
      $("shared-board-label").textContent = meta.clearing
        ? "Доска очищается…"
        : `${meta.title} · ${d.objects.length} объектов`;
    }
    if (mode === "shared" && !modal.classList.contains("hidden")) {
      if (!exists) {
        board = null;
        gesture = null;
        draft = null;
        $("board-editor").classList.add("hidden");
        say(
          "Доска удалена. Закройте окно; создать её заново можно только отдельным действием.",
          true,
        );
        return;
      }
      const generation = meta.generation || 0;
      if (board?._generation !== generation) {
        history = [];
        future = [];
        gesture = null;
        draft = null;
        selectedId = null;
        transformPreview = null;
        pointers.clear();
        pinch = null;
      }
      if (
        selectedId &&
        !sharedDocument().objects.some((o) => o.id === selectedId)
      ) {
        selectedId = null;
        gesture = null;
        transformPreview = null;
      }
      board = { ...sharedDocument(), _generation: generation };
      controls();
      say(
        meta.clearing
          ? "Доска очищается другим участником. Рисование временно недоступно."
          : "Общая доска: изменения видны участникам чата. Сквозного шифрования нет.",
      );
    }
  }
  async function openShared() {
    if (!chatId) return;
    if (busy) return;
    if (!meta || meta.deleted) {
      if (
        !confirm(
          "Создать общую доску чата? Её содержимое увидят участники и сервер Firebase; сквозного шифрования нет.",
        )
      )
        return;
      const token = epoch;
      const created = await api.createShared(chatId);
      if (token !== epoch) return;
      meta = created;
      updateShared();
    }
    if (!meta || meta.deleted) {
      say("Дождитесь загрузки общей доски.");
      return;
    }
    mode = "shared";
    resetView();
    board = { ...sharedDocument(), _generation: meta.generation || 0 };
    background = null;
    history = [];
    future = [];
    gesture = null;
    draft = null;
    $("board-heading").textContent = board.title;
    $("board-subtitle").textContent = "Общая доска чата · 1200 × 900";
    $("board-library").classList.add("hidden");
    $("board-editor").classList.remove("hidden");
    $("board-library-back").classList.add("hidden");
    $("board-send").disabled = !api.canSend();
    show();
    controls();
    say("Изменения синхронизируются с Android. Сквозного шифрования нет.");
  }
  $("btn-boards").onclick = safe(async () => {
    show();
    await library();
  });
  $("btn-shared-board").onclick = safe(openShared);
  $("shared-board-preview").onclick = safe(openShared);
  return {
    setContext(uid, id, c) {
      epoch++;
      opening++;
      resetView();
      stop?.();
      stop = null;
      owner = uid;
      chatId = id;
      chat = c;
      meta = null;
      remote = [];
      board = null;
      gesture = null;
      draft = null;
      history = [];
      future = [];
      modal.classList.add("hidden");
      $("shared-board-preview").classList.add("hidden");
      const allowed =
        !!uid &&
        !!id &&
        ["PRIVATE", "GROUP"].includes(c?.type) &&
        !id.startsWith("saved_") &&
        !id.startsWith("support_") &&
        (c.participantIds || []).includes(uid);
      $("btn-shared-board").classList.toggle("hidden", !allowed);
      if (allowed) {
        const token = epoch;
        stop = api.watchShared(
          id,
          (m) => {
            if (token !== epoch) return;
            meta = m;
            updateShared();
          },
          (r) => {
            if (token !== epoch) return;
            remote = r;
            updateShared();
          },
          (err) => {
            if (token !== epoch) return;
            meta = null;
            remote = [];
            updateShared();
            $("shared-board-label").textContent = "Общая доска недоступна";
            if (mode === "shared")
              say("Не удалось загрузить доску: " + err.message, true);
          },
        );
      }
    },
    close,
  };
}
