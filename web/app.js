/**
 * YODO Messenger — веб-версия (MVP)
 * Чистый JS + Firebase SDK через CDN. Модель данных полностью совпадает с
 * Android-приложением (см. ChatRepositoryImpl / MessageRepositoryImpl):
 * все таймстампы — миллисекунды, type-поля заглавными, chatId личных чатов — авто-ID.
 *
 * Рефакторинг: тот же функционал, реорганизовано по секциям для читаемости.
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  sendEmailVerification,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  getDocs,
  setDoc,
  collection,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  updateDoc,
  writeBatch,
  increment,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

/* ------------------------------------------------------------------ */
/* Firebase init                                                       */
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

/* ------------------------------------------------------------------ */
/* Утилиты DOM / форматирование                                        */
/* ------------------------------------------------------------------ */

const $ = (id) => document.getElementById(id);

function showScreen(id) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
  $(id).classList.add("active");
}

function esc(text) {
  const div = document.createElement("div");
  div.textContent = text ?? "";
  return div.innerHTML;
}

function formatTime(ms) {
  if (!ms) return "";
  const d = new Date(ms);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  const hm = d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
  if (sameDay) return hm;
  return d.toLocaleDateString("ru-RU", { day: "2-digit", month: "2-digit" }) + " " + hm;
}

const AUTH_ERROR_MESSAGES = {
  "auth/invalid-credential": "Неверный email или пароль",
  "auth/wrong-password": "Неверный пароль",
  "auth/user-not-found": "Пользователь не найден",
  "auth/invalid-email": "Некорректный email",
  "auth/email-already-in-use": "Email уже занят",
  "auth/weak-password": "Слабый пароль (минимум 6 символов)",
  "auth/too-many-requests": "Слишком много попыток. Попробуйте позже",
  "auth/network-request-failed": "Ошибка сети. Проверьте интернет",
};

async function getErrorMessage(error) {
  return AUTH_ERROR_MESSAGES[error?.code] || error?.message || "Неизвестная ошибка";
}

/* ------------------------------------------------------------------ */
/* Состояние приложения                                                */
/* ------------------------------------------------------------------ */

let currentUser = null; // { uid, email, displayName, username }
let chatsUnsub = null; // отписка слушателя списка чатов
let messagesUnsub = null; // отписка слушателя сообщений
let blockedUnsub = null; // отписка слушателя блокировки
let isBlocked = false;
let activeChatId = null;
let activeChatData = null;
let chatsCache = new Map(); // chatId -> данные чата
let userNamesCache = new Map(); // uid -> displayName (для групп)
let searchTimer = null;

/* ------------------------------------------------------------------ */
/* Роутинг экранов авторизации                                         */
/* ------------------------------------------------------------------ */

$("link-to-register").addEventListener("click", (e) => {
  e.preventDefault();
  showScreen("screen-register");
});

$("link-to-login").addEventListener("click", (e) => {
  e.preventDefault();
  showScreen("screen-login");
});

/* ------------------------------------------------------------------ */
/* Логин (email или username)                                          */
/* ------------------------------------------------------------------ */

async function resolveEmailForLogin(identifier) {
  if (identifier.includes("@")) return identifier;

  // Вход по username: резолвим email ДО авторизации (как в Android)
  const username = identifier.replace(/^@/, "").toLowerCase();
  const usernameSnap = await getDoc(doc(db, "usernames", username));
  if (!usernameSnap.exists()) {
    throw new Error("USERNAME_NOT_FOUND");
  }
  const uid = usernameSnap.data().uid;
  const userSnap = await getDoc(doc(db, "users", uid));
  if (!userSnap.exists() || !userSnap.data().email) {
    throw new Error("EMAIL_NOT_FOUND");
  }
  return userSnap.data().email;
}

