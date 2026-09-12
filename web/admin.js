/**
 * YODO Admin — веб-админ-панель раздела «Школа» (mirror SchoolAdminScreen
 * из Android-приложения). Работает напрямую с Firestore через веб-SDK:
 * права проверяют серверные правила (isOfficialChannelAdmin по email в
 * auth-токене) — того же списка ADMIN_EMAILS, что и в app.js.
 *
 * Модель данных 1:1 с SchoolRepositoryImpl / AppSettingsRepositoryImpl:
 *  - schoolNews:      {sender, text, eventDate, pubDate, pinned, notified}
 *  - schoolPolls:     {question, options[], votes{idx->n}, voters{uid->idx},
 *                      createdAt, notified}
 *  - schoolTeacherProfiles/{имя}: {name, subject, linkedUserId,
 *                      linkedUserName, fileUrl, fileNote, ...}
 *      /questions:    {fromUid, fromName, text, hidden, answer, answeredAt,
 *                      createdAt, notified}
 *  - schoolIdeas:     {authorId, authorName, text, createdAt} — только чтение
 *  - schoolReviews:   {authorId, authorName, stars, liked, disliked, updatedAt}
 *                      — только чтение
 *  - config/appSettings: {requireEmailVerification, schoolSectionHidden,
 *                      schoolScheduleActual, schoolScheduleUntilDate,
 *                      schoolScheduleUpdatedAt, schoolHolidayDate}
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  getDocs,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  collectionGroup,
  deleteField,
  writeBatch,
  increment,
  query,
  where,
  orderBy,
  limit,
  startAfter,
  onSnapshot,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

/* ------------------------------------------------------------------ */
/* Firebase init (тот же проект, что и веб-версия мессенджера)         */
/* ------------------------------------------------------------------ */

const firebaseConfig = {
  apiKey: "AIzaSyBN0R6R54f1Dah3vp7WrYrsY95e5NgMZA4",
  authDomain: "yodomessenger.firebaseapp.com",
  projectId: "yodomessenger",
  storageBucket: "yodomessenger.firebasestorage.app",
  messagingSenderId: "509907567167",
  appId: "1:509907567167:web:b7ea079acbf1ab2272ae8a",
  measurementId: "G-YGGHJ2BWEM",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const ADMIN_EMAILS = ["artemmetra2022spb@gmail.com", "artemmelnik2@yandex.ru"];
// Официальный канал — id 1:1 с ChatRepository.OFFICIAL_CHANNEL_ID и app.js.
const OFFICIAL_CHANNEL_ID = "yodo_official_channel";

/* ------------------------------------------------------------------ */
/* Утилиты                                                             */
/* ------------------------------------------------------------------ */

const $ = (id) => document.getElementById(id);

function esc(text) {
  const div = document.createElement("div");
  div.textContent = text ?? "";
  return div.innerHTML;
}

function isAdminEmail(email) {
  return !!email && ADMIN_EMAILS.includes(email.toLowerCase());
}

let toastTimer = null;
function toast(message, ok = true) {
  const el = $("toast");
  el.textContent = message;
  el.className = "toast " + (ok ? "ok" : "err");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), 3500);
}

function fmtDate(ms) {
  if (!ms) return "";
  return new Date(ms).toLocaleString("ru-RU", {
    day: "2-digit", month: "2-digit", year: "2-digit",
    hour: "2-digit", minute: "2-digit",
  });
}

/** Человекочитаемая длительность для среднего времени ответа. */
function fmtDuration(ms) {
  if (ms <= 0) return "—";
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return minutes + " мин";
  const hours = Math.round(minutes / 60);
  if (hours < 24) return hours + " ч";
  return Math.round(hours / 24) + " дн";
}

/** Заменить список, не пересоздавая панель (снимает listeners). */
function setLoading(listEl) {
  listEl.innerHTML = '<p class="empty-note">Загрузка…</p>';
}

/** ms → значение для <input type="datetime-local"> (локальное время). */
function toDatetimeLocal(ms) {
  if (!ms) return "";
  const d = new Date(ms);
  const pad = (n) => String(n).padStart(2, "0");
  return (
    d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
    "T" + pad(d.getHours()) + ":" + pad(d.getMinutes())
  );
}

/** Значение <input type="datetime-local"> → ms (0 = не задано). */
function fromDatetimeLocal(value) {
  if (!value) return 0;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? 0 : d.getTime();
}

function handleErr(prefix) {
  return (err) => {
    console.error(prefix, err);
    const forbidden = (err?.code || "").includes("permission-denied");
    toast(
      forbidden
        ? "Нет прав: войдите под аккаунтом администратора"
        : prefix + ": " + (err?.message || "ошибка"),
      false
    );
  };
}

/* ------------------------------------------------------------------ */
/* Аудит действий панели (adminAuditLog)                               */
/* ------------------------------------------------------------------ */

const AUDIT_LABELS = {
  USER_GLOBALLY_BLOCKED: "Глобальная блокировка пользователя",
  USER_GLOBALLY_UNBLOCKED: "Снятие глобальной блокировки",
  REQUIRE_EMAIL_VERIFICATION_CHANGED: "Изменение требования подтверждения email",
  SCHOOL_NEWS_ADDED: "Новая новость",
  SCHOOL_NEWS_EDITED: "Правка новости",
  SCHOOL_NEWS_PINNED: "Закрепление новости",
  SCHOOL_NEWS_DELETED: "Удаление новости",
  SCHOOL_NEWS_PUBLISHED: "Публикация черновика новости",
  SCHOOL_NEWS_DRAFT_SAVED: "Сохранён черновик новости",
  SCHOOL_POLL_ADDED: "Новый опрос",
  SCHOOL_POLL_DELETED: "Удаление опроса",
  SCHOOL_POLL_CLOSED: "Закрытие/открытие опроса",
  SCHOOL_PUSH_RESENT: "Ручная отправка push",
  SCHOOL_TEACHER_CREATED: "Создан профиль учителя",
  SCHOOL_TEACHER_LINKED: "Привязка аккаунта учителя",
  SCHOOL_TEACHER_UNLINKED: "Отвязка аккаунта учителя",
  SCHOOL_TEACHER_DELETED: "Удалён профиль учителя",
  SCHOOL_TEACHER_FILE_SET: "Файл урока учителя обновлён",
  SCHOOL_QUESTION_ANSWERED: "Ответ на вопрос ученика",
  SCHOOL_QUESTION_HIDDEN: "Скрытие/показ вопроса",
  SCHOOL_SCHEDULE_STATUS_SET: "Пометка актуальности расписания",
  SCHOOL_HOLIDAY_DATE_SET: "Дата каникул",
  SCHOOL_SECTION_VISIBILITY: "Видимость раздела «Школа»",
  SYSTEM_BANNER_SET: "Баннер для пользователей",
  SUPPORT_RESTRICTION_SET: "Ограничение доступа к поддержке",
  SUPPORT_RESTRICTION_REMOVED: "Снятие ограничения доступа к поддержке",
  SUPPORT_MESSAGE_SENT: "Ответ пользователю от поддержки",
  SUPPORT_FAQ_SAVED: "Правка FAQ-бота поддержки",
  SUPPORT_FAQ_RESET: "Сброс FAQ к встроенному списку",
  CHANNEL_POST_ADDED: "Пост в официальном канале",
  CHANNEL_POST_EDITED: "Правка поста в официальном канале",
  CHANNEL_POST_PINNED: "Закрепление/открепление поста",
  CHANNEL_POST_DELETED: "Удаление поста в официальном канале",
  ADMIN_BROADCAST_QUEUED: "Рассылка из Push-центра",
  REPORT_BULK_MESSAGES_DELETED: "Массовое удаление сообщений",
  // НОВОЕ (автофильтр и история удалённых): удаление с причиной, восстановление
  // из истории, правка правил автофильтра, автоудаление и очистка архива.
  MESSAGE_DELETED_WITH_REASON: "Удаление сообщения с причиной",
  MESSAGE_RESTORED: "Восстановление удалённого сообщения",
  AUTO_FILTER_RULE_SAVED: "Правка правил автофильтра",
  AUTO_FILTER_MESSAGES_DELETED: "Автоудаление сообщений по фильтру",
  DELETED_HISTORY_CLEANED: "Очистка истории удалённых (30 дней)",
};

// Отображаемое имя админа для записей аудита (users/{uid}.displayName).
let adminActorName = "";

async function loadAdminActorName(uid) {
  try {
    const snap = await getDoc(doc(db, "users", uid));
    adminActorName = snap.exists() ? snap.data().displayName || "" : "";
  } catch (e) { /* best-effort */ }
}

// Best-effort запись действия в adminAuditLog — формат 1:1 с
// UserRepositoryImpl.logGlobalAdminAction. Неизвестные Android-клиенту
// actionType просто не показываются в его журнале (runCatching valueOf).
function logAdminAction(actionType, details = "", targetUserId = null, targetUserName = null) {
  if (!auth.currentUser) return;
  addDoc(collection(db, "adminAuditLog"), {
    actorId: auth.currentUser.uid,
    actorName: adminActorName || auth.currentUser.email || "Админ",
    actionType,
    details,
    targetUserId,
    targetUserName,
    timestamp: Date.now(),
  }).catch(() => {});
}

/* ------------------------------------------------------------------ */
/* Навигация по секциям                                                */
/* ------------------------------------------------------------------ */

document.querySelectorAll(".nav-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".nav-btn").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    document
      .querySelectorAll(".admin-section")
      .forEach((s) => s.classList.remove("active"));
    $("section-" + btn.dataset.section).classList.add("active");
  });
});

/* ------------------------------------------------------------------ */
/* Авторизация                                                         */
/* ------------------------------------------------------------------ */

$("form-login").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("login-error").textContent = "";
  try {
    await signInWithEmailAndPassword(
      auth,
      $("login-email").value.trim(),
      $("login-password").value
    );
  } catch (err) {
    $("login-error").textContent =
      err?.code === "auth/invalid-credential" || err?.code === "auth/wrong-password"
        ? "Неверный email или пароль"
        : err?.code === "auth/too-many-requests"
          ? "Слишком много попыток — попробуйте позже"
          : "Ошибка входа: " + (err?.message || "");
  }
});

async function doLogout() {
  await signOut(auth).catch(() => {});
  location.reload();
}

$("btn-logout").addEventListener("click", doLogout);
$("btn-denied-logout").addEventListener("click", doLogout);

let panelStarted = false;

onAuthStateChanged(auth, async (user) => {
  if (!user) {
    panelStarted = false;
    $("screen-admin").classList.add("hidden");
    $("screen-denied").classList.add("hidden");
    $("screen-login").classList.remove("hidden");
    return;
  }
  // Email из аккаунта надёжнее, чем из auth-токена Firebase Admin,
  // если создатели удалены из Auth — панель пускает по списку ADMIN_EMAILS.
  if (!isAdminEmail(user.email)) {
    $("screen-admin").classList.add("hidden");
    $("screen-login").classList.add("hidden");
    $("screen-denied").classList.remove("hidden");
    return;
  }
  $("screen-login").classList.add("hidden");
  $("screen-denied").classList.add("hidden");
  $("screen-admin").classList.remove("hidden");
  $("admin-email").textContent = user.email;
  loadAdminActorName(user.uid);
  if (!panelStarted) {
    panelStarted = true;
    startPanel();
  }
});

/* ------------------------------------------------------------------ */
/* Секция «Настройки»                                                  */
/* ------------------------------------------------------------------ */

