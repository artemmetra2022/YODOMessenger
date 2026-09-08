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
 *                      schoolScheduleUpdatedAt}
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
  query,
  where,
  orderBy,
  limit,
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

/** Заменить список, не пересоздавая панель (снимает listeners). */
function setLoading(listEl) {
  listEl.innerHTML = '<p class="empty-note">Загрузка…</p>';
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
  SCHOOL_POLL_ADDED: "Новый опрос",
  SCHOOL_POLL_DELETED: "Удаление опроса",
  SCHOOL_TEACHER_CREATED: "Создан профиль учителя",
  SCHOOL_TEACHER_LINKED: "Привязка аккаунта учителя",
  SCHOOL_TEACHER_UNLINKED: "Отвязка аккаунта учителя",
  SCHOOL_TEACHER_DELETED: "Удалён профиль учителя",
  SCHOOL_QUESTION_ANSWERED: "Ответ на вопрос ученика",
  SCHOOL_QUESTION_HIDDEN: "Скрытие/показ вопроса",
  SCHOOL_SCHEDULE_STATUS_SET: "Пометка актуальности расписания",
  SCHOOL_SECTION_VISIBILITY: "Видимость раздела «Школа»",
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
}

/* ------------------------------------------------------------------ */
/* Секция «Новости»                                                    */
/* ------------------------------------------------------------------ */

function newsItem(docSnap) {
  const n = docSnap.data();
  const el = document.createElement("div");
  el.className = "item";
  el.innerHTML = `
    <div class="item-head">
      ${n.pinned ? '<span class="badge badge-blue">📌 Закреплена</span>' : ""}
      ${n.notified === false
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
  $("form-add-news").addEventListener("submit", async (e) => {
    e.preventDefault();
    const sender = $("news-sender").value.trim();
    const text = $("news-text").value.trim();
    const eventDate = $("news-event-date").value.trim();
    if (!sender || !text) return;
    try {
      await addDoc(collection(db, "schoolNews"), {
        sender,
        text,
        eventDate,
        pubDate: Date.now(),
        pinned: false,
        notified: false, // очередь push-воркера
      });
      $("news-text").value = "";
      $("news-event-date").value = "";
      toast("Новость опубликована — push уйдёт подписчикам автоматически");
      logAdminAction("SCHOOL_NEWS_ADDED", sender + ": " + text.slice(0, 100));
    } catch (err) {
      handleErr("Не удалось опубликовать новость")(err);
    }
  });

  const listEl = $("news-list");
  setLoading(listEl);
  onSnapshot(
    query(
      collection(db, "schoolNews"),
      orderBy("pinned", "desc"),
      orderBy("pubDate", "desc"),
      limit(50)
    ),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Новостей пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => listEl.appendChild(newsItem(d)));
    },
    handleErr("Не удалось загрузить новости")
  );
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
      <button class="btn-danger" data-act="delete">Удалить опрос</button>
    </div>`;
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
    try {
      const votes = {};
      options.forEach((_, i) => (votes[String(i)] = 0));
      await addDoc(collection(db, "schoolPolls"), {
        question,
        options,
        votes,
        voters: {},
        createdAt: Date.now(),
        notified: false, // очередь push-воркера
      });
      $("poll-question").value = "";
      $("poll-options").value = "";
      toast("Опрос создан — push уйдёт подписчикам автоматически");
      logAdminAction("SCHOOL_POLL_ADDED", question);
    } catch (err) {
      handleErr("Не удалось создать опрос")(err);
    }
  });

  const listEl = $("polls-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "schoolPolls"), orderBy("createdAt", "desc"), limit(50)),
    (snap) => {
      if (snap.empty) {
        listEl.innerHTML = '<p class="empty-note">Опросов пока нет.</p>';
        return;
      }
      listEl.innerHTML = "";
      snap.forEach((d) => listEl.appendChild(pollItem(d)));
    },
    handleErr("Не удалось загрузить опросы")
  );
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
    <div class="item-sub">
      ${linked ? "🔗 Аккаунт: " + esc(t.linkedUserName || t.linkedUserId) : "ℓ Нет привязанного аккаунта"}
    </div>
    <div class="item-actions">
      <button class="btn-secondary" data-act="link">${linked ? "Перепривязать" : "Привязать аккаунт"}</button>
      ${linked ? '<button class="btn-secondary" data-act="unlink">Отвязать</button>' : ""}
      <button class="btn-secondary" data-act="questions">Вопросы</button>
      <button class="btn-danger" data-act="delete">Удалить профиль</button>
    </div>`;

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
    '<p class="empty-note">Начните вводить имя или @username учителя</p>';
  $("user-search-overlay").classList.remove("hidden");
  $("user-search-input").focus();
}

function closeUserSearch() {
  pendingLink = null;
  $("user-search-overlay").classList.add("hidden");
}

async function runUserSearch() {
  const term = $("user-search-input").value.trim().toLowerCase().replace(/^@/, "");
  const resultsEl = $("user-search-results");
  if (term.length < 2) {
    resultsEl.innerHTML = '<p class="empty-note">Минимум 2 символа</p>';
    return;
  }
  resultsEl.innerHTML = '<p class="empty-note">Поиск…</p>';
  try {
    const byUsername = await searchUsersByField("usernameLowercase", term);
    const byName = await searchUsersByField("displayNameLowercase", term);
    const seen = new Set();
    const docs = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } }
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
    if (e.key === "Escape" && !$("user-search-overlay").classList.contains("hidden")) {
      closeUserSearch();
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
/* Секции «Идеи» и «Отзывы» (только чтение)                            */
/* ------------------------------------------------------------------ */

function startIdeas() {
  const listEl = $("ideas-list");
  setLoading(listEl);
  onSnapshot(
    query(collection(db, "schoolIdeas"), orderBy("createdAt", "desc"), limit(100)),
    (snap) => {
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
    const [news, polls, ideas, reviews, teachers, questions] = await Promise.all([
      getDocs(collection(db, "schoolNews")),
      getDocs(collection(db, "schoolPolls")),
      getDocs(collection(db, "schoolIdeas")),
      getDocs(collection(db, "schoolReviews")),
      getDocs(collection(db, "schoolTeacherProfiles")),
      getDocs(query(collectionGroup(db, "questions"), orderBy("createdAt", "desc"), limit(300))),
    ]);
    let starsSum = 0;
    reviews.forEach((d) => (starsSum += d.data().stars || 0));
    const unanswered = questions.docs.filter((d) => {
      const q = d.data();
      return !q.answer && q.hidden !== true;
    }).length;
    const linked = teachers.docs.filter((d) => !!d.data().linkedUserId).length;
    const stats = [
      { value: news.size, label: "новостей", section: "news" },
      { value: polls.size, label: "опросов", section: "polls" },
      { value: unanswered, label: "вопросов без ответа", section: "inbox" },
      { value: teachers.size + " (" + linked + " привяз.)", label: "учителей", section: "teachers" },
      { value: ideas.size, label: "идей", section: "ideas" },
      {
        value: reviews.size
          ? (starsSum / reviews.size).toFixed(1) + " / 5 (" + reviews.size + ")"
          : "—",
        label: "средняя оценка",
        section: "reviews",
      },
    ];
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
/* Старт панели                                                        */
/* ------------------------------------------------------------------ */

function startPanel() {
  startSummary();
  startSettings();
  startNews();
  startPolls();
  startInbox();
  startTeachers();
  startIdeas();
  startReviews();
  startAudit();
  initUserSearchModal();
}