$("form-login").addEventListener("submit", async (e) => {
  e.preventDefault();
  const errorEl = $("login-error");
  errorEl.textContent = "";

  const identifier = $("login-identifier").value.trim();
  const password = $("login-password").value;
  if (!identifier || !password) {
    errorEl.textContent = "Заполните все поля";
    return;
  }

  try {
    let email;
    try {
      email = await resolveEmailForLogin(identifier);
    } catch (resolveErr) {
      if (resolveErr.message === "USERNAME_NOT_FOUND") {
        errorEl.textContent = "Пользователь с таким username не найден";
      } else if (resolveErr.message === "EMAIL_NOT_FOUND") {
        errorEl.textContent = "Не удалось найти email этого пользователя";
      } else {
        errorEl.textContent = await getErrorMessage(resolveErr);
      }
      return;
    }
    await signInWithEmailAndPassword(auth, email, password);
    // дальнейшая навигация — в onAuthStateChanged
  } catch (err) {
    errorEl.textContent = await getErrorMessage(err);
  }
});

/* ------------------------------------------------------------------ */
/* Регистрация                                                         */
/* ------------------------------------------------------------------ */

function validateRegistration({ username, displayName, email, password }) {
  if (!/^[a-z0-9_]{3,20}$/.test(username)) {
    return "Username: латиница/цифры/_, 3-20 символов";
  }
  if (!displayName) {
    return "Введите отображаемое имя";
  }
  if (!email.includes("@")) {
    return "Некорректный email";
  }
  if (password.length < 6) {
    return "Пароль минимум 6 символов";
  }
  return null;
}

function buildPublicId(uid) {
  return "YODO-" + uid.slice(0, 4).toUpperCase() + "-" + uid.slice(4, 8).toUpperCase();
}

$("form-register").addEventListener("submit", async (e) => {
  e.preventDefault();
  const errorEl = $("register-error");
  errorEl.textContent = "";

  const username = $("reg-username").value.trim().toLowerCase();
  const displayName = $("reg-displayname").value.trim();
  const email = $("reg-email").value.trim();
  const password = $("reg-password").value;

  const validationError = validateRegistration({ username, displayName, email, password });
  if (validationError) {
    errorEl.textContent = validationError;
    return;
  }

  try {
    // Проверяем занятость username (как транзакция в Android, здесь — get перед create)
    const usernameSnap = await getDoc(doc(db, "usernames", username));
    if (usernameSnap.exists()) {
      errorEl.textContent = "Этот username уже занят";
      return;
    }

    const cred = await createUserWithEmailAndPassword(auth, email, password);
    const uid = cred.user.uid;
    const now = Date.now();

    // users/{uid} — поля один в один с AuthRepositoryImpl.register
    await setDoc(doc(db, "users", uid), {
      uid: uid,
      displayName: displayName,
      displayNameLowercase: displayName.toLowerCase(),
      username: username,
      usernameLowercase: username,
      email: email,
      publicId: buildPublicId(uid),
      isEmailVerified: false,
      createdAt: now,
    });

    // usernames/{username} → { uid }
    await setDoc(doc(db, "usernames", username), { uid: uid });
    await sendEmailVerification(cred.user);
    // навигация — в onAuthStateChanged
  } catch (err) {
    errorEl.textContent = await getErrorMessage(err);
  }
});

/* ------------------------------------------------------------------ */
/* Подтверждение email                                                 */
/* ------------------------------------------------------------------ */

$("btn-check-verified").addEventListener("click", async () => {
  await auth.currentUser.reload();
  if (auth.currentUser.emailVerified) {
    // синхронизируем флаг в Firestore (best-effort, как Android)
    updateDoc(doc(db, "users", auth.currentUser.uid), { isEmailVerified: true }).catch(() => {});
    navigateAfterAuth();
  } else {
    $("verify-error").textContent = "Email всё ещё не подтверждён. Проверьте почту.";
  }
});