function startSettings() {
  const settingsRef = doc(db, "config/appSettings");

  onSnapshot(settingsRef, (snap) => {
    const hidden = snap.get("schoolSectionHidden") === true;
    $("toggle-school-hidden").checked = hidden;

    const requireVerification =
      snap.get("requireEmailVerification") === undefined
        ? true // дефолт true, как в AppSettingsRepositoryImpl
        : snap.get("requireEmailVerification") === true;
    $("toggle-require-email-verification").checked = requireVerification;

    const updatedAt = snap.get("schoolScheduleUpdatedAt") || 0;
    if (updatedAt > 0) {
      $("schedule-until").value = snap.get("schoolScheduleUntilDate") || "";
      const actual =
        snap.get("schoolScheduleActual") === undefined
          ? false
          : snap.get("schoolScheduleActual") === true;
      document.querySelector(
        'input[name="schedule-actual"][value="' + actual + '"]'
      ).checked = true;
      $("schedule-updated-at").textContent =
        "Последняя пометка: " + fmtDate(updatedAt);
    } else {
      $("schedule-updated-at").textContent = "Пометка ещё не ставилась.";
    }

    // Дата каникул от админа (пустая строка — используется зашитая из сборки).
    $("holiday-date").value = snap.get("schoolHolidayDate") || "";

    // Баннер для пользователей (веб-версия мессенджера).
    $("banner-enabled").checked = snap.get("bannerEnabled") === true;
    $("banner-title").value = snap.get("bannerTitle") || "";
    $("banner-text").value = snap.get("bannerText") || "";
    $("banner-link-text").value = snap.get("bannerLinkText") || "";
    $("banner-link-url").value = snap.get("bannerLinkUrl") || "";
    $("banner-start-at").value = toDatetimeLocal(snap.get("bannerStartAt"));
    $("banner-end-at").value = toDatetimeLocal(snap.get("bannerEndAt"));
  }, handleErr("Не удалось загрузить настройки"));

  $("toggle-school-hidden").addEventListener("change", async (e) => {
    try {
      await setDoc(settingsRef, { schoolSectionHidden: e.target.checked }, { merge: true });
      toast(e.target.checked ? "Раздел «Школа» скрыт у всех" : "Раздел «Школа» снова виден");
      logAdminAction(
        "SCHOOL_SECTION_VISIBILITY",
        e.target.checked ? "раздел скрыт у всех" : "раздел снова виден"
      );
    } catch (err) {
      e.target.checked = !e.target.checked;
      handleErr("Не удалось изменить видимость раздела")(err);
    }
  });

  $("toggle-require-email-verification").addEventListener("change", async (e) => {
    try {
      await setDoc(
        settingsRef,
        { requireEmailVerification: e.target.checked },
        { merge: true }
      );
      toast(e.target.checked ? "Подтверждение email обязательно" : "Подтверждение email не требуется");
      // Тот же actionType, что у Android (UserRepositoryImpl) — запись видна
      // в журнале и в приложении.
      logAdminAction(
        "REQUIRE_EMAIL_VERIFICATION_CHANGED",
        e.target.checked ? "включено" : "выключено"
      );
    } catch (err) {
      e.target.checked = !e.target.checked;
      handleErr("Не удалось изменить настройку")(err);
    }
  });

  $("form-schedule").addEventListener("submit", async (e) => {
    e.preventDefault();
    const actual =
      document.querySelector('input[name="schedule-actual"]:checked').value === "true";
    const untilDate = $("schedule-until").value.trim();
    try {
      await setDoc(
        settingsRef,
        {
          schoolScheduleActual: actual,
          schoolScheduleUntilDate: untilDate,
          schoolScheduleUpdatedAt: Date.now(),
        },
        { merge: true }
      );
      toast("Пометка расписания сохранена");
      logAdminAction(
        "SCHOOL_SCHEDULE_STATUS_SET",
        (actual ? "актуально" : "неактуально") + (untilDate ? " до " + untilDate : "")
      );
    } catch (err) {
      handleErr("Не удалось сохранить пометку")(err);
    }
  });

  // Дата каникул: ISO-строка yyyy-MM-dd или "" (сброс к зашитой из сборки).
  $("form-holiday").addEventListener("submit", async (e) => {
    e.preventDefault();
    let value = $("holiday-date").value.trim();
    if (value === "-") value = "";
    if (value && !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      toast("Формат даты: ГГГГ-ММ-ДД, например 2026-10-26", false);
      return;
    }
    if (value) {
      const d = new Date(value + "T00:00:00");
      if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== value) {
        toast("Такой даты не существует — проверьте ГГГГ-ММ-ДД", false);
        return;
      }
    }
    try {
      await setDoc(settingsRef, { schoolHolidayDate: value }, { merge: true });
      toast(value ? "Дата каникул сохранена: " + value : "Дата каникул сброшена");
      logAdminAction(
        "SCHOOL_HOLIDAY_DATE_SET",
        value ? "каникулы с " + value : "сброшена (используется зашитая)"
      );
    } catch (err) {
      handleErr("Не удалось сохранить дату каникул")(err);
    }
  });

  // Баннер для пользователей (веб-версия мессенджера). Пустые даты = всегда.
  $("form-banner").addEventListener("submit", async (e) => {
    e.preventDefault();
    const enabled = $("banner-enabled").checked;
    const title = $("banner-title").value.trim();
    const text = $("banner-text").value.trim();
    const linkText = $("banner-link-text").value.trim();
    const linkUrl = $("banner-link-url").value.trim();
    if (enabled && !title) {
      toast("Заголовок обязателен, когда баннер включён", false);
      return;
    }
    if (linkText && !linkUrl) {
      toast("Укажите ссылку для кнопки", false);
      return;
    }
    const startAt = fromDatetimeLocal($("banner-start-at").value);
    const endAt = fromDatetimeLocal($("banner-end-at").value);
    if (startAt && endAt && endAt <= startAt) {
      toast("Дата окончания должна быть позже начала", false);
      return;
    }
    try {
      await setDoc(
        settingsRef,
        {
          bannerEnabled: enabled,
          bannerTitle: title,
          bannerText: text,
          bannerLinkText: linkText,
          bannerLinkUrl: linkUrl,
          bannerStartAt: startAt,
          bannerEndAt: endAt,
          bannerUpdatedAt: Date.now(),
        },
        { merge: true }
      );
      toast(enabled ? "Баннер включён и показан пользователям" : "Баннер выключен");
      logAdminAction(
        "SYSTEM_BANNER_SET",
        (enabled ? "включён: " : "выключен: ") + (title || "")
      );
    } catch (err) {
      handleErr("Не удалось сохранить баннер")(err);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Секция «Новости»                                                    */
/* ------------------------------------------------------------------ */

function newsItem(docSnap) {
  const n = docSnap.data();
  const isDraft = n.published === false;
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      ${n.pinned ? '<span class="badge badge-blue">📌 Закреплена</span>' : ""}
      ${isDraft
        ? n.publishAt
          ? '<span class="badge badge-blue">🕐 Запланирована: ' + esc(fmtDate(n.publishAt)) + '</span>'
          : '<span class="badge badge-dim">✏️ Черновик</span>'
        : n.notified === false
          ? '<span class="badge badge-yellow">push: в очереди</span>'
          : '<span class="badge badge-green">push: отправлен</span>'}
      <span class="item-title">${esc(n.sender || "")}</span>
      <span class="item-date">${fmtDate(n.pubDate)}</span>
    </div>
    <div class="item-text">${esc(n.text || "")}</div>
    ${n.eventDate ? `<div class="item-sub">📅 Событие: ${esc(n.eventDate)}</div>` : ""}
    <div class="item-actions">
      <button class="btn-secondary" data-act="pin">${n.pinned ? "Открепить" : "Закрепить"}</button>
      <button class="btn-secondary" data-act="edit">Изменить текст</button>
      ${isDraft ? '<button class="btn-secondary" data-act="publish">Опубликовать сейчас</button>' : ""}
      ${!isDraft && n.notified !== false ? '<button class="btn-secondary" data-act="push">Отправить push</button>' : ""}
      <button class="btn-danger" data-act="delete">Удалить</button>
    </div>`;
  el.querySelector('[data-act="pin"]').addEventListener("click", () =>
    setNewsPinned(docSnap.id, !n.pinned)
  );
  el.querySelector('[data-act="edit"]').addEventListener("click", () => {
    const newText = prompt("Новый текст новости:", n.text || "");
    if (newText === null) return;
    if (!newText.trim()) return toast("Текст не может быть пустым", false);
    updateDoc(doc(db, "schoolNews", docSnap.id), { text: newText.trim() })
      .then(() => {
        toast("Текст новости обновлён");
        logAdminAction("SCHOOL_NEWS_EDITED", (n.sender || "") + ": " + newText.trim().slice(0, 100));
      })
      .catch(handleErr("Не удалось обновить новость"));
  });
  // НОВОЕ (отложенная публикация): черновик/запланированная новость публикуется
  // вручную — push уйдёт автоматически в ближайшем прогоне воркера.
  const publishBtn = el.querySelector('[data-act="publish"]');
  if (publishBtn) {
    publishBtn.addEventListener("click", () => {
      updateDoc(doc(db, "schoolNews", docSnap.id), { published: true })
        .then(() => {
          toast("Новость опубликована — push уйдёт подписчикам автоматически");
          logAdminAction("SCHOOL_NEWS_PUBLISHED", (n.sender || "") + ": " + (n.text || "").slice(0, 100));
        })
        .catch(handleErr("Не удалось опубликовать новость"));
    });
  }
  // НОВОЕ (ручной push): сбрасываем notified — воркер разошлёт в течение ~5 минут.
  const pushBtn = el.querySelector('[data-act="push"]');
  if (pushBtn) {
    pushBtn.addEventListener("click", () => {
      if (!confirm("Отправить push об этой новости всем подписчикам ещё раз?")) return;
      updateDoc(doc(db, "schoolNews", docSnap.id), { notified: false })
        .then(() => {
          toast("Push поставлен в очередь — воркер отправит его в течение ~5 минут");
          logAdminAction("SCHOOL_PUSH_RESENT", "Новость: " + (n.text || "").slice(0, 100));
        })
        .catch(handleErr("Не удалось отправить push"));
    });
  }
  el.querySelector('[data-act="delete"]').addEventListener("click", () => {
    if (!confirm("Удалить эту новость?")) return;
    deleteDoc(doc(db, "schoolNews", docSnap.id))
      .then(() => {
        toast("Новость удалена");
        logAdminAction(
          "SCHOOL_NEWS_DELETED",
          (n.sender || "") + ": " + (n.text || "").slice(0, 100)
        );
      })
      .catch(handleErr("Не удалось удалить новость"));
  });
  return el;
}

async function setNewsPinned(newsId, pinned) {
  try {
    if (pinned) {
      // Закреплена может быть только одна — снимаем закрепление с остальных
      // (как в SchoolRepositoryImpl.setNewsPinned).
      const pinnedNews = await getDocs(
        query(collection(db, "schoolNews"), where("pinned", "==", true))
      );
      for (const d of pinnedNews.docs) {
        if (d.id !== newsId) {
          await updateDoc(doc(db, "schoolNews", d.id), { pinned: false });
        }
      }
    }
    await updateDoc(doc(db, "schoolNews", newsId), { pinned });
    toast(pinned ? "Новость закреплена" : "Новость откреплена");
    logAdminAction("SCHOOL_NEWS_PINNED", pinned ? "закреплена" : "откреплена");
  } catch (err) {
    handleErr("Не удалось закрепить новость")(err);
  }
}

function startNews() {
  // НОВОЕ (отложенная публикация): поле времени показывается только для
  // режима «по времени».
  $("news-publish-mode").addEventListener("change", () => {
    $("news-publish-at").classList.toggle(
      "hidden",
      $("news-publish-mode").value !== "scheduled"
    );
  });

  // НОВОЕ (предпросмотр): кнопка показывает/скрывает живой предпросмотр,
  // который обновляется по мере ввода — как новость будет выглядеть у учеников.
  const previewEl = $("news-preview");
  function renderNewsPreview() {
    const sender = $("news-sender").value.trim();
    const text = $("news-text").value.trim();
    const eventDate = $("news-event-date").value.trim();
    const el = document.createElement("div");
    el.className = "item";
    el.innerHTML = `
      <div class="item-head">
        <span class="item-title">${esc(sender || "Отправитель")}</span>
        <span class="item-date">${esc(eventDate ? "📅 Событие: " + eventDate : "")}</span>
      </div>
      <div class="item-text">${esc(text || "Текст новости…")}</div>`;
    previewEl.innerHTML = "";
    previewEl.appendChild(el);
  }
  $("btn-news-preview").addEventListener("click", () => {
    previewEl.classList.toggle("hidden");
    if (!previewEl.classList.contains("hidden")) renderNewsPreview();
  });
  ["news-sender", "news-text", "news-event-date"].forEach((id) => {
    $(id).addEventListener("input", () => {
      if (!previewEl.classList.contains("hidden")) renderNewsPreview();
    });
  });

  $("form-add-news").addEventListener("submit", async (e) => {
    e.preventDefault();
    const sender = $("news-sender").value.trim();
    const text = $("news-text").value.trim();
    const eventDate = $("news-event-date").value.trim();
    if (!sender || !text) return;
    const mode = $("news-publish-mode").value;
    const publishAtRaw = $("news-publish-at").value;
    const publishAt = mode === "scheduled" && publishAtRaw
      ? new Date(publishAtRaw).getTime()
      : null;
    if (mode === "scheduled" && !publishAt) {
      return toast("Укажите дату и время публикации", false);
    }
    if (publishAt && publishAt <= Date.now()) {
      return toast("Время публикации уже прошло — выберите будущее время или «сразу»", false);
    }
    try {
      await addDoc(collection(db, "schoolNews"), {
        sender,
        text,
        eventDate,
        pubDate: Date.now(),
        pinned: false,
        // НОВОЕ (отложенная публикация): false = черновик/запланированная —
        // ученики её не видят, push не уходит; воркер опубликует в publishAt
        // или админ кнопкой «Опубликовать сейчас».
        published: mode !== "draft" && mode !== "scheduled",
        publishAt: publishAt || null,
        notified: false, // очередь push-воркера (сработает после публикации)
      });
      $("news-text").value = "";
      $("news-event-date").value = "";
      toast(
        mode === "now"
          ? "Новость опубликована — push уйдёт подписчикам автоматически"
          : mode === "scheduled"
            ? "Новость запланирована — опубликуется автоматически " + fmtDate(publishAt)
            : "Черновик сохранён — ученики его не видят"
      );
      logAdminAction(
        mode === "draft" ? "SCHOOL_NEWS_DRAFT_SAVED" : "SCHOOL_NEWS_ADDED",
        sender + ": " + text.slice(0, 100) +
          (publishAt ? " (запланировано на " + fmtDate(publishAt) + ")" : "")
      );
    } catch (err) {
      handleErr("Не удалось сохранить новость")(err);
    }
  });

  const listEl = $("news-list");
  const moreBtn = $("btn-news-more");
  let newsUnsub = null;
  let newsFilter = "all"; // all | published | drafts
  let newsLimit = 50;

  function loadNews() {
    if (newsUnsub) newsUnsub();
    setLoading(listEl);
    moreBtn.classList.add("hidden");
    newsUnsub = onSnapshot(
      query(
        collection(db, "schoolNews"),
        orderBy("pinned", "desc"),
        orderBy("pubDate", "desc"),
        limit(newsLimit)
      ),
      (snap) => {
        newsCache = snap.docs.map((d) => d.data());
        let docs = snap.docs;
        // Фильтр на клиенте: не нужен составной индекс на published.
        if (newsFilter === "published") docs = docs.filter((d) => d.data().published !== false);
        if (newsFilter === "drafts") docs = docs.filter((d) => d.data().published === false);
        if (!docs.length) {
          listEl.innerHTML = '<p class="empty-note">Новостей пока нет.</p>';
        } else {
          listEl.innerHTML = "";
          docs.forEach((d) => listEl.appendChild(newsItem(d)));
        }
        moreBtn.classList.toggle("hidden", snap.size < newsLimit);
      },
      handleErr("Не удалось загрузить новости")
    );
  }

  document.querySelectorAll(".news-filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".news-filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      newsFilter = btn.dataset.filter;
      newsLimit = 50; // смена фильтра сбрасывает пагинацию
      loadNews();
    });
  });

  moreBtn.addEventListener("click", () => {
    newsLimit += 50;
    loadNews();
  });

  loadNews();
}

/* ------------------------------------------------------------------ */
/* Секция «Опросы»                                                     */
/* ------------------------------------------------------------------ */

function pollItem(docSnap) {
  const p = docSnap.data();
  const options = p.options || [];
  const votes = p.votes || {};
  const votersCount = Object.keys(p.voters || {}).length;
  const totalVotes = options.reduce((sum, _, i) => sum + (votes[String(i)] || 0), 0);

  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      <span class="item-title">${esc(p.question || "")}</span>
      ${p.closed ? '<span class="badge badge-dim">🔒 Завершён</span>' : ""}
      ${p.notified === false
        ? '<span class="badge badge-yellow">push: в очереди</span>'
        : '<span class="badge badge-green">push: отправлен</span>'}
      <span class="item-date">${fmtDate(p.createdAt)} · голосов: ${votersCount}</span>
    </div>
    ${options
      .map((opt, i) => {
        const v = votes[String(i)] || 0;
        const pct = totalVotes ? Math.round((v / totalVotes) * 100) : 0;
        return `
      <div class="poll-option">
        <div class="poll-option-label">
          <span>${esc(opt)}</span><span>${v} (${pct}%)</span>
        </div>
        <div class="poll-bar-bg"><div class="poll-bar-fill" style="width:${pct}%"></div></div>
      </div>`;
      })
      .join("")}
    <div class="item-actions">
      ${!p.closed ? '<button class="btn-secondary" data-act="close">Завершить</button>' : '<button class="btn-secondary" data-act="reopen">Открыть снова</button>'}
      ${p.notified !== false ? '<button class="btn-secondary" data-act="push">Отправить push</button>' : ""}
      <button class="btn-danger" data-act="delete">Удалить опрос</button>
    </div>`;
  // НОВОЕ (закрытие опросов): закрытый опрос виден с результатами, но
  // голосование запрещено (rules + повторная проверка в vote()).
  el.querySelector('[data-act="close"], [data-act="reopen"]').addEventListener("click", () => {
    const closing = !p.closed;
    if (!confirm(closing ? "Завершить опрос? Голосование закроется, результаты останутся видимыми." : "Открыть опрос снова?")) return;
    updateDoc(doc(db, "schoolPolls", docSnap.id), { closed: closing })
      .then(() => {
        toast(closing ? "Опрос завершён — голосование закрыто" : "Опрос снова открыт");
        logAdminAction("SCHOOL_POLL_CLOSED", (closing ? "завершён: " : "открыт: ") + (p.question || ""));
      })
      .catch(handleErr("Не удалось изменить статус опроса"));
  });
  // НОВОЕ (ручной push): сбрасываем notified — воркер разошлёт в течение ~5 минут.
  const pushBtn = el.querySelector('[data-act="push"]');
  if (pushBtn) {
    pushBtn.addEventListener("click", () => {
      if (!confirm("Отправить push об этом опросе всем подписчикам ещё раз?")) return;
      updateDoc(doc(db, "schoolPolls", docSnap.id), { notified: false })
        .then(() => {
          toast("Push поставлен в очередь — воркер отправит его в течение ~5 минут");
          logAdminAction("SCHOOL_PUSH_RESENT", "Опрос: " + (p.question || "").slice(0, 100));
        })
        .catch(handleErr("Не удалось отправить push"));
    });
  }
  el.querySelector('[data-act="delete"]').addEventListener("click", () => {
    if (!confirm("Удалить опрос вместе с результатами?")) return;
    deleteDoc(doc(db, "schoolPolls", docSnap.id))
      .then(() => {
        toast("Опрос удалён");
        logAdminAction("SCHOOL_POLL_DELETED", p.question || "");
      })
      .catch(handleErr("Не удалось удалить опрос"));
  });
  return el;
}

function startPolls() {
  $("form-add-poll").addEventListener("submit", async (e) => {
    e.preventDefault();
    const question = $("poll-question").value.trim();
    const options = $("poll-options").value
      .split("\n")
      .map((s) => s.trim())
      .filter(Boolean);
    if (!question) return;
    if (options.length < 2) return toast("Нужно минимум два варианта ответа", false);
    // НОВОЕ (тихое создание): без push при создании — notified=true, чтобы
    // воркер не рассылал; push отправится позже кнопкой «Отправить push».
    const silent = $("poll-silent").checked;
    try {
      const votes = {};
      options.forEach((_, i) => (votes[String(i)] = 0));
      await addDoc(collection(db, "schoolPolls"), {
        question,
        options,
        votes,
        voters: {},
        createdAt: Date.now(),
        notified: !silent, // false = очередь push-воркера
      });
      $("poll-question").value = "";
      $("poll-options").value = "";
      $("poll-silent").checked = false;
      toast(silent
        ? "Опрос создан без push — разошлите кнопкой «Отправить push»"
        : "Опрос создан — push уйдёт подписчикам автоматически");
      logAdminAction("SCHOOL_POLL_ADDED", question + (silent ? " (без push)" : ""));
    } catch (err) {
      handleErr("Не удалось создать опрос")(err);
    }
  });

  const listEl = $("polls-list");
  const moreBtn = $("btn-polls-more");
  let pollsUnsub = null;
  let pollsFilter = "all"; // all | open | closed
  let pollsLimit = 50;

  function loadPolls() {
    if (pollsUnsub) pollsUnsub();
    setLoading(listEl);
    moreBtn.classList.add("hidden");
    pollsUnsub = onSnapshot(
      query(collection(db, "schoolPolls"), orderBy("createdAt", "desc"), limit(pollsLimit)),
      (snap) => {
        pollsCache = snap.docs.map((d) => d.data());
        let docs = snap.docs;
        if (pollsFilter === "open") docs = docs.filter((d) => d.data().closed !== true);
        if (pollsFilter === "closed") docs = docs.filter((d) => d.data().closed === true);
        if (!docs.length) {
          listEl.innerHTML = '<p class="empty-note">Опросов пока нет.</p>';
        } else {
          listEl.innerHTML = "";
          docs.forEach((d) => listEl.appendChild(pollItem(d)));
        }
        moreBtn.classList.toggle("hidden", snap.size < pollsLimit);
      },
      handleErr("Не удалось загрузить опросы")
    );
  }

  document.querySelectorAll(".polls-filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".polls-filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      pollsFilter = btn.dataset.filter;
      pollsLimit = 50; // смена фильтра сбрасывает пагинацию
      loadPolls();
    });
  });

  moreBtn.addEventListener("click", () => {
    pollsLimit += 50;
    loadPolls();
  });

  loadPolls();
}

/* ------------------------------------------------------------------ */
/* Секция «Учителя»                                                    */
/* ------------------------------------------------------------------ */

let questionsUnsub = null;

function teacherItem(docSnap) {
  const t = docSnap.data();
  const linked = !!t.linkedUserId;
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      <span class="item-title">${esc(t.name || docSnap.id)}</span>
      <span class="item-date">${t.subscribers ? "подписчиков: " + t.subscribers.length : ""}</span>
    </div>
    ${t.subject ? `<div class="item-sub">Предмет: ${esc(t.subject)}</div>` : ""}
    ${
      t.fileUrl
        ? `<div class="item-sub">📎 <a href="${esc(t.fileUrl)}" target="_blank" rel="noopener">${esc(t.fileNote || t.fileUrl)}</a> · обновлён ${fmtDate(t.fileUpdatedAt)}</div>`
        : ""
    }
    <div class="item-sub">
      ${linked ? "🔗 Аккаунт: " + esc(t.linkedUserName || t.linkedUserId) : "ℓ Нет привязанного аккаунта"}
    </div>
    <div class="item-actions">
      <button class="btn-secondary" data-act="file">Файл урока</button>
      <button class="btn-secondary" data-act="link">${linked ? "Перепривязать" : "Привязать аккаунт"}</button>
      ${linked ? '<button class="btn-secondary" data-act="unlink">Отвязать</button>' : ""}
      <button class="btn-secondary" data-act="questions">Вопросы</button>
      <button class="btn-danger" data-act="delete">Удалить профиль</button>
    </div>`;

  el.querySelector('[data-act="file"]').addEventListener("click", () => {
    openFileModal(docSnap.id, t);
  });

  el.querySelector('[data-act="link"]').addEventListener("click", () => {
    openUserSearch({ teacherId: docSnap.id, teacherName: t.name || docSnap.id });
  });

  el.querySelector('[data-act="unlink"]')?.addEventListener("click", () => {
    if (!confirm("Отвязать аккаунт учителя?")) return;
    setDoc(
      doc(db, "schoolTeacherProfiles", docSnap.id),
      { linkedUserId: "", linkedUserName: "" },
      { merge: true }
    )
      .then(() => {
        toast("Аккаунт отвязан");
        logAdminAction("SCHOOL_TEACHER_UNLINKED", t.name || docSnap.id);
      })
      .catch(handleErr("Не удалось отвязать аккаунт"));
  });

  el.querySelector('[data-act="questions"]').addEventListener("click", () => {
    openQuestions(docSnap.id, t.name || docSnap.id);
  });

  el.querySelector('[data-act="delete"]').addEventListener("click", () => {
    if (
      !confirm(
        "Удалить профиль учителя? Вопросы и история файлов останутся в Firestore недоступными."
      )
    )
      return;
    deleteDoc(doc(db, "schoolTeacherProfiles", docSnap.id))
      .then(() => {
        toast("Профиль удалён");
        logAdminAction("SCHOOL_TEACHER_DELETED", t.name || docSnap.id);
      })
      .catch(handleErr("Не удалось удалить профиль"));
  });
  return el;
}

/* ------------------------------------------------------------------ */
/* Модалка поиска пользователя (привязка учителя)                      */
/* ------------------------------------------------------------------ */

let pendingLink = null; // { teacherId, teacherName } — ждём выбор аккаунта
let userSearchTimer = null;

// Префиксный поиск по users (как runSearch в app.js): displayNameLowercase
// и usernameLowercase поддерживаются индексами по умолчанию.
async function searchUsersByField(field, term) {
  return getDocs(query(
    collection(db, "users"),
    where(field, ">=", term),
    where(field, "<=", term + "\uf8ff"),
    limit(10)
  ));
}

function openUserSearch(link) {
  pendingLink = link;
  $("user-search-input").value = "";
  $("user-search-results").innerHTML =
    '<p class="empty-note">Начните вводить имя, @username или ID учителя (YODO-…-…)</p>';
  $("user-search-overlay").classList.remove("hidden");
  $("user-search-input").focus();
}

function closeUserSearch() {
  pendingLink = null;
  $("user-search-overlay").classList.add("hidden");
}

async function runUserSearch() {
  const raw = $("user-search-input").value.trim();
  const term = raw.toLowerCase().replace(/^@/, "");
  const resultsEl = $("user-search-results");
  if (term.length < 2) {
    resultsEl.innerHTML = '<p class="empty-note">Минимум 2 символа</p>';
    return;
  }
  resultsEl.innerHTML = '<p class="empty-note">Поиск…</p>';
  try {
    const byUsername = await searchUsersByField("usernameLowercase", term);
    const byName = await searchUsersByField("displayNameLowercase", term);
    // Публичный ID (YODO-XXXX-XXXX) хранится в верхнем регистре — точное совпадение.
    const byPublicId = term.startsWith("yodo-")
      ? await getDocs(query(collection(db, "users"), where("publicId", "==", raw.toUpperCase()), limit(10)))
      : { docs: [] };
    const seen = new Set();
    const docs = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    for (const d of byPublicId.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    if (!docs.length) {
      resultsEl.innerHTML =
        '<p class="empty-note">Никого не найдено. Попробуйте другое имя или укажите UID вручную.</p>';
      return;
    }
    resultsEl.innerHTML = "";
    docs.forEach((d) => {
      const u = d.data();
      const el = document.createElement("div");
      el.className = "user-result";
      el.innerHTML = `
        <div class="user-result-name">${esc(u.displayName || "Без имени")}</div>
        <div class="user-result-username">@${esc(u.username || "—")}</div>`;
      el.addEventListener("click", () => {
        const link = pendingLink;
        closeUserSearch();
        if (!link) return;
        performTeacherLink(link, d.id, u.displayName || u.username || d.id);
      });
      resultsEl.appendChild(el);
    });
  } catch (err) {
    handleErr("Поиск пользователей")(err);
  }
}

async function performTeacherLink(link, uid, userName) {
  const { teacherId, teacherName } = link;
  try {
    // Снимаем привязку с прошлых профилей этого uid (как linkTeacherProfile).
    const previous = await getDocs(
      query(collection(db, "schoolTeacherProfiles"), where("linkedUserId", "==", uid))
    );
    for (const d of previous.docs) {
      if (d.id !== teacherId) {
        await setDoc(
          doc(db, "schoolTeacherProfiles", d.id),
          { linkedUserId: "", linkedUserName: "" },
          { merge: true }
        );
      }
    }
    await setDoc(
      doc(db, "schoolTeacherProfiles", teacherId),
      { linkedUserId: uid, linkedUserName: userName },
      { merge: true }
    );
    toast("Аккаунт " + userName + " привязан к «" + teacherName + "»");
    logAdminAction("SCHOOL_TEACHER_LINKED", teacherName + " → " + userName, uid, userName);
  } catch (err) {
    handleErr("Не удалось привязать аккаунт")(err);
  }
}

function initUserSearchModal() {
  $("user-search-input").addEventListener("input", () => {
    clearTimeout(userSearchTimer);
    userSearchTimer = setTimeout(runUserSearch, 350);
  });
  $("btn-cancel-user-search").addEventListener("click", closeUserSearch);
  $("user-search-overlay").addEventListener("click", (e) => {
    if (e.target === $("user-search-overlay")) closeUserSearch();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (!$("user-search-overlay").classList.contains("hidden")) closeUserSearch();
      if (!$("file-modal-overlay").classList.contains("hidden")) closeFileModal();
    }
  });
  // Fallback для случаев, когда учитель не находится поиском.
  $("btn-manual-uid").addEventListener("click", () => {
    const link = pendingLink;
    if (!link) return;
    const uid = prompt("UID аккаунта учителя (Firebase Console → Authentication):");
    if (uid === null) return;
    const uidTrim = uid.trim();
    if (!uidTrim) return toast("UID не может быть пустым", false);
    const userName = prompt("Имя пользователя для отображения:", "") || "";
    closeUserSearch();
    performTeacherLink(link, uidTrim, userName.trim());
  });
}

/* ------------------------------------------------------------------ */
/* Модалка файла урока (пайплайн setTeacherProfile.setTeacherFile)     */
/* ------------------------------------------------------------------ */

let fileModalTeacherId = null;
let lessonFilesUnsub = null;

function openFileModal(teacherId, t) {
  fileModalTeacherId = teacherId;
  $("file-modal-teacher").textContent = t.name || teacherId;
  $("file-url").value = t.fileUrl || "";
  $("file-note").value = t.fileNote || "";
  $("file-modal-subscribers").textContent = t.subscribers && t.subscribers.length
    ? "Подписчиков файла: " + t.subscribers.length + " — при смене ссылки им уйдёт push."
    : "Подписчиков файла пока нет — push не отправится, но ссылка обновится.";
  $("file-modal-overlay").classList.remove("hidden");

  if (lessonFilesUnsub) lessonFilesUnsub();
  const listEl = $("lesson-files-list");
  setLoading(listEl);
  lessonFilesUnsub = onSnapshot(
    query(
      collection(db, "schoolTeacherProfiles", teacherId, "lessonFiles"),
      orderBy("updatedAt", "desc"),
      limit(5)
    ),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">История пуста — файл ещё не меняли.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => {
        const f = d.data();
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            <span class="item-title">📎 <a href="${esc(f.fileUrl)}" target="_blank" rel="noopener">${esc(f.fileUrl)}</a></span>
            <span class="item-date">${fmtDate(f.updatedAt)}</span>
          </div>
          ${f.fileNote ? `<div class="item-text">${esc(f.fileNote)}</div>` : ""}`;
        listEl.appendChild(el);
      });
    },
    handleErr("Не удалось загрузить историю файлов")
  );
}

function closeFileModal() {
  fileModalTeacherId = null;
  if (lessonFilesUnsub) lessonFilesUnsub();
  lessonFilesUnsub = null;
  $("file-modal-overlay").classList.add("hidden");
}

async function saveTeacherFile() {
  const teacherId = fileModalTeacherId;
  if (!teacherId) return;
  const fileUrl = $("file-url").value.trim();
  const fileNote = $("file-note").value.trim();
  const teacherRef = doc(db, "schoolTeacherProfiles", teacherId);
  try {
    // Снимок ДО обновления: старая ссылка (дедупликация пуша) и подписчики —
    // ровно как SchoolRepositoryImpl.setTeacherFile.
    const before = await getDoc(teacherRef);
    const oldUrl = before.exists() ? before.data().fileUrl || "" : "";
    const subscribers = before.exists() && Array.isArray(before.data().subscribers)
      ? before.data().subscribers
      : [];
    const now = Date.now();
    await setDoc(
      teacherRef,
      { fileUrl, fileNote, fileUpdatedAt: now },
      { merge: true }
    );
    // История — только при реальной смене ссылки (правка описания не спамит).
    if (fileUrl !== oldUrl) {
      await addDoc(collection(teacherRef, "lessonFiles"), {
        fileUrl,
        fileNote,
        updatedAt: now,
      });
      if (subscribers.length > 0) {
        await addDoc(collection(teacherRef, "fileNotifications"), {
          subscribers,
          fileNote,
          createdAt: now,
          notified: false,
        });
      }
    }
    toast("Файл урока сохранён" + (fileUrl !== oldUrl ? " — подписчики получат push" : ""));
    logAdminAction(
      "SCHOOL_TEACHER_FILE_SET",
      teacherId + (fileUrl ? " → " + fileUrl.slice(0, 100) : " → (ссылка снята)")
    );
    closeFileModal();
  } catch (err) {
    handleErr("Не удалось сохранить файл урока")(err);
  }
}

