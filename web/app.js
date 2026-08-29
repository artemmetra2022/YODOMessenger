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
  limitToLast,
  endBefore,
  onSnapshot,
  updateDoc,
  writeBatch,
  increment,
  serverTimestamp,
  deleteField,
  deleteDoc,
  arrayUnion,
  arrayRemove,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { initQrLogin } from "./qr-login.js";

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
/* Константы официального канала / поддержки (см. ChatRepository.kt)   */
/* ------------------------------------------------------------------ */

const OFFICIAL_CHANNEL_ID = "yodo_official_channel";
const ADMIN_EMAILS = ["artemmetra2022spb@gmail.com", "artemmelnik2@yandex.ru"];
const SUPPORT_CHAT_PREFIX = "support_";
const SUPPORT_TITLE = "Поддержка YodoMessenger";
const supportChatIdFor = (uid) => SUPPORT_CHAT_PREFIX + uid;

function isAdminEmail(email) {
  return !!email && ADMIN_EMAILS.includes(email.toLowerCase());
}

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

/* ------------------------------------------------------------------ */
/* Вход по QR-коду (см. web/qr-login.js)                               */
/* ------------------------------------------------------------------ */

initQrLogin({
  auth,
  db,
  firestoreFns: { doc, setDoc, onSnapshot, deleteDoc, serverTimestamp },
  signInWithEmailAndPassword,
  showScreen,
  $,
  onError: (message) => {
    $("login-error").textContent = message;
  },
});

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
/* RateLimiter — порт core/util/RateLimiter.kt (sliding window)         */
/* ------------------------------------------------------------------ */

class RateLimiter {
  constructor(maxEvents, windowMillis) {
    this.maxEvents = maxEvents;
    this.windowMillis = windowMillis;
    this.timestamps = [];
  }
  tryAcquire(now = Date.now()) {
    while (this.timestamps.length && now - this.timestamps[0] > this.windowMillis) {
      this.timestamps.shift();
    }
    if (this.timestamps.length >= this.maxEvents) return false;
    this.timestamps.push(now);
    return true;
  }
  retryAfterMillis(now = Date.now()) {
    while (this.timestamps.length && now - this.timestamps[0] > this.windowMillis) {
      this.timestamps.shift();
    }
    if (this.timestamps.length < this.maxEvents) return 0;
    const remaining = this.windowMillis - (now - this.timestamps[0]);
    return remaining > 0 ? remaining : 0;
  }
}

// Как в Android: не больше 5 отправок за 10 секунд.
const sendRateLimiter = new RateLimiter(5, 10_000);

/* ------------------------------------------------------------------ */
/* Уведомления браузера                                                 */
/* ------------------------------------------------------------------ */

let notificationsEnabled = false;

async function ensureNotificationPermission() {
  if (!("Notification" in window)) return false;
  if (Notification.permission === "granted") { notificationsEnabled = true; return true; }
  if (Notification.permission === "denied") return false;
  const perm = await Notification.requestPermission();
  notificationsEnabled = perm === "granted";
  return notificationsEnabled;
}

function notifyNewMessage(title, body, chatId) {
  if (!notificationsEnabled) return;
  if (document.visibilityState === "visible" && activeChatId === chatId) return;
  try {
    const n = new Notification(title, { body, tag: "yodo-" + chatId, icon: undefined });
    n.onclick = () => { window.focus(); n.close(); };
  } catch (e) { /* игнорируем */ }
}

/* ------------------------------------------------------------------ */
/* Состояние приложения                                                */
/* ------------------------------------------------------------------ */

let currentUser = null; // { uid, email, displayName, username }
let chatsUnsub = null; // отписка слушателя списка чатов
let messagesUnsub = null; // отписка слушателя сообщений
let blockedUnsub = null; // отписка слушателя блокировки
let presenceUnsub = null; // отписка слушателя presence собеседника (личные чаты)
let typingUnsub = null; // отписка слушателя "печатает..." в активном чате
let myPresenceInterval = null; // periodic heartbeat для собственного presence
let isBlocked = false;
let activeChatId = null;
let activeChatData = null;
let chatsCache = new Map(); // chatId -> данные чата
let userNamesCache = new Map(); // uid -> displayName (для групп)
let searchTimer = null;

// Групповой чат: выбранные участники в модалке создания
let groupSelectedMembers = new Map(); // uid -> { displayName, username }
let groupSearchTimer = null;

// Пагинация истории сообщений
const MESSAGES_PAGE_SIZE = 30;
let oldestLoadedMessageDoc = null;
let allMessagesLoaded = false;

// Вложение (фото) для следующей отправки
let pendingImageBase64 = null;

// Сообщение, на которое отвечаем (reply/цитирование)
let pendingReply = null; // { id, senderId, senderName, text }

// Emoji для быстрых реакций
const QUICK_REACTIONS = ["👍", "❤️", "😂", "😮", "😢", "🔥"];

// Индикатор "печатает..." — троттлинг собственных апдейтов
let lastTypingSentAt = 0;
let typingClearTimer = null;

// Онлайн-статус текущего пользователя, обновляемый раз в 25с (presence)
const PRESENCE_STALE_THRESHOLD_MILLIS = 60_000;

// Официальный канал и чат поддержки — отдельные листенеры, т.к. не участвуют
// в общем participantIds-запросе (официальный канал виден всем, поддержка
// детерминирована по uid и доступна ещё и админам).
let officialChannelUnsub = null;
let officialChannelData = null;
let supportChatUnsub = null;
let supportChatData = null;
let isAdmin = false;
// Для админов: список всех бесед поддержки (простая админ-панель).
let supportConversationsUnsub = null;
let supportConversationsCache = new Map();
let viewingSupportInbox = false;

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
  if (presenceUnsub) { presenceUnsub(); presenceUnsub = null; }
  if (typingUnsub) { typingUnsub(); typingUnsub = null; }
  if (myPresenceInterval) { clearInterval(myPresenceInterval); myPresenceInterval = null; }
  if (officialChannelUnsub) { officialChannelUnsub(); officialChannelUnsub = null; }
  if (supportChatUnsub) { supportChatUnsub(); supportChatUnsub = null; }
  if (supportConversationsUnsub) { supportConversationsUnsub(); supportConversationsUnsub = null; }
  officialChannelData = null;
  supportChatData = null;
  supportConversationsCache.clear();
  viewingSupportInbox = false;
  isAdmin = false;
  activeChatId = null;
  activeChatData = null;
  isBlocked = false;
  chatsCache.clear();
  oldestLoadedMessageDoc = null;
  allMessagesLoaded = false;
  pendingImageBase64 = null;
}

/* ------------------------------------------------------------------ */
/* Presence: онлайн-статус текущего пользователя                       */
/* ------------------------------------------------------------------ */