$("btn-resend-verification").addEventListener("click", async () => {
  try {
    await sendEmailVerification(auth.currentUser);
    $("verify-error").textContent = "Письмо отправлено";
  } catch (err) {
    $("verify-error").textContent = await getErrorMessage(err);
  }
});

$("btn-logout-from-verify").addEventListener("click", () => signOut(auth));
$("btn-logout-from-blocked").addEventListener("click", () => signOut(auth));
$("btn-logout").addEventListener("click", () => signOut(auth));

/* ------------------------------------------------------------------ */
/* Auth state / навигация                                              */
/* ------------------------------------------------------------------ */

function resetSessionState() {
  if (chatsUnsub) { chatsUnsub(); chatsUnsub = null; }
  if (messagesUnsub) { messagesUnsub(); messagesUnsub = null; }
  if (blockedUnsub) { blockedUnsub(); blockedUnsub = null; }
  activeChatId = null;
  activeChatData = null;
  isBlocked = false;
  chatsCache.clear();
}

onAuthStateChanged(auth, async (user) => {
  resetSessionState();

  if (!user) {
    currentUser = null;
    showScreen("screen-login");
    return;
  }

  currentUser = { uid: user.uid, email: user.email };

  // Глобальная блокировка: слушатель на свой документ (как Android-оверлей)
  blockedUnsub = onSnapshot(
    doc(db, "globalBlocks", user.uid),
    (snap) => {
      isBlocked = snap.exists();
      if (isBlocked) {
        const data = snap.data();
        $("blocked-reason").textContent =
          "Причина: " + (data.reason || "не указана") +
          (data.blockedByName ? " (заблокировал: " + data.blockedByName + ")" : "");
        showScreen("screen-blocked");
      } else {
        navigateAfterAuth();
      }
    },
    () => { /* нет прав на чтение — считаем, что не заблокирован */ }
  );

  navigateAfterAuth();
});

// Единая точка навигации после авторизации: блокировка → email → приложение
function navigateAfterAuth() {
  if (!currentUser || isBlocked) return;
  if (!auth.currentUser.emailVerified) {
    $("verify-email").textContent = auth.currentUser.email;
    showScreen("screen-verify");
    return;
  }
  enterApp();
}

async function enterApp() {
  if (!currentUser) return;
  // Подтягиваем профиль
  try {
    const snap = await getDoc(doc(db, "users", currentUser.uid));
    if (snap.exists()) {
      currentUser.displayName = snap.data().displayName || "Без имени";
      currentUser.username = snap.data().username || "";
    }
  } catch (e) { /* профиль может отсутствовать для Google-аккаунтов */ }
  showScreen("screen-app");
  listenChats();
}

/* ------------------------------------------------------------------ */
/* Список чатов                                                        */
/* ------------------------------------------------------------------ */

function listenChats() {
  if (chatsUnsub) chatsUnsub();

  const q = query(
    collection(db, "chats"),
    where("participantIds", "array-contains", currentUser.uid),
    limit(200)
  );

  chatsUnsub = onSnapshot(q, (snap) => {
    snap.docChanges().forEach((change) => {
      const data = change.doc.data();
      data.id = change.doc.id;
      chatsCache.set(change.doc.id, data);
    });
    // Удалённые чаты убираем
    snap.docs.forEach((d) => {
      if (!chatsCache.has(d.id)) chatsCache.delete(d.id);
    });
    renderChatList();
  });
}

function chatDisplayName(chat) {
  if (chat.titles && chat.titles[currentUser.uid]) return chat.titles[currentUser.uid];
  if (chat.title) return chat.title;
  return "Без названия";
}