function initFileModal() {
  $("form-teacher-file").addEventListener("submit", (e) => {
    e.preventDefault();
    saveTeacherFile();
  });
  $("btn-close-file-modal").addEventListener("click", closeFileModal);
  $("file-modal-overlay").addEventListener("click", (e) => {
    if (e.target === $("file-modal-overlay")) closeFileModal();
  });
}

function questionItem(teacherName, docSnap, showTeacher = false) {
  const q = docSnap.data();
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      ${showTeacher ? '<span class="badge badge-blue">👩\u200d🏫 ' + esc(teacherName) + "</span>" : ""}
      <span class="item-title">${esc(q.fromName || "Ученик")}</span>
      ${q.hidden ? '<span class="badge badge-dim">Скрыт</span>' : ""}
      <span class="item-date">${fmtDate(q.createdAt)}</span>
    </div>
    <div class="item-text">${esc(q.text || "")}</div>
    ${
      q.answer
        ? `<div class="question-answer">💬 <b>Ответ:</b> ${esc(q.answer)}
             <div class="item-sub">${fmtDate(q.answeredAt)}</div></div>`
        : ""
    }
    <div class="answer-form">
      <input type="text" placeholder="${q.answer ? "Новый ответ…" : "Ответить…"}">
      <button class="btn-primary" data-act="answer">Ответить</button>
    </div>
    <div class="item-actions">
      <button class="btn-secondary" data-act="hide">${q.hidden ? "Показать" : "Скрыть"}</button>
    </div>`;

  el.querySelector('[data-act="answer"]').addEventListener("click", () => {
    const input = el.querySelector(".answer-form input");
    const answer = input.value.trim();
    if (!answer) return toast("Ответ не может быть пустым", false);
    setDoc(
      doc(db, "schoolTeacherProfiles", teacherName, "questions", docSnap.id),
      { answer, answeredAt: Date.now() },
      { merge: true }
    )
      .then(() => {
        input.value = "";
        toast("Ответ сохранён");
        logAdminAction(
          "SCHOOL_QUESTION_ANSWERED",
          teacherName + " · вопрос: " + (q.text || "").slice(0, 80)
        );
      })
      .catch(handleErr("Не удалось сохранить ответ"));
  });

  el.querySelector('[data-act="hide"]').addEventListener("click", () => {
    setDoc(
      doc(db, "schoolTeacherProfiles", teacherName, "questions", docSnap.id),
      { hidden: !q.hidden },
      { merge: true }
    )
      .then(() => {
        toast(q.hidden ? "Вопрос показан" : "Вопрос скрыт");
        logAdminAction(
          "SCHOOL_QUESTION_HIDDEN",
          (q.hidden ? "показан" : "скрыт") + " · " + teacherName
        );
      })
      .catch(handleErr("Не удалось изменить видимость вопроса"));
  });
  return el;
}

function openQuestions(teacherName, displayName) {
  if (questionsUnsub) questionsUnsub();
  $("questions-teacher-name").textContent = displayName;
  $("questions-card").classList.remove("hidden");
  $("questions-card").scrollIntoView({ behavior: "smooth" });
  const listEl = $("questions-list");
  setLoading(listEl);
  questionsUnsub = onSnapshot(
    query(
      collection(db, "schoolTeacherProfiles", teacherName, "questions"),
      orderBy("createdAt", "desc"),
      limit(100)
    ),
    (snap) => {
      // Статистика: сколько вопросов, сколько отвечено и среднее время ответа
      // (answeredAt - createdAt по отвеченным, как на странице учителя).
      const answered = snap.docs.filter((d) => d.data().answer);
      const avgMs = answered.length
        ? answered.reduce(
            (sum, d) => sum + Math.max(0, (d.data().answeredAt || 0) - (d.data().createdAt || 0)),
            0
          ) / answered.length
        : 0;
      $("questions-stats").textContent =
        "Вопросов: " + snap.size + " · отвечено: " + answered.length +
        (avgMs > 0 ? " · ср. время ответа: " + fmtDuration(avgMs) : "");
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Вопросов пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => listEl.appendChild(questionItem(teacherName, d)));
    },
    handleErr("Не удалось загрузить вопросы")
  );
}

$("btn-close-questions").addEventListener("click", () => {
  if (questionsUnsub) questionsUnsub();
  questionsUnsub = null;
  $("questions-card").classList.add("hidden");
});

function startTeachers() {
  $("form-add-teacher").addEventListener("submit", async (e) => {
    e.preventDefault();
    const name = $("teacher-name").value.trim();
    const subject = $("teacher-subject").value.trim();
    if (!name) return;
    try {
      // id документа = имя учителя (стабильный ключ, как upsertTeacherProfile)
      await setDoc(
        doc(db, "schoolTeacherProfiles", name),
        {
          name,
          subject,
          linkedUserId: "",
          linkedUserName: "",
        },
        { merge: true }
      );
      $("teacher-name").value = "";
      $("teacher-subject").value = "";
      toast("Профиль учителя создан");
      logAdminAction(
        "SCHOOL_TEACHER_CREATED",
        name + (subject ? " · " + subject : "")
      );
    } catch (err) {
      handleErr("Не удалось создать профиль")(err);
    }
  });

  const listEl = $("teachers-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "schoolTeacherProfiles"), orderBy("name", "asc"), limit(200)),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Профилей пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => listEl.appendChild(teacherItem(d)));
    },
    handleErr("Не удалось загрузить профили учителей")
  );
}

/* ------------------------------------------------------------------ */
/* Секции «Идеи» и «Отзывы» (только чтение) + CSV-экспорт              */
/* ------------------------------------------------------------------ */

let ideasCache = [];
let reviewsCache = [];
// НОВОЕ (экспорт CSV): снимки последних загруженных новостей и опросов —
// разделы уже держат подписку, поэтому выгрузка не делает лишних запросов.
let newsCache = [];
let pollsCache = [];

function csvEscape(value) {
  return '"' + String(value ?? "").replace(/"/g, '""') + '"';
}

// BOM + «;» — чтобы CSV с кириллицей открывался в Excel без настройки импорта.
function downloadCsv(filename, header, rows) {
  const lines = [header, ...rows]
    .map((row) => row.map(csvEscape).join(";"))
    .join("\r\n");
  const blob = new Blob(["\ufeff" + lines], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

function initCsvExport() {
  $("btn-export-ideas").addEventListener("click", () => {
    if (!ideasCache.length) return toast("Идей пока нет — выгружать нечего", false);
    downloadCsv(
      "yodo-school-ideas.csv",
      ["Автор", "Дата", "Идея"],
      ideasCache.map((i) => [
        i.authorName || "Ученик",
        fmtDate(i.createdAt),
        i.text || "",
      ])
    );
    toast("CSV идей скачан (" + ideasCache.length + ")");
  });
  $("btn-export-reviews").addEventListener("click", () => {
    if (!reviewsCache.length) return toast("Отзывов пока нет — выгружать нечего", false);
    downloadCsv(
      "yodo-school-reviews.csv",
      ["Автор", "Оценка", "Понравилось", "Не понравилось", "Дата"],
      reviewsCache.map((r) => [
        r.authorName || "Ученик",
        r.stars || 0,
        r.liked || "",
        r.disliked || "",
        fmtDate(r.updatedAt),
      ])
    );
    toast("CSV отзывов скачан (" + reviewsCache.length + ")");
  });

  // НОВОЕ (экспорт CSV): новости — из уже загруженного списка (newsCache).
  $("btn-export-news").addEventListener("click", () => {
    if (!newsCache.length) return toast("Новостей пока нет — выгружать нечего", false);
    downloadCsv(
      "yodo-school-news.csv",
      ["Отправитель", "Текст", "Дата события", "Статус", "Дата публикации", "Push"],
      newsCache.map((n) => [
        n.sender || "",
        n.text || "",
        n.eventDate || "",
        n.published === false
          ? n.publishAt
            ? "запланирована на " + fmtDate(n.publishAt)
            : "черновик"
          : "опубликована",
        fmtDate(n.pubDate),
        n.notified === false ? "в очереди" : "отправлен",
      ])
    );
    toast("CSV новостей скачан (" + newsCache.length + ")");
  });

  // НОВОЕ (экспорт CSV): опросы с результатами голосования (pollsCache).
  $("btn-export-polls").addEventListener("click", () => {
    if (!pollsCache.length) return toast("Опросов пока нет — выгружать нечего", false);
    downloadCsv(
      "yodo-school-polls.csv",
      ["Вопрос", "Создан", "Статус", "Голосовавших", "Всего голосов", "Результаты"],
      pollsCache.map((p) => {
        const options = p.options || [];
        const votes = p.votes || {};
        const totalVotes = options.reduce((sum, _, i) => sum + (votes[String(i)] || 0), 0);
        return [
          p.question || "",
          fmtDate(p.createdAt),
          p.closed ? "завершён" : "открыт",
          Object.keys(p.voters || {}).length,
          totalVotes,
          options.map((opt, i) => opt + ": " + (votes[String(i)] || 0)).join(" | "),
        ];
      })
    );
    toast("CSV опросов скачан (" + pollsCache.length + ")");
  });

  // НОВОЕ (экспорт CSV): пользователи читаются по кнопке (полный список, как
  // в сводке) — раздел «Пользователи» держит только поиск, а не весь список.
  $("btn-export-users").addEventListener("click", async () => {
    const btn = $("btn-export-users");
    btn.disabled = true;
    try {
      const snap = await getDocs(collection(db, "users"));
      if (snap.empty) return toast("Пользователей нет — выгружать нечего", false);
      const rows = snap.docs.map((d) => {
        const u = d.data();
        return [
          u.displayName || "",
          u.username ? "@" + u.username : "",
          u.email || "",
          u.publicId || "",
          d.id,
          fmtDate(u.createdAt),
          fmtDate(u.lastSeen),
          u.isOnline === true && u.hideOnlineStatus !== true ? "да" : "",
        ];
      });
      downloadCsv(
        "yodo-users.csv",
        ["Имя", "Username", "Email", "Публичный ID", "UID", "Регистрация", "Последняя активность", "Онлайн"],
        rows
      );
      toast("CSV пользователей скачан (" + rows.length + ")");
    } catch (err) {
      handleErr("Не удалось выгрузить пользователей")(err);
    } finally {
      btn.disabled = false;
    }
  });
}

function startIdeas() {
  const listEl = $("ideas-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "schoolIdeas"), orderBy("createdAt", "desc"), limit(100)),
    (snap) => {
      ideasCache = snap.docs.map((d) => d.data());
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Идей пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => {
        const i = d.data();
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            <span class="item-title">${esc(i.authorName || "Ученик")}</span>
            <span class="item-date">${fmtDate(i.createdAt)}</span>
          </div>
          <div class="item-text">${esc(i.text || "")}</div>`;
        listEl.appendChild(el);
      });
    },
    handleErr("Не удалось загрузить идеи")
  );
}