async function pingMyPresence(online) {
  if (!currentUser) return;
  try {
    await setDoc(doc(db, "users", currentUser.uid), {
      isOnline: online,
      lastSeen: Date.now(),
    }, { merge: true });
  } catch (e) { /* best-effort, как в Android */ }
}

function startPresenceHeartbeat() {
  pingMyPresence(true);
  myPresenceInterval = setInterval(() => pingMyPresence(true), 25_000);
  window.addEventListener("beforeunload", () => pingMyPresence(false));
  document.addEventListener("visibilitychange", () => {
    pingMyPresence(document.visibilityState === "visible");
  });
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

  isAdmin = isAdminEmail(currentUser.email);

  showScreen("screen-app");
  listenChats();
  listenOfficialChannel();
  startPresenceHeartbeat();
  ensureNotificationPermission();

  if (isAdmin) {
    // Админ: гарантируем существование официального канала, показываем кнопку "Поддержка".
    ensureOfficialChannelExists();
    listenSupportConversations();
    $("btn-support-inbox").classList.remove("hidden");
  } else {
    // Обычный пользователь: гарантируем существование его беседы поддержки.
    getOrCreateSupportChat();
  }
  listenSupportChat();
}

/* ------------------------------------------------------------------ */
/* Официальный канал (yodo_official_channel)                           */
/* ------------------------------------------------------------------ */

async function ensureOfficialChannelExists() {
  try {
    const ref = doc(db, "chats", OFFICIAL_CHANNEL_ID);
    const snap = await getDoc(ref);
    if (snap.exists()) return;
    const now = Date.now();
    await setDoc(ref, {
      participantIds: [currentUser.uid],
      type: "CHANNEL",
      title: "YodoMessenger",
      titleLowercase: "yodomessenger",
      createdAt: now,
      isVerified: true,
      lastMessage: "",
      lastMessageTimestamp: now,
      lastMessageSenderId: currentUser.uid,
      lastMessageStatus: "SENT",
      unreadCounts: { [currentUser.uid]: 0 },
      isOnline: false,
      createdBy: currentUser.uid,
    });
  } catch (e) { /* best-effort, как в Android */ }
}

function listenOfficialChannel() {
  if (officialChannelUnsub) officialChannelUnsub();
  officialChannelUnsub = onSnapshot(
    doc(db, "chats", OFFICIAL_CHANNEL_ID),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        data.id = snap.id;
        officialChannelData = data;
      } else {
        officialChannelData = null;
      }
      renderChatList();
      // Если сейчас открыт официальный канал — обновим шапку/список сообщений идёт своим слушателем.
      if (activeChatId === OFFICIAL_CHANNEL_ID) {
        activeChatData = officialChannelData;
        updateChatHeader();
      }
    },
    () => { officialChannelData = null; renderChatList(); }
  );
}

/* ------------------------------------------------------------------ */
/* Чат поддержки (support_<uid>)                                       */
/* ------------------------------------------------------------------ */

async function getOrCreateSupportChat() {
  const chatId = supportChatIdFor(currentUser.uid);
  try {
    const ref = doc(db, "chats", chatId);
    const snap = await getDoc(ref);
    if (snap.exists()) return chatId;

    const now = Date.now();
    const welcomeText =
      "Здравствуйте, это аккаунт поддержки. Задавайте вопросы именно в него — " +
      "мы отвечаем в этом же чате. Опишите проблему подробно и, если есть, приложите " +
      "скриншот — так мы разберёмся быстрее.";

    await setDoc(ref, {
      participantIds: [currentUser.uid],
      type: "SUPPORT",
      title: SUPPORT_TITLE,
      isVerified: true,
      supportUserId: currentUser.uid,
      supportUserName: currentUser.displayName || "Пользователь",
      supportUserEmail: currentUser.email || "",
      lastMessage: welcomeText,
      lastMessageTimestamp: now,
      lastMessageSenderId: "support_system",
      lastMessageStatus: "SENT",
      unreadCounts: { [currentUser.uid]: 1 },
      isOnline: false,
      createdBy: currentUser.uid,
      createdAt: now,
    });

    // Приветственное сообщение с фиксированным id — разрешено правилами ровно один раз.
    try {
      await setDoc(doc(db, "chats", chatId, "messages", "support_welcome"), {
        senderId: "support_system",
        text: welcomeText,
        timestamp: now,
        status: "SENT",
        notified: true,
      });
    } catch (e) { /* не критично, чат уже создан */ }

    return chatId;
  } catch (e) {
    console.error("Не удалось открыть чат поддержки:", e);
    return chatId;
  }
}

function listenSupportChat() {
  if (supportChatUnsub) supportChatUnsub();
  const chatId = isAdmin ? null : supportChatIdFor(currentUser.uid);
  if (!chatId) return; // админы видят список бесед поддержки отдельно (listenSupportConversations)
  supportChatUnsub = onSnapshot(
    doc(db, "chats", chatId),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        data.id = snap.id;
        supportChatData = data;
      } else {
        supportChatData = null;
      }
      renderChatList();
      if (activeChatId === chatId) {
        activeChatData = supportChatData;
        updateChatHeader();
      }
    },
    () => { supportChatData = null; renderChatList(); }
  );
}

// Для админов: список всех бесед поддержки (собираем через chats where type == SUPPORT).
function listenSupportConversations() {
  if (supportConversationsUnsub) supportConversationsUnsub();
  const q = query(collection(db, "chats"), where("type", "==", "SUPPORT"));
  supportConversationsUnsub = onSnapshot(q, (snap) => {
    supportConversationsCache.clear();
    snap.forEach((d) => {
      const data = d.data();
      data.id = d.id;
      supportConversationsCache.set(d.id, data);
    });
    if (viewingSupportInbox) renderSupportInbox();
  });
}

// Экран "Входящие поддержки" (для админов) — переиспользуем список чатов,
// открытие элемента ведёт в конкретный support_<uid> как обычный чат.
$("btn-support-inbox").addEventListener("click", () => {
  viewingSupportInbox = true;
  activeChatId = null;
  activeChatData = null;
  $("chat-active").classList.add("hidden");
  $("chat-empty").classList.remove("hidden");
  document.getElementById("screen-app").classList.remove("chat-open");
  renderSupportInbox();
});