function buildChatListItem(chat) {
  const item = document.createElement("div");
  item.className = "chat-item" + (chat.id === activeChatId ? " selected" : "");
  const unread = (chat.unreadCounts && chat.unreadCounts[currentUser.uid]) || 0;
  const letter = (chatDisplayName(chat)[0] || "?").toUpperCase();

  item.innerHTML = `
    <div class="chat-avatar">${esc(letter)}</div>
    <div class="chat-item-info">
      <div class="chat-item-top">
        <span class="chat-item-name">${esc(chatDisplayName(chat))}</span>
        <span class="chat-item-time">${esc(formatTime(chat.lastMessageTimestamp))}</span>
      </div>
      <div class="chat-item-bottom">
        <span class="chat-item-preview">${esc(chat.lastMessage || "Нет сообщений")}</span>
        ${unread > 0 ? `<span class="chat-badge">${unread}</span>` : ""}
      </div>
    </div>`;

  item.addEventListener("click", () => openChat(chat.id));
  return item;
}

function renderChatList() {
  const listEl = $("chat-list");
  const chats = Array.from(chatsCache.values())
    .filter((c) => c.id !== "yodo_official_channel")
    .sort((a, b) => (b.lastMessageTimestamp || 0) - (a.lastMessageTimestamp || 0));

  listEl.innerHTML = "";
  for (const chat of chats) {
    listEl.appendChild(buildChatListItem(chat));
  }
}

/* ------------------------------------------------------------------ */
/* Открытие чата / сообщения                                           */
/* ------------------------------------------------------------------ */

function openChat(chatId) {
  activeChatId = chatId;
  activeChatData = chatsCache.get(chatId) || null;
  $("chat-title").textContent = activeChatData ? chatDisplayName(activeChatData) : "Чат";
  $("chat-empty").classList.add("hidden");
  $("chat-active").classList.remove("hidden");
  document.getElementById("screen-app").classList.add("chat-open");
  $("messages").innerHTML = "";
  renderChatList();

  // Сброс непрочитанных: dot-notation, как markChatAsRead в Android
  updateDoc(doc(db, "chats", chatId), {
    ["unreadCounts." + currentUser.uid]: 0,
  }).catch(() => {});

  if (messagesUnsub) messagesUnsub();
  const q = query(
    collection(db, "chats", chatId, "messages"),
    orderBy("timestamp", "asc")
  );
  messagesUnsub = onSnapshot(q, (snap) => {
    const container = $("messages");
    container.innerHTML = "";
    snap.forEach((d) => {
      const msg = d.data();
      msg.id = d.id;
      container.appendChild(renderMessage(msg));
    });
    container.scrollTop = container.scrollHeight;
  });
}

$("btn-back").addEventListener("click", () => {
  activeChatId = null;
  activeChatData = null;
  if (messagesUnsub) { messagesUnsub(); messagesUnsub = null; }
  $("chat-active").classList.add("hidden");
  $("chat-empty").classList.remove("hidden");
  document.getElementById("screen-app").classList.remove("chat-open");
  renderChatList();
});

/* ------------------------------------------------------------------ */
/* Отрисовка сообщения                                                  */
/* ------------------------------------------------------------------ */

function statusIconFor(status) {
  if (status === "READ") return "✓✓";
  if (status === "DELIVERED") return "✓✓";
  return "✓";
}

function buildDeletedBubbleContent() {
  const span = document.createElement("span");
  span.className = "msg-deleted";
  span.textContent = "Сообщение удалено";
  return span;
}

function buildSenderLabel(uid) {
  const div = document.createElement("div");
  div.className = "msg-sender";
  div.textContent = senderNameFor(uid);
  return div;
}

function buildImageElement(imageBase64) {
  const img = document.createElement("img");
  img.src = "data:image/jpeg;base64," + imageBase64;
  img.style.maxWidth = "100%";
  img.style.borderRadius = "8px";
  img.style.marginBottom = "4px";
  img.style.cursor = "pointer";
  img.addEventListener("click", () => window.open(img.src, "_blank"));
  return img;
}