function startReviews() {
  const listEl = $("reviews-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "schoolReviews"), orderBy("updatedAt", "desc"), limit(100)),
    (snap) => {
      reviewsCache = snap.docs.map((d) => d.data());
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Отзывов пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      let starsSum = 0;
      snap.forEach((d) => {
        const r = d.data();
        starsSum += r.stars || 0;
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            <span class="item-title">${"⭐".repeat(Math.max(0, Math.min(5, r.stars || 0)))}</span>
            <span class="item-date">${fmtDate(r.updatedAt)}</span>
          </div>
          <div class="item-sub">${esc(r.authorName || "Ученик")}</div>
          ${r.liked ? `<div class="item-text">👍 ${esc(r.liked)}</div>` : ""}
          ${r.disliked ? `<div class="item-text">👎 ${esc(r.disliked)}</div>` : ""}`;
        listEl.appendChild(el);
      });
      const summary = document.createElement("p");
      summary.className = "card-hint";
      summary.textContent = snap.size
        ? `Средняя оценка: ${(starsSum / snap.size).toFixed(1)} из 5 (${snap.size} отзывов)`
        : "";
      listEl.prepend(summary);
    },
    handleErr("Не удалось загрузить отзывы")
  );
}

/* ------------------------------------------------------------------ */
/* Секция «Жалобы» — сквозная очередь модерации (chats/{chatId}/reports) */
/* ------------------------------------------------------------------ */

// 1:1 с Report.kt — значения enum и подписи причин/статусов.
const REPORT_REASONS = {
  SPAM: "Спам",
  HARASSMENT: "Оскорбления или травля",
  VIOLENCE: "Насилие или угрозы",
  ILLEGAL_CONTENT: "Запрещённый контент",
  FRAUD: "Мошенничество",
  NSFW: "Неприемлемый контент (NSFW)",
  ADVERTISING: "Реклама",
  OTHER: "Другое",
  APPEAL: "Обжалование блокировки",
};
const REPORT_STATUS_LABELS = {
  PENDING: "На рассмотрении",
  RESOLVED: "Решена",
  DISMISSED: "Отклонена",
};

let reportsCache = []; // снапшот последнего запроса для CSV
let reportsLimit = 200; // НОВОЕ (пагинация): размер окна ленты жалоб
let reportsFilter = "PENDING";
// НОВОЕ (расширенная модерация): независимый от статуса фильтр по типу жалобы.
let reportsReasonFilter = "ALL";
let reportsUnsub = null;

// Мягкое удаление сообщения — поля 1:1 с MessageRepositoryImpl.deleteMessage
// (deletedByAdmin=true показывает в чате «Сообщение удалено администратором»).
// Один и тот же набор полей используется точечным и массовым удалением.
function softDeletePayload() {
  return {
    isDeleted: true,
    text: "",
    deletedByAdmin: true,
    imageBase64: deleteField(),
    fileBase64: deleteField(),
    fileName: deleteField(),
    fileMimeType: deleteField(),
    fileSizeBytes: deleteField(),
    locationLat: deleteField(),
    locationLng: deleteField(),
  };
}

async function softDeleteMessage(chatId, messageId) {
  await updateDoc(doc(db, "chats", chatId, "messages", messageId), softDeletePayload());
}

/** Текст-превью сообщения для списков админки (та же логика, что в превью чата). */
function messagePreviewText(m) {
  return m.encrypted ? "🔒 Сообщение"
    : m.text ? m.text
    : m.voiceBase64 ? "🎤 Голосовое сообщение"
    : m.isViewOnce ? "📷 Фото (один просмотр)"
    : m.imagesBase64 ? "📷 Фото (" + (m.imagesBase64.length || 1) + ")"
    : m.imageBase64 ? "📷 Фото"
    : m.locationLat != null ? "📍 Геопозиция"
    : m.fileBase64 ? "📎 " + (m.fileName || "Файл")
    : "";
}

// Пересчёт превью списка чатов, если удалили последнее сообщение —
// перенос refreshLastMessagePreviewIfNeeded (иначе в списке чатов
// останется висеть текст удалённого сообщения).
async function refreshChatPreviewAfterDelete(chatId, messageId) {
  const chatRef = doc(db, "chats", chatId);
  const chatSnap = await getDoc(chatRef);
  if (!chatSnap.exists()) return;
  // lastMessageId может отсутствовать у старых чатов — тогда пересчёт не нужен.
  if (chatSnap.get("lastMessageId") !== messageId) return;
  const latest = await getDocs(
    query(collection(db, "chats", chatId, "messages"), orderBy("timestamp", "desc"), limit(20))
  );
  // isDeleted отсутствует у неудалённых сообщений — фильтруем на клиенте.
  const remaining = latest.docs.find((d) => d.get("isDeleted") !== true);
  if (!remaining) {
    await updateDoc(chatRef, {
      lastMessage: "",
      lastMessageSenderId: null,
      lastMessageStatus: null,
      lastMessageId: null,
    });
    return;
  }
  const r = remaining.data();
  const previewText = messagePreviewText(r);
  await updateDoc(chatRef, {
    lastMessage: previewText,
    lastMessageTimestamp: r.timestamp || 0,
    lastMessageSenderId: r.senderId || null,
    lastMessageStatus: r.status || "SENT",
    lastMessageId: remaining.id,
  });
}

// Блокировка аккаунта — формат 1:1 с UserRepositoryImpl.setGlobalBlock.
async function setGlobalBlock(uid, reason) {
  await setDoc(doc(db, "globalBlocks", uid), {
    reason: reason || "",
    blockedBy: auth.currentUser.uid,
    blockedByName: adminActorName || auth.currentUser.email || "Админ",
    blockedAt: Date.now(),
  });
  addDoc(collection(db, "moderationNotifications"), {
    userId: uid,
    title: "Аккаунт заблокирован",
    body: reason ? "Причина: " + reason.slice(0, 200) : "Ваш аккаунт заблокирован администрацией",
    notified: false,
    createdAt: Date.now(),
  }).catch(() => {});
}

function reportItem(docSnap) {
  const r = docSnap.data();
  const status = r.status || "PENDING";
  const isPending = status === "PENDING";
  const el = document.createElement("div");
  el.className = "item";
  const statusBadge =
    status === "PENDING" ? '<span class="badge badge-yellow">На рассмотрении</span>'
    : status === "RESOLVED" ? '<span class="badge badge-green">Решена</span>'
    : '<span class="badge badge-dim">Отклонена</span>';
  el.innerHTML = `
    <div class="item-head">
      <span class="item-title">${r.isAppeal ? "🔔 Обжалование: " : ""}${esc(r.targetUserName || "Пользователь")}</span>
      ${statusBadge}
      <span class="item-date">${fmtDate(r.createdAt)}</span>
    </div>
    <div class="item-sub">Жалоба от ${esc(r.reporterName || "—")} · причина: ${esc(REPORT_REASONS[r.reason] || r.reason || "?")}</div>
    ${r.targetMessagePreview ? `<div class="item-text report-preview">💬 ${esc(r.targetMessagePreview)}</div>` : ""}
    ${r.customReasonText ? `<div class="item-text">${esc(r.customReasonText)}</div>` : ""}
    ${
      !isPending
        ? `<div class="question-answer">✅ <b>${esc(REPORT_STATUS_LABELS[status] || status)}</b> · ${esc(r.reviewedByName || "")}, ${fmtDate(r.reviewedAt)}
             ${r.resolution ? " · " + esc(r.resolution) : ""}${r.reviewerComment ? "<br>" + esc(r.reviewerComment) : ""}</div>`
        : ""
    }`;
  const actions = document.createElement("div");
  actions.className = "item-actions";
  if (r.targetType === "MESSAGE" && r.targetMessageId) {
    if (isPending) {
      const deleteBtn = document.createElement("button");
      deleteBtn.type = "button";
      deleteBtn.className = "btn-danger";
      deleteBtn.textContent = "Удалить сообщение";
      deleteBtn.addEventListener("click", () => resolveReportAction(docSnap, "deleteMessage"));
      actions.appendChild(deleteBtn);
    }
    // НОВОЕ (расширенная модерация): контекст сообщения доступен и по закрытым
    // жалобам — чтобы понимать, почему решение было принято.
    const ctxBtn = document.createElement("button");
    ctxBtn.type = "button";
    ctxBtn.className = "btn-secondary";
    ctxBtn.textContent = "Контекст";
    ctxBtn.addEventListener("click", () => openReportContext(docSnap));
    actions.appendChild(ctxBtn);
  }
  if (isPending) {
    if (!r.isAppeal) {
      const blockBtn = document.createElement("button");
      blockBtn.type = "button";
      blockBtn.className = "btn-danger";
      blockBtn.textContent = "Заблокировать автора";
      blockBtn.addEventListener("click", () => resolveReportAction(docSnap, "blockUser"));
      actions.appendChild(blockBtn);
    }
    const dismissBtn = document.createElement("button");
    dismissBtn.type = "button";
    dismissBtn.className = "btn-secondary";
    dismissBtn.textContent = "Отклонить";
    dismissBtn.addEventListener("click", () => resolveReportAction(docSnap, "dismiss"));
    actions.appendChild(dismissBtn);
  }
  if (actions.children.length) el.appendChild(actions);
  return el;
}

async function resolveReportAction(docSnap, action) {
  const r = docSnap.data();
  const chatId = docSnap.ref.parent.parent.id;
  const targetName = r.targetUserName || "пользователя";
  try {
    if (action === "deleteMessage") {
      // НОВОЕ (причины удаления): спрашиваем причину и сохраняем копию сообщения
      // в историю удалённых (восстановление возможно 30 дней).
      const pick = await askDeleteReason(
        `Удалить сообщение «${(r.targetMessagePreview || "").slice(0, 60)}» у ${targetName}? В чате появится «Сообщение удалено администратором».`
      );
      if (!pick) return;
      const msgSnap = await getDoc(doc(db, "chats", chatId, "messages", r.targetMessageId));
      const entry = msgSnap.exists()
        ? buildDeletedEntry(chatId, r.targetMessageId, msgSnap.data(), {
            reason: pick.reason, reasonText: pick.reasonText, source: "WEB",
          })
        : null;
      await softDeleteMessage(chatId, r.targetMessageId);
      if (entry) await archiveDeletedEntries([entry]);
      await refreshChatPreviewAfterDelete(chatId, r.targetMessageId);
      await finalizeReport(docSnap, "RESOLVED", "MESSAGE_DELETED", "Сообщение удалено администратором");
      logAdminAction(
        "MESSAGE_DELETED_WITH_REASON",
        `Причина: ${DELETE_REASONS[pick.reason] || pick.reason}${pick.reasonText ? " — " + pick.reasonText : ""} (жалоба ${chatId}/${docSnap.id})`,
        r.targetUserId, r.targetUserName
      );
      toast("Сообщение удалено, жалоба закрыта");
      return;
    }
    const confirmText =
      action === "blockUser"
        ? `Заблокировать аккаунт ${targetName}? ${r.isAppeal ? "Обжалование при этом будет отклонено. " : ""}Он не сможет пользоваться приложением.`
        : `Отклонить жалобу на ${targetName}?`;
    if (!confirm(confirmText)) return;
    if (action === "blockUser") {
      await setGlobalBlock(r.targetUserId, "Нарушение правил по жалобе: " + (REPORT_REASONS[r.reason] || r.reason));
      await finalizeReport(docSnap, "RESOLVED", "USER_BANNED", "Аккаунт заблокирован администратором");
      logAdminAction("REPORT_RESOLVED_USER_BANNED", "Жалоба " + chatId + "/" + docSnap.id, r.targetUserId, r.targetUserName);
      toast("Аккаунт заблокирован, жалоба закрыта");
    } else {
      await finalizeReport(docSnap, "DISMISSED", "DISMISSED", "Жалоба отклонена администратором");
      logAdminAction("REPORT_DISMISSED", "Жалоба " + chatId + "/" + docSnap.id, r.targetUserId, r.targetUserName);
      toast("Жалоба отклонена");
    }
  } catch (err) {
    handleErr("Не удалось выполнить действие")(err);
  }
}

// Закрытие жалобы — поля 1:1 с ReportRepositoryImpl.finalizeReport.
async function finalizeReport(docSnap, status, resolution, comment) {
  await updateDoc(docSnap.ref, {
    status,
    resolution,
    reviewedBy: auth.currentUser.uid,
    reviewedByName: adminActorName || auth.currentUser.email || "Админ",
    reviewedAt: Date.now(),
    reviewerComment: (comment || "").slice(0, 1000),
  });
}

/* ------------------------------------------------------------------ */
/* Контекст жалобы: сообщения до и после нарушителя                     */
/* ------------------------------------------------------------------ */

const CONTEXT_WINDOW = 3; // по 3 сообщения с каждой стороны + само сообщение
const contextUserNames = new Map();

/** Имя автора сообщения для контекста (кэш на сессию страницы). */
async function resolveUserName(uid, fallback = "") {
  if (!uid) return fallback || "—";
  if (uid === "support_system") return "Поддержка";
  if (contextUserNames.has(uid)) return contextUserNames.get(uid);
  let name = fallback;
  if (!name) {
    try {
      const snap = await getDoc(doc(db, "users", uid));
      name = snap.exists() ? snap.data().displayName || snap.data().username || "" : "";
    } catch (e) { /* best-effort */ }
  }
  name = name || "UID " + uid.slice(0, 10) + "…";
  contextUserNames.set(uid, name);
  return name;
}

function closeReportContext() {
  $("report-context-overlay").classList.add("hidden");
}

async function openReportContext(docSnap) {
  const r = docSnap.data();
  const chatId = docSnap.ref.parent.parent.id;
  const box = $("report-context-messages");
  $("report-context-sub").textContent =
    `Чат ${chatId} · жалоба от ${r.reporterName || "—"} · причина: ${REPORT_REASONS[r.reason] || r.reason || "?"}`;
  box.innerHTML = '<p class="empty-note">Загрузка…</p>';
  $("report-context-overlay").classList.remove("hidden");
  try {
    const targetId = r.targetMessageId;
    const targetSnap = await getDoc(doc(db, "chats", chatId, "messages", targetId));
    if (!targetSnap.exists()) {
      box.innerHTML =
        '<p class="empty-note">Сообщение уже удалено — доступен только текст из жалобы.</p>' +
        (r.targetMessagePreview
          ? `<div class="ctx-msg ctx-msg-target"><div class="item-text">${esc(r.targetMessagePreview)}</div></div>`
          : "");
      return;
    }
    const ts = targetSnap.get("timestamp") || 0;
    const msgs = collection(db, "chats", chatId, "messages");
    const [beforeSnap, afterSnap] = await Promise.all([
      getDocs(query(msgs, where("timestamp", "<=", ts), orderBy("timestamp", "desc"), limit(CONTEXT_WINDOW + 1))),
      getDocs(query(msgs, where("timestamp", ">=", ts), orderBy("timestamp", "asc"), limit(CONTEXT_WINDOW + 1))),
    ]);
    const seen = new Set();
    const ordered = [];
    beforeSnap.docs
      .slice()
      .reverse()
      .concat(afterSnap.docs)
      .forEach((d) => {
        if (seen.has(d.id)) return;
        seen.add(d.id);
        ordered.push(d);
      });
    const names = await Promise.all(
      ordered.map((d) =>
        resolveUserName(
          d.get("senderId") || "",
          d.get("senderId") === r.targetUserId ? r.targetUserName || "" : ""
        )
      )
    );
    box.innerHTML = "";
    ordered.forEach((d, i) => {
      const m = d.data();
      const isTarget = d.id === targetId;
      const text =
        m.isDeleted === true
          ? m.deletedByAdmin
            ? "Сообщение удалено администратором"
            : "Сообщение удалено"
          : messagePreviewText(m);
      const el = document.createElement("div");
      el.className = "ctx-msg" + (isTarget ? " ctx-msg-target" : "");
      el.innerHTML = `
        <div class="ctx-msg-head">
          <span class="ctx-msg-name">${esc(names[i])}</span>
          ${isTarget ? '<span class="badge badge-yellow">жалоба</span>' : ""}
          <span class="item-date">${fmtDate(m.timestamp)}</span>
        </div>
        <div class="item-text">${esc(text || "(без текста)")}</div>`;
      box.appendChild(el);
    });
  } catch (err) {
    box.innerHTML = "";
    handleErr("Не удалось загрузить контекст (проверьте правила Firestore)")(err);
  }
}

/* ------------------------------------------------------------------ */
/* Массовое удаление сообщений нарушителя по периоду                    */
/* ------------------------------------------------------------------ */

// Сквозной поиск по всем чатам: требует collection-group индекса
// messages(senderId, timestamp) и сквозного чтения messages админам.
const BULK_DELETE_LIMIT = 500;
const BULK_DELETE_BATCH = 400;
let bulkDeleteMatches = [];

function datetimeLocalToMs(value) {
  if (!value) return null;
  const ms = new Date(value).getTime();
  return Number.isNaN(ms) ? null : ms;
}

async function bulkDeleteFind() {
  const uid = $("bulk-del-uid").value.trim();
  const statusEl = $("bulk-del-status");
  const previewEl = $("bulk-del-preview");
  if (!uid) {
    toast("Укажите UID пользователя", false);
    return;
  }
  const from = datetimeLocalToMs($("bulk-del-from").value);
  const to = datetimeLocalToMs($("bulk-del-to").value);
  if (from != null && to != null && from > to) {
    toast("Дата «С» позже даты «По»", false);
    return;
  }
  bulkDeleteMatches = [];
  $("btn-bulk-del-run").disabled = true;
  statusEl.textContent = "";
  setLoading(previewEl);
  try {
    const conds = [where("senderId", "==", uid)];
    if (from != null) conds.push(where("timestamp", ">=", from));
    if (to != null) conds.push(where("timestamp", "<=", to));
    const snap = await getDocs(
      query(
        collectionGroup(db, "messages"),
        ...conds,
        orderBy("timestamp", "asc"),
        limit(BULK_DELETE_LIMIT)
      )
    );
    // Уже удалённые не трогаем; у старых сообщений поля isDeleted может не быть.
    const docs = snap.docs.filter((d) => d.get("isDeleted") !== true);
    bulkDeleteMatches = docs.map((d) => ({
      ref: d.ref,
      id: d.id,
      chatId: d.ref.parent.parent.id,
      senderId: d.get("senderId") || "",
      timestamp: d.get("timestamp") || 0,
      preview: messagePreviewText(d.data()),
      // НОВОЕ (история удалённых): сохраняем текст и (если небольшое) медиа,
      // чтобы удалённое можно было восстановить из истории.
      original: pickOriginalFields(d.data(), 200000),
    }));
    const chatCount = new Set(bulkDeleteMatches.map((m) => m.chatId)).size;
    const limited =
      snap.size >= BULK_DELETE_LIMIT ? " Окно поиска ограничено 500 сообщениями — сузьте период." : "";
    statusEl.textContent = bulkDeleteMatches.length
      ? `Найдено сообщений: ${bulkDeleteMatches.length} в ${chatCount} чатах.${limited}`
      : `Сообщений не найдено.${limited}`;
    renderBulkDeletePreview();
    $("btn-bulk-del-run").disabled = !bulkDeleteMatches.length;
  } catch (err) {
    previewEl.innerHTML = "";
    handleErr("Не удалось найти сообщения (проверьте, что правила и индекс Firestore задеплоены)")(err);
  }
}

function renderBulkDeletePreview() {
  const el = $("bulk-del-preview");
  if (!bulkDeleteMatches.length) {
    el.innerHTML = "";
    return;
  }
  el.innerHTML = "";
  bulkDeleteMatches.slice(0, 50).forEach((m) => {
    const item = document.createElement("div");
    item.className = "item";
    item.innerHTML = `
      <div class="item-head">
        <span class="item-title">${esc(m.preview || "(без текста)")}</span>
        <span class="item-date">${fmtDate(m.timestamp)}</span>
      </div>
      <div class="item-sub">чат ${esc(m.chatId)} · сообщение ${esc(m.id)}</div>`;
    el.appendChild(item);
  });
  if (bulkDeleteMatches.length > 50) {
    const note = document.createElement("p");
    note.className = "empty-note";
    note.textContent = `…и ещё ${bulkDeleteMatches.length - 50} сообщений (в списке первые 50).`;
    el.appendChild(note);
  }
}

async function bulkDeleteRun() {
  if (!bulkDeleteMatches.length) {
    toast("Сначала найдите сообщения", false);
    return;
  }
  const matches = bulkDeleteMatches;
  const chatCount = new Set(matches.map((m) => m.chatId)).size;
  // НОВОЕ (причины удаления): одна причина на всю партию.
  const pick = await askDeleteReason(
    `Удалить ${matches.length} сообщений в ${chatCount} чатах? Отменить это нельзя: в переписке появится «Сообщение удалено администратором».`
  );
  if (!pick) return;
  const uid = $("bulk-del-uid").value.trim();
  const statusEl = $("bulk-del-status");
  $("btn-bulk-del-run").disabled = true;
  $("btn-bulk-del-find").disabled = true;
  let deleted = 0;
  try {
    for (let i = 0; i < matches.length; i += BULK_DELETE_BATCH) {
      const batch = writeBatch(db);
      matches
        .slice(i, i + BULK_DELETE_BATCH)
        .forEach((m) => batch.update(m.ref, softDeletePayload()));
      await batch.commit();
      deleted += Math.min(BULK_DELETE_BATCH, matches.length - i);
      statusEl.textContent = `Удалено ${deleted} из ${matches.length}…`;
    }
    // НОВОЕ (история удалённых): архивируем каждое удалённое сообщение с общей
    // причиной, чтобы его можно было восстановить в течение 30 дней.
    await archiveDeletedEntries(
      matches.map((m) => deletedEntry(m.chatId, m.id, m.senderId, m.original, {
        reason: pick.reason, reasonText: pick.reasonText, source: "WEB",
      }))
    );
    // Пересчёт превью списка чата нужен только там, где удалили последнее
    // сообщение, поэтому на чат достаточно самого свежего удалённого.
    const newestPerChat = new Map();
    matches.forEach((m) => {
      const cur = newestPerChat.get(m.chatId);
      if (!cur || m.timestamp > cur.timestamp) newestPerChat.set(m.chatId, m);
    });
    let staleChats = 0;
    for (const [chatId, m] of newestPerChat) {
      try {
        await refreshChatPreviewAfterDelete(chatId, m.id);
      } catch (e) {
        staleChats += 1;
      }
    }
    logAdminAction(
      "REPORT_BULK_MESSAGES_DELETED",
      `Массовое удаление: ${deleted} сообщений в ${chatCount} чатах · причина: ${DELETE_REASONS[pick.reason] || pick.reason}${pick.reasonText ? " — " + pick.reasonText : ""}`,
      uid,
      await resolveUserName(uid, "")
    );
    toast(`Удалено сообщений: ${deleted}`);
    statusEl.textContent =
      `Готово: удалено ${deleted} сообщений в ${chatCount} чатах.` +
      (staleChats
        ? ` Превью ${staleChats} чатов не обновлено (админ не участник) — обновится при следующем сообщении.`
        : "");
    bulkDeleteMatches = [];
    $("bulk-del-preview").innerHTML = "";
  } catch (err) {
    handleErr("Не удалось удалить сообщения")(err);
  } finally {
    $("btn-bulk-del-find").disabled = false;
  }
}

/* ------------------------------------------------------------------ */
/* Причины удаления, история удалённых и автофильтр                    */
/* ------------------------------------------------------------------ */

// НОВОЕ (причины удаления): стандартный набор причин, который предлагается при
// любом удалении сообщения. Ключи совпадают с Android-константами истории
// удалённых (MessageRepositoryImpl.archiveDeletedMessage) — менять согласованно.
const DELETE_REASONS = {
  SPAM: "Спам",
  INSULT: "Оскорбление",
  NSFW: "NSFW",
  RULES: "Нарушение правил",
  OTHER: "Другое",
};
// Для отображения в истории: плюс причина, которую проставляет автофильтр.
const DELETE_REASONS_WITH_AUTO = Object.assign({ AUTO: "Автофильтр" }, DELETE_REASONS);
const DELETED_RETENTION_MS = 30 * 24 * 60 * 60 * 1000; // 30 дней
const AUTOFILTER_SCAN_LIMIT = 200;

function reasonLabel(entry) {
  if (entry.source === "AUTO" && !DELETE_REASONS[entry.reason]) return "Автофильтр";
  return DELETE_REASONS_WITH_AUTO[entry.reason] || entry.reason || "?";
}

/**
 * Модалка выбора причины удаления. Возвращает Promise с { reason, reasonText }
 * либо null, если админ отменил. Кнопка «Удалить» сама служит подтверждением.
 */
function askDeleteReason(subtitle) {
  return new Promise((resolve) => {
    const overlay = $("delete-reason-overlay");
    const options = $("delete-reason-options");
    const customText = $("delete-reason-text");
    $("delete-reason-sub").textContent = subtitle || "";
    customText.value = "";
    customText.classList.add("hidden");
    let selected = "RULES";
    options.innerHTML = "";
    Object.keys(DELETE_REASONS).forEach((key) => {
      const label = document.createElement("label");
      label.className = "radio-row";
      label.innerHTML =
        `<input type="radio" name="delete-reason" value="${key}"${key === selected ? " checked" : ""}> ${esc(DELETE_REASONS[key])}`;
      label.querySelector("input").addEventListener("change", () => {
        selected = key;
        customText.classList.toggle("hidden", key !== "OTHER");
      });
      options.appendChild(label);
    });
    overlay.classList.remove("hidden");
    const onOk = () => {
      if (selected === "OTHER" && !customText.value.trim()) {
        toast("Укажите причину в поле «Другое»", false);
        return;
      }
      finish({ reason: selected, reasonText: selected === "OTHER" ? customText.value.trim() : "" });
    };
    const onCancel = () => finish(null);
    const onOverlay = (e) => { if (e.target === overlay) finish(null); };
    const onKey = (e) => { if (e.key === "Escape") finish(null); };
    function finish(value) {
      overlay.classList.add("hidden");
      $("btn-delete-reason-ok").removeEventListener("click", onOk);
      $("btn-delete-reason-cancel").removeEventListener("click", onCancel);
      overlay.removeEventListener("click", onOverlay);
      document.removeEventListener("keydown", onKey);
      resolve(value);
    }
    $("btn-delete-reason-ok").addEventListener("click", onOk);
    $("btn-delete-reason-cancel").addEventListener("click", onCancel);
    overlay.addEventListener("click", onOverlay);
    document.addEventListener("keydown", onKey);
  });
}

/**
 * Поля сообщения, которые мягкое удаление стирает, — их и нужно сохранить в
 * архив, чтобы можно было восстановить. Медиа (base64) может быть тяжёлым:
 * пишем его, только если запись укладывается в лимит документа Firestore
 * (~1 МиБ); иначе текст восстановится, а медиа — нет (mediaSkipped).
 */
function pickOriginalFields(m, maxMediaChars = 700000) {
  const media = {};
  ["imageBase64", "fileBase64", "fileName", "fileMimeType", "fileSizeBytes", "locationLat", "locationLng"]
    .forEach((key) => {
      if (m[key] !== undefined && m[key] !== null) media[key] = m[key];
    });
  const out = {
    text: typeof m.text === "string" ? m.text : "",
    preview: messagePreviewText(m),
  };
  if (Object.keys(media).length) {
    let weight = 0;
    Object.values(media).forEach((v) => { weight += typeof v === "string" ? v.length : 16; });
    if (weight < maxMediaChars) out.media = media;
    else out.mediaSkipped = true;
  }
  return out;
}

/** Архивная запись истории удалённых — формат 1:1 с Android (archiveDeletedMessage). */
function deletedEntry(chatId, messageId, senderId, original, opts) {
  const entry = {
    chatId,
    messageId,
    senderId: senderId || "",
    preview: original.preview || "",
    originalText: original.text || "",
    reason: opts.reason || "OTHER",
    reasonText: opts.reasonText || "",
    source: opts.source || "WEB",
    deletedBy: auth.currentUser ? auth.currentUser.uid : "",
    deletedByName: adminActorName || (auth.currentUser ? auth.currentUser.email || "" : "Админ"),
    deletedAt: Date.now(),
    restored: false,
  };
  if (original.media) entry.media = original.media;
  if (original.mediaSkipped) entry.mediaSkipped = true;
  return entry;
}

function buildDeletedEntry(chatId, messageId, m, opts) {
  return deletedEntry(chatId, messageId, m.senderId || "", pickOriginalFields(m), opts);
}

/** Best-effort запись в архив: ошибка архива не должна ломать само удаление. */
async function archiveDeletedEntries(entries) {
  for (const entry of entries) {
    try {
      await addDoc(collection(db, "deletedMessages"), entry);
    } catch (e) { /* best-effort */ }
  }
}

/* ---------------------------- Автофильтр ---------------------------- */

const AUTOFILTER_DOC = "config/autoModeration";
const AUTOFILTER_TYPES = {
  LINK: "Ссылка / подстрока",
  DOMAIN: "Домен",
  TEXT: "Слово / фраза",
  REGEX: "Регулярное выражение",
};
let autofilterModel = { enabled: false, rules: [] };
let autofilterMatches = [];

function autofilterNewId() {
  return "r" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

/** Совпадение текста с одним правилом — та же семантика, что в Android (ruleMatches). */
function ruleMatches(rule, text) {
  if (!rule || !rule.pattern || !text) return false;
  const type = (rule.type || "LINK").toUpperCase();
  if (type === "REGEX") {
    try { return new RegExp(rule.pattern, "i").test(text); } catch (e) { return false; }
  }
  const pattern = String(rule.pattern).toLowerCase();
  if (type === "DOMAIN") {
    const hosts = text.match(/(?:https?:\/\/)?([a-z0-9-]+(?:\.[a-z0-9-]+)+)/gi) || [];
    return hosts.some((h) => {
      const host = h.toLowerCase().replace(/^https?:\/\//, "");
      return host === pattern || host.endsWith("." + pattern) || host.includes(pattern);
    });
  }
  return String(text).toLowerCase().includes(pattern);
}

function activeAutofilterRules() {
  return autofilterModel.rules.filter((r) => r && r.enabled !== false && r.pattern);
}

function firstMatchingRule(text) {
  return activeAutofilterRules().find((r) => ruleMatches(r, text)) || null;
}

function renderAutofilter() {
  $("autofilter-enabled").checked = autofilterModel.enabled === true;
  const el = $("autofilter-rules");
  const rules = autofilterModel.rules;
  if (!rules.length) {
    el.innerHTML = '<p class="empty-note">Правил пока нет — добавьте первое.</p>';
    return;
  }
  el.innerHTML = "";
  rules.forEach((rule) => {
    const row = document.createElement("div");
    row.className = "item";
    const disabled = rule.enabled === false;
    row.innerHTML = `
      <div class="autofilter-rule-row">
        <span class="badge badge-dim">${esc(AUTOFILTER_TYPES[(rule.type || "LINK").toUpperCase()] || rule.type || "?")}</span>
        <span class="autofilter-pattern">${esc(rule.pattern || "")}</span>
        <span class="badge ${disabled ? "badge-dim" : "badge-green"}">${esc(DELETE_REASONS[rule.reason] || rule.reason || "Нарушение правил")}</span>
        <span class="item-sub">${disabled ? "выключено" : "включено"}</span>
      </div>`;
    const actions = document.createElement("div");
    actions.className = "item-actions";
    const toggleBtn = document.createElement("button");
    toggleBtn.type = "button";
    toggleBtn.className = "btn-secondary";
    toggleBtn.textContent = disabled ? "Включить" : "Выключить";
    toggleBtn.addEventListener("click", () => {
      rule.enabled = disabled;
      saveAutofilter("Переключение правила: " + (rule.pattern || ""));
    });
    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "btn-danger";
    delBtn.textContent = "Удалить";
    delBtn.addEventListener("click", () => {
      if (!confirm("Удалить правило «" + (rule.pattern || "") + "»?")) return;
      autofilterModel.rules = autofilterModel.rules.filter((r) => r !== rule);
      saveAutofilter("Удаление правила: " + (rule.pattern || ""));
    });
    actions.appendChild(toggleBtn);
    actions.appendChild(delBtn);
    row.appendChild(actions);
    el.appendChild(row);
  });
}

async function loadAutofilter() {
  try {
    const snap = await getDoc(doc(db, AUTOFILTER_DOC));
    const d = snap.exists() ? snap.data() : {};
    autofilterModel = {
      enabled: d.enabled === true,
      rules: Array.isArray(d.rules) ? d.rules : [],
    };
  } catch (err) {
    handleErr("Не удалось загрузить правила автофильтра")(err);
    autofilterModel = { enabled: false, rules: [] };
  }
  renderAutofilter();
}

async function saveAutofilter(details) {
  try {
    await setDoc(doc(db, AUTOFILTER_DOC), {
      enabled: autofilterModel.enabled === true,
      rules: autofilterModel.rules,
      updatedAt: Date.now(),
      updatedByName: adminActorName || (auth.currentUser ? auth.currentUser.email || "" : "Админ"),
    });
    renderAutofilter();
    logAdminAction("AUTO_FILTER_RULE_SAVED", details || "Правила автофильтра");
    toast("Правила автофильтра сохранены");
  } catch (err) {
    handleErr("Не удалось сохранить правила автофильтра")(err);
  }
}

function renderAutofilterPreview() {
  const el = $("autofilter-preview");
  if (!autofilterMatches.length) { el.innerHTML = ""; return; }
  el.innerHTML = "";
  autofilterMatches.slice(0, 50).forEach((m) => {
    const item = document.createElement("div");
    item.className = "item";
    item.innerHTML = `
      <div class="item-head">
        <span class="item-title">${esc(m.preview || "(без текста)")}</span>
        <span class="badge badge-yellow">${esc(AUTOFILTER_TYPES[(m.rule.type || "LINK").toUpperCase()] || m.rule.type || "?")}</span>
        <span class="item-date">${fmtDate(m.timestamp)}</span>
      </div>
      <div class="item-sub">правило «${esc(m.rule.pattern || "")}» · причина: ${esc(DELETE_REASONS[m.rule.reason] || m.rule.reason || "Нарушение правил")} · чат ${esc(m.chatId)}</div>`;
    el.appendChild(item);
  });
  if (autofilterMatches.length > 50) {
    const note = document.createElement("p");
    note.className = "empty-note";
    note.textContent = `…и ещё ${autofilterMatches.length - 50} сообщений (в списке первые 50).`;
    el.appendChild(note);
  }
}

/** Проверка последних сообщений всех чатов на совпадение с правилами. */
async function autofilterScan() {
  const statusEl = $("autofilter-status");
  const previewEl = $("autofilter-preview");
  autofilterMatches = [];
  $("btn-autofilter-apply").disabled = true;
  statusEl.textContent = "";
  if (!activeAutofilterRules().length) {
    toast("Нет включённых правил автофильтра", false);
    return;
  }
  setLoading(previewEl);
  try {
    const snap = await getDocs(
      query(collectionGroup(db, "messages"), orderBy("timestamp", "desc"), limit(AUTOFILTER_SCAN_LIMIT))
    );
    // Уже удалённые не трогаем; в личных (E2EE) чатах текст пуст — они не видны.
    const docs = snap.docs.filter((d) => d.get("isDeleted") !== true);
    docs.forEach((d) => {
      const m = d.data();
      const rule = firstMatchingRule(typeof m.text === "string" ? m.text : "");
      if (!rule) return;
      autofilterMatches.push({
        ref: d.ref,
        id: d.id,
        chatId: d.ref.parent.parent.id,
        senderId: m.senderId || "",
        timestamp: m.timestamp || 0,
        preview: messagePreviewText(m),
        rule,
        original: pickOriginalFields(m, 200000),
      });
    });
    statusEl.textContent = autofilterMatches.length
      ? `Найдено сообщений: ${autofilterMatches.length} из ${docs.length} проверенных.`
      : `Совпадений нет (проверено ${docs.length} сообщений).`;
    renderAutofilterPreview();
    $("btn-autofilter-apply").disabled = !autofilterMatches.length;
  } catch (err) {
    previewEl.innerHTML = "";
    handleErr("Не удалось проверить сообщения (нужен индекс messages.timestamp)")(err);
  }
}

async function autofilterApply() {
  if (!autofilterMatches.length) return;
  const matches = autofilterMatches;
  if (!confirm(`Удалить ${matches.length} сообщений, совпавших с правилами автофильтра? Причина у каждого — из сработавшегося правила.`)) return;
  const statusEl = $("autofilter-status");
  $("btn-autofilter-apply").disabled = true;
  $("btn-autofilter-scan").disabled = true;
  let deleted = 0;
  try {
    for (let i = 0; i < matches.length; i += BULK_DELETE_BATCH) {
      const batch = writeBatch(db);
      matches.slice(i, i + BULK_DELETE_BATCH).forEach((m) => batch.update(m.ref, softDeletePayload()));
      await batch.commit();
      deleted += Math.min(BULK_DELETE_BATCH, matches.length - i);
      statusEl.textContent = `Удалено ${deleted} из ${matches.length}…`;
    }
    await archiveDeletedEntries(
      matches.map((m) => deletedEntry(m.chatId, m.id, m.senderId, m.original, {
        reason: m.rule.reason || "RULES", reasonText: "", source: "AUTO",
      }))
    );
    const newestPerChat = new Map();
    matches.forEach((m) => {
      const cur = newestPerChat.get(m.chatId);
      if (!cur || m.timestamp > cur.timestamp) newestPerChat.set(m.chatId, m);
    });
    for (const [chatId, m] of newestPerChat) {
      try { await refreshChatPreviewAfterDelete(chatId, m.id); } catch (e) { /* админ не участник */ }
    }
    logAdminAction("AUTO_FILTER_MESSAGES_DELETED", `Автофильтр: удалено ${deleted} сообщений`);
    toast(`Автофильтр удалил сообщений: ${deleted}`);
    statusEl.textContent = `Готово: удалено ${deleted} сообщений.`;
    autofilterMatches = [];
    renderAutofilterPreview();
  } catch (err) {
    handleErr("Не удалось удалить сообщения автофильтром")(err);
  } finally {
    $("btn-autofilter-scan").disabled = false;
  }
}

/* ---------------------- История удалённых ---------------------- */

let deletedMessagesCache = [];
let deletedUnsub = null;
const deletedNames = new Map();

function deletedSenderLabel(uid, spanEl) {
  if (!uid) { spanEl.textContent = "—"; return; }
  if (deletedNames.has(uid)) { spanEl.textContent = deletedNames.get(uid); return; }
  spanEl.textContent = "…";
  resolveUserName(uid, "").then((name) => {
    deletedNames.set(uid, name);
    spanEl.textContent = name;
  });
}

function deletedActorLabel(d, spanEl) {
  if (d.deletedByName) { spanEl.textContent = d.deletedByName; return; }
  deletedSenderLabel(d.deletedBy, spanEl);
}

function renderDeletedHistory() {
  const el = $("deleted-list");
  if (!deletedMessagesCache.length) {
    el.innerHTML = '<p class="empty-note">Удалённых сообщений пока нет.</p>';
    return;
  }
  el.innerHTML = "";
  deletedMessagesCache.forEach((docSnap) => {
    const d = docSnap.data();
    const expired = Date.now() - (d.deletedAt || 0) > DELETED_RETENTION_MS;
    const item = document.createElement("div");
    item.className = "item";
    item.innerHTML = `
      <div class="item-head">
        <span class="item-title">${esc(d.preview || "(без текста)")}</span>
        <span class="badge badge-dim">${esc(reasonLabel(d))}</span>
        <span class="item-date">${fmtDate(d.deletedAt)}</span>
      </div>
      <div class="item-sub">чат <span class="deleted-chat"></span> · автор <span class="deleted-sender"></span> · удалил <span class="deleted-actor"></span>${d.source === "AUTO" ? " · автофильтр" : ""}</div>
      ${d.reasonText ? `<div class="item-text">Причина: ${esc(d.reasonText)}</div>` : ""}
      ${d.mediaSkipped ? '<div class="item-text">⚠️ Медиа не сохранено в архив — восстановится только текст.</div>' : ""}`;
    item.querySelector(".deleted-chat").textContent = d.chatId || "—";
    deletedSenderLabel(d.senderId, item.querySelector(".deleted-sender"));
    deletedActorLabel(d, item.querySelector(".deleted-actor"));
    if (d.restored) {
      const note = document.createElement("div");
      note.className = "deleted-restored";
      note.textContent = `✅ Восстановлено${d.restoredAt ? " " + fmtDate(d.restoredAt) : ""}${d.restoredByName ? " — " + d.restoredByName : ""}`;
      item.appendChild(note);
    } else if (expired) {
      const note = document.createElement("div");
      note.className = "deleted-restored";
      note.textContent = "⏳ Срок восстановления (30 дней) истёк.";
      item.appendChild(note);
    } else {
      const actions = document.createElement("div");
      actions.className = "item-actions deleted-entry-actions";
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn-secondary";
      btn.textContent = "Восстановить";
      btn.addEventListener("click", () => restoreDeletedMessage(docSnap));
      actions.appendChild(btn);
      item.appendChild(actions);
    }
    el.appendChild(item);
  });
}

async function restoreDeletedMessage(docSnap) {
  const d = docSnap.data();
  if (!confirm(`Восстановить сообщение в чате ${d.chatId}? Оно снова станет видно участникам.`)) return;
  try {
    const restore = { isDeleted: false, text: d.originalText || "", deletedByAdmin: false };
    // Медиа восстанавливаем, если оно было сохранено в архиве.
    if (d.media && typeof d.media === "object") Object.assign(restore, d.media);
    await updateDoc(doc(db, "chats", d.chatId, "messages", d.messageId), restore);
    await updateDoc(docSnap.ref, {
      restored: true,
      restoredAt: Date.now(),
      restoredBy: auth.currentUser ? auth.currentUser.uid : "",
      restoredByName: adminActorName || (auth.currentUser ? auth.currentUser.email || "" : "Админ"),
    });
    // Возвращаем корректное превью списка чатов (если это было последнее сообщение).
    await refreshChatPreviewAfterDelete(d.chatId, d.messageId);
    logAdminAction("MESSAGE_RESTORED", `Восстановлено сообщение ${d.chatId}/${d.messageId}`, d.senderId, await resolveUserName(d.senderId, ""));
    toast("Сообщение восстановлено");
  } catch (err) {
    handleErr("Не удалось восстановить сообщение")(err);
  }
}

async function deletedHistoryCleanup() {
  const cutoff = Date.now() - DELETED_RETENTION_MS;
  if (!confirm("Удалить из архива записи старше 30 дней? Восстановить их уже нельзя.")) return;
  try {
    const snap = await getDocs(
      query(collection(db, "deletedMessages"), where("deletedAt", "<", cutoff), limit(400))
    );
    if (!snap.size) { toast("Записей старше 30 дней нет"); return; }
    const batch = writeBatch(db);
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    logAdminAction("DELETED_HISTORY_CLEANED", `Удалено архивных записей: ${snap.size}`);
    toast(`Удалено архивных записей: ${snap.size}`);
  } catch (err) {
    handleErr("Не удалось очистить архив")(err);
  }
}

/** Инициализация блока «Причины удаления» / «Автофильтр» / «История удалённых». */
function startModerationExtras() {
  // Стандартный набор причин — только для справки (он зашит в модалку удаления).
  const reasonList = $("reason-list");
  reasonList.innerHTML = "";
  Object.keys(DELETE_REASONS).forEach((key) => {
    const row = document.createElement("div");
    row.className = "item";
    row.innerHTML = `<div class="item-head"><span class="item-title">${esc(DELETE_REASONS[key])}</span></div>`;
    reasonList.appendChild(row);
  });

  // Автофильтр.
  const reasonSelect = $("autofilter-reason");
  reasonSelect.innerHTML = "";
  Object.keys(DELETE_REASONS).forEach((key) => {
    const opt = document.createElement("option");
    opt.value = key;
    opt.textContent = DELETE_REASONS[key];
    reasonSelect.appendChild(opt);
  });
  $("autofilter-enabled").addEventListener("change", () => {
    autofilterModel.enabled = $("autofilter-enabled").checked;
    saveAutofilter(autofilterModel.enabled ? "Автофильтр включён" : "Автофильтр выключен");
  });
  $("form-autofilter-rule").addEventListener("submit", (e) => {
    e.preventDefault();
    const pattern = $("autofilter-pattern").value.trim();
    if (!pattern) return;
    autofilterModel.rules.push({
      id: autofilterNewId(),
      type: $("autofilter-type").value,
      pattern,
      reason: reasonSelect.value,
      enabled: true,
    });
    $("autofilter-pattern").value = "";
    saveAutofilter("Добавлено правило: " + pattern);
  });
  $("btn-autofilter-scan").addEventListener("click", autofilterScan);
  $("btn-autofilter-apply").addEventListener("click", autofilterApply);
  loadAutofilter();

  // История удалённых.
  setLoading($("deleted-list"));
  deletedUnsub = onSnapshot(
    query(collection(db, "deletedMessages"), orderBy("deletedAt", "desc"), limit(200)),
    (snap) => { deletedMessagesCache = snap.docs; renderDeletedHistory(); },
    handleErr("Не удалось загрузить историю удалённых")
  );
  $("btn-deleted-cleanup").addEventListener("click", deletedHistoryCleanup);
}

// Количество висящих жалоб для бейджа — тот же запрос, что и лента.
function updateReportsBadge(docs) {
  const pending = docs.filter((d) => (d.data().status || "PENDING") === "PENDING");
  const badge = $("reports-count");
  badge.textContent = pending.length ? "на рассмотрении: " + pending.length : "";
  badge.classList.toggle("hidden", !pending.length);
}

function renderReports(snap) {
  reportsCache = snap.docs;
  updateReportsBadge(snap.docs);
  // НОВОЕ (пагинация): «Показать ещё», пока запрос вернул полное окно.
  $("btn-reports-more").classList.toggle("hidden", snap.size < reportsLimit);
  renderReportsView();
}

/** Жалобы, прошедшие фильтр статуса (без учёта фильтра по типу). */
function statusFilteredReports() {
  return reportsCache.filter(
    (d) => reportsFilter === "ALL" || (d.data().status || "PENDING") === reportsFilter
  );
}

/** Жалобы, прошедшие оба фильтра — статус и тип. */
function filteredReports() {
  return statusFilteredReports().filter(
    (d) => reportsReasonFilter === "ALL" || (d.data().reason || "") === reportsReasonFilter
  );
}

function setReasonFilter(reason) {
  reportsReasonFilter = reason;
  renderReportsView();
}

/** Число жалоб по типу в текущем статусе — база диаграммы и чипов. */
function reportReasonCounts() {
  const counts = new Map();
  statusFilteredReports().forEach((d) => {
    const key = d.data().reason || "";
    counts.set(key, (counts.get(key) || 0) + 1);
  });
  return counts;
}

/**
 * Диаграмма «жалобы по типам» — горизонтальные полосы по убыванию.
 * Серия одна («число жалоб»), поэтому цвет один (--accent), а не
 * категориальная палитра; значения продублированы числами и процентами,
 * так что смысл не зависит от цвета. Клик по полосе включает фильтр типа.
 */
function renderReportsChart(counts) {
  const el = $("reports-chart");
  const base = statusFilteredReports();
  if (!base.length) {
    el.innerHTML = "";
    return;
  }
  const rows = Object.keys(REPORT_REASONS)
    .map((key) => ({ key, label: REPORT_REASONS[key], count: counts.get(key) || 0 }))
    .filter((row) => row.count > 0)
    .sort((a, b) => b.count - a.count);
  const max = rows[0].count;
  const statusLabel =
    reportsFilter === "ALL"
      ? "все статусы"
      : (REPORT_STATUS_LABELS[reportsFilter] || reportsFilter).toLowerCase();
  el.innerHTML =
    `<div class="report-chart-title">Жалобы по типам (${esc(statusLabel)}, всего ${base.length})</div>` +
    '<div class="report-chart-list"></div>';
  const list = el.querySelector(".report-chart-list");
  rows.forEach((row) => {
    const pct = Math.round((row.count / base.length) * 100);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "report-chart-row" + (reportsReasonFilter === row.key ? " active" : "");
    btn.title = `${row.label}: ${row.count} из ${base.length} (${pct}%)`;
    btn.innerHTML = `
      <span class="chart-label">${esc(row.label)}</span>
      <span class="chart-track"><span class="chart-fill" style="width:${Math.max(4, Math.round((row.count / max) * 100))}%"></span></span>
      <span class="chart-value">${row.count} · ${pct}%</span>`;
    btn.addEventListener("click", () => setReasonFilter(row.key));
    list.appendChild(btn);
  });
}

/** Чипы фильтра по типу — только те типы, что реально есть в текущем статусе. */
function renderReasonFilters(counts) {
  const el = $("reports-reason-filters");
  const items = [{ key: "ALL", label: "Все типы" }].concat(
    Object.keys(REPORT_REASONS)
      .filter((key) => counts.get(key))
      .map((key) => ({ key, label: REPORT_REASONS[key] }))
  );
  el.innerHTML = "";
  items.forEach((item) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "filter-btn" + (reportsReasonFilter === item.key ? " active" : "");
    btn.dataset.reason = item.key;
    btn.textContent =
      item.key === "ALL" ? item.label : `${item.label} · ${counts.get(item.key)}`;
    btn.addEventListener("click", () => setReasonFilter(item.key));
    el.appendChild(btn);
  });
}

function renderReportsView() {
  const counts = reportReasonCounts();
  // Выбранный тип мог исчезнуть из текущего статуса (или из окна ленты) —
  // иначе список выглядел бы пустым без единого активного чипа.
  if (reportsReasonFilter !== "ALL" && !counts.get(reportsReasonFilter)) {
    reportsReasonFilter = "ALL";
  }
  renderReasonFilters(counts);
  renderReportsChart(counts);
  const listEl = $("reports-list");
  const docs = filteredReports();
  if (!docs.length) {
    const nothingNew = reportsFilter === "PENDING" && reportsReasonFilter === "ALL";
    listEl.innerHTML = `<p class="empty-note">${nothingNew ? "Новых жалоб нет — всё чисто! ✅" : "Жалоб с такими фильтрами нет."}</p>`;
    return;
  }
  listEl.innerHTML = "";
  docs.forEach((d) => listEl.appendChild(reportItem(d)));
}

function startReports() {
  // Переключение фильтра перерисовывает ленту из уже загруженного снапшота
  // (live-подписка фильтра не меняет — данные те же, отдельный запрос не нужен).
  $("reports-filters").querySelectorAll(".filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      $("reports-filters").querySelectorAll(".filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      reportsFilter = btn.dataset.filter;
      renderReportsView();
    });
  });
  setLoading($("reports-list"));
  reportsUnsub = onSnapshot(
    query(collectionGroup(db, "reports"), orderBy("createdAt", "desc"), limit(reportsLimit)),
    renderReports,
    handleErr("Не удалось загрузить жалобы")
  );
  // НОВОЕ (пагинация): увеличиваем окно и переподписываемся.
  $("btn-reports-more").addEventListener("click", () => {
    reportsLimit += 200;
    if (reportsUnsub) reportsUnsub();
    setLoading($("reports-list"));
    reportsUnsub = onSnapshot(
      query(collectionGroup(db, "reports"), orderBy("createdAt", "desc"), limit(reportsLimit)),
      renderReports,
      handleErr("Не удалось загрузить жалобы")
    );
  });
  $("btn-export-reports").addEventListener("click", () => {
    if (!reportsCache.length) return toast("Жалоб пока нет — выгружать нечего", false);
    downloadCsv(
      "yodo-reports.csv",
      ["Дата", "Статус", "Причина", "Тип", "Нарушитель", "Жаловался", "Превью сообщения", "Комментарий жалобы", "Решение", "Рецензент"],
      reportsCache.map((d) => {
        const r = d.data();
        return [
          fmtDate(r.createdAt),
          REPORT_STATUS_LABELS[r.status] || r.status || "",
          REPORT_REASONS[r.reason] || r.reason || "",
          r.isAppeal ? "Обжалование" : r.targetType === "MESSAGE" ? "Сообщение" : "Пользователь",
          r.targetUserName || "",
          r.reporterName || "",
          r.targetMessagePreview || "",
          r.customReasonText || "",
          r.reviewerComment || "",
          r.reviewedByName || "",
        ];
      })
    );
    toast("CSV жалоб скачан (" + reportsCache.length + ")");
  });

  // НОВОЕ (расширенная модерация): модалка контекста жалобы.
  $("btn-close-report-context").addEventListener("click", closeReportContext);
  $("report-context-overlay").addEventListener("click", (e) => {
    if (e.target === $("report-context-overlay")) closeReportContext();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !$("report-context-overlay").classList.contains("hidden")) {
      closeReportContext();
    }
  });

  // НОВОЕ (расширенная модерация): массовое удаление сообщений по периоду.
  $("btn-bulk-del-find").addEventListener("click", bulkDeleteFind);
  $("btn-bulk-del-run").addEventListener("click", bulkDeleteRun);

  // НОВОЕ (расширенная модерация): причины удаления, автофильтр, история удалённых.
  startModerationExtras();
}

/* ------------------------------------------------------------------ */
/* Секция «Поддержка» — обращения и мягкие ограничения                 */
/* ------------------------------------------------------------------ */

// Схема ограничения 1:1 с ChatRepositoryImpl.setSupportRestriction (Android).
// expiresAt == null => бессрочно; проверяется и в firestore.rules, и в приложении.
const SUPPORT_TEMPLATES_KEY = "yodo.admin.supportTemplates";
const DEFAULT_SUPPORT_TEMPLATES = [
  "Здравствуйте! Опишите, пожалуйста, проблему подробнее.",
  "Спасибо за обращение! Мы разбираемся и вернёмся с ответом.",
  "Попробуйте, пожалуйста, перезайти в приложение — проблема должна уйти.",
  "Приложите, пожалуйста, скриншот — так мы разберёмся быстрее.",
];

let supportChatsUnsub = null;
let supportRestrictionsUnsub = null;
let supportThreadUnsub = null;
let supportThreadChatId = null;
let supportFilter = "all";
const supportChatsCache = new Map();
const supportRestrictionsCache = new Map();
// chatId -> { ts, adminReplied }: отвечал ли админ в этом обращении (для «Повторного ответа»).
const supportReplyInfoCache = new Map();

function isActiveSupportRestriction(r) {
  return !r.expiresAt || r.expiresAt > Date.now();
}

/**
 * Чат-«пустышка»: кроме авто-приветствия пользователь ничего не написал.
 * Такие обращения не показываем — они засоряют раздел.
 */
function supportIsEmpty(c) {
  return !c.lastMessageSenderId || c.lastMessageSenderId === "support_system";
}

/** Обращение «ждёт ответа», если последним писал сам пользователь. */
function supportWaiting(c) {
  return !!c.supportUserId && c.lastMessageSenderId === c.supportUserId;
}

/** Повторный ответ: админ уже отвечал, а пользователь написал снова. */
function supportIsRepeat(c) {
  return supportWaiting(c) && supportReplyInfoCache.get(c.id)?.adminReplied === true;
}

// Определяем «отвечал ли админ» по истории сообщений (учитывает ответы из
// приложения и веб-мессенджера, не только из этой панели). Результат кэшируем
// до следующего изменения обращения.
async function refreshSupportReplyInfo(c) {
  try {
    const snap = await getDocs(
      query(collection(db, "chats", c.id, "messages"), orderBy("timestamp", "desc"), limit(50))
    );
    const adminReplied = snap.docs.some((d) => {
      const s = d.data().senderId;
      return !!s && s !== c.supportUserId && s !== "support_system";
    });
    supportReplyInfoCache.set(c.id, { ts: c.lastMessageTimestamp || 0, adminReplied });
  } catch (e) {
    supportReplyInfoCache.set(c.id, { ts: c.lastMessageTimestamp || 0, adminReplied: false });
  }
}

async function classifySupportReplies() {
  const jobs = [];
  supportChatsCache.forEach((c) => {
    if (!supportWaiting(c)) return;
    const cached = supportReplyInfoCache.get(c.id);
    if (cached && cached.ts === (c.lastMessageTimestamp || 0)) return;
    jobs.push(refreshSupportReplyInfo(c));
  });
  if (!jobs.length) return;
  await Promise.allSettled(jobs);
  renderSupportConversations();
}

function fmtSupportDuration(expiresAt) {
  if (!expiresAt) return "бессрочно";
  const left = expiresAt - Date.now();
  if (left <= 0) return "истекло";
  if (left < 3600000) return "осталось " + Math.max(1, Math.round(left / 60000)) + " мин";
  if (left < 86400000) return "осталось " + Math.round(left / 3600000) + " ч";
  return "осталось " + Math.round(left / 86400000) + " дн (до " + fmtDate(expiresAt) + ")";
}

function supportRestrictionName(r) {
  const c = supportChatsCache.get(r.userId);
  return c?.supportUserName || "";
}

async function setSupportRestriction(uid, reason, durationMillis) {
  if (!auth.currentUser) return;
  const now = Date.now();
  await setDoc(doc(db, "supportRestrictions", uid), {
    reason: (reason || "").slice(0, 500),
    restrictedBy: auth.currentUser.uid,
    restrictedByName: adminActorName || auth.currentUser.email || "Админ",
    restrictedAt: now,
    expiresAt: durationMillis ? now + durationMillis : null,
  });
}

async function removeSupportRestriction(uid) {
  await deleteDoc(doc(db, "supportRestrictions", uid));
}

// Кнопка у обращения: не ограничивает сразу, а подставляет UID в форму, где
// админ выбирает причину и срок.
function restrictSupportUser(uid, name) {
  $("support-restrict-uid").value = uid;
  $("support-restrict-reason").value = "";
  $("support-restrict-duration").value = "604800000";
  $("support-restrict-reason").focus();
  $("form-support-restrict").scrollIntoView({ behavior: "smooth", block: "center" });
  toast("Заполните причину и срок" + (name ? " для " + name : ""));
}

async function unrestrictSupportUser(uid, name) {
  if (!confirm("Снять ограничение доступа к поддержке" + (name ? " с " + name : "") + "?")) return;
  try {
    await removeSupportRestriction(uid);
    logAdminAction("SUPPORT_RESTRICTION_REMOVED", "", uid, name || null);
    toast("Ограничение снято");
  } catch (err) {
    handleErr("Не удалось снять ограничение")(err);
  }
}

function supportConversationRow(c) {
  const r = supportRestrictionsCache.get(c.supportUserId);
  const restricted = r && isActiveSupportRestriction(r);
  const repeat = supportIsRepeat(c);
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      <span class="item-title">${esc(c.supportUserName || "Пользователь")}${supportWaiting(c) ? ' <span class="badge badge-yellow">ждёт ответа</span>' : ""}${repeat ? ' <span class="badge badge-blue">повторный</span>' : ""}${restricted ? ' <span class="badge badge-dim">ограничен</span>' : ""}</span>
      <span class="item-date">${fmtDate(c.lastMessageTimestamp)}</span>
    </div>
    <div class="item-sub">${esc(c.supportUserEmail || "без email")}</div>
    <div class="item-text">${esc(c.lastMessage || "")}</div>`;
  const actions = document.createElement("div");
  actions.className = "item-actions";
  const openBtn = document.createElement("button");
  openBtn.type = "button";
  openBtn.className = "btn-primary";
  openBtn.textContent = "Открыть";
  openBtn.addEventListener("click", () => openSupportThread(c.id));
  actions.appendChild(openBtn);
  const restrBtn = document.createElement("button");
  restrBtn.type = "button";
  restrBtn.className = "btn-secondary";
  restrBtn.textContent = restricted ? "Снять ограничение" : "Ограничить";
  restrBtn.addEventListener("click", () =>
    restricted
      ? unrestrictSupportUser(c.supportUserId, c.supportUserName)
      : restrictSupportUser(c.supportUserId, c.supportUserName)
  );
  actions.appendChild(restrBtn);
  el.appendChild(actions);
  return el;
}

function renderSupportConversations() {
  const all = Array.from(supportChatsCache.values())
    .filter((c) => !supportIsEmpty(c))
    .sort((a, b) => (b.lastMessageTimestamp || 0) - (a.lastMessageTimestamp || 0));
  const waiting = all.filter(supportWaiting);
  const repeats = all.filter(supportIsRepeat);
  const firstTouch = waiting.filter((c) => !supportIsRepeat(c));
  const activeRestrictions = Array.from(supportRestrictionsCache.values())
    .filter(isActiveSupportRestriction).length;

  $("support-stats").textContent =
    "Всего обращений: " + all.length + " · ждут ответа: " + waiting.length +
    " · повторный ответ: " + repeats.length +
    " · ограничений: " + activeRestrictions;
  $("support-waiting-badge").textContent = waiting.length ? "ждут ответа: " + waiting.length : "";
  $("support-waiting-badge").classList.toggle("hidden", !waiting.length);

  let shown = all;
  let emptyText = "Обращений в поддержку пока нет.";
  if (supportFilter === "waiting") {
    shown = firstTouch;
    emptyText = "Обращений, ожидающих первого ответа, нет.";
  } else if (supportFilter === "repeat") {
    shown = repeats;
    emptyText = "Повторных обращений нет.";
  }

  const listEl = $("support-list");
  if (!shown.length) {
    listEl.innerHTML = `<p class="empty-note">${emptyText}</p>`;
    return;
  }
  listEl.innerHTML = "";
  shown.forEach((c) => listEl.appendChild(supportConversationRow(c)));
}

function renderSupportRestrictions() {
  const listEl = $("support-restrictions-list");
  const rows = Array.from(supportRestrictionsCache.values())
    .filter(isActiveSupportRestriction)
    .sort((a, b) => (b.restrictedAt || 0) - (a.restrictedAt || 0));
  if (!rows.length) {
    listEl.innerHTML = '<p class="empty-note">Действующих ограничений нет.</p>';
    return;
  }
  listEl.innerHTML = "";
  rows.forEach((r) => {
    const name = supportRestrictionName(r);
    const el = document.createElement("div");
    el.className = "item";
    el.innerHTML = `
      <div class="item-head">
        <span class="item-title">${esc(name || r.userId)}</span>
        <span class="item-date">${fmtDate(r.restrictedAt)}</span>
      </div>
      <div class="item-sub">UID: ${esc(r.userId)} · ${esc(fmtSupportDuration(r.expiresAt))}${r.restrictedByName ? " · ограничил: " + esc(r.restrictedByName) : ""}</div>
      ${r.reason ? `<div class="item-text">${esc(r.reason)}</div>` : ""}`;
    const actions = document.createElement("div");
    actions.className = "item-actions";
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "btn-secondary";
    btn.textContent = "Снять ограничение";
    btn.addEventListener("click", () => unrestrictSupportUser(r.userId, name));
    actions.appendChild(btn);
    el.appendChild(actions);
    listEl.appendChild(el);
  });
}

/* --- Переписка --- */

function supportMessageEl(m, conv) {
  const mine = m.senderId === auth.currentUser?.uid;
  let text;
  if (m.isDeleted) text = "Сообщение удалено";
  else if (m.text) text = m.text;
  else if (m.imageBase64 || m.imagesBase64) text = "[фото]";
  else if (m.voiceBase64) text = "[голосовое сообщение]";
  else if (m.fileBase64) text = "[файл] " + (m.fileName || "");
  else if (m.locationLat != null) text = "[геолокация]";
  else text = "[сообщение]";

  const who =
    m.senderId === "support_system"
      ? "Поддержка YODO"
      : mine
        ? "Вы (админ)"
        : conv?.supportUserName || "Пользователь";

  const el = document.createElement("div");
  el.className = "support-msg" + (mine ? " mine" : "");
  el.innerHTML = `<div class="support-msg-meta">${esc(who)} · ${fmtDate(m.timestamp)}</div>
    <div class="support-msg-text">${esc(text)}</div>`;
  return el;
}

function updateSupportThreadButtons() {
  const conv = supportThreadChatId ? supportChatsCache.get(supportThreadChatId) : null;
  const r = conv?.supportUserId ? supportRestrictionsCache.get(conv.supportUserId) : null;
  const restricted = r && isActiveSupportRestriction(r);
  $("btn-support-restrict-thread").classList.toggle("hidden", !!restricted);
  $("btn-support-unrestrict-thread").classList.toggle("hidden", !restricted);
}

function openSupportThread(chatId) {
  const conv = supportChatsCache.get(chatId);
  if (supportThreadUnsub) supportThreadUnsub();
  supportThreadChatId = chatId;
  $("support-thread-title").textContent = "Переписка · " + (conv?.supportUserName || chatId);
  $("support-thread-sub").textContent =
    (conv?.supportUserEmail || "") + (conv?.supportUserId ? " · UID: " + conv.supportUserId : "");
  updateSupportThreadButtons();
  $("support-thread-overlay").classList.remove("hidden");

  const box = $("support-thread-messages");
  box.innerHTML = '<p class="empty-note">Загрузка…</p>';
  supportThreadUnsub = onSnapshot(
    query(collection(db, "chats", chatId, "messages"), orderBy("timestamp", "desc"), limit(100)),
    (snap) => {
      if (snap.empty) {
        box.innerHTML = '<p class="empty-note">Сообщений нет.</p>';
        return;
      }
      box.innerHTML = "";
      snap.docs.slice().reverse().forEach((d) => box.appendChild(supportMessageEl(d.data(), conv)));
      box.scrollTop = box.scrollHeight;
    },
    handleErr("Не удалось загрузить переписку")
  );
}

function closeSupportThread() {
  if (supportThreadUnsub) supportThreadUnsub();
  supportThreadUnsub = null;
  supportThreadChatId = null;
  $("support-thread-overlay").classList.add("hidden");
}

/* --- Шаблоны ответов (локально в браузере) --- */

function loadSupportTemplates() {
  try {
    const raw = localStorage.getItem(SUPPORT_TEMPLATES_KEY);
    if (!raw) return DEFAULT_SUPPORT_TEMPLATES.slice();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((t) => typeof t === "string") : DEFAULT_SUPPORT_TEMPLATES.slice();
  } catch (e) {
    return DEFAULT_SUPPORT_TEMPLATES.slice();
  }
}

function saveSupportTemplates(arr) {
  try { localStorage.setItem(SUPPORT_TEMPLATES_KEY, JSON.stringify(arr)); } catch (e) { /* приватный режим */ }
}

function insertSupportTemplate(text) {
  const ta = $("support-reply-text");
  if ($("support-thread-overlay").classList.contains("hidden")) {
    navigator.clipboard?.writeText(text).catch(() => {});
    toast("Переписка не открыта — шаблон скопирован в буфер");
    return;
  }
  ta.value = ta.value.trim() ? ta.value.replace(/\s*$/, "") + "\n" + text : text;
  ta.focus();
}

function syncSupportTemplateSelect() {
  const sel = $("support-reply-template");
  sel.innerHTML = '<option value="">— шаблон ответа —</option>';
  loadSupportTemplates().forEach((t, i) => {
    const opt = document.createElement("option");
    opt.value = String(i);
    opt.textContent = t.length > 60 ? t.slice(0, 60) + "…" : t;
    sel.appendChild(opt);
  });
}

function renderSupportTemplates() {
  const templates = loadSupportTemplates();
  const listEl = $("support-templates-list");
  if (!templates.length) {
    listEl.innerHTML = '<p class="empty-note">Шаблонов пока нет.</p>';
  } else {
    listEl.innerHTML = "";
    templates.forEach((t, i) => {
      const el = document.createElement("div");
      el.className = "item";
      el.innerHTML = `<div class="item-text">${esc(t)}</div>`;
      const actions = document.createElement("div");
      actions.className = "item-actions";
      const insertBtn = document.createElement("button");
      insertBtn.type = "button";
      insertBtn.className = "btn-secondary";
      insertBtn.textContent = "Вставить";
      insertBtn.addEventListener("click", () => insertSupportTemplate(t));
      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "btn-link";
      delBtn.textContent = "Удалить";
      delBtn.addEventListener("click", () => {
        const next = loadSupportTemplates();
        next.splice(i, 1);
        saveSupportTemplates(next);
        renderSupportTemplates();
      });
      actions.appendChild(insertBtn);
      actions.appendChild(delBtn);
      el.appendChild(actions);
      listEl.appendChild(el);
    });
  }
  syncSupportTemplateSelect();
}

function startSupport() {
  document.querySelectorAll(".support-filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".support-filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      supportFilter = btn.dataset.filter;
      renderSupportConversations();
    });
  });

  // Обращения: как в app.js — без orderBy в запросе, сортировка на клиенте
  // (не требует составного индекса). Правила разрешают админам list по type=SUPPORT.
  const listEl = $("support-list");
  setLoading(listEl);
  if (supportChatsUnsub) supportChatsUnsub();
  supportChatsUnsub = onSnapshot(
    query(collection(db, "chats"), where("type", "==", "SUPPORT")),
    (snap) => {
      supportChatsCache.clear();
      snap.forEach((d) => {
        const data = d.data();
        data.id = d.id;
        supportChatsCache.set(d.id, data);
      });
      renderSupportConversations();
      updateSupportThreadButtons();
      classifySupportReplies();
    },
    handleErr("Не удалось загрузить обращения в поддержку")
  );

  const restrEl = $("support-restrictions-list");
  setLoading(restrEl);
  if (supportRestrictionsUnsub) supportRestrictionsUnsub();
  supportRestrictionsUnsub = onSnapshot(
    query(collection(db, "supportRestrictions")),
    (snap) => {
      supportRestrictionsCache.clear();
      snap.forEach((d) => supportRestrictionsCache.set(d.id, { ...d.data(), userId: d.id }));
      renderSupportRestrictions();
      renderSupportConversations();
      updateSupportThreadButtons();
    },
    handleErr("Не удалось загрузить ограничения")
  );

  $("form-support-restrict").addEventListener("submit", async (e) => {
    e.preventDefault();
    const uid = $("support-restrict-uid").value.trim();
    const reason = $("support-restrict-reason").value.trim();
    if (!uid) return toast("Укажите UID пользователя", false);
    const durationMillis = Number($("support-restrict-duration").value) || null;
    try {
      await setSupportRestriction(uid, reason, durationMillis);
      logAdminAction("SUPPORT_RESTRICTION_SET", reason, uid);
      toast("Доступ к поддержке ограничен" + (durationMillis ? " на срок" : " бессрочно"));
      $("support-restrict-uid").value = "";
      $("support-restrict-reason").value = "";
    } catch (err) {
      handleErr("Не удалось ограничить доступ к поддержке")(err);
    }
  });

  $("form-support-reply").addEventListener("submit", async (e) => {
    e.preventDefault();
    const chatId = supportThreadChatId;
    if (!chatId || !auth.currentUser) return;
    const conv = supportChatsCache.get(chatId);
    const text = $("support-reply-text").value.trim();
    if (!text) return toast("Введите текст ответа", false);
    try {
      const now = Date.now();
      const batch = writeBatch(db);
      batch.set(doc(collection(db, "chats", chatId, "messages")), {
        senderId: auth.currentUser.uid,
        text,
        timestamp: now,
        status: "SENT",
        notified: false,
      });
      const chatUpdate = {
        lastMessage: text,
        lastMessageTimestamp: now,
        lastMessageSenderId: auth.currentUser.uid,
        lastMessageStatus: "SENT",
      };
      if (conv?.supportUserId && conv.supportUserId !== auth.currentUser.uid) {
        chatUpdate["unreadCounts." + conv.supportUserId] = increment(1);
      }
      batch.update(doc(db, "chats", chatId), chatUpdate);
      await batch.commit();
      $("support-reply-text").value = "";
      $("support-reply-template").value = "";
      logAdminAction("SUPPORT_MESSAGE_SENT", text.slice(0, 120), conv?.supportUserId || null, conv?.supportUserName || null);
      toast("Ответ отправлен — пользователь получит push");
    } catch (err) {
      handleErr("Не удалось отправить ответ")(err);
    }
  });

  $("form-support-template").addEventListener("submit", (e) => {
    e.preventDefault();
    const text = $("support-template-text").value.trim();
    if (!text) return;
    const arr = loadSupportTemplates();
    arr.push(text);
    saveSupportTemplates(arr);
    $("support-template-text").value = "";
    renderSupportTemplates();
    toast("Шаблон добавлен");
  });

  $("support-reply-template").addEventListener("change", (e) => {
    if (e.target.value === "") return;
    const t = loadSupportTemplates()[Number(e.target.value)];
    if (t) insertSupportTemplate(t);
    e.target.value = "";
  });

  $("btn-support-restrict-thread").addEventListener("click", () => {
    const conv = supportChatsCache.get(supportThreadChatId);
    if (conv?.supportUserId) restrictSupportUser(conv.supportUserId, conv.supportUserName);
  });
  $("btn-support-unrestrict-thread").addEventListener("click", () => {
    const conv = supportChatsCache.get(supportThreadChatId);
    if (conv?.supportUserId) unrestrictSupportUser(conv.supportUserId, conv.supportUserName);
  });
  $("btn-close-support-thread").addEventListener("click", closeSupportThread);
  $("support-thread-overlay").addEventListener("click", (e) => {
    if (e.target === $("support-thread-overlay")) closeSupportThread();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !$("support-thread-overlay").classList.contains("hidden")) {
      closeSupportThread();
    }
  });

  renderSupportTemplates();
}

