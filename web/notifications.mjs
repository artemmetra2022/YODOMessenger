import {
  defaultNotifications,
  normalizeNotifications,
  shouldNotify,
  notificationText,
  durationUntil,
  isMuted,
  remainingMute,
} from "./notification-prefs.mjs";
const $ = (id) => document.getElementById(id);
export function initNotifications(api) {
  const banners = new Set();
  let owner = null,
    chatId = null,
    title = "",
    prefs = defaultNotifications(),
    seen = new Set(),
    audio = null,
    writeError = "";
  const storageKey = () => (owner ? "yodo-notifications:v1:" + owner : null);
  function read() {
    try {
      prefs = normalizeNotifications(
        JSON.parse(localStorage.getItem(storageKey()) || "null"),
      );
    } catch {
      prefs = defaultNotifications();
    }
    render();
  }
  function save() {
    try {
      localStorage.setItem(storageKey(), JSON.stringify(prefs));
      writeError = "";
    } catch {
      writeError =
        "Настройки действуют до закрытия вкладки: браузер не разрешил сохранение.";
    }
    render();
  }
  document.body.insertAdjacentHTML(
    "beforeend",
    `<div id="notification-chat-modal" class="modal hidden" role="dialog" aria-modal="true" aria-labelledby="notification-chat-heading"><div class="modal-card web-preferences-card"><h3 id="notification-chat-heading">Уведомления чата</h3><p id="notification-chat-name"></p><label for="notification-chat-mute">Отключить уведомления</label><select id="notification-chat-mute"><option value="off">Не отключать</option><option value="3600000">На 1 час</option><option value="28800000">На 8 часов</option><option value="86400000">На 24 часа</option><option value="forever">Без срока</option></select><p id="notification-chat-status" role="status"></p><p>Только для этого аккаунта в этом браузере. Настройки телефона не меняются.</p><div class="modal-actions"><button id="notification-chat-close" class="btn-secondary" type="button">Закрыть</button></div></div></div>`,
  );
  let focusBefore = null;
  function closeChat() {
    $("notification-chat-modal").classList.add("hidden");
    focusBefore?.focus();
  }
  function openChat() {
    if (!owner || !chatId) return;
    focusBefore = document.activeElement;
    render();
    $("notification-chat-modal").classList.remove("hidden");
    $("notification-chat-mute").focus();
  }
  function syncMuteSelect(id, value) {
    const el = $(id);
    el.querySelector("option[data-current]")?.remove();
    if (value !== -1 && isMuted(value)) {
      const o = document.createElement("option");
      o.dataset.current = "true";
      o.value = "current";
      o.disabled = true;
      o.textContent = remainingMute(value);
      el.append(o);
      el.value = "current";
    } else el.value = value === -1 ? "forever" : "off";
  }
  function render() {
    const supported = "Notification" in window,
      permission = supported ? Notification.permission : "unsupported";
    $("settings-notifications-toggle").checked = prefs.enabled;
    $("settings-notifications-sound").checked = prefs.sound;
    $("settings-notifications-previews").checked = prefs.previews;
    $("settings-notifications-hint").textContent =
      writeError ||
      (permission === "denied"
        ? "Баннеры заблокированы браузером. Разрешение можно изменить в настройках сайта."
        : permission === "granted"
          ? "Браузер разрешает баннеры."
          : permission === "unsupported"
            ? "Браузер не поддерживает баннеры уведомлений."
            : "Разрешение браузера ещё не запрошено.");
    $("settings-notifications-permission").textContent =
      permission === "granted"
        ? "Баннеры разрешены"
        : permission === "denied"
          ? "Баннеры заблокированы"
          : !supported
            ? "Баннеры недоступны"
            : "Разрешить баннеры браузера";
    $("settings-notifications-permission").disabled =
      !supported || permission === "denied" || permission === "granted";
    $("settings-notifications-quiet-status").textContent =
      "Режим «Не беспокоить»: " +
      (isMuted(prefs.quietUntil)
        ? remainingMute(prefs.quietUntil)
        : "выключен");
    $("notification-chat-name").textContent = title;
    $("notification-chat-status").textContent =
      "Уведомления: " +
      remainingMute(prefs.chats[chatId]?.muteUntil || 0) +
      (prefs.enabled ? "" : " · Общие уведомления выключены в настройках.");
    syncMuteSelect(
      "notification-chat-mute",
      prefs.chats[chatId]?.muteUntil || 0,
    );
    syncMuteSelect("settings-notifications-quiet", prefs.quietUntil);
    const muted = isMuted(prefs.chats[chatId]?.muteUntil || 0);
    $("btn-chat-notifications").textContent = muted ? "Тихо" : "Звук";
    $("btn-chat-notifications").setAttribute(
      "aria-label",
      muted
        ? "Уведомления чата отключены. Настроить"
        : "Настроить уведомления чата",
    );
    $("btn-chat-notifications").title = muted
      ? "Уведомления чата отключены"
      : "Настроить уведомления чата";
  }
  async function unlockSound() {
    try {
      const C = window.AudioContext || window.webkitAudioContext;
      if (!C) return;
      audio ??= new C();
      if (audio.state === "suspended") await audio.resume();
    } catch {
      /* Sound is optional and needs an explicit user gesture. */
    }
  }
  function sound() {
    if (!prefs.sound || !audio || audio.state !== "running") return;
    try {
      const t = audio.currentTime,
        o = audio.createOscillator(),
        g = audio.createGain();
      o.type = "sine";
      o.frequency.setValueAtTime(740, t);
      o.frequency.exponentialRampToValueAtTime(520, t + 0.12);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.08, t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.18);
      o.connect(g).connect(audio.destination);
      o.start(t);
      o.stop(t + 0.2);
    } catch {}
  }
  $("settings-notifications-toggle").onchange = (e) => {
    prefs.enabled = e.target.checked;
    save();
    if (prefs.enabled && prefs.sound) unlockSound();
  };
  $("settings-notifications-sound").onchange = (e) => {
    prefs.sound = e.target.checked;
    save();
    if (prefs.sound) unlockSound();
  };
  $("settings-notifications-previews").onchange = (e) => {
    prefs.previews = e.target.checked;
    save();
  };
  $("settings-notifications-permission").onclick = async () => {
    const uid = owner;
    try {
      await Notification.requestPermission();
      if (owner !== uid) return;
      render();
    } catch {
      if (owner === uid) {
        writeError = "Не удалось запросить разрешение браузера.";
        render();
      }
    }
  };
  $("settings-notifications-quiet").onchange = (e) => {
    prefs.quietUntil = durationUntil(e.target.value);
    save();
  };
  $("settings-notifications-test").onclick = async () => {
    await unlockSound();
    if (prefs.sound) sound();
    const supported = "Notification" in window;
    if (supported && Notification.permission === "granted") {
      try {
        const n = new Notification("YODO — проверка уведомлений", {
          body: "Так будет выглядеть уведомление.",
          silent: true,
        });
        setTimeout(() => n.close(), 5000);
      } catch {
        writeError = "Этот браузер не разрешил показать баннер из страницы.";
        render();
      }
    } else {
      writeError =
        "Для тестового баннера сначала разрешите уведомления браузера.";
      render();
    }
  };
  $("notification-chat-mute").onchange = (e) => {
    if (!owner || !chatId) return;
    prefs.chats[chatId] = { muteUntil: durationUntil(e.target.value) };
    save();
  };
  $("btn-chat-notifications").onclick = openChat;
  $("notification-chat-close").onclick = closeChat;
  $("notification-chat-modal").onkeydown = (e) => {
    if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      closeChat();
    }
    if (e.key === "Tab") {
      const first = $("notification-chat-mute"),
        last = $("notification-chat-close");
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  };
  $("notification-chat-modal").onclick = (e) => {
    if (e.target.id === "notification-chat-modal") closeChat();
  };
  window.addEventListener("storage", (e) => {
    if (owner && (e.key === storageKey() || e.key === null)) read();
  });
  document.addEventListener("visibilitychange", () => {
    if (owner && !document.hidden) render();
  });
  setInterval(() => {
    if (owner && !document.hidden) render();
  }, 30000);
  // Resume an opted-in sound on the next explicit interaction after a reload.
  document.addEventListener(
    "pointerdown",
    () => {
      if (owner && prefs.enabled && prefs.sound) unlockSound();
    },
    { passive: true },
  );
  return {
    setOwner(uid) {
      if (uid === owner) return;
      owner = uid;
      chatId = null;
      seen.clear();
      writeError = "";
      for (const n of banners) n.close();
      banners.clear();
      audio?.close().catch(() => {});
      audio = null;
      $("notification-chat-modal").classList.add("hidden");
      if (uid) read();
      else {
        prefs = defaultNotifications();
        render();
      }
    },
    setChat(id, name = "") {
      chatId = id;
      title = name;
      $("notification-chat-modal").classList.add("hidden");
      render();
    },
    refresh: render,
    openChat,
    toggleMute() {
      if (!owner || !chatId) return;
      prefs.chats[chatId] = {
        muteUntil: isMuted(prefs.chats[chatId]?.muteUntil || 0) ? 0 : -1,
      };
      save();
      return isMuted(prefs.chats[chatId].muteUntil);
    },
    notify(event) {
      if (!owner) return;
      const key = event.chatId + ":" + event.messageId;
      if (seen.has(key)) return;
      seen.add(key);
      if (seen.size > 500) seen.delete(seen.values().next().value);
      if (
        !shouldNotify(prefs, {
          ...event,
          owner,
          visible: !document.hidden,
          activeChatId: api.activeChatId(),
        })
      )
        return;
      sound();
      if (!("Notification" in window) || Notification.permission !== "granted")
        return;
      try {
        const n = new Notification(event.title || "YODO", {
          body: notificationText(prefs, event),
          tag: "yodo-" + event.chatId,
          silent: true,
        });
        const noticeOwner = owner;
        banners.add(n);
        n.onclick = () => {
          n.close();
          banners.delete(n);
          if (owner !== noticeOwner) return;
          window.focus();
          api.openChat(event.chatId);
        };
        setTimeout(() => {
          n.close();
          banners.delete(n);
        }, 10000);
      } catch {
        /* The page is not a service worker; some mobile browsers do not support this constructor. */
      }
    },
  };
}