function buildReplyPreview(replyToSenderName, replyToText) {
  const div = document.createElement("div");
  div.style.borderLeft = "3px solid var(--accent)";
  div.style.padding = "2px 8px";
  div.style.margin = "2px 0 6px";
  div.style.color = "var(--text-dim)";
  div.style.fontSize = "13px";
  div.textContent = (replyToSenderName || "") + ": " + replyToText;
  return div;
}

function buildMessageMeta(msg) {
  const div = document.createElement("div");
  div.className = "msg-meta";

  const timeSpan = document.createElement("span");
  timeSpan.textContent = formatTime(msg.timestamp);
  div.appendChild(timeSpan);

  if (msg.senderId === currentUser.uid) {
    const statusSpan = document.createElement("span");
    statusSpan.textContent = statusIconFor(msg.status);
    div.appendChild(statusSpan);
  }

  return div;
}

function renderMessage(msg) {
  const wrap = document.createElement("div");
  wrap.className = "msg " + (msg.senderId === currentUser.uid ? "out" : "in");
  const bubble = document.createElement("div");
  bubble.className = "msg-bubble";

  if (msg.isDeleted) {
    bubble.appendChild(buildDeletedBubbleContent());
  } else {
    if (msg.senderId !== currentUser.uid && activeChatData && activeChatData.type !== "PRIVATE") {
      bubble.appendChild(buildSenderLabel(msg.senderId));
    }
    if (msg.imageBase64) {
      bubble.appendChild(buildImageElement(msg.imageBase64));
    }
    if (msg.replyToText) {
      bubble.appendChild(buildReplyPreview(msg.replyToSenderName, msg.replyToText));
    }
    if (msg.text) {
      bubble.appendChild(document.createTextNode(msg.text));
    }
    bubble.appendChild(buildMessageMeta(msg));
  }

  wrap.appendChild(bubble);
  return wrap;
}

// Имена отправителей для групп: подгружаем лениво
function senderNameFor(uid) {
  if (userNamesCache.has(uid)) return userNamesCache.get(uid);
  getDoc(doc(db, "users", uid))
    .then((snap) => {
      userNamesCache.set(uid, snap.exists() ? snap.data().displayName || "???" : "???");
      if (activeChatId) {
        // перерисовка одного сообщения не критична — при следующем снапшоте подтянется
      }
    })
    .catch(() => {});
  return "…";
}

/* ------------------------------------------------------------------ */
/* Отправка сообщения                                                   */
/* ------------------------------------------------------------------ */

$("form-send").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = $("message-input");
  const text = input.value.trim();
  if (!text || !activeChatId) return;
  input.value = "";

  try {
    const now = Date.now();
    const chat = activeChatData || chatsCache.get(activeChatId);

    // WriteBatch: сообщение + обновление чата (как sendRawMessage в Android)
    const batch = writeBatch(db);
    const msgRef = doc(collection(db, "chats", activeChatId, "messages"));
    batch.set(msgRef, {
      senderId: currentUser.uid,
      text: text,
      timestamp: now,
      status: "SENT",
      notified: false,
    });

    const chatRef = doc(db, "chats", activeChatId);
    const chatUpdate = {
      lastMessage: text,
      lastMessageTimestamp: now,
      lastMessageSenderId: currentUser.uid,
      lastMessageStatus: "SENT",
    };

    // Инкремент непрочитанных у всех участников, кроме себя (dot-notation)
    for (const uid of (chat.participantIds || [])) {
      if (uid !== currentUser.uid) {
        chatUpdate["unreadCounts." + uid] = increment(1);
      }
    }

    batch.update(chatRef, chatUpdate);
    await batch.commit();
  } catch (err) {
    console.error("Ошибка отправки:", err);
    input.value = text; // возвращаем текст в поле, чтобы не потерять
  }
});

/* ------------------------------------------------------------------ */
/* Новый чат: поиск пользователей                                       */
/* ------------------------------------------------------------------ */