function renderSupportInbox() {
  const listEl = $("chat-list");
  listEl.innerHTML = "";

  const header = document.createElement("div");
  header.className = "search-empty";
  header.style.padding = "10px 16px";
  header.textContent = "Обращения в поддержку (" + supportConversationsCache.size + ")";
  listEl.appendChild(header);

  const conversations = Array.from(supportConversationsCache.values())
    .sort((a, b) => {
      // Ожидающие ответа (последнее сообщение от пользователя) — выше.
      const aWaiting = a.lastMessageSenderId === a.supportUserId ? 1 : 0;
      const bWaiting = b.lastMessageSenderId === b.supportUserId ? 1 : 0;
      if (aWaiting !== bWaiting) return bWaiting - aWaiting;
      return (b.lastMessageTimestamp || 0) - (a.lastMessageTimestamp || 0);
    });

  for (const conv of conversations) {
    const item = document.createElement("div");
    const awaitingReply = conv.lastMessageSenderId === conv.supportUserId &&
      (conv.lastMessage || "").trim() !== "";
    item.className = "chat-item" + (conv.id === activeChatId ? " selected" : "");
    const letter = (conv.supportUserName || "?")[0].toUpperCase();
    item.innerHTML = `
      <div class="chat-avatar">${esc(letter)}</div>
      <div class="chat-item-info">
        <div class="chat-item-top">
          <span class="chat-item-name">${esc(conv.supportUserName || "Пользователь")}</span>
          <span class="chat-item-time">${esc(formatTime(conv.lastMessageTimestamp))}</span>
        </div>
        <div class="chat-item-bottom">
          <span class="chat-item-preview">${esc(conv.lastMessage || "")}</span>
          ${awaitingReply ? '<span class="chat-badge">!</span>' : ""}
        </div>
      </div>`;
    item.addEventListener("click", () => openChat(conv.id));
    listEl.appendChild(item);
  }

  if (conversations.length === 0) {
    const empty = document.createElement("div");
    empty.className = "search-empty";
    empty.style.padding = "10px 16px";
    empty.textContent = "Обращений пока нет";
    listEl.appendChild(empty);
  }
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
  const verified = chat.isVerified ? ' <span class="verified-badge" title="Официальный">✓</span>' : "";

  item.innerHTML = `
    <div class="chat-avatar">${esc(letter)}</div>
    <div class="chat-item-info">
      <div class="chat-item-top">
        <span class="chat-item-name">${esc(chatDisplayName(chat))}${verified}</span>
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
    .filter((c) => c.id !== OFFICIAL_CHANNEL_ID && c.type !== "SUPPORT")
    .sort((a, b) => (b.lastMessageTimestamp || 0) - (a.lastMessageTimestamp || 0));

  listEl.innerHTML = "";

  // Официальный канал — всегда сверху, если существует.
  if (officialChannelData) {
    listEl.appendChild(buildChatListItem(officialChannelData));
  }

  // Собственная беседа поддержки (для обычных пользователей).
  if (!isAdmin && supportChatData) {
    listEl.appendChild(buildChatListItem(supportChatData));
  }

  for (const chat of chats) {
    listEl.appendChild(buildChatListItem(chat));
  }
}

/* ------------------------------------------------------------------ */
/* Открытие чата / сообщения                                           */
/* ------------------------------------------------------------------ */

function findChatData(chatId) {
  if (chatId === OFFICIAL_CHANNEL_ID) return officialChannelData;
  if (chatId.startsWith(SUPPORT_CHAT_PREFIX)) {
    if (!isAdmin) return supportChatData;
    return supportConversationsCache.get(chatId) || null;
  }
  return chatsCache.get(chatId) || null;
}

function canSendInActiveChat() {
  if (!activeChatId) return false;
  if (activeChatId === OFFICIAL_CHANNEL_ID) return isAdmin;
  if (activeChatData && activeChatData.type === "CHANNEL") {
    return isChannelManagerOf(activeChatData);
  }
  return true;
}

function updateChatHeader() {
  if (!activeChatId) return;
  const verified = activeChatData && activeChatData.isVerified
    ? ' <span class="verified-badge" title="Официальный">✓</span>' : "";
  $("chat-title").innerHTML = (activeChatData ? esc(chatDisplayName(activeChatData)) : "Чат") + verified;
  $("chat-header-avatar").textContent = activeChatData ? (chatDisplayName(activeChatData)[0] || "?").toUpperCase() : "?";

  // Форма отправки скрыта для не-владельцев/админов канала (read-only лента).
  const canSend = canSendInActiveChat();
  $("form-send").classList.toggle("hidden", !canSend);
  $("btn-attach").classList.toggle("hidden", !canSend);
  if (!canSend) {
    if (activeChatId === OFFICIAL_CHANNEL_ID) {
      $("chat-subtitle").textContent = "Только для чтения — официальные объявления";
    } else if (activeChatData && activeChatData.type === "CHANNEL") {
      const count = (activeChatData.participantIds || []).length;
      $("chat-subtitle").textContent = count + " подписчиков · только владелец/админы публикуют посты";
    } else {
      $("chat-subtitle").textContent = "Только для чтения";
    }
    $("chat-subtitle").classList.remove("online");
  } else if (activeChatData && activeChatData.type === "CHANNEL") {
    const count = (activeChatData.participantIds || []).length;
    $("chat-subtitle").textContent = count + " подписчиков";
    $("chat-subtitle").classList.remove("online");
  }

  updateChannelSubscribeButton();
}

// НОВОЕ: кнопка подписки/отписки в шапке для обычных (не служебных) каналов,
// когда пользователь не владелец/админ — как isSubscribed/isChannelOwner в Android.
function updateChannelSubscribeButton() {
  let btn = $("btn-channel-subscribe");
  const isRegularChannel = activeChatData && activeChatData.type === "CHANNEL" && activeChatId !== OFFICIAL_CHANNEL_ID;
  if (!isRegularChannel) {
    if (btn) btn.classList.add("hidden");
    return;
  }
  const isOwner = activeChatData.createdBy === currentUser.uid;
  const isSubscribed = (activeChatData.participantIds || []).includes(currentUser.uid);
  const hasPending = false; // заявка проверяется лениво, см. requestToJoinChannel

  if (!btn) {
    btn = document.createElement("button");
    btn.id = "btn-channel-subscribe";
    btn.className = "btn-secondary";
    btn.style.marginLeft = "auto";
    btn.style.flexShrink = "0";
    $("chat-header-info").after(btn);
  }
  btn.classList.remove("hidden");
  if (isOwner) {
    btn.classList.add("hidden"); // владелец не отписывается от своего канала
  } else if (isSubscribed) {
    btn.textContent = "Отписаться";
    btn.onclick = () => unsubscribeFromChannel(activeChatId);
  } else {
    const mode = channelAccessModeOf(activeChatData);
    btn.textContent = mode === "MODERATED" ? "Подать заявку" : "Подписаться";
    btn.onclick = () => joinOrOpenChannel(activeChatData);
  }
}

function openChat(chatId) {
  activeChatId = chatId;
  activeChatData = findChatData(chatId);
  viewingSupportInbox = false;
  $("chat-subtitle").textContent = "";
  $("chat-subtitle").classList.remove("online");
  updateChatHeader();
  $("chat-empty").classList.add("hidden");
  $("chat-active").classList.remove("hidden");
  document.getElementById("screen-app").classList.add("chat-open");
  $("messages").innerHTML = "";
  $("btn-load-more").classList.add("hidden");
  $("typing-indicator").classList.add("hidden");
  cancelPendingImage();
  cancelReply();
  oldestLoadedMessageDoc = null;
  allMessagesLoaded = false;
  renderChatList();

  // Сброс непрочитанных: dot-notation, как markChatAsRead в Android.
  // Для официального канала — только у себя, доступно всем читателям.
  updateDoc(doc(db, "chats", chatId), {
    ["unreadCounts." + currentUser.uid]: 0,
  }).catch(() => {});

  if (presenceUnsub) { presenceUnsub(); presenceUnsub = null; }
  if (typingUnsub) { typingUnsub(); typingUnsub = null; }

  // Presence собеседника — только для личных чатов
  if (activeChatData && activeChatData.type === "PRIVATE") {
    const otherUid = (activeChatData.participantIds || []).find((u) => u !== currentUser.uid);
    if (otherUid) {
      presenceUnsub = onSnapshot(doc(db, "users", otherUid), (snap) => {
        if (!snap.exists()) return;
        const data = snap.data();
        const isStale = Date.now() - (data.lastSeen || 0) > PRESENCE_STALE_THRESHOLD_MILLIS;
        const online = !!data.isOnline && !isStale;
        const subEl = $("chat-subtitle");
        if (online) {
          subEl.textContent = "в сети";
          subEl.classList.add("online");
        } else {
          subEl.classList.remove("online");
          subEl.textContent = data.lastSeen ? "был(а) " + formatTime(data.lastSeen) : "";
        }
      });
    }
  }

  // "Печатает..." — слушаем typing-подколлекцию чата (не для официального канала).
  if (chatId !== OFFICIAL_CHANNEL_ID) {
    typingUnsub = onSnapshot(doc(db, "chats", chatId, "typing", "state"), (snap) => {
      const indicator = $("typing-indicator");
      if (!snap.exists()) { indicator.classList.add("hidden"); return; }
      const data = snap.data() || {};
      const othersTyping = Object.entries(data)
        .filter(([uid, ts]) => uid !== currentUser.uid && ts && Date.now() - ts < 6000);
      indicator.classList.toggle("hidden", othersTyping.length === 0);
    }, () => { /* нет прав / нет коллекции — молча игнорируем */ });
  }

  listenActiveMessages();
}

function listenActiveMessages() {
  if (messagesUnsub) messagesUnsub();
  const chatId = activeChatId;

  const q = query(
    collection(db, "chats", chatId, "messages"),
    orderBy("timestamp", "asc"),
    limitToLast(MESSAGES_PAGE_SIZE)
  );
  messagesUnsub = onSnapshot(q, (snap) => {
    const container = $("messages");
    const wasNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 80;

    container.innerHTML = "";
    $("btn-load-more").classList.toggle("hidden", allMessagesLoaded || snap.size < MESSAGES_PAGE_SIZE);

    if (snap.docs.length > 0) oldestLoadedMessageDoc = snap.docs[0];

    snap.forEach((d) => {
      const msg = d.data();
      msg.id = d.id;
      container.appendChild(renderMessage(msg));

      // Помечаем чужие сообщения как READ, если ещё не прочитаны (как в Android).
      // Не пытаемся это делать в официальном канале/чужой беседе поддержки (админ,
      // просматривающий чужой support-чат) — там правила Firestore это не разрешают
      // (обновлять status может только сам получатель личного/группового чата).
      const readableHere = chatId !== OFFICIAL_CHANNEL_ID &&
        !(isAdmin && chatId.startsWith(SUPPORT_CHAT_PREFIX) && chatId !== supportChatIdFor(currentUser.uid));
      if (readableHere && msg.senderId !== currentUser.uid && msg.status && msg.status !== "READ") {
        updateDoc(doc(db, "chats", chatId, "messages", d.id), { status: "READ" }).catch(() => {});
      }
    });

    if (wasNearBottom) container.scrollTop = container.scrollHeight;

    // Уведомление о новом входящем сообщении
    snap.docChanges().forEach((change) => {
      if (change.type !== "added") return;
      const msg = change.doc.data();
      if (msg.senderId && msg.senderId !== currentUser.uid && Date.now() - (msg.timestamp || 0) < 5000) {
        const name = activeChatData ? chatDisplayName(activeChatData) : "YODO";
        notifyNewMessage(name, msg.text || (msg.imageBase64 ? "📷 Фото" : "Новое сообщение"), chatId);
      }
    });
  });
}

$("btn-load-more").addEventListener("click", async () => {
  if (!activeChatId || !oldestLoadedMessageDoc || allMessagesLoaded) return;
  const container = $("messages");
  const prevHeight = container.scrollHeight;

  const q = query(
    collection(db, "chats", activeChatId, "messages"),
    orderBy("timestamp", "asc"),
    endBefore(oldestLoadedMessageDoc),
    limitToLast(MESSAGES_PAGE_SIZE)
  );
  const snap = await getDocs(q);
  if (snap.empty) { allMessagesLoaded = true; $("btn-load-more").classList.add("hidden"); return; }

  const frag = document.createDocumentFragment();
  snap.forEach((d) => {
    const msg = d.data();
    msg.id = d.id;
    frag.appendChild(renderMessage(msg));
  });
  container.prepend(frag);
  oldestLoadedMessageDoc = snap.docs[0];
  if (snap.size < MESSAGES_PAGE_SIZE) { allMessagesLoaded = true; $("btn-load-more").classList.add("hidden"); }

  // Сохраняем позицию скролла относительно новой высоты
  container.scrollTop = container.scrollHeight - prevHeight;
});

$("btn-back").addEventListener("click", () => {
  clearTypingState();
  activeChatId = null;
  activeChatData = null;
  if (messagesUnsub) { messagesUnsub(); messagesUnsub = null; }
  if (presenceUnsub) { presenceUnsub(); presenceUnsub = null; }
  if (typingUnsub) { typingUnsub(); typingUnsub = null; }
  cancelPendingImage();
  cancelReply();
  $("btn-load-more").classList.add("hidden");
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

function statusClassFor(status) {
  return status === "READ" ? "msg-status read" : "msg-status";
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

/* ------------------------------------------------------------------ */
/* Reply / цитирование                                                  */
/* ------------------------------------------------------------------ */

function cancelReply() {
  pendingReply = null;
  $("reply-preview-bar").classList.add("hidden");
}

function startReply(msg) {
  if (!msg || msg.isDeleted) return;
  pendingReply = {
    id: msg.id,
    senderId: msg.senderId,
    senderName: msg.senderId === currentUser.uid ? "Вы" : senderNameForReply(msg.senderId),
    text: msg.text || (msg.imageBase64 ? "📷 Фото" : ""),
  };
  $("reply-preview-name").textContent = pendingReply.senderName;
  $("reply-preview-text").textContent = pendingReply.text;
  $("reply-preview-bar").classList.remove("hidden");
  $("message-input").focus();
}

function senderNameForReply(uid) {
  if (uid === "support_system") return "Поддержка YODO";
  if (userNamesCache.has(uid)) return userNamesCache.get(uid);
  return "…";
}

$("btn-cancel-reply").addEventListener("click", cancelReply);

/* ------------------------------------------------------------------ */
/* Реакции                                                              */
/* ------------------------------------------------------------------ */

function closeAnyEmojiPicker() {
  document.querySelectorAll(".emoji-picker").forEach((el) => el.remove());
}

function toggleReaction(chatId, msgId, emoji, currentReactions) {
  const uid = currentUser.uid;
  const list = (currentReactions && currentReactions[emoji]) || [];
  const mine = list.includes(uid);
  const field = "reactions." + emoji;
  const msgRef = doc(db, "chats", chatId, "messages", msgId);
  if (mine) {
    const next = list.filter((u) => u !== uid);
    if (next.length === 0) {
      updateDoc(msgRef, { [field]: deleteField() }).catch(() => {});
    } else {
      updateDoc(msgRef, { [field]: next }).catch(() => {});
    }
  } else {
    updateDoc(msgRef, { [field]: [...list, uid] }).catch(() => {});
  }
}

function openEmojiPicker(anchorBtn, chatId, msg) {
  closeAnyEmojiPicker();
  const picker = document.createElement("div");
  picker.className = "emoji-picker";
  for (const emoji of QUICK_REACTIONS) {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = emoji;
    b.addEventListener("click", (e) => {
      e.stopPropagation();
      toggleReaction(chatId, msg.id, emoji, msg.reactions);
      closeAnyEmojiPicker();
    });
    picker.appendChild(b);
  }
  anchorBtn.closest(".msg").appendChild(picker);
  setTimeout(() => {
    document.addEventListener("click", closeAnyEmojiPicker, { once: true });
  }, 0);
}

function buildReactionsBar(chatId, msg) {
  const reactions = msg.reactions;
  if (!reactions || Object.keys(reactions).length === 0) return null;
  const bar = document.createElement("div");
  bar.className = "msg-reactions";
  for (const [emoji, uids] of Object.entries(reactions)) {
    if (!uids || uids.length === 0) continue;
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "reaction-chip" + (uids.includes(currentUser.uid) ? " mine" : "");
    chip.innerHTML = `${esc(emoji)} <span>${uids.length}</span>`;
    chip.addEventListener("click", () => toggleReaction(chatId, msg.id, emoji, reactions));
    bar.appendChild(chip);
  }
  return bar.childElementCount ? bar : null;
}

/* ------------------------------------------------------------------ */
/* Пересылка сообщений                                                  */
/* ------------------------------------------------------------------ */

let forwardingMessage = null; // { text, imageBase64, senderId }

function forwardableChatsList() {
  const list = [];
  if (!isAdmin && supportChatData) list.push(supportChatData);
  for (const chat of chatsCache.values()) {
    if (chat.type === "GROUP" || chat.type === "PRIVATE" || chat.type === "CHANNEL") list.push(chat);
  }
  return list;
}

function openForwardModal(msg) {
  forwardingMessage = {
    text: msg.text || "",
    imageBase64: msg.imageBase64 || null,
    senderId: msg.senderId,
  };
  $("forward-preview").textContent = msg.text || (msg.imageBase64 ? "📷 Фото" : "");
  $("forward-search-input").value = "";
  renderForwardResults(forwardableChatsList());
  $("modal-forward").classList.remove("hidden");
  $("forward-search-input").focus();
}

function renderForwardResults(chats) {
  const el = $("forward-results");
  el.innerHTML = "";
  if (chats.length === 0) {
    el.innerHTML = '<div class="search-empty">Нет доступных чатов</div>';
    return;
  }
  for (const chat of chats) {
    const item = document.createElement("div");
    item.className = "user-result";
    const letter = (chatDisplayName(chat)[0] || "?").toUpperCase();
    item.innerHTML = `
      <div class="chat-avatar">${esc(letter)}</div>
      <div>
        <div class="user-result-name">${esc(chatDisplayName(chat))}</div>
      </div>`;
    item.addEventListener("click", () => sendForward(chat.id));
    el.appendChild(item);
  }
}

$("forward-search-input").addEventListener("input", () => {
  const term = $("forward-search-input").value.trim().toLowerCase();
  const chats = forwardableChatsList().filter((c) => chatDisplayName(c).toLowerCase().includes(term));
  renderForwardResults(chats);
});

$("btn-close-forward-modal").addEventListener("click", () => {
  $("modal-forward").classList.add("hidden");
  forwardingMessage = null;
});
$("modal-forward").addEventListener("click", (e) => {
  if (e.target === $("modal-forward")) $("btn-close-forward-modal").click();
});

async function sendForward(targetChatId) {
  if (!forwardingMessage) return;
  const fwd = forwardingMessage;
  $("modal-forward").classList.add("hidden");
  forwardingMessage = null;

  try {
    const now = Date.now();
    const chat = findChatData(targetChatId) || chatsCache.get(targetChatId);

    const batch = writeBatch(db);
    const msgRef = doc(collection(db, "chats", targetChatId, "messages"));
    const msgData = {
      senderId: currentUser.uid,
      timestamp: now,
      status: "SENT",
      notified: false,
      forwardedFromId: fwd.senderId,
    };
    if (fwd.text) msgData.text = fwd.text;
    if (fwd.imageBase64) msgData.imageBase64 = fwd.imageBase64;
    batch.set(msgRef, msgData);

    const chatRef = doc(db, "chats", targetChatId);
    const chatUpdate = {
      lastMessage: (fwd.text || "📷 Фото"),
      lastMessageTimestamp: now,
      lastMessageSenderId: currentUser.uid,
      lastMessageStatus: "SENT",
    };
    const recipients = new Set(chat && chat.participantIds ? chat.participantIds : []);
    recipients.delete(currentUser.uid);
    for (const uid of recipients) {
      chatUpdate["unreadCounts." + uid] = increment(1);
    }
    batch.update(chatRef, chatUpdate);
    await batch.commit();
  } catch (err) {
    console.error("Ошибка пересылки:", err);
    alert("Не удалось переслать сообщение");
  }
}

function buildMessageMeta(msg) {
  const div = document.createElement("div");
  div.className = "msg-meta";

  const timeSpan = document.createElement("span");
  timeSpan.textContent = formatTime(msg.timestamp);
  div.appendChild(timeSpan);

  if (msg.senderId === currentUser.uid) {
    const statusSpan = document.createElement("span");
    statusSpan.className = statusClassFor(msg.status);
    statusSpan.textContent = statusIconFor(msg.status);
    div.appendChild(statusSpan);
  }

  return div;
}

function buildForwardedLabel() {
  const div = document.createElement("div");
  div.className = "msg-sender";
  div.style.opacity = "0.7";
  div.textContent = "Переслано";
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
    const isGroupLike = activeChatData && (activeChatData.type === "GROUP" || activeChatData.type === "CHANNEL");
    const isSupportLike = activeChatData && activeChatData.type === "SUPPORT";
    if (msg.forwardedFromId) {
      bubble.appendChild(buildForwardedLabel());
    } else if (msg.senderId !== currentUser.uid && (isGroupLike || isSupportLike)) {
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
    const reactionsBar = buildReactionsBar(activeChatId, msg);
    if (reactionsBar) bubble.appendChild(reactionsBar);
    bubble.appendChild(buildMessageMeta(msg));
  }

  wrap.appendChild(bubble);

  if (!msg.isDeleted) {
    const actions = document.createElement("div");
    actions.className = "msg-actions";

    const reactBtn = document.createElement("button");
    reactBtn.type = "button";
    reactBtn.className = "msg-action-btn";
    reactBtn.title = "Реакция";
    reactBtn.textContent = "🙂";
    reactBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      openEmojiPicker(reactBtn, activeChatId, msg);
    });
    actions.appendChild(reactBtn);

    const replyBtn = document.createElement("button");
    replyBtn.type = "button";
    replyBtn.className = "msg-action-btn";
    replyBtn.title = "Ответить";
    replyBtn.textContent = "↩️";
    replyBtn.addEventListener("click", () => startReply(msg));
    actions.appendChild(replyBtn);

    const forwardBtn = document.createElement("button");
    forwardBtn.type = "button";
    forwardBtn.className = "msg-action-btn";
    forwardBtn.title = "Переслать";
    forwardBtn.textContent = "➡️";
    forwardBtn.addEventListener("click", () => openForwardModal(msg));
    actions.appendChild(forwardBtn);

    wrap.appendChild(actions);
  }

  return wrap;
}

// Имена отправителей для групп/поддержки: подгружаем лениво
function senderNameFor(uid) {
  if (uid === "support_system") return "Поддержка YODO";
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
  const imageBase64 = pendingImageBase64;
  if ((!text && !imageBase64) || !activeChatId) return;
  if (!canSendInActiveChat()) return; // read-only официальный канал для не-админов

  // Rate limiting: не больше 5 отправок за 10 секунд (порт RateLimiter.kt)
  if (!sendRateLimiter.tryAcquire()) {
    const waitSec = Math.ceil(sendRateLimiter.retryAfterMillis() / 1000);
    alert("Слишком много сообщений подряд. Подождите " + waitSec + " сек.");
    return;
  }

  const replySnapshot = pendingReply;
  input.value = "";
  cancelPendingImage();
  cancelReply();
  clearTypingState();

  try {
    const now = Date.now();
    const chat = activeChatData || findChatData(activeChatId);

    // WriteBatch: сообщение + обновление чата (как sendRawMessage в Android)
    const batch = writeBatch(db);
    const msgRef = doc(collection(db, "chats", activeChatId, "messages"));
    const msgData = {
      senderId: currentUser.uid,
      timestamp: now,
      status: "SENT",
      notified: false,
    };
    if (text) msgData.text = text;
    if (imageBase64) msgData.imageBase64 = imageBase64;
    if (replySnapshot) {
      msgData.replyToId = replySnapshot.id;
      msgData.replyToSenderName = replySnapshot.senderName;
      msgData.replyToText = replySnapshot.text;
    }
    batch.set(msgRef, msgData);

    const chatRef = doc(db, "chats", activeChatId);
    const chatUpdate = {
      lastMessage: text || "📷 Фото",
      lastMessageTimestamp: now,
      lastMessageSenderId: currentUser.uid,
      lastMessageStatus: "SENT",
    };

    // Получатели непрочитанных: обычно все participantIds, кроме себя. Для беседы
    // поддержки, когда отвечает админ, реальный получатель — владелец беседы
    // (supportUserId), который в participantIds чата поддержки уже присутствует —
    // но на случай расхождения схемы подстрахуемся им явно.
    const recipients = new Set(chat && chat.participantIds ? chat.participantIds : []);
    if (chat && chat.supportUserId) recipients.add(chat.supportUserId);
    recipients.delete(currentUser.uid);
    for (const uid of recipients) {
      chatUpdate["unreadCounts." + uid] = increment(1);
    }

    batch.update(chatRef, chatUpdate);
    await batch.commit();
  } catch (err) {
    console.error("Ошибка отправки:", err);
    input.value = text; // возвращаем текст в поле, чтобы не потерять
    if (replySnapshot) startReply({ id: replySnapshot.id, senderId: replySnapshot.senderId, text: replySnapshot.text, isDeleted: false });
  }
});

/* ------------------------------------------------------------------ */
/* Индикатор "печатает..." (собственный, отправляем в chats/{id}/typing/state) */
/* ------------------------------------------------------------------ */

function clearTypingState() {
  if (typingClearTimer) { clearTimeout(typingClearTimer); typingClearTimer = null; }
  if (!activeChatId || !currentUser) return;
  updateDoc(doc(db, "chats", activeChatId, "typing", "state"), {
    [currentUser.uid]: deleteField(),
  }).catch(() => {});
}

$("message-input").addEventListener("input", () => {
  if (!activeChatId || !currentUser) return;
  const now = Date.now();
  if (now - lastTypingSentAt < 2000) return; // троттлинг: не чаще раза в 2с
  lastTypingSentAt = now;
  setDoc(doc(db, "chats", activeChatId, "typing", "state"), {
    [currentUser.uid]: now,
  }, { merge: true }).catch(() => {});

  if (typingClearTimer) clearTimeout(typingClearTimer);
  typingClearTimer = setTimeout(clearTypingState, 4000);
});

/* ------------------------------------------------------------------ */
/* Вложение изображения                                                 */
/* ------------------------------------------------------------------ */

function cancelPendingImage() {
  pendingImageBase64 = null;
  $("image-preview").classList.add("hidden");
  $("image-preview-img").src = "";
  $("file-input").value = "";
}

$("btn-attach").addEventListener("click", () => $("file-input").click());
$("btn-cancel-image").addEventListener("click", cancelPendingImage);

$("file-input").addEventListener("change", async () => {
  const file = $("file-input").files[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    alert("Выберите файл изображения");
    return;
  }
  try {
    const base64 = await downscaleImageToBase64(file, 1280, 0.8);
    pendingImageBase64 = base64;
    $("image-preview-img").src = "data:image/jpeg;base64," + base64;
    $("image-preview").classList.remove("hidden");
  } catch (err) {
    console.error("Ошибка обработки изображения:", err);
    alert("Не удалось обработать изображение");
  }
});

function downscaleImageToBase64(file, maxDim, quality) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error("Не удалось загрузить изображение"));
      img.onload = () => {
        let { width, height } = img;
        if (width > maxDim || height > maxDim) {
          const scale = maxDim / Math.max(width, height);
          width = Math.round(width * scale);
          height = Math.round(height * scale);
        }
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, width, height);
        const dataUrl = canvas.toDataURL("image/jpeg", quality);
        resolve(dataUrl.split(",")[1]);
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

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
    const channels = await searchChannels(term);

    const seen = new Set();
    const users = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }

    resultsEl.innerHTML = "";
    if (users.length === 0 && channels.length === 0) {
      resultsEl.innerHTML = '<div class="search-empty">Никого не найдено</div>';
      return;
    }

    for (const channel of channels) resultsEl.appendChild(buildChannelResultItem(channel));
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

/* ------------------------------------------------------------------ */
/* Групповые чаты (createGroupChat из Android ChatRepositoryImpl)       */
/* ------------------------------------------------------------------ */

$("btn-new-group").addEventListener("click", () => {
  groupSelectedMembers.clear();
  $("group-title-input").value = "";
  $("group-search-input").value = "";
  $("group-error").textContent = "";
  renderGroupSelectedMembers();
  $("group-search-results").innerHTML = '<div class="search-empty">Начните вводить имя или @username</div>';
  $("modal-new-group").classList.remove("hidden");
  $("group-title-input").focus();
});

$("btn-close-group-modal").addEventListener("click", () => {
  $("modal-new-group").classList.add("hidden");
});

$("modal-new-group").addEventListener("click", (e) => {
  if (e.target === $("modal-new-group")) $("btn-close-group-modal").click();
});

$("group-search-input").addEventListener("input", () => {
  clearTimeout(groupSearchTimer);
  groupSearchTimer = setTimeout(runGroupSearch, 300);
});

async function runGroupSearch() {
  const term = $("group-search-input").value.trim().toLowerCase();
  const resultsEl = $("group-search-results");
  if (term.length < 2) {
    resultsEl.innerHTML = '<div class="search-empty">Минимум 2 символа</div>';
    return;
  }
  try {
    const byUsername = await searchUsersByField("usernameLowercase", term);
    const byName = await searchUsersByField("displayNameLowercase", term);
    const seen = new Set();
    const users = [];
    for (const d of byUsername.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }
    for (const d of byName.docs) { if (!seen.has(d.id)) { seen.add(d.id); users.push(d); } }

    resultsEl.innerHTML = "";
    const filtered = users.filter((d) => d.id !== currentUser.uid && !groupSelectedMembers.has(d.id));
    if (filtered.length === 0) {
      resultsEl.innerHTML = '<div class="search-empty">Никого не найдено</div>';
      return;
    }
    for (const d of filtered) {
      const u = d.data();
      const item = document.createElement("div");
      item.className = "user-result";
      item.innerHTML = `
        <div class="chat-avatar">${esc((u.displayName || "?")[0].toUpperCase())}</div>
        <div>
          <div class="user-result-name">${esc(u.displayName || "")}</div>
          <div class="user-result-username">@${esc(u.username || "")}</div>
        </div>`;
      item.addEventListener("click", () => {
        groupSelectedMembers.set(d.id, { displayName: u.displayName || u.username || "Участник" });
        $("group-search-input").value = "";
        resultsEl.innerHTML = '<div class="search-empty">Начните вводить имя или @username</div>';
        renderGroupSelectedMembers();
      });
      resultsEl.appendChild(item);
    }
  } catch (err) {
    console.error("Поиск участников группы:", err);
    resultsEl.innerHTML = '<div class="search-empty">Ошибка поиска</div>';
  }
}

function renderGroupSelectedMembers() {
  const container = $("group-selected-members");
  container.innerHTML = "";
  for (const [uid, info] of groupSelectedMembers) {
    const chip = document.createElement("div");
    chip.className = "member-chip";
    chip.innerHTML = `
      <div class="chat-avatar">${esc((info.displayName[0] || "?").toUpperCase())}</div>
      <span>${esc(info.displayName)}</span>
      <button type="button" title="Убрать">✕</button>`;
    chip.querySelector("button").addEventListener("click", () => {
      groupSelectedMembers.delete(uid);
      renderGroupSelectedMembers();
    });
    container.appendChild(chip);
  }
}

/* ------------------------------------------------------------------ */
/* Каналы — точный порт ChatRepositoryImpl.createChannel/subscribe/       */
/* unsubscribe/requestToJoin/searchChannels/isChannelManager (Android)    */
/* ------------------------------------------------------------------ */

$("btn-new-channel").addEventListener("click", () => {
  $("channel-title-input").value = "";
  $("channel-desc-input").value = "";
  $("channel-access-mode").value = "OPEN";
  $("channel-error").textContent = "";
  $("modal-new-channel").classList.remove("hidden");
  $("channel-title-input").focus();
});

$("btn-close-channel-modal").addEventListener("click", () => {
  $("modal-new-channel").classList.add("hidden");
});
$("modal-new-channel").addEventListener("click", (e) => {
  if (e.target === $("modal-new-channel")) $("btn-close-channel-modal").click();
});

// НОВОЕ (переработка каналов): создание — как createChannel в Android.
// Владелец = createdBy, admins изначально пуст, режим доступа по умолчанию OPEN,
// ограничения (forwarding/comments/reactions/saving/linkPreviews) — всё разрешено.
$("btn-create-channel").addEventListener("click", async () => {
  const errorEl = $("channel-error");
  errorEl.textContent = "";
  const title = $("channel-title-input").value.trim();
  const description = $("channel-desc-input").value.trim();
  const accessMode = $("channel-access-mode").value; // OPEN | MODERATED | HIDDEN

  if (!title) { errorEl.textContent = "Введите название канала"; return; }

  try {
    const now = Date.now();
    const chatRef = doc(collection(db, "chats"));
    await setDoc(chatRef, {
      participantIds: [currentUser.uid],
      type: "CHANNEL",
      title: title,
      titleLowercase: title.toLowerCase(),
      description: description,
      isVerified: false,
      lastMessage: "",
      lastMessageTimestamp: now,
      lastMessageSenderId: currentUser.uid,
      unreadCounts: { [currentUser.uid]: 0 },
      isOnline: false,
      createdBy: currentUser.uid,
      adminIds: [],
      createdAt: now,
      accessMode: accessMode,
      allowForwarding: true,
      allowComments: true,
      allowReactions: true,
      allowSaving: true,
      allowLinkPreviews: true,
    });

    $("modal-new-channel").classList.add("hidden");
    openChat(chatRef.id);
  } catch (err) {
    console.error("Создание канала:", err);
    errorEl.textContent = "Не удалось создать канал: " + (err.message || "");
  }
});

// НОВОЕ: режим доступа канала определяет тип входа в него.
function channelAccessModeOf(chat) {
  const raw = (chat && chat.accessMode ? String(chat.accessMode) : "").toUpperCase();
  return raw === "MODERATED" || raw === "HIDDEN" ? raw : "OPEN";
}

function isChannelManagerOf(chat) {
  if (!chat || !currentUser) return false;
  const admins = chat.adminIds || [];
  return chat.createdBy === currentUser.uid || admins.includes(currentUser.uid);
}

// Порт subscribeToChannel: доступно только для OPEN-каналов (MODERATED идёт
// через requestToJoinChannel, HIDDEN не должен попадать сюда из поиска).
async function subscribeToChannel(chatId) {
  try {
    await updateDoc(doc(db, "chats", chatId), {
      participantIds: arrayUnion(currentUser.uid),
      ["unreadCounts." + currentUser.uid]: 0,
    });
    // НОВОЕ (статистика владельца): событие подписки — best-effort, как в Android.
    setDoc(doc(db, "chats", chatId, "subscriberEvents", currentUser.uid), {
      subscribedAt: Date.now(),
      type: "SUBSCRIBE",
    }).catch(() => {});
  } catch (err) {
    console.error("Не удалось подписаться на канал:", err);
    alert("Не удалось подписаться на канал");
  }
}

// Порт unsubscribeFromChannel: владелец не может отписаться (проверка как в Android).
async function unsubscribeFromChannel(chatId) {
  const chat = findChatData(chatId);
  if (chat && chat.createdBy === currentUser.uid) return;
  try {
    await updateDoc(doc(db, "chats", chatId), {
      participantIds: arrayRemove(currentUser.uid),
    });
    setDoc(doc(db, "chats", chatId, "subscriberEvents", currentUser.uid + "_unsub_" + Date.now()), {
      subscribedAt: Date.now(),
      type: "UNSUBSCRIBE",
      uid: currentUser.uid,
    }).catch(() => {});
  } catch (err) {
    console.error("Не удалось отписаться от канала:", err);
  }
}

// Порт requestToJoinChannel/cancelJoinRequest (MODERATED-каналы).
async function requestToJoinChannel(chatId) {
  try {
    await setDoc(doc(db, "chats", chatId, "joinRequests", currentUser.uid), {
      userId: currentUser.uid,
      displayName: currentUser.displayName || "Пользователь",
      username: currentUser.username || null,
      requestedAt: Date.now(),
    });
    alert("Заявка отправлена. Владелец канала должен её одобрить.");
  } catch (err) {
    console.error("Не удалось отправить заявку:", err);
    alert("Не удалось отправить заявку на вступление");
  }
}

async function cancelJoinRequest(chatId) {
  try {
    await deleteDoc(doc(db, "chats", chatId, "joinRequests", currentUser.uid));
  } catch (err) { /* best-effort */ }
}

// Единая точка входа в канал из результатов поиска — маршрутизация по accessMode,
// как в Android ChannelProfileScreen/DiscoverChannelsScreen.
async function joinOrOpenChannel(channel) {
  const alreadyIn = (channel.participantIds || []).includes(currentUser.uid);
  if (alreadyIn) { openChat(channel.id); return; }

  const mode = channelAccessModeOf(channel);
  if (mode === "MODERATED") {
    await requestToJoinChannel(channel.id);
  } else {
    // OPEN (и HIDDEN, куда можно попасть только по прямому знанию chatId — доступ уже есть)
    await subscribeToChannel(channel.id);
    openChat(channel.id);
  }
}

// Порт searchChannels: фильтр type==CHANNEL, подстрока по title/titleLowercase/
// description, HIDDEN скрыт от не-подписчиков, верифицированные выше.
async function searchChannels(term) {
  const normalized = term.trim().toLowerCase();
  if (!normalized) return [];
  try {
    const snap = await getDocs(query(collection(db, "chats"), where("type", "==", "CHANNEL"), limit(300)));
    const items = [];
    snap.forEach((d) => {
      const data = d.data();
      data.id = d.id;
      const mode = channelAccessModeOf(data);
      const participantIds = data.participantIds || [];
      const isSub = participantIds.includes(currentUser.uid);
      if (mode === "HIDDEN" && !isSub) return;
      const title = (data.title || "").toLowerCase();
      const titleLc = data.titleLowercase || title;
      const desc = (data.description || "").toLowerCase();
      if (titleLc.includes(normalized) || title.includes(normalized) || desc.includes(normalized)) {
        items.push(data);
      }
    });
    items.sort((a, b) => {
      const va = a.isVerified ? 1 : 0, vb = b.isVerified ? 1 : 0;
      if (va !== vb) return vb - va;
      return (b.participantIds || []).length - (a.participantIds || []).length;
    });
    return items.slice(0, 30);
  } catch (err) {
    console.error("Поиск каналов:", err);
    return [];
  }
}

function buildChannelResultItem(channel) {
  const item = document.createElement("div");
  item.className = "user-result";
  const alreadyIn = (channel.participantIds || []).includes(currentUser.uid);
  const subCount = (channel.participantIds || []).length;
  const mode = channelAccessModeOf(channel);
  const modeLabel = mode === "MODERATED" ? " · по заявке" : mode === "HIDDEN" ? " · скрытый" : "";
  const verified = channel.isVerified ? " ✓" : "";
  item.innerHTML = `
    <div class="chat-avatar">📢</div>
    <div>
      <div class="user-result-name">${esc(channel.title || "")}${verified}</div>
      <div class="user-result-username">${subCount} подписчиков${esc(modeLabel)}${alreadyIn ? " · вы подписаны" : ""}</div>
    </div>`;
  item.addEventListener("click", () => {
    $("btn-close-modal").click();
    joinOrOpenChannel(channel);
  });
  return item;
}

$("btn-create-group").addEventListener("click", async () => {
  const errorEl = $("group-error");
  errorEl.textContent = "";
  const title = $("group-title-input").value.trim();

  if (!title) { errorEl.textContent = "Введите название группы"; return; }
  // Как в Android: минимум 2 участника + сам создатель = 3 в participantIds
  if (groupSelectedMembers.size < 2) {
    errorEl.textContent = "Выберите хотя бы 2 участников";
    return;
  }

  try {
    const memberIds = Array.from(groupSelectedMembers.keys());
    const allParticipants = Array.from(new Set([currentUser.uid, ...memberIds]));
    const now = Date.now();
    const unreadCounts = {};
    allParticipants.forEach((uid) => { unreadCounts[uid] = 0; });

    const chatRef = doc(collection(db, "chats"));
    await setDoc(chatRef, {
      participantIds: allParticipants,
      type: "GROUP",
      title: title,
      titleLowercase: title.toLowerCase(),
      description: "",
      lastMessage: "",
      lastMessageTimestamp: now,
      unreadCounts: unreadCounts,
      isOnline: false,
      createdBy: currentUser.uid,
      createdAt: now,
    });

    $("modal-new-group").classList.add("hidden");
    openChat(chatRef.id);
  } catch (err) {
    console.error("Создание группы:", err);
    errorEl.textContent = "Не удалось создать группу: " + (err.message || "");
  }
});