/* ------------------------------------------------------------------ */
/* Секция «FAQ-бот поддержки» — редактор разделов и вопросов            */
/* ------------------------------------------------------------------ */

/* Документ с отредактированным списком (config/supportFaq). Читают его
   веб-версия (faq-data.js, публичный REST) и Android-приложение; при
   отсутствии документа/пустом списке показывается встроенный набор. */
const FAQ_DOC_PATH = "config/supportFaq";

let faqModel = null;

/** Глубокая копия встроенного набора (faq-data.js) — фолбэк и «сброс». */
function builtinFaq() {
  const src = window.SUPPORT_FAQ_DEFAULTS || { otherSectionId: "other", sections: [] };
  return JSON.parse(JSON.stringify(src));
}

function newFaqId(prefix) {
  return prefix + "_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}

function moveInArray(arr, index, delta) {
  const target = index + delta;
  if (target < 0 || target >= arr.length) return false;
  const [item] = arr.splice(index, 1);
  arr.splice(target, 0, item);
  return true;
}

function faqIconButton(text, title, onClick, disabled, danger) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "faq-icon-btn" + (danger ? " danger" : "");
  btn.textContent = text;
  btn.title = title;
  btn.disabled = !!disabled;
  btn.addEventListener("click", onClick);
  return btn;
}