$("btn-new-chat").addEventListener("click", () => {
  $("modal-new-chat").classList.remove("hidden");
  $("search-input").value = "";
  $("search-results").innerHTML = '<div class="search-empty">Начните вводить имя или @username</div>';
  $("search-input").focus();
});

$("btn-close-modal").addEventListener("click", () => {
  $("modal-new-chat").classList.add("hidden");
});

$("search-input").addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(runSearch, 300);
});

function buildUserResultItem(uid, u) {
  const item = document.createElement("div");
  item.className = "user-result";
  item.innerHTML = `
    <div class="chat-avatar">${esc((u.displayName || "?")[0].toUpperCase())}</div>
    <div>
      <div class="user-result-name">${esc(u.displayName || "")}</div>
      <div class="user-result-username">@${esc(u.username || "")}</div>
    </div>`;
  item.addEventListener("click", () => createPrivateChat(uid, u.displayName || u.username || "Чат"));
  return item;
}

async function searchUsersByField(field, term) {
  return getDocs(query(
    collection(db, "users"),
    where(field, ">=", term),
    where(field, "<=", term + "\uf8ff"),
    limit(10)
  ));
}

async function runSearch() {
  const term = $("search-input").value.trim().toLowerCase();
  const resultsEl = $("search-results");

  if (term.length < 2) {
    resultsEl.innerHTML = '<div class="search-empty">Минимум 2 символа</div>';
    return;
  }

  try {
    // Поиск по usernameLowercase (префикс) + displayNameLowercase (префикс)
    const byUsername = await searchUsersByField("usernameLowercase", term);
    const byName = await searchUsersByField("displayNameLowercase", term);

    const seen = new Set();
    const users = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }

    resultsEl.innerHTML = "";
    if (users.length === 0) {
      resultsEl.innerHTML = '<div class="search-empty">Никого не найдено</div>';
      return;
    }

    for (const d of users) {
      if (d.id === currentUser.uid) continue;
      resultsEl.appendChild(buildUserResultItem(d.id, d.data()));
    }
  } catch (err) {
    console.error("Поиск:", err);
    resultsEl.innerHTML = '<div class="search-empty">Ошибка поиска</div>';
  }
}

/* ------------------------------------------------------------------ */
/* Создание личного чата (createOrGetPrivateChat из Android)            */
/* ------------------------------------------------------------------ */

async function findExistingPrivateChat(otherUid) {
  const snap = await getDocs(query(
    collection(db, "chats"),
    where("participantIds", "array-contains", currentUser.uid),
    where("type", "==", "PRIVATE"),
    limit(200)
  ));
  for (const d of snap.docs) {
    const participants = d.data().participantIds || [];
    if (participants.includes(otherUid)) return d.id;
  }
  return null;
}

async function createPrivateChat(otherUid, otherName) {
  $("btn-close-modal").click();
  try {
    // Ищем существующий PRIVATE-чат с этим участником
    const existingId = await findExistingPrivateChat(otherUid);
    if (existingId) {
      openChat(existingId);
      return;
    }

    // Не нашли — создаём (поля один в один с Android)
    const now = Date.now();
    const myName = currentUser.displayName || "Я";
    const chatRef = doc(collection(db, "chats"));
    await setDoc(chatRef, {
      participantIds: [currentUser.uid, otherUid],
      type: "PRIVATE",
      titles: { [currentUser.uid]: otherName, [otherUid]: myName },
      lastMessage: "",
      lastMessageTimestamp: now,
      unreadCounts: { [currentUser.uid]: 0, [otherUid]: 0 },
      isOnline: false,
      createdBy: currentUser.uid,
    });
    openChat(chatRef.id);
  } catch (err) {
    console.error("Создание чата:", err);
    alert("Не удалось создать чат: " + err.message);
  }
}

// Модалка закрывается по клику на фон
$("modal-new-chat").addEventListener("click", (e) => {
  if (e.target === $("modal-new-chat")) $("btn-close-modal").click();
});
