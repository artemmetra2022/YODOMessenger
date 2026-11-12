export function keyCode(e) {
  return (
    e.code ||
    { ",": "Comma", "+": "Equal", "=": "Equal", "-": "Minus", 0: "Digit0" }[
      e.key
    ] ||
    (/^\w$/.test(e.key || "") ? "Key" + e.key.toUpperCase() : e.key)
  );
}
export function editableTarget(target) {
  return !!target?.closest?.(
    'input,textarea,select,[contenteditable="true"],[contenteditable=""]',
  );
}
export function globalShortcut(e) {
  if (e.isComposing || e.repeat || e.getModifierState?.("AltGraph"))
    return null;
  const code = keyCode(e),
    mod = e.ctrlKey || e.metaKey;
  if (code === "F1" && !mod && !e.altKey) return "help";
  if (mod && !e.altKey) {
    if (code === "KeyK" && !e.shiftKey) return "search";
    if (code === "Comma" && !e.shiftKey) return "settings";
    if (code === "Enter" && !e.shiftKey) return "send";
    if (e.shiftKey && code === "KeyB") return "boards";
    if (e.shiftKey && code === "KeyS") return "shared";
    if (e.shiftKey && code === "KeyM") return "mute";
  }
  if (
    e.altKey &&
    !mod &&
    !e.shiftKey &&
    ["ArrowUp", "ArrowDown"].includes(code)
  )
    return code === "ArrowUp" ? "previous" : "next";
  return null;
}
export function boardShortcut(e) {
  if (
    e.isComposing ||
    e.getModifierState?.("AltGraph") ||
    editableTarget(e.target)
  )
    return null;
  const c = keyCode(e),
    mod = e.ctrlKey || e.metaKey;
  if (mod && !e.altKey) {
    if (c === "KeyZ") return e.shiftKey ? "redo" : "undo";
    if (c === "KeyY" && !e.shiftKey) return "redo";
    return null;
  }
  if (e.altKey || mod || editableTarget(e.target)) return null;
  return (
    {
      KeyV: "SELECT",
      KeyH: "HAND",
      KeyB: "PEN",
      KeyP: "PEN",
      KeyE: "ERASER",
      KeyT: "TEXT",
      KeyL: "LINE",
      KeyR: "RECTANGLE",
      KeyO: "ELLIPSE",
      Delete: "delete",
      Backspace: "delete",
      Equal: "zoomIn",
      NumpadAdd: "zoomIn",
      Minus: "zoomOut",
      NumpadSubtract: "zoomOut",
      Digit0: "zoomReset",
      Numpad0: "zoomReset",
      ArrowLeft: "left",
      ArrowRight: "right",
      ArrowUp: "up",
      ArrowDown: "down",
    }[c] || null
  );
}
export function initShortcuts(api) {
  const $ = (id) => document.getElementById(id);
  let focus = null,
    query = "",
    results = [];
  document.body.insertAdjacentHTML(
    "beforeend",
    `<div id="shortcut-search" class="modal hidden shortcut-overlay" role="dialog" aria-modal="true" aria-labelledby="shortcut-search-heading"><section class="modal-card web-preferences-card"><h3 id="shortcut-search-heading">Перейти к чату</h3><input id="shortcut-query" type="search" placeholder="Название чата…" aria-label="Название чата" autocomplete="off"><div id="shortcut-results"></div><p id="shortcut-search-hint">Поиск только по названиям доступных чатов, не по сообщениям.</p><button id="shortcut-search-close" class="btn-secondary" type="button">Закрыть</button></section></div><div id="shortcut-help" class="modal hidden shortcut-overlay" role="dialog" aria-modal="true" aria-labelledby="shortcut-help-heading"><section class="modal-card web-preferences-card"><div class="shortcut-help-header"><h3 id="shortcut-help-heading" tabindex="-1">Горячие клавиши</h3><button id="shortcut-help-close-top" class="icon-btn" aria-label="Закрыть справку" type="button">×</button></div><p>Ctrl на Windows/Linux, ⌘ на Mac. Буквенные сочетания работают и с русской раскладкой.</p><dl class="shortcut-list"><dt>Ctrl/⌘ + K</dt><dd>Найти и открыть чат</dd><dt>Alt + ↑ / ↓</dt><dd>Предыдущий / следующий чат</dd><dt>Ctrl/⌘ + Enter</dt><dd>Отправить сообщение</dd><dt>Ctrl/⌘ + ,</dt><dd>Настройки</dd><dt>Ctrl/⌘ + Shift + B</dt><dd>Мои доски</dd><dt>Ctrl/⌘ + Shift + S</dt><dd>Общая доска текущего чата</dd><dt>Ctrl/⌘ + Shift + M</dt><dd>Отключить / вернуть уведомления чата</dd><dt>F1</dt><dd>Эта справка</dd></dl><h4>В редакторе доски</h4><dl class="shortcut-list"><dt>V / H</dt><dd>Выделение / перемещение холста</dd><dt>B или P / E / T</dt><dd>Карандаш / ластик / текст</dd><dt>L / R / O</dt><dd>Линия / прямоугольник / эллипс</dd><dt>Ctrl/⌘ + Z</dt><dd>Отменить</dd><dt>Ctrl/⌘ + Shift + Z или Y</dt><dd>Повторить</dd><dt>Delete</dt><dd>Удалить выделенный объект</dd><dt>Стрелки / Shift + стрелки</dt><dd>Сдвиг объекта на 1 / 10 точек</dd><dt>+ / − / 0</dt><dd>Приблизить / отдалить / вписать</dd><dt>Ctrl/⌘ + колесо</dt><dd>Масштаб у указателя</dd><dt>Escape</dt><dd>Отменить жест, снять выделение, затем закрыть</dd></dl><p>Обычный ввод текста и сочетания в полях не перехватываются. Некоторые сочетания могут быть заняты браузером или системой.</p><button id="shortcut-help-close" class="btn-secondary" type="button">Закрыть</button></section></div><p id="web-action-status" role="status" aria-live="polite"></p>`,
  );
  function announce(text) {
    $("web-action-status").textContent = text;
    setTimeout(() => {
      if ($("web-action-status").textContent === text)
        $("web-action-status").textContent = "";
    }, 4000);
  }
  function close(id) {
    $(id).classList.add("hidden");
    focus?.focus();
  }
  function open(id) {
    focus = document.activeElement;
    $(id).classList.remove("hidden");
    $(id).querySelector(".web-preferences-card").scrollTop = 0;
    $(
      id === "shortcut-help" ? "shortcut-help-heading" : "shortcut-query",
    ).focus({ preventScroll: true });
  }
  $("shortcut-help-close-top").onclick = () => close("shortcut-help");
  function showHelp() {
    if (!$("shortcut-help").classList.contains("hidden"))
      return close("shortcut-help");
    open("shortcut-help");
  }
  function render() {
    const q = query.trim().toLocaleLowerCase("ru");
    results = api
      .chats()
      .filter((c) => c.name.toLocaleLowerCase("ru").includes(q));
    const list = $("shortcut-results");
    list.replaceChildren();
    for (const c of results.slice(0, 50)) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "shortcut-chat-result";
      b.textContent = c.name;
      b.onclick = () => {
        close("shortcut-search");
        api.openChat(c.id);
      };
      list.append(b);
    }
    if (!results.length) list.textContent = "Чаты не найдены.";
  }
  function search() {
    if (!$("shortcut-search").classList.contains("hidden")) {
      $("shortcut-query").focus();
      return;
    }
    query = "";
    $("shortcut-query").value = "";
    render();
    open("shortcut-search");
  }
  $("shortcut-query").oninput = (e) => {
    query = e.target.value;
    render();
  };
  $("shortcut-query").onkeydown = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      document.querySelector("#shortcut-results button")?.click();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      document.querySelector("#shortcut-results button")?.focus();
    }
  };
  for (const id of ["shortcut-search", "shortcut-help"]) {
    $(id + "-close").onclick = () => close(id);
    $(id).onclick = (e) => {
      if (e.target.id === id) close(id);
    };
    $(id).onkeydown = (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        close(id);
      }
      if (e.key === "Tab") {
        const ns = [...$(id).querySelectorAll("input,button")].filter(
          (x) => !x.disabled && x.getClientRects().length,
        );
        if (
          e.shiftKey &&
          (document.activeElement === ns[0] ||
            !ns.includes(document.activeElement))
        ) {
          e.preventDefault();
          ns.at(-1).focus();
        } else if (!e.shiftKey && document.activeElement === ns.at(-1)) {
          e.preventDefault();
          ns[0].focus();
        }
      }
    };
  }
  document.addEventListener("keydown", (e) => {
    if (e.defaultPrevented || !api.signedIn()) return;
    const action = globalShortcut(e);
    if (!action) return;
    if (action === "help") {
      e.preventDefault();
      showHelp();
      return;
    }
    if (
      document.querySelector(".modal:not(.hidden),.board-overlay:not(.hidden)")
    )
      return;
    e.preventDefault();
    if (action === "search") search();
    else if (action === "settings") api.settings();
    else if (action === "boards") api.boards();
    else if (action === "shared") api.shared();
    else if (action === "send") api.send();
    else if (action === "mute") {
      const muted = api.mute();
      if (muted != null)
        announce(
          muted ? "Уведомления чата отключены" : "Уведомления чата включены",
        );
    } else if (action === "next" || action === "previous") {
      const cs = api.chats();
      if (!cs.length) return;
      const i = cs.findIndex((c) => c.id === api.activeChatId());
      api.openChat(
        cs[
          i < 0
            ? action === "next"
              ? 0
              : cs.length - 1
            : (i + (action === "next" ? 1 : -1) + cs.length) % cs.length
        ].id,
      );
    }
  });
  $("btn-shortcuts").onclick = showHelp;
  return {
    showHelp,
    search,
    announce,
    reset() {
      for (const id of ["shortcut-search", "shortcut-help"])
        $(id).classList.add("hidden");
      $("web-action-status").textContent = "";
    },
  };
}