function faqField(labelText, value, placeholder, multiline) {
  const wrap = document.createElement("label");
  wrap.className = "faq-field";
  const label = document.createElement("span");
  label.className = "faq-field-label";
  label.textContent = labelText;
  wrap.appendChild(label);
  const input = document.createElement(multiline ? "textarea" : "input");
  if (multiline) input.rows = 3;
  else input.type = "text";
  input.value = value || "";
  input.placeholder = placeholder || "";
  wrap.appendChild(input);
  return { wrap, input };
}

function renderFaqEditor() {
  const editor = $("faq-editor");
  editor.innerHTML = "";
  const sections = faqModel.sections || [];

  if (!sections.length) {
    editor.innerHTML = '<p class="empty-note">Разделов пока нет — добавьте первый.</p>';
    return;
  }

  sections.forEach((section, sIndex) => {
    const card = document.createElement("div");
    card.className = "faq-section-card";

    const head = document.createElement("div");
    head.className = "faq-section-head";

    const emoji = document.createElement("input");
    emoji.type = "text";
    emoji.className = "faq-emoji-input";
    emoji.value = section.emoji || "";
    emoji.maxLength = 8;
    emoji.placeholder = "🙂";
    emoji.title = "Эмодзи раздела";
    emoji.addEventListener("input", () => { section.emoji = emoji.value.trim(); });
    head.appendChild(emoji);

    const title = document.createElement("input");
    title.type = "text";
    title.className = "faq-title-input";
    title.value = section.title || "";
    title.placeholder = "Название раздела";
    title.addEventListener("input", () => { section.title = title.value; });
    head.appendChild(title);

    head.appendChild(faqIconButton("↑", "Выше", () => {
      if (moveInArray(faqModel.sections, sIndex, -1)) renderFaqEditor();
    }, sIndex === 0));
    head.appendChild(faqIconButton("↓", "Ниже", () => {
      if (moveInArray(faqModel.sections, sIndex, 1)) renderFaqEditor();
    }, sIndex === sections.length - 1));
    head.appendChild(faqIconButton("🗑", "Удалить раздел", () => {
      const name = section.title || "без названия";
      if (!confirm("Удалить раздел «" + name + "» вместе с вопросами?")) return;
      faqModel.sections.splice(sIndex, 1);
      renderFaqEditor();
    }, false, true));
    card.appendChild(head);

    const questionsWrap = document.createElement("div");
    questionsWrap.className = "faq-questions";

    const questions = section.questions || [];
    questions.forEach((question, qIndex) => {
      const qCard = document.createElement("div");
      qCard.className = "faq-question-card";

      const qHead = document.createElement("div");
      qHead.className = "faq-question-head";
      const qLabel = document.createElement("span");
      qLabel.className = "faq-question-label";
      qLabel.textContent = "Вопрос " + (qIndex + 1);
      qHead.appendChild(qLabel);
      qHead.appendChild(faqIconButton("↑", "Выше", () => {
        if (moveInArray(section.questions, qIndex, -1)) renderFaqEditor();
      }, qIndex === 0));
      qHead.appendChild(faqIconButton("↓", "Ниже", () => {
        if (moveInArray(section.questions, qIndex, 1)) renderFaqEditor();
      }, qIndex === questions.length - 1));
      qHead.appendChild(faqIconButton("🗑", "Удалить вопрос", () => {
        section.questions.splice(qIndex, 1);
        renderFaqEditor();
      }, false, true));
      qCard.appendChild(qHead);

      const qField = faqField("Вопрос", question.question, "Текст вопроса");
      qField.input.addEventListener("input", () => { question.question = qField.input.value; });
      qCard.appendChild(qField.wrap);

      const aField = faqField("Ответ", question.answer, "Текст ответа", true);
      aField.input.addEventListener("input", () => { question.answer = aField.input.value; });
      qCard.appendChild(aField.wrap);

      questionsWrap.appendChild(qCard);
    });

    const addQuestion = document.createElement("button");
    addQuestion.type = "button";
    addQuestion.className = "btn-secondary";
    addQuestion.textContent = "+ Добавить вопрос";
    addQuestion.addEventListener("click", () => {
      if (!section.questions) section.questions = [];
      section.questions.push({ id: newFaqId("q"), question: "", answer: "" });
      renderFaqEditor();
    });
    questionsWrap.appendChild(addQuestion);

    card.appendChild(questionsWrap);
    editor.appendChild(card);
  });
}

