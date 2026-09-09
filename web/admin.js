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
  SCHOOL_TEACHER_FILE_SET: "Файл урока учителя обновлён",
  SCHOOL_QUESTION_ANSWERED: "Ответ на вопрос ученика",
  SCHOOL_QUESTION_HIDDEN: "Скрытие/показ вопроса",
  SCHOOL_SCHEDULE_STATUS_SET: "Пометка актуальности расписания",
  SCHOOL_HOLIDAY_DATE_SET: "Дата каникул",
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

    // Дата каникул от админа (пустая строка — используется зашитая из сборки).
    $("holiday-date").value = snap.get("schoolHolidayDate") || "";
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
/* Секция «Жалобы» — сквозная очередь модерации (chats/*/reports)      */
/* ------------------------------------------------------------------ */

// 1:1 с Report.kt — значения enum и подписи причин/статусов.
const REPORT_REASONS = {
  SPAM: "Спам",
  HARASSMENT: "Оскорбления или травля",
  VIOLENCE: "Насилие или угрозы",
  ILLEGAL_CONTENT: "Запрещённый контент",
  FRAUD: "Мошенничество",
  OTHER: "Другое",
  APPEAL: "Обжалование блокировки",
};
const REPORT_STATUS_LABELS = {
  PENDING: "На рассмотрении",
  RESOLVED: "Решена",
  DISMISSED: "Отклонена",
};

let reportsCache = []; // снапшот последнего запроса для CSV
let reportsFilter = "PENDING";
let reportsUnsub = null;

// Мягкое удаление сообщения — поля 1:1 с MessageRepositoryImpl.deleteMessage
// (deletedByAdmin=true показывает в чате «Сообщение удалено администратором»).
async function softDeleteMessage(chatId, messageId) {
  await updateDoc(doc(db, "chats", chatId, "messages", messageId), {
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
  });
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
  const previewText =
    r.encrypted ? "🔒 Сообщение"
    : r.text ? r.text
    : r.voiceBase64 ? "🎤 Голосовое сообщение"
    : r.isViewOnce ? "📷 Фото (один просмотр)"
    : r.imagesBase64 ? "📷 Фото (" + (r.imagesBase64.length || 1) + ")"
    : r.imageBase64 ? "📷 Фото"
    : r.locationLat != null ? "📍 Геопозиция"
    : r.fileBase64 ? "📎 " + (r.fileName || "Файл")
    : "";
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
  if (isPending) {
    const actions = document.createElement("div");
    actions.className = "item-actions";
    if (r.targetType === "MESSAGE" && r.targetMessageId) {
      const deleteBtn = document.createElement("button");
      deleteBtn.type = "button";
      deleteBtn.className = "btn-danger";
      deleteBtn.textContent = "Удалить сообщение";
      deleteBtn.addEventListener("click", () => resolveReportAction(docSnap, "deleteMessage"));
      actions.appendChild(deleteBtn);
    }
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
    el.appendChild(actions);
  }
  return el;
}

async function resolveReportAction(docSnap, action) {
  const r = docSnap.data();
  const chatId = docSnap.ref.parent.parent.id;
  const targetName = r.targetUserName || "пользователя";
  const confirmText =
    action === "deleteMessage"
      ? `Удалить сообщение «${(r.targetMessagePreview || "").slice(0, 60)}» у ${targetName}? В чате появится «Сообщение удалено администратором».`
      : action === "blockUser"
        ? `Заблокировать аккаунт ${targetName}? ${r.isAppeal ? "Обжалование при этом будет отклонено. " : ""}Он не сможет пользоваться приложением.`
        : `Отклонить жалобу на ${targetName}?`;
  if (!confirm(confirmText)) return;
  try {
    if (action === "deleteMessage") {
      await softDeleteMessage(chatId, r.targetMessageId);
      await refreshChatPreviewAfterDelete(chatId, r.targetMessageId);
      await finalizeReport(docSnap, "RESOLVED", "MESSAGE_DELETED", "Сообщение удалено администратором");
      logAdminAction("REPORT_RESOLVED_MESSAGE_DELETED", "Жалоба " + chatId + "/" + docSnap.id, r.targetUserId, r.targetUserName);
      toast("Сообщение удалено, жалоба закрыта");
    } else if (action === "blockUser") {
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
  const listEl = $("reports-list");
  const status = reportsFilter;
  const docs = status === "ALL"
    ? snap.docs
    : snap.docs.filter((d) => (d.data().status || "PENDING") === status);
  if (!docs.length) {
    listEl.innerHTML = `<p class="empty-note">${status === "PENDING" ? "Новых жалоб нет — всё чисто! ✅" : "Жалоб с этим статусом нет."}</p>`;
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
      renderReports({ docs: reportsCache });
    });
  });
  setLoading($("reports-list"));
  reportsUnsub = onSnapshot(
    query(collectionGroup(db, "reports"), orderBy("createdAt", "desc"), limit(200)),
    renderReports,
    handleErr("Не удалось загрузить жалобы")
  );
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

function startUsers() {
  $("users-search-input").addEventListener("input", () => {
    clearTimeout(usersSearchTimer);
    usersSearchTimer = setTimeout(runUsersSearch, 350);
  });
  $("btn-close-user-card").addEventListener("click", closeUserCard);
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
    const [news, polls, ideas, reviews, teachers, questions, reports] = await Promise.all([
      getDocs(collection(db, "schoolNews")),
      getDocs(collection(db, "schoolPolls")),
      getDocs(collection(db, "schoolIdeas")),
      getDocs(collection(db, "schoolReviews")),
      getDocs(collection(db, "schoolTeacherProfiles")),
      getDocs(query(collectionGroup(db, "questions"), orderBy("createdAt", "desc"), limit(300))),
      getDocs(query(collectionGroup(db, "reports"), orderBy("createdAt", "desc"), limit(200))),
    ]);
    let starsSum = 0;
    reviews.forEach((d) => (starsSum += d.data().stars || 0));
    const unanswered = questions.docs.filter((d) => {
      const q = d.data();
      return !q.answer && q.hidden !== true;
    }).length;
    const linked = teachers.docs.filter((d) => !!d.data().linkedUserId).length;
    const pendingReports = reports.docs.filter(
      (d) => (d.data().status || "PENDING") === "PENDING"
    ).length;
    const stats = [
      { value: news.size, label: "новостей", section: "news" },
      { value: polls.size, label: "опросов", section: "polls" },
      { value: unanswered, label: "вопросов без ответа", section: "inbox" },
      { value: pendingReports, label: "жалоб на рассмотрении", section: "reports" },
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
  startReports();
  startUsers();
  startBlocks();
  startAudit();
  initUserSearchModal();
  initFileModal();
  initCsvExport();
}