/** Оставляем только заполненные разделы/вопросы (остальное не публикуем). */
function sanitizeFaq(model) {
  const sections = [];
  (model.sections || []).forEach((section) => {
    const title = (section.title || "").trim();
    if (!title) return;
    const questions = (section.questions || [])
      .map((q) => ({
        id: q.id || newFaqId("q"),
        question: (q.question || "").trim(),
        answer: (q.answer || "").trim(),
      }))
      .filter((q) => q.question);
    if (!questions.length) return;
    sections.push({
      id: section.id || newFaqId("s"),
      title,
      emoji: (section.emoji || "").trim(),
      questions,
    });
  });
  return { otherSectionId: model.otherSectionId || "other", sections };
}

async function refreshFaqMeta() {
  const el = $("faq-updated-at");
  try {
    const snap = await getDoc(doc(db, FAQ_DOC_PATH));
    if (snap.exists() && snap.get("updatedAt")) {
      const who = snap.get("updatedBy") ? " · " + snap.get("updatedBy") : "";
      el.textContent = "Изменено: " + fmtDate(snap.get("updatedAt")) + who;
    } else {
      el.textContent = "Изменений нет — показывается встроенный список.";
    }
  } catch (e) {
    el.textContent = "";
  }
}

async function saveFaq() {
  const payload = sanitizeFaq(faqModel);
  if (!payload.sections.length) {
    toast("Нужен хотя бы один раздел с заполненным вопросом", false);
    return;
  }
  const btn = $("btn-faq-save");
  btn.disabled = true;
  try {
    await setDoc(doc(db, FAQ_DOC_PATH), {
      otherSectionId: payload.otherSectionId,
      sections: payload.sections,
      updatedAt: Date.now(),
      updatedBy: auth.currentUser?.email || "",
    }, { merge: true });
    logAdminAction("SUPPORT_FAQ_SAVED", payload.sections.length + " разд. в FAQ-боте");
    faqModel = payload;
    renderFaqEditor();
    await refreshFaqMeta();
    toast("FAQ сохранён");
  } catch (err) {
    handleErr("Не удалось сохранить FAQ")(err);
  } finally {
    btn.disabled = false;
  }
}

function startFaq() {
  $("btn-faq-add-section").addEventListener("click", () => {
    if (!faqModel) return;
    faqModel.sections.push({ id: newFaqId("s"), title: "", emoji: "📌", questions: [] });
    renderFaqEditor();
  });

  $("btn-faq-save").addEventListener("click", saveFaq);

  $("btn-faq-reset").addEventListener("click", async () => {
    if (!confirm("Убрать изменения из админки и вернуть встроенный список FAQ?")) return;
    try {
      await deleteDoc(doc(db, FAQ_DOC_PATH));
    } catch (err) {
      return handleErr("Не удалось сбросить FAQ")(err);
    }
    faqModel = builtinFaq();
    renderFaqEditor();
    await refreshFaqMeta();
    logAdminAction("SUPPORT_FAQ_RESET", "");
    toast("FAQ сброшен к встроенному");
  });

  $("faq-editor").innerHTML = '<p class="empty-note">Загрузка…</p>';
  loadFaqModel().then((model) => {
    faqModel = model;
    renderFaqEditor();
    refreshFaqMeta();
  });
}

/** Текущий список из Firestore; при отсутствии/ошибке — встроенный набор. */
async function loadFaqModel() {
  try {
    const snap = await getDoc(doc(db, FAQ_DOC_PATH));
    const data = snap.exists() ? snap.data() : null;
    if (data && Array.isArray(data.sections) && data.sections.length) {
      return {
        otherSectionId: data.otherSectionId || "other",
        sections: data.sections.map((section) => ({
          id: section.id || newFaqId("s"),
          title: section.title || "",
          emoji: section.emoji || "",
          questions: (section.questions || []).map((q) => ({
            id: q.id || newFaqId("q"),
            question: q.question || "",
            answer: q.answer || "",
          })),
        })),
      };
    }
  } catch (err) {
    console.error("FAQ load", err);
  }
  return builtinFaq();
}

/* ------------------------------------------------------------------ */
/* Секция «Пользователи» — поиск и карточка с блокировкой              */
/* ------------------------------------------------------------------ */

let usersSearchTimer = null;
let currentUserCardUid = null;
let userCardUnsub = null;

function userCardRow(label, value) {
  return `<div class="user-card-row"><span class="user-card-label">${esc(label)}</span><span>${esc(value ?? "—")}</span></div>`;
}

// Карточка: профиль users/{uid} + live-статус блокировки globalBlocks/{uid}.
async function openUserCard(uid) {
  currentUserCardUid = uid;
  const bodyEl = $("user-card-body");
  bodyEl.innerHTML = '<p class="empty-note">Загружаю профиль…</p>';
  $("user-card").classList.remove("hidden");
  try {
    const snap = await getDoc(doc(db, "users", uid));
    if (!snap.exists()) {
      bodyEl.innerHTML = `<p class="empty-note">Профиль users/${esc(uid)} не найден. Блокировать по этому UID всё равно можно в разделе «Блокировки».</p>`;
      return;
    }
    const u = snap.data();
    $("user-card-name").textContent = u.displayName || u.username || uid;
    if (userCardUnsub) userCardUnsub();
    userCardUnsub = onSnapshot(
      doc(db, "globalBlocks", uid),
      (blockSnap) => renderUserCard(u, uid, blockSnap.exists() ? blockSnap.data() : null),
      () => renderUserCard(u, uid, null)
    );
  } catch (err) {
    bodyEl.innerHTML = "";
    handleErr("Не удалось открыть профиль")(err);
  }
}

function renderUserCard(u, uid, block) {
  const bodyEl = $("user-card-body");
  const rows = [
    userCardRow("Имя", u.displayName),
    userCardRow("Username", u.username ? "@" + u.username : ""),
    userCardRow("Публичный ID", u.publicId),
    userCardRow("UID", uid),
    userCardRow("Email", u.email),
    userCardRow("Регистрация", u.createdAt ? fmtDate(u.createdAt) : ""),
  ];
  let blockHtml = "";
  if (block) {
    blockHtml = `
      <div class="user-block-status blocked">
        ⛔ Заблокирован${block.blockedAt ? " · " + fmtDate(block.blockedAt) : ""}
        ${block.reason ? `<div class="item-text">${esc(block.reason)}</div>` : ""}
      </div>`;
  } else {
    blockHtml = '<div class="user-block-status ok">✅ Не заблокирован</div>';
  }
  const actions = document.createElement("div");
  actions.className = "item-actions";
  if (block) {
    const unblockBtn = document.createElement("button");
    unblockBtn.type = "button";
    unblockBtn.className = "btn-secondary";
    unblockBtn.textContent = "Разблокировать";
    unblockBtn.addEventListener("click", () => unblockUser(uid, u.displayName));
    actions.appendChild(unblockBtn);
  } else {
    const blockBtn = document.createElement("button");
    blockBtn.type = "button";
    blockBtn.className = "btn-danger";
    blockBtn.textContent = "Заблокировать аккаунт";
    blockBtn.addEventListener("click", () => blockUserWithPrompt(uid, u.displayName));
    actions.appendChild(blockBtn);
  }
  bodyEl.innerHTML = rows.join("") + blockHtml;
  bodyEl.appendChild(actions);
}

function closeUserCard() {
  currentUserCardUid = null;
  if (userCardUnsub) userCardUnsub();
  userCardUnsub = null;
  $("user-card").classList.add("hidden");
}

async function blockUserWithPrompt(uid, name) {
  const reason = prompt("Причина блокировки (видна " + (name || "пользователю") + "):", "");
  if (reason === null) return;
  try {
    await setGlobalBlock(uid, reason.trim());
    logAdminAction("USER_GLOBALLY_BLOCKED", reason.trim(), uid, name);
    toast("Аккаунт заблокирован" + (reason.trim() ? " — пользователь получит push" : ""));
  } catch (err) {
    handleErr("Не удалось заблокировать")(err);
  }
}

async function unblockUser(uid, name) {
  if (!confirm("Разблокировать аккаунт " + (name || uid) + "? Доступ восстановится.")) return;
  try {
    await deleteDoc(doc(db, "globalBlocks", uid));
    addDoc(collection(db, "moderationNotifications"), {
      userId: uid,
      title: "Блокировка снята",
      body: "Доступ к аккаунту восстановлен",
      notified: false,
      createdAt: Date.now(),
    }).catch(() => {});
    logAdminAction("USER_GLOBALLY_UNBLOCKED", "", uid, name);
    toast("Блокировка снята — пользователь получит push");
  } catch (err) {
    handleErr("Не удалось снять блокировку")(err);
  }
}

async function runUsersSearch() {
  const raw = $("users-search-input").value.trim();
  const term = raw.toLowerCase().replace(/^@/, "");
  const resultsEl = $("users-search-results");
  if (term.length < 2) {
    resultsEl.innerHTML = '<p class="empty-note">Минимум 2 символа</p>';
    return;
  }
  resultsEl.innerHTML = '<p class="empty-note">Поиск…</p>';
  try {
    const [byUsername, byName] = await Promise.all([
      searchUsersByField("usernameLowercase", term),
      searchUsersByField("displayNameLowercase", term),
    ]);
    const byPublicId = term.startsWith("yodo-")
      ? await getDocs(query(collection(db, "users"), where("publicId", "==", raw.toUpperCase()), limit(10)))
      : { docs: [] };
    const seen = new Set();
    const docs = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    for (const d of byPublicId.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    if (!docs.length) {
      resultsEl.innerHTML = '<p class="empty-note">Никого не найдено. Попробуйте другое имя или YODO-ID.</p>';
      return;
    }
    resultsEl.innerHTML = "";
    docs.forEach((d) => {
      const u = d.data();
      const el = document.createElement("div");
      el.className = "user-result";
      el.innerHTML = `
        <div class="user-result-name">${esc(u.displayName || "Без имени")}</div>
        <div class="user-result-username">@${esc(u.username || "—")} · ${esc(u.publicId || d.id)}</div>`;
      el.addEventListener("click", () => openUserCard(d.id));
      resultsEl.appendChild(el);
    });
  } catch (err) {
    handleErr("Поиск пользователей")(err);
  }
}

// НОВОЕ (список всех пользователей): страницы по 50 через startAfter,
// фильтр «Заблокированные» — по набору uid из globalBlocks.
let usersListState = {
  lastDoc: null,
  filter: "all", // all | blocked
  blockedUids: new Set(),
  hasMore: true,
  loading: false,
};

async function loadBlockedUids() {
  try {
    const snap = await getDocs(collection(db, "globalBlocks"));
    usersListState.blockedUids = new Set(snap.docs.map((d) => d.id));
  } catch (e) { /* фильтр «Заблокированные» просто покажет всех */ }
}

function userListRow(d) {
  const u = d.data();
  const blocked = usersListState.blockedUids.has(d.id);
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      <span class="item-title">${esc(u.displayName || "Без имени")}${blocked ? ' <span class="badge badge-dim">⛔ заблокирован</span>' : ""}</span>
      <span class="item-date">${fmtDate(u.createdAt)}</span>
    </div>
    <div class="item-sub">${esc(u.username ? "@" + u.username : "")}${u.email ? " · " + esc(u.email) : ""}</div>
    <div class="item-sub">UID: ${esc(d.id)}${u.lastSeen ? " · активен: " + fmtDate(u.lastSeen) : ""}</div>
    <div class="item-actions">
      <button class="btn-secondary">Открыть карточку</button>
    </div>`;
  el.querySelector("button").addEventListener("click", () => openUserCard(d.id));
  return el;
}

async function loadUsersPage(reset) {
  if (usersListState.loading) return;
  const listEl = $("users-list");
  const moreBtn = $("btn-users-more");
  if (reset) {
    usersListState.lastDoc = null;
    usersListState.hasMore = true;
    listEl.innerHTML = "";
  }
  if (!usersListState.hasMore) return;
  usersListState.loading = true;
  moreBtn.disabled = true;
  try {
    let q = query(collection(db, "users"), orderBy("createdAt", "desc"), limit(50));
    if (usersListState.lastDoc) q = query(q, startAfter(usersListState.lastDoc));
    const snap = await getDocs(q);
    const docs = snap.docs;
    usersListState.lastDoc = docs.length ? docs[docs.length - 1] : null;
    usersListState.hasMore = docs.length === 50;
    const visible = usersListState.filter === "blocked"
      ? docs.filter((d) => usersListState.blockedUids.has(d.id))
      : docs;
    if (reset && !visible.length) {
      listEl.innerHTML = '<p class="empty-note">Пользователей пока нет.</p>';
    }
    visible.forEach((d) => listEl.appendChild(userListRow(d)));
    moreBtn.classList.toggle("hidden", !usersListState.hasMore);
  } catch (err) {
    handleErr("Не удалось загрузить пользователей")(err);
  } finally {
    usersListState.loading = false;
    moreBtn.disabled = false;
  }
}

function startUsers() {
  $("users-search-input").addEventListener("input", () => {
    clearTimeout(usersSearchTimer);
    usersSearchTimer = setTimeout(runUsersSearch, 350);
  });
  $("btn-close-user-card").addEventListener("click", closeUserCard);

  // НОВОЕ (список всех + пагинация): страницами по 50, фильтр «Все /
  // Заблокированные» (uid из globalBlocks читаются один раз).
  document.querySelectorAll(".users-filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".users-filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      usersListState.filter = btn.dataset.filter;
      loadUsersPage(true);
    });
  });
  $("btn-users-more").addEventListener("click", () => loadUsersPage(false));
  loadBlockedUids().then(() => loadUsersPage(true));
}

/* ------------------------------------------------------------------ */
/* Секция «Блокировки» — список globalBlocks и ручной бан по UID       */
/* ------------------------------------------------------------------ */

let blocksUnsub = null;

function startBlocks() {
  $("form-block-uid").addEventListener("submit", async (e) => {
    e.preventDefault();
    const uid = $("block-uid").value.trim();
    const reason = $("block-reason").value.trim();
    if (!uid) return toast("Укажите UID пользователя", false);
    try {
      await setGlobalBlock(uid, reason);
      logAdminAction("USER_GLOBALLY_BLOCKED", reason, uid);
      toast("Аккаунт заблокирован");
      $("block-uid").value = "";
      $("block-reason").value = "";
    } catch (err) {
      handleErr("Не удалось заблокировать")(err);
    }
  });
  const listEl = $("blocks-list");
  setLoading(listEl);
  blocksUnsub = onSnapshot(
    query(collection(db, "globalBlocks"), orderBy("blockedAt", "desc")),
    async (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Заблокированных аккаунтов нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      // Имена подтягиваем по одному (get по uid публичен) — блок-лист маленький.
      for (const d of snap.docs) {
        const b = d.data();
        let name = "";
        try {
          const u = await getDoc(doc(db, "users", d.id));
          name = u.exists() ? u.data().displayName || "" : "";
        } catch (e) { /* best-effort */ }
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            <span class="item-title">${esc(name || d.id)}</span>
            <span class="item-date">${fmtDate(b.blockedAt)}</span>
          </div>
          <div class="item-sub">UID: ${esc(d.id)}${b.blockedByName ? " · заблокировал: " + esc(b.blockedByName) : ""}</div>
          ${b.reason ? `<div class="item-text">${esc(b.reason)}</div>` : ""}`;
        const actions = document.createElement("div");
        actions.className = "item-actions";
        const unblockBtn = document.createElement("button");
        unblockBtn.type = "button";
        unblockBtn.className = "btn-secondary";
        unblockBtn.textContent = "Разблокировать";
        unblockBtn.addEventListener("click", () => unblockUser(d.id, name));
        actions.appendChild(unblockBtn);
        el.appendChild(actions);
        listEl.appendChild(el);
      }
    },
    handleErr("Не удалось загрузить блокировки")
  );
}

/* ------------------------------------------------------------------ */
/* Секция «Вопросы» — неотвеченные вопросы всех учителей               */
/* ------------------------------------------------------------------ */

function startInbox() {
  const listEl = $("inbox-list");
  const countEl = $("inbox-count");
  setLoading(listEl);
  // collectionGroup, как у push-воркера; фильтр «без ответа» на клиенте,
  // чтобы не требовать составной индекс answer+createdAt.
  onSnapshot(
    query(collectionGroup(db, "questions"), orderBy("createdAt", "desc"), limit(200)),
    (snap) => {
      const pending = snap.docs.filter((d) => {
        const q = d.data();
        return !q.answer && q.hidden !== true;
      });
      countEl.textContent = pending.length ? "без ответа: " + pending.length : "";
      countEl.classList.toggle("hidden", !pending.length);
      if (!pending.length) {
        listEl.innerHTML = '<p class="empty-note">Неотвеченных вопросов нет — всё чисто! ✅</p>';
        return;
      }
      listEl.innerHTML = "";
      pending.forEach((d) => {
        // Имя учителя = id родительского документа schoolTeacherProfiles.
        const teacherName = d.ref.parent.parent.id;
        listEl.appendChild(questionItem(teacherName, d, true));
      });
    },
    handleErr("Не удалось загрузить вопросы")
  );
}

/* ------------------------------------------------------------------ */
/* Секция «Аудит» — журнал действий администраторов                    */
/* ------------------------------------------------------------------ */

function startAudit() {
  const listEl = $("audit-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "adminAuditLog"), orderBy("timestamp", "desc"), limit(50)),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Записей пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => {
        const a = d.data();
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            <span class="item-title">${esc(AUDIT_LABELS[a.actionType] || a.actionType || "?")}</span>
            <span class="item-date">${fmtDate(a.timestamp)}</span>
          </div>
          <div class="item-sub">${esc(a.actorName || a.actorId || "")}${a.targetUserName ? " → " + esc(a.targetUserName) : ""}</div>
          ${a.details ? `<div class="item-text">${esc(a.details)}</div>` : ""}`;
        listEl.appendChild(el);
      });
    },
    handleErr("Не удалось загрузить журнал")
  );
}

/* ------------------------------------------------------------------ */
/* Сводка (Обзор)                                                      */
/* ------------------------------------------------------------------ */

async function refreshSummary() {
  const grid = $("summary-grid");
  grid.innerHTML = '<p class="empty-note">Считаю…</p>';
  try {
    // ИСПРАВЛЕНО: раньше одно недоступное чтение (например, список users или
    // globalBlocks, если правила Firestore не задеплоены) роняло Promise.all и
    // всю сводку целиком. Теперь каждый источник читается независимо: те, что
    // доступны, отображаются; недоступные пропускаются и логируются в консоль
    // с указанием кода ошибки — это же даёт диагностику при настройке правил.
    const results = await Promise.allSettled([
      getDocs(collection(db, "schoolNews")),                                        // 0
      getDocs(collection(db, "schoolPolls")),                                       // 1
      getDocs(collection(db, "schoolIdeas")),                                       // 2
      getDocs(collection(db, "schoolReviews")),                                     // 3
      getDocs(collection(db, "schoolTeacherProfiles")),                             // 4
      getDocs(query(collectionGroup(db, "questions"), orderBy("createdAt", "desc"), limit(300))), // 5
      getDocs(query(collectionGroup(db, "reports"), orderBy("createdAt", "desc"), limit(200))),   // 6
      getDocs(collection(db, "users")),                                             // 7
      getDocs(collection(db, "globalBlocks")),                                      // 8
    ]);
    const names = ["Новости", "Опросы", "Идеи", "Отзывы", "Учителя", "Вопросы", "Жалобы", "Пользователи", "Блокировки"];
    const src = [];
    results.forEach((r, i) => {
      if (r.status === "fulfilled") src[i] = r.value;
      else {
        console.warn("Сводка: источник недоступен:", names[i], r.reason?.code || r.reason?.message || r.reason);
      }
    });
    const avail = (i) => !!src[i];

    const now = Date.now();
    const weekAgo = now - 7 * 24 * 60 * 60 * 1000;
    const stats = [];

    if (avail(7)) {
      const users = src[7];
      const newUsers = users.docs.filter((d) => (d.data().createdAt || 0) >= weekAgo).length;
      const activeUsers = users.docs.filter((d) => (d.data().lastSeen || 0) >= weekAgo).length;
      const onlineUsers = users.docs.filter((d) => {
        const u = d.data();
        return u.isOnline === true && u.hideOnlineStatus !== true && now - (u.lastSeen || 0) <= 60_000;
      }).length;
      stats.push(
        { value: users.size, label: "пользователей", section: "users" },
        { value: activeUsers, label: "активных за 7 дней", section: "users" },
        { value: newUsers, label: "новых за 7 дней", section: "users" },
        { value: onlineUsers, label: "сейчас онлайн", section: "users" },
      );
    }
    if (avail(8)) stats.push({ value: src[8].size, label: "глобальных блокировок", section: "blocks" });
    if (avail(0)) stats.push({ value: src[0].size, label: "новостей", section: "news" });
    if (avail(0) || avail(1)) {
      const newsWaiting = avail(0)
        ? src[0].docs.filter((d) => d.data().published !== false && d.data().notified === false).length
        : 0;
      const pollsWaiting = avail(1)
        ? src[1].docs.filter((d) => d.data().notified === false).length
        : 0;
      stats.push({ value: newsWaiting + pollsWaiting, label: "push в очереди", section: "news" });
    }
    if (avail(1)) {
      const openPolls = src[1].docs.filter((d) => d.data().closed !== true).length;
      stats.push({ value: src[1].size + " (" + openPolls + " открыто)", label: "опросов", section: "polls" });
    }
    if (avail(5)) {
      const questions = src[5];
      const unanswered = questions.docs.filter((d) => {
        const q = d.data();
        return !q.answer && q.hidden !== true;
      }).length;
      const answeredQuestions = questions.docs.filter((d) => !!d.data().answer);
      const avgAnswerMs = answeredQuestions.length
        ? answeredQuestions.reduce(
            (sum, d) => sum + Math.max(0, (d.data().answeredAt || 0) - (d.data().createdAt || 0)),
            0
          ) / answeredQuestions.length
        : 0;
      stats.push(
        { value: unanswered, label: "вопросов без ответа", section: "inbox" },
        {
          value: avgAnswerMs > 0 ? fmtDuration(avgAnswerMs) : "—",
          label: "среднее время ответа",
          section: "inbox",
        },
      );
    }
    if (avail(6)) {
      const pendingReports = src[6].docs.filter(
        (d) => (d.data().status || "PENDING") === "PENDING"
      ).length;
      stats.push({ value: pendingReports, label: "жалоб на рассмотрении", section: "reports" });
    }
    if (avail(4)) {
      const linked = src[4].docs.filter((d) => !!d.data().linkedUserId).length;
      stats.push({ value: src[4].size + " (" + linked + " привяз.)", label: "учителей", section: "teachers" });
    }
    if (avail(2)) stats.push({ value: src[2].size, label: "идей", section: "ideas" });
    if (avail(3)) {
      const reviews = src[3];
      let starsSum = 0;
      reviews.forEach((d) => (starsSum += d.data().stars || 0));
      stats.push({
        value: reviews.size
          ? (starsSum / reviews.size).toFixed(1) + " / 5 (" + reviews.size + ")"
          : "—",
        label: "средняя оценка",
        section: "reviews",
      });
    }

    if (!stats.length) {
      grid.innerHTML =
        '<p class="empty-note">Не удалось получить данные. Подробности — в консоли браузера (F12).</p>';
      return;
    }
    grid.innerHTML = "";
    for (const s of stats) {
      const el = document.createElement("div");
      el.className = "summary-stat";
      el.innerHTML =
        '<a href="#"><div class="stat-value">' + esc(String(s.value)) +
        '</div><div class="stat-label">' + esc(s.label) + "</div></a>";
      el.querySelector("a").addEventListener("click", (e) => {
        e.preventDefault();
        document.querySelector('.nav-btn[data-section="' + s.section + '"]').click();
      });
      grid.appendChild(el);
    }
  } catch (err) {
    grid.innerHTML = "";
    handleErr("Не удалось собрать сводку")(err);
  }
}

function startSummary() {
  $("btn-refresh-summary").addEventListener("click", refreshSummary);
  refreshSummary();
}

/* ------------------------------------------------------------------ */
/* Секция «Канал» — посты в официальный канал                          */
/* ------------------------------------------------------------------ */

// Гарантирует существование документа официального канала (формат 1:1 с
// ensureOfficialChannelExists в app.js): без него updateDoc превью упадёт.
async function ensureOfficialChannelDoc() {
  const ref = doc(db, "chats", OFFICIAL_CHANNEL_ID);
  const snap = await getDoc(ref);
  if (snap.exists()) return snap;
  const now = Date.now();
  const uid = auth.currentUser.uid;
  await setDoc(ref, {
    participantIds: [uid],
    type: "CHANNEL",
    title: "YodoMessenger",
    titleLowercase: "yodomessenger",
    createdAt: now,
    isVerified: true,
    lastMessage: "",
    lastMessageTimestamp: now,
    lastMessageSenderId: uid,
    lastMessageStatus: "SENT",
    unreadCounts: { [uid]: 0 },
    isOnline: false,
    createdBy: uid,
  });
  return await getDoc(ref);
}

/* Темы постов канала — формат ⟦news:Тема⟧тело, 1:1 с NewsTopic.kt. */
const NEWS_TOPICS = ["Новые функции", "Исправления", "Анонсы", "Важное", "Обновление"];
const NEWS_TOPIC_PREFIX = "\u27E6news:";
const NEWS_TOPIC_SUFFIX = "\u27E7";

function encodeNewsTopic(topic, body) {
  return topic ? NEWS_TOPIC_PREFIX + topic + NEWS_TOPIC_SUFFIX + body : body;
}

function decodeNewsTopic(text) {
  if (text && text.startsWith(NEWS_TOPIC_PREFIX)) {
    const end = text.indexOf(NEWS_TOPIC_SUFFIX, NEWS_TOPIC_PREFIX.length);
    if (end > NEWS_TOPIC_PREFIX.length) {
      return {
        topic: text.slice(NEWS_TOPIC_PREFIX.length, end),
        body: text.slice(end + NEWS_TOPIC_SUFFIX.length),
      };
    }
  }
  return { topic: "", body: text || "" };
}

/** Превью для списка чатов — как previewText в MessageRepositoryImpl. */
function channelPreviewText(text, photoCount) {
  if (text) return text.slice(0, 120);
  if (photoCount > 1) return "📷 Фото (" + photoCount + ")";
  if (photoCount === 1) return "📷 Фото";
  return "";
}

/* Фото постов хранятся base64 прямо в документе сообщения (imageBase64 /
   imagesBase64), как в приложении. Документ Firestore < 1 МБ, поэтому сжимаем
   в canvas адаптивно под общий бюджет и не даём превысить лимит. */
const CHANNEL_MAX_PHOTOS = 10;
const CHANNEL_PHOTO_BUDGET = 850000;

function loadImageFromFile(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("не картинка")); };
    img.src = url;
  });
}

function canvasJpegBase64(img, maxDim, quality) {
  const ratio = Math.min(1, maxDim / Math.max(img.width, img.height));
  const w = Math.max(1, Math.round(img.width * ratio));
  const h = Math.max(1, Math.round(img.height * ratio));
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  canvas.getContext("2d").drawImage(img, 0, 0, w, h);
  const dataUrl = canvas.toDataURL("image/jpeg", quality);
  return dataUrl.slice(dataUrl.indexOf(",") + 1);
}

// Подбор качества/размера как в ImageUtils.compressAdaptive: шаг качества -8,
// затем уменьшение разрешения, пока base64 не влезет в maxBase64.
function compressChannelImage(img, maxBase64) {
  let dim = 1600;
  for (let attempt = 0; attempt < 4; attempt++) {
    for (let q = 90; q >= 40; q -= 8) {
      const b64 = canvasJpegBase64(img, dim, q / 100);
      if (b64.length <= maxBase64) return b64;
    }
    dim = Math.round(dim * 0.75);
  }
  return canvasJpegBase64(img, 640, 0.4);
}

function startChannel() {
  const listEl = $("channel-posts-list");
  const subEl = $("channel-subscribers");

  onSnapshot(
    doc(db, "chats", OFFICIAL_CHANNEL_ID),
    (snap) => {
      if (!snap.exists()) {
        subEl.textContent = "Канал ещё не создан — он появится после первой публикации.";
        return;
      }
      const count = (snap.get("participantIds") || []).length;
      subEl.textContent = "Подписчиков: " + count;
    },
    handleErr("Не удалось загрузить канал")
  );

  // Свежие посты сверху — запрос по одному полю, составной индекс не нужен.
  setLoading(listEl);
  onSnapshot(
    query(
      collection(db, "chats", OFFICIAL_CHANNEL_ID, "messages"),
      orderBy("timestamp", "desc"),
      limit(30)
    ),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Постов пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => {
        const m = d.data();
        const dec = decodeNewsTopic(m.text || "");
        const imgs = (m.imagesBase64 && m.imagesBase64.length)
          ? m.imagesBase64
          : (m.imageBase64 ? [m.imageBase64] : []);
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            ${m.isPinned ? '<span class="badge badge-blue">📌 Закреплён</span>' : ""}
            ${dec.topic ? '<span class="badge badge-blue">' + esc(dec.topic) + '</span>' : ""}
            ${m.notified === false
              ? '<span class="badge badge-yellow">push: в очереди</span>'
              : '<span class="badge badge-green">push: отправлен</span>'}
            <span class="item-date">${fmtDate(m.timestamp)}</span>
          </div>
          ${dec.body ? '<div class="item-text">' + esc(dec.body) + '</div>' : ""}
          ${imgs.length
            ? '<div class="channel-post-images">' + imgs.map((b, i) =>
                '<img src="data:image/jpeg;base64,' + b + '" alt="Фото ' + (i + 1) + '">').join("") + '</div>'
            : ""}
          <div class="item-sub">👁 ${Number(m.viewCount || 0)} · 💬 ${Number(m.commentsCount || 0)}</div>
          <div class="item-actions">
            <button class="btn-secondary" data-act="edit">Изменить</button>
            <button class="btn-secondary" data-act="pin">${m.isPinned ? "Открепить" : "Закрепить"}</button>
            <button class="btn-danger" data-act="delete">Удалить</button>
          </div>`;
        el.querySelector('[data-act="edit"]').addEventListener("click", async () => {
          const body = prompt("Текст поста:", dec.body);
          if (body === null) return;
          const topic = prompt(
            "Тема поста (пусто — без темы). Доступно: " + NEWS_TOPICS.join(", "),
            dec.topic
          );
          if (topic === null) return;
          const cleanTopic = topic.replace(NEWS_TOPIC_SUFFIX, "").trim();
          try {
            await updateDoc(doc(db, "chats", OFFICIAL_CHANNEL_ID, "messages", d.id), {
              text: encodeNewsTopic(cleanTopic, body.trim()),
            });
            await refreshChatPreviewAfterDelete(OFFICIAL_CHANNEL_ID, d.id);
            logAdminAction("CHANNEL_POST_EDITED", (body.trim() || "Фото").slice(0, 100));
            toast("Пост обновлён");
          } catch (err) {
            handleErr("Не удалось изменить пост")(err);
          }
        });
        el.querySelector('[data-act="pin"]').addEventListener("click", () => {
          updateDoc(doc(db, "chats", OFFICIAL_CHANNEL_ID, "messages", d.id), { isPinned: !m.isPinned })
            .then(() => {
              toast(m.isPinned ? "Пост откреплён" : "Пост закреплён");
              logAdminAction("CHANNEL_POST_PINNED", (dec.body || "").slice(0, 100));
            })
            .catch(handleErr("Не удалось закрепить пост"));
        });
        el.querySelector('[data-act="delete"]').addEventListener("click", async () => {
          if (!confirm("Удалить этот пост из официального канала?")) return;
          try {
            await deleteDoc(doc(db, "chats", OFFICIAL_CHANNEL_ID, "messages", d.id));
            await refreshChatPreviewAfterDelete(OFFICIAL_CHANNEL_ID, d.id);
            logAdminAction("CHANNEL_POST_DELETED", (dec.body || "").slice(0, 100));
            toast("Пост удалён");
          } catch (err) {
            handleErr("Не удалось удалить пост")(err);
          }
        });
        listEl.appendChild(el);
      });
    },
    handleErr("Не удалось загрузить посты")
  );

  // ── Фото и предпросмотр (в стиле приложения: плитка темы + тело + фото)
  let channelPhotoFiles = [];
  const channelPreviewEl = $("channel-preview");

  function renderChannelThumbs() {
    const thumbs = $("channel-post-thumbs");
    thumbs.innerHTML = "";
    channelPhotoFiles.forEach((file, i) => {
      const wrap = document.createElement("div");
      wrap.className = "channel-thumb";
      const img = document.createElement("img");
      const url = URL.createObjectURL(file);
      img.src = url;
      img.onload = () => URL.revokeObjectURL(url);
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = "×";
      btn.title = "Убрать фото";
      btn.addEventListener("click", () => {
        channelPhotoFiles.splice(i, 1);
        renderChannelThumbs();
        renderChannelPreview();
      });
      wrap.appendChild(img);
      wrap.appendChild(btn);
      thumbs.appendChild(wrap);
    });
  }

  function renderChannelPreview() {
    const topic = $("channel-post-topic").value;
    const body = $("channel-post-text").value.trim();
    const el = document.createElement("div");
    el.className = "item";
    el.innerHTML = `
      ${topic ? '<div class="item-head"><span class="badge badge-blue">' + esc(topic) + '</span></div>' : ""}
      <div class="item-text">${esc(body || "Текст поста…")}</div>
      ${channelPhotoFiles.length
        ? '<div class="item-sub">📷 Фото: ' + channelPhotoFiles.length + '</div>' : ""}`;
    channelPreviewEl.innerHTML = "";
    channelPreviewEl.appendChild(el);
  }

  // Добавление фото — из выбора файлов или drag-and-drop.
  function addChannelPhotos(files) {
    if (!files || files.length === 0) return;
    const images = files.filter((f) => f.type && f.type.startsWith("image/"));
    if (images.length === 0) {
      toast("Можно прикрепить только изображения", false);
      return;
    }
    const room = Math.max(0, CHANNEL_MAX_PHOTOS - channelPhotoFiles.length);
    channelPhotoFiles = channelPhotoFiles.concat(images.slice(0, room));
    if (images.length > room) toast("Можно прикрепить не больше " + CHANNEL_MAX_PHOTOS + " фото", false);
    renderChannelThumbs();
    if (!channelPreviewEl.classList.contains("hidden")) renderChannelPreview();
  }

  $("channel-post-photos").addEventListener("change", (e) => {
    addChannelPhotos(Array.from(e.target.files || []));
    e.target.value = "";
  });

  // Drag-and-drop: перетаскивание фото в зону выбора.
  const dropzone = $("channel-dropzone");
  ["dragenter", "dragover"].forEach((ev) => {
    dropzone.addEventListener(ev, (e) => {
      e.preventDefault();
      dropzone.classList.add("dragover");
    });
  });
  dropzone.addEventListener("dragleave", (e) => {
    // dragleave срабатывает и при переходе на дочерний элемент — не мигаем.
    if (dropzone.contains(e.relatedTarget)) return;
    dropzone.classList.remove("dragover");
  });
  dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    addChannelPhotos(Array.from(e.dataTransfer.files || []));
  });

  $("btn-channel-preview").addEventListener("click", () => {
    channelPreviewEl.classList.toggle("hidden");
    if (!channelPreviewEl.classList.contains("hidden")) renderChannelPreview();
  });
  ["channel-post-text", "channel-post-topic"].forEach((id) => {
    const refresh = () => {
      if (!channelPreviewEl.classList.contains("hidden")) renderChannelPreview();
    };
    $(id).addEventListener("input", refresh);
    $(id).addEventListener("change", refresh);
  });

  // Сжимает прикреплённые фото под общий бюджет документа (< 1 МБ).
  async function compressChannelPhotos() {
    if (channelPhotoFiles.length === 0) return [];
    const perImage = Math.floor(CHANNEL_PHOTO_BUDGET / channelPhotoFiles.length);
    const out = [];
    for (const file of channelPhotoFiles) {
      try {
        const img = await loadImageFromFile(file);
        out.push(compressChannelImage(img, perImage));
      } catch (e) {
        console.warn("Фото пропущено:", file.name, e);
      }
    }
    if (out.length === 0) throw new Error("не удалось обработать фото");
    return out;
  }

  $("form-channel-post").addEventListener("submit", async (e) => {
    e.preventDefault();
    const rawText = $("channel-post-text").value.trim();
    const topic = $("channel-post-topic").value;
    if (!rawText && channelPhotoFiles.length === 0) {
      return toast("Добавьте текст или фото", false);
    }
    const silent = $("channel-post-silent").checked;
    try {
      const photos = await compressChannelPhotos();
      const chatSnap = await ensureOfficialChannelDoc();
      const uid = auth.currentUser.uid;
      const participants = (chatSnap.get("participantIds") || []).filter((x) => x !== uid);
      const now = Date.now();
      const msgRef = doc(collection(db, "chats", OFFICIAL_CHANNEL_ID, "messages"));
      // Тема кодируется префиксом как в NewsTopic.encode; без текста тема не нужна.
      const text = encodeNewsTopic(rawText ? topic : "", rawText);
      const data = {
        senderId: uid,
        text,
        timestamp: now,
        status: "SENT",
        notified: silent, // тихая публикация — push не уходит
        ...(silent ? { silent: true } : {}),
      };
      if (photos.length === 1) {
        data.imageBase64 = photos[0];
      } else if (photos.length > 1) {
        data.imagesBase64 = photos;
        data.imageBase64 = photos[0]; // совместимость со старыми клиентами
      }
      // Сообщение и превью чата — одним батчем, как sendRawMessage в Android.
      const batch = writeBatch(db);
      batch.set(msgRef, data);
      const chatUpdate = {
        lastMessage: channelPreviewText(text, photos.length),
        lastMessageTimestamp: now,
        lastMessageSenderId: uid,
        lastMessageStatus: "SENT",
        lastMessageId: msgRef.id,
      };
      participants.forEach((otherUid) => {
        chatUpdate["unreadCounts." + otherUid] = increment(1);
      });
      batch.update(doc(db, "chats", OFFICIAL_CHANNEL_ID), chatUpdate);
      await batch.commit();
      $("channel-post-text").value = "";
      $("channel-post-topic").value = "";
      refreshNiceSelect($("channel-post-topic"));
      $("channel-post-silent").checked = false;
      channelPhotoFiles = [];
      renderChannelThumbs();
      channelPreviewEl.classList.add("hidden");
      toast(silent ? "Пост опубликован без push" : "Пост опубликован — push уйдёт подписчикам");
      logAdminAction("CHANNEL_POST_ADDED", (rawText || "Фото").slice(0, 100));
    } catch (err) {
      handleErr("Не удалось опубликовать пост")(err);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Секция «Push-центр» — произвольные рассылки                          */
/* ------------------------------------------------------------------ */

function startPush() {
  const audienceSel = $("push-broadcast-audience");
  const uidInput = $("push-broadcast-uid");
  audienceSel.addEventListener("change", () => {
    uidInput.classList.toggle("hidden", audienceSel.value !== "uid");
  });

  const listEl = $("push-broadcasts-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "pushBroadcasts"), orderBy("createdAt", "desc"), limit(50)),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Рассылок пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => {
        const b = d.data();
        const audienceLabel =
          b.audience === "all" ? "Все пользователи"
            : b.audience === "uid" ? "Один пользователь"
              : "Подписчики школы";
        const stats = b.notified !== false && b.sentCount != null
          ? " · доставлено: " + b.sentCount + (b.errorCount ? ", ошибок: " + b.errorCount : "")
          : "";
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `
          <div class="item-head">
            ${b.notified === false
              ? '<span class="badge badge-yellow">в очереди</span>'
              : '<span class="badge badge-green">отправлено</span>'}
            <span class="item-title">${esc(b.title || "")}</span>
            <span class="item-date">${fmtDate(b.createdAt)}</span>
          </div>
          <div class="item-text">${esc(b.body || "")}</div>
          <div class="item-sub">${audienceLabel}${b.userId ? " · " + esc(b.userId) : ""}${stats}</div>
          ${b.notified === false ? '<div class="item-actions"><button class="btn-danger" data-act="cancel">Отменить</button></div>' : ""}`;
        const cancelBtn = el.querySelector('[data-act="cancel"]');
        if (cancelBtn) {
          cancelBtn.addEventListener("click", () => {
            if (!confirm("Отменить эту рассылку? Она ещё не отправлена.")) return;
            deleteDoc(doc(db, "pushBroadcasts", d.id))
              .then(() => toast("Рассылка отменена"))
              .catch(handleErr("Не удалось отменить рассылку"));
          });
        }
        listEl.appendChild(el);
      });
    },
    handleErr("Не удалось загрузить рассылки")
  );

  $("form-push-broadcast").addEventListener("submit", async (e) => {
    e.preventDefault();
    const title = $("push-broadcast-title").value.trim();
    const body = $("push-broadcast-body").value.trim();
    const audience = audienceSel.value;
    const userId = uidInput.value.trim();
    if (!title || !body) return toast("Заполните заголовок и текст", false);
    if (audience === "uid" && !userId) return toast("Укажите UID получателя", false);
    if (audience === "all" && !confirm("Отправить уведомление ВСЕМ пользователям с приложением?")) return;
    try {
      await addDoc(collection(db, "pushBroadcasts"), {
        title,
        body,
        audience,
        userId: audience === "uid" ? userId : null,
        createdBy: auth.currentUser.uid,
        createdByName: adminActorName || auth.currentUser.email || "Админ",
        createdAt: Date.now(),
        notified: false,
      });
      $("push-broadcast-title").value = "";
      $("push-broadcast-body").value = "";
      uidInput.value = "";
      toast("Рассылка в очереди — воркер отправит её в течение ~5 минут");
      logAdminAction(
        "ADMIN_BROADCAST_QUEUED",
        title + " → " + (audience === "all" ? "все" : audience === "uid" ? userId : "подписчики школы")
      );
    } catch (err) {
      handleErr("Не удалось поставить рассылку в очередь")(err);
    }
  });
}

/* ------------------------------------------------------------------ */
/* Красивые выпадающие списки (вместо системного «прямоугольника»)      */
/* ------------------------------------------------------------------ */

// Оборачивает нативный <select> в кастомный закруглённый контрол: select
// скрыт, но остаётся источником значения (select.value/selectedIndex), а
// выбор варианта обновляет его и диспатчит change — поэтому все прежние
// обработчики (переключение полей, чтение значения при отправке) работают.
function enhanceSelect(select) {
  if (!select || select.dataset.niceSelect === "1") return;
  select.dataset.niceSelect = "1";

  const wrap = document.createElement("div");
  wrap.className = "nice-select";
  select.parentNode.insertBefore(wrap, select);
  wrap.appendChild(select);

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "nice-select-trigger";
  const label = document.createElement("span");
  label.className = "nice-select-label";
  trigger.appendChild(label);

  const panel = document.createElement("div");
  panel.className = "nice-select-panel hidden";
  wrap.appendChild(trigger);
  wrap.appendChild(panel);

  const syncLabel = () => {
    const opt = select.options[select.selectedIndex];
    label.textContent = opt ? opt.textContent : "";
  };
  const onDocDown = (e) => { if (!wrap.contains(e.target)) close(); };
  const onKey = (e) => { if (e.key === "Escape") close(); };

  function close() {
    panel.classList.add("hidden");
    trigger.classList.remove("open");
    document.removeEventListener("mousedown", onDocDown, true);
    document.removeEventListener("keydown", onKey);
  }

  function open() {
    closeAllNiceSelects(wrap);
    panel.innerHTML = "";
    Array.from(select.options).forEach((opt, idx) => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "nice-select-option" + (idx === select.selectedIndex ? " active" : "");
      item.textContent = opt.textContent;
      item.addEventListener("click", () => {
        if (select.selectedIndex !== idx) {
          select.selectedIndex = idx;
          select.dispatchEvent(new Event("change", { bubbles: true }));
        }
        syncLabel();
        close();
      });
      panel.appendChild(item);
    });
    panel.classList.remove("hidden");
    trigger.classList.add("open");
    document.addEventListener("mousedown", onDocDown, true);
    document.addEventListener("keydown", onKey);
  }

  trigger.addEventListener("click", () => {
    panel.classList.contains("hidden") ? open() : close();
  });

  wrap._sync = syncLabel;
  wrap._close = close;
  syncLabel();
}

function closeAllNiceSelects(except) {
  document.querySelectorAll(".nice-select").forEach((w) => {
    if (w !== except && typeof w._close === "function") w._close();
  });
}

/** Обновляет подпись после программной смены select.value (например, сброса). */
function refreshNiceSelect(select) {
  const wrap = select && select.closest(".nice-select");
  if (wrap && typeof wrap._sync === "function") wrap._sync();
}

function startPanel() {
  startSummary();
  startSettings();
  startNews();
  startPolls();
  startChannel();
  startPush();
  startInbox();
  startTeachers();
  startIdeas();
  startReviews();
  startReports();
  startSupport();
  startFaq();
  startUsers();
  startBlocks();
  startAudit();
  initUserSearchModal();
  initFileModal();
  initCsvExport();

  // Кастомные выпадающие списки. #support-reply-template не трогаем — его
  // опции заполняются динамически (шаблоны ответов поддержки).
  ["news-publish-mode", "channel-post-topic", "push-broadcast-audience", "support-restrict-duration"]
    .forEach((id) => enhanceSelect($(id)));
}
