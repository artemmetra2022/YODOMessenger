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
  updateProfile,
  updatePassword,
  reauthenticateWithCredential,
  EmailAuthProvider,
  sendPasswordResetEmail,
  GoogleAuthProvider,
  signInWithPopup,
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
  if (evaluatePasswordStrength(password).level !== "STRONG") {
    return "Пароль должен быть надёжным: минимум 8 символов и хотя бы один спецсимвол";
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
/* Пароль: показать/скрыть, надёжность, генератор                       */
/* Порт PasswordStrength.kt / PasswordStrengthIndicator.kt (Android)     */
/* ------------------------------------------------------------------ */

const SPECIAL_CHARS_REGEX = /[^A-Za-z0-9]/;

function evaluatePasswordStrength(password) {
  const checklist = {
    hasMinLength: password.length >= 8,
    hasSpecialChar: SPECIAL_CHARS_REGEX.test(password),
    hasDigit: /\d/.test(password),
    hasUpperAndLower: /[A-Z]/.test(password) && /[a-z]/.test(password),
  };
  const score = Object.values(checklist).filter(Boolean).length;

  let level;
  if (password.length === 0) level = "WEAK";
  else if (score <= 1) level = "WEAK";
  else if (score <= 3) level = "MEDIUM";
  else level = "STRONG";

  // Как в Android: "надёжный" только если выполнены оба обязательных условия.
  if (level === "STRONG" && (!checklist.hasMinLength || !checklist.hasSpecialChar)) {
    level = "MEDIUM";
  }
  return { level, checklist };
}

function generateStrongPassword(length = 14) {
  const lower = "abcdefghijkmnopqrstuvwxyz";
  const upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  const digits = "23456789";
  const special = "!@#$%^&*()-_=+?";
  const all = lower + upper + digits + special;

  const pick = (chars) => chars[Math.floor(Math.random() * chars.length)];
  const required = [pick(lower), pick(upper), pick(digits), pick(special)];
  const rest = Math.max(length - required.length, 0);
  const body = required.concat(Array.from({ length: rest }, () => pick(all)));

  // Перемешиваем (Fisher-Yates), чтобы обязательные символы не оказались в начале.
  for (let i = body.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [body[i], body[j]] = [body[j], body[i]];
  }
  return body.join("");
}

const STRENGTH_LABELS = { WEAK: "Ненадёжный", MEDIUM: "Средний", STRONG: "Надёжный" };
const STRENGTH_CLASSES = { WEAK: "weak", MEDIUM: "medium", STRONG: "strong" };

function renderPasswordStrength(password) {
  const wrap = $("password-strength-wrap");
  if (!password) { wrap.classList.add("hidden"); return null; }
  wrap.classList.remove("hidden");

  const result = evaluatePasswordStrength(password);
  const cls = STRENGTH_CLASSES[result.level];

  const valueEl = $("password-strength-value");
  valueEl.textContent = STRENGTH_LABELS[result.level];
  valueEl.className = "password-strength-value " + cls;

  const fillEl = $("password-strength-bar-fill");
  fillEl.className = "password-strength-bar-fill " + cls;

  const setCheck = (id, passed, label) => {
    const el = $(id);
    el.classList.toggle("passed", passed);
    el.innerHTML = `<span class="pw-check-icon">${passed ? "●" : "○"}</span> ${label}`;
  };
  setCheck("pw-check-length", result.checklist.hasMinLength, "Минимум 8 символов");
  setCheck("pw-check-special", result.checklist.hasSpecialChar, "Хотя бы один спецсимвол");
  setCheck("pw-check-digit", result.checklist.hasDigit, "Хотя бы одна цифра");
  setCheck("pw-check-case", result.checklist.hasUpperAndLower, "Заглавные и строчные буквы");

  return result;
}

$("reg-password").addEventListener("input", (e) => {
  renderPasswordStrength(e.target.value);
});

$("btn-generate-password").addEventListener("click", () => {
  const generated = generateStrongPassword();
  $("reg-password").value = generated;
  $("reg-password").type = "text";
  $("btn-toggle-reg-password").textContent = "🙈";
  renderPasswordStrength(generated);
});

function wireupPasswordToggle(buttonId, inputId) {
  $(buttonId).addEventListener("click", () => {
    const input = $(inputId);
    const isHidden = input.type === "password";
    input.type = isHidden ? "text" : "password";
    $(buttonId).textContent = isHidden ? "🙈" : "👁";
  });
}
wireupPasswordToggle("btn-toggle-login-password", "login-password");
wireupPasswordToggle("btn-toggle-reg-password", "reg-password");

/* ------------------------------------------------------------------ */
/* Восстановление пароля (email со ссылкой сброса, как в Android)       */
/* ------------------------------------------------------------------ */

$("link-forgot-password").addEventListener("click", () => {
  $("forgot-password-form-wrap").classList.remove("hidden");
  $("forgot-password-success-wrap").classList.add("hidden");
  $("forgot-identifier").value = $("login-identifier").value.trim();
  $("forgot-password-error").textContent = "";
  showScreen("screen-forgot-password");
});

$("link-back-to-login-from-forgot").addEventListener("click", () => {
  showScreen("screen-login");
});

$("form-forgot-password").addEventListener("submit", async (e) => {
  e.preventDefault();
  const errorEl = $("forgot-password-error");
  errorEl.textContent = "";

  const identifier = $("forgot-identifier").value.trim();
  if (!identifier) { errorEl.textContent = "Введите email или username"; return; }

  try {
    const email = await resolveEmailForLogin(identifier);
    await sendPasswordResetEmail(auth, email);
    $("forgot-password-form-wrap").classList.add("hidden");
    $("forgot-password-success-wrap").classList.remove("hidden");
  } catch (err) {
    // Как и на Android — не раскрываем, существует ли аккаунт: одинаковое сообщение
    // об успехе для несуществующего email, но явные ошибки резолва username показываем.
    if (err.message === "USERNAME_NOT_FOUND") {
      errorEl.textContent = "Пользователь с таким username не найден";
    } else if (err.code === "auth/invalid-email") {
      errorEl.textContent = "Некорректный email";
    } else {
      $("forgot-password-form-wrap").classList.add("hidden");
      $("forgot-password-success-wrap").classList.remove("hidden");
    }
  }
});

/* ------------------------------------------------------------------ */
/* Google Sign-In (popup)                                               */
/* ------------------------------------------------------------------ */

async function signInWithGoogle(errorElId) {
  const errorEl = $(errorElId);
  errorEl.textContent = "";
  try {
    const provider = new GoogleAuthProvider();
    const result = await signInWithPopup(auth, provider);
    const uid = result.user.uid;

    // Если это первый вход через Google — создаём документ профиля, как при обычной регистрации.
    const snap = await getDoc(doc(db, "users", uid));
    if (!snap.exists()) {
      const now = Date.now();
      const displayName = result.user.displayName || "Пользователь";
      await setDoc(doc(db, "users", uid), {
        uid: uid,
        displayName: displayName,
        displayNameLowercase: displayName.toLowerCase(),
        email: result.user.email || "",
        publicId: buildPublicId(uid),
        isEmailVerified: true,
        createdAt: now,
      });
    }
    // дальнейшая навигация — в onAuthStateChanged
  } catch (err) {
    if (err.code === "auth/popup-closed-by-user" || err.code === "auth/cancelled-popup-request") return;
    errorEl.textContent = await getErrorMessage(err);
  }
}

$("btn-login-google").addEventListener("click", () => signInWithGoogle("login-error"));
$("btn-register-google").addEventListener("click", () => signInWithGoogle("register-error"));

/* ------------------------------------------------------------------ */
/* Вход по номеру телефона — пока заглушка (в разработке)               */
/* ------------------------------------------------------------------ */

$("btn-login-phone").addEventListener("click", () => {
  $("login-error").textContent = "Вход по номеру телефона пока в разработке — скоро будет доступен.";
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

function buildDeletedBubbleContent(msg) {
  const span = document.createElement("span");
  span.className = "msg-deleted";
  span.textContent = (msg && msg.deletedByAdmin) ? "Сообщение удалено администратором" : "Сообщение удалено";
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
    bubble.appendChild(buildDeletedBubbleContent(msg));
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

/* ------------------------------------------------------------------ */
/* Настройки: тема, профиль, приватность, безопасность, уведомления     */
/* ------------------------------------------------------------------ */

const THEME_STORAGE_KEY = "yodo-theme";

function applyTheme(theme) {
  document.documentElement.classList.toggle("theme-light", theme === "light");
  document.querySelectorAll(".settings-theme-option").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.theme === theme);
  });
}

function initTheme() {
  let saved = "dark";
  try { saved = localStorage.getItem(THEME_STORAGE_KEY) || "dark"; } catch (e) { /* приватный режим и т.п. */ }
  applyTheme(saved);
}
initTheme();

document.querySelectorAll(".settings-theme-option").forEach((btn) => {
  btn.addEventListener("click", () => {
    const theme = btn.dataset.theme;
    applyTheme(theme);
    try { localStorage.setItem(THEME_STORAGE_KEY, theme); } catch (e) { /* игнорируем */ }
  });
});

/* ------------------------------------------------------------------ */
/* Профиль пользователя: полный набор полей из Android (users/{uid})   */
/* ------------------------------------------------------------------ */

// Кэш последнего загруженного документа профиля — чтобы не перезатирать
// поля, которые ещё не подтягивали (аналог YodoUser с Android).
let settingsProfileDoc = null;

async function loadSettingsProfileDoc() {
  try {
    const snap = await getDoc(doc(db, "users", currentUser.uid));
    settingsProfileDoc = snap.exists() ? snap.data() : {};
  } catch (e) {
    settingsProfileDoc = {};
  }
  return settingsProfileDoc;
}

function renderAvatarPreview(el, displayName, avatarBase64) {
  if (avatarBase64) {
    el.innerHTML = `<img src="data:image/jpeg;base64,${avatarBase64}" alt="">`;
  } else {
    el.textContent = ((displayName || "?")[0] || "?").toUpperCase();
  }
}

async function fillSettingsProfile() {
  if (!currentUser) return;
  const name = currentUser.displayName || "Без имени";
  $("settings-displayname").value = name;
  $("settings-username-input").value = currentUser.username || "";
  $("settings-email").value = currentUser.email || "";
  $("settings-display-name-preview").textContent = name;
  $("settings-username-preview").textContent = currentUser.username ? "@" + currentUser.username : "";

  const data = await loadSettingsProfileDoc();
  renderAvatarPreview($("settings-avatar-preview"), name, data.avatarBase64);
  $("settings-bio").value = data.bio || "";
  $("settings-birthdate").value = data.birthDate || "";
  $("settings-location").value = data.location || "";
  $("settings-website").value = data.website || "";

  $("settings-show-online").checked = data.showOnlineStatus !== false;
  $("settings-show-read-receipts").checked = data.showReadReceipts !== false;
  $("settings-show-aboutme").checked = data.showAboutMe !== false;
  $("settings-show-birthdate").checked = data.showBirthDate !== false;
  $("settings-show-location").checked = data.showLocation !== false;
  $("settings-show-website").checked = data.showWebsite !== false;
  $("settings-show-phone").checked = data.showPhoneNumber === true;
  $("settings-show-email").checked = data.showEmail === true;

  $("settings-autodelete-toggle").checked = data.autoDeleteEnabled === true;
  const days = data.autoDeleteDays || 30;
  $("settings-autodelete-days").value = days;
  $("settings-autodelete-days-value").textContent = days;

  renderBlockedUsersList(data.blockedUsers || []);
}

function resetSettingsModalFields() {
  ["settings-profile-error", "settings-profile-success", "settings-privacy-error", "settings-privacy-success",
   "settings-security-error", "settings-security-success", "settings-pin-error", "settings-pin-success",
   "settings-autodelete-error", "settings-autodelete-success", "settings-avatar-error"].forEach((id) => {
    $(id).textContent = "";
  });
  $("settings-current-password").value = "";
  $("settings-new-password").value = "";
  $("settings-new-password-confirm").value = "";
  $("settings-pin-new").value = "";
  $("settings-pin-confirm").value = "";
  $("settings-notifications-hint").textContent =
    ("Notification" in window)
      ? (Notification.permission === "denied"
          ? "Уведомления заблокированы в настройках браузера."
          : "")
      : "Ваш браузер не поддерживает уведомления.";
  $("settings-notifications-toggle").checked = notificationsEnabled;
  $("settings-notifications-toggle").disabled = ("Notification" in window) ? Notification.permission === "denied" : true;

  updatePinStatusLabel();
}

$("btn-open-settings").addEventListener("click", async () => {
  resetSettingsModalFields();
  $("modal-settings").classList.remove("hidden");
  await fillSettingsProfile();
});

$("btn-close-settings-modal").addEventListener("click", () => {
  $("modal-settings").classList.add("hidden");
});

$("modal-settings").addEventListener("click", (e) => {
  if (e.target.id === "modal-settings") $("modal-settings").classList.add("hidden");
});

/* ------------------------------------------------------------------ */
/* Загрузка аватара (сжатие в base64, как в Android ImageUtils)         */
/* ------------------------------------------------------------------ */

// Те же лимиты, что и на Android: 512px, JPEG, качество 88 с шагом -8 до
// минимума 60, итог должен влезать в AVATAR_MAX_BASE64 (550 000 символов).
const AVATAR_MAX_DIMENSION = 512;
const AVATAR_STARTING_QUALITY = 0.88;
const AVATAR_MIN_QUALITY = 0.60;
const AVATAR_MAX_BASE64 = 550_000;

function loadImageFromFile(file) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = (e) => { URL.revokeObjectURL(url); reject(e); };
    img.src = url;
  });
}

async function compressAvatarToBase64(file) {
  const img = await loadImageFromFile(file);
  let { width, height } = img;
  if (width > AVATAR_MAX_DIMENSION || height > AVATAR_MAX_DIMENSION) {
    if (width >= height) {
      height = Math.round(height * (AVATAR_MAX_DIMENSION / width));
      width = AVATAR_MAX_DIMENSION;
    } else {
      width = Math.round(width * (AVATAR_MAX_DIMENSION / height));
      height = AVATAR_MAX_DIMENSION;
    }
  }
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(img, 0, 0, width, height);

  let quality = AVATAR_STARTING_QUALITY;
  let base64 = "";
  while (quality >= AVATAR_MIN_QUALITY) {
    const dataUrl = canvas.toDataURL("image/jpeg", quality);
    base64 = dataUrl.split(",")[1] || "";
    if (base64.length <= AVATAR_MAX_BASE64) return base64;
    quality -= 0.08;
  }
  // Даже на минимальном качестве не влезло — уменьшаем разрешение вдвое и пробуем ещё раз.
  if (width > 128 && height > 128) {
    const canvas2 = document.createElement("canvas");
    canvas2.width = Math.round(width / 2);
    canvas2.height = Math.round(height / 2);
    const ctx2 = canvas2.getContext("2d");
    ctx2.drawImage(canvas, 0, 0, canvas2.width, canvas2.height);
    const dataUrl2 = canvas2.toDataURL("image/jpeg", AVATAR_MIN_QUALITY);
    base64 = dataUrl2.split(",")[1] || "";
  }
  return base64;
}

$("btn-upload-avatar").addEventListener("click", () => $("settings-avatar-input").click());

$("settings-avatar-input").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file) return;
  const errorEl = $("settings-avatar-error");
  errorEl.textContent = "";
  if (!file.type.startsWith("image/")) {
    errorEl.textContent = "Выберите файл изображения";
    return;
  }
  try {
    const base64 = await compressAvatarToBase64(file);
    if (!base64 || base64.length > AVATAR_MAX_BASE64) {
      errorEl.textContent = "Не удалось обработать изображение";
      return;
    }
    await updateDoc(doc(db, "users", currentUser.uid), { avatarBase64: base64, avatarUrl: deleteField() });
    settingsProfileDoc = settingsProfileDoc || {};
    settingsProfileDoc.avatarBase64 = base64;
    renderAvatarPreview($("settings-avatar-preview"), currentUser.displayName, base64);
  } catch (err) {
    console.error("Загрузка аватара:", err);
    errorEl.textContent = "Не удалось загрузить фото: " + (err.message || "");
  }
});

/* ------------------------------------------------------------------ */
/* Сохранение профиля (имя, username, о себе, дата/локация/сайт)        */
/* ------------------------------------------------------------------ */

$("btn-save-profile").addEventListener("click", async () => {
  const errorEl = $("settings-profile-error");
  const successEl = $("settings-profile-success");
  errorEl.textContent = "";
  successEl.textContent = "";

  const newName = $("settings-displayname").value.trim();
  const newUsernameRaw = $("settings-username-input").value.trim().toLowerCase();
  const bio = $("settings-bio").value.trim().slice(0, 300);
  const birthDate = $("settings-birthdate").value.trim();
  const location = $("settings-location").value.trim().slice(0, 100);
  const website = $("settings-website").value.trim().slice(0, 200);

  if (!newName) { errorEl.textContent = "Введите отображаемое имя"; return; }
  if (newUsernameRaw && !/^[a-z0-9_]{3,20}$/.test(newUsernameRaw)) {
    errorEl.textContent = "Username: латиница/цифры/_, 3-20 символов";
    return;
  }

  try {
    // Смена username — так же, как в Android: проверка занятости + перенос usernames/{name}.
    if (newUsernameRaw && newUsernameRaw !== (currentUser.username || "")) {
      const takenSnap = await getDoc(doc(db, "usernames", newUsernameRaw));
      if (takenSnap.exists()) {
        errorEl.textContent = "Этот username уже занят";
        return;
      }
      const oldUsername = currentUser.username;
      await setDoc(doc(db, "usernames", newUsernameRaw), { uid: currentUser.uid });
      if (oldUsername) {
        try { await deleteDoc(doc(db, "usernames", oldUsername)); } catch (e) { /* best-effort */ }
      }
    }

    await updateDoc(doc(db, "users", currentUser.uid), {
      displayName: newName,
      displayNameLowercase: newName.toLowerCase(),
      username: newUsernameRaw || currentUser.username || "",
      usernameLowercase: newUsernameRaw || currentUser.username || "",
      bio: bio,
      birthDate: birthDate,
      location: location,
      website: website,
    });
    try { await updateProfile(auth.currentUser, { displayName: newName }); } catch (e) { /* не критично */ }

    currentUser.displayName = newName;
    if (newUsernameRaw) currentUser.username = newUsernameRaw;
    fillSettingsProfile();
    successEl.textContent = "Профиль обновлён";
  } catch (err) {
    errorEl.textContent = "Не удалось сохранить: " + (err.message || "");
  }
});

/* ------------------------------------------------------------------ */
/* Приватность: онлайн-статус, прочитано, видимость полей профиля       */
/* ------------------------------------------------------------------ */

$("btn-save-privacy").addEventListener("click", async () => {
  const errorEl = $("settings-privacy-error");
  const successEl = $("settings-privacy-success");
  errorEl.textContent = "";
  successEl.textContent = "";

  try {
    await updateDoc(doc(db, "users", currentUser.uid), {
      showOnlineStatus: $("settings-show-online").checked,
      showReadReceipts: $("settings-show-read-receipts").checked,
      showAboutMe: $("settings-show-aboutme").checked,
      showBirthDate: $("settings-show-birthdate").checked,
      showLocation: $("settings-show-location").checked,
      showWebsite: $("settings-show-website").checked,
      showPhoneNumber: $("settings-show-phone").checked,
      showEmail: $("settings-show-email").checked,
    });
    successEl.textContent = "Настройки приватности сохранены";
  } catch (err) {
    errorEl.textContent = "Не удалось сохранить: " + (err.message || "");
  }
});

/* ------------------------------------------------------------------ */
/* Заблокированные пользователи (users/{uid}.blockedUsers: string[])    */
/* ------------------------------------------------------------------ */

async function renderBlockedUsersList(blockedIds) {
  const listEl = $("settings-blocked-list");
  listEl.innerHTML = "";
  if (!blockedIds.length) {
    listEl.innerHTML = '<div class="settings-blocked-empty">Заблокированных пользователей нет</div>';
    return;
  }
  for (const uid of blockedIds) {
    let name = uid;
    try {
      const snap = await getDoc(doc(db, "users", uid));
      if (snap.exists()) name = snap.data().displayName || uid;
    } catch (e) { /* оставляем uid, если профиль не читается */ }

    const row = document.createElement("div");
    row.className = "settings-blocked-item";
    row.innerHTML = `<span>${esc(name)}</span>`;
    const btn = document.createElement("button");
    btn.className = "btn-secondary settings-small-btn";
    btn.textContent = "Разблокировать";
    btn.addEventListener("click", async () => {
      try {
        await updateDoc(doc(db, "users", currentUser.uid), { blockedUsers: arrayRemove(uid) });
        row.remove();
        if (!listEl.children.length) {
          listEl.innerHTML = '<div class="settings-blocked-empty">Заблокированных пользователей нет</div>';
        }
      } catch (err) {
        console.error("Разблокировка:", err);
      }
    });
    row.appendChild(btn);
    listEl.appendChild(row);
  }
}

/* ------------------------------------------------------------------ */
/* Смена пароля (требует реаутентификации)                              */
/* ------------------------------------------------------------------ */

$("btn-change-password").addEventListener("click", async () => {
  const errorEl = $("settings-security-error");
  const successEl = $("settings-security-success");
  errorEl.textContent = "";
  successEl.textContent = "";

  const currentPassword = $("settings-current-password").value;
  const newPassword = $("settings-new-password").value;
  const confirmPassword = $("settings-new-password-confirm").value;

  if (!currentPassword || !newPassword || !confirmPassword) {
    errorEl.textContent = "Заполните все поля";
    return;
  }
  if (newPassword.length < 6) {
    errorEl.textContent = "Новый пароль минимум 6 символов";
    return;
  }
  if (newPassword !== confirmPassword) {
    errorEl.textContent = "Пароли не совпадают";
    return;
  }

  try {
    const credential = EmailAuthProvider.credential(currentUser.email, currentPassword);
    await reauthenticateWithCredential(auth.currentUser, credential);
    await updatePassword(auth.currentUser, newPassword);
    $("settings-current-password").value = "";
    $("settings-new-password").value = "";
    $("settings-new-password-confirm").value = "";
    successEl.textContent = "Пароль изменён";
  } catch (err) {
    errorEl.textContent = await getErrorMessage(err);
  }
});

/* ------------------------------------------------------------------ */
/* PIN-код для веб-сессии (локально в браузере, PBKDF2 как в Android)   */
/* ------------------------------------------------------------------ */

const PIN_ITERATIONS = 50_000;
const PIN_KEY_LENGTH_BITS = 256;

function pinStorageKeyFor(uid) { return "yodo-pin-" + uid; }

function bufToBase64(buf) {
  return btoa(String.fromCharCode(...new Uint8Array(buf)));
}
function base64ToBuf(b64) {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)).buffer;
}

async function hashPin(pin, saltBase64) {
  const enc = new TextEncoder();
  const saltBuf = base64ToBuf(saltBase64);
  const keyMaterial = await crypto.subtle.importKey("raw", enc.encode(pin), "PBKDF2", false, ["deriveBits"]);
  const derived = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt: saltBuf, iterations: PIN_ITERATIONS, hash: "SHA-256" },
    keyMaterial,
    PIN_KEY_LENGTH_BITS
  );
  return bufToBase64(derived);
}

function generatePinSalt() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return bufToBase64(bytes.buffer);
}

function getStoredPin() {
  if (!currentUser) return null;
  try {
    const raw = localStorage.getItem(pinStorageKeyFor(currentUser.uid));
    return raw ? JSON.parse(raw) : null;
  } catch (e) { return null; }
}

function updatePinStatusLabel() {
  const stored = getStoredPin();
  $("settings-pin-status").textContent = stored ? "PIN установлен для этого браузера" : "PIN не установлен";
}

$("btn-save-pin").addEventListener("click", async () => {
  const errorEl = $("settings-pin-error");
  const successEl = $("settings-pin-success");
  errorEl.textContent = "";
  successEl.textContent = "";

  const pin = $("settings-pin-new").value.trim();
  const confirm = $("settings-pin-confirm").value.trim();

  if (!/^\d{4,6}$/.test(pin)) {
    errorEl.textContent = "PIN должен состоять из 4-6 цифр";
    return;
  }
  if (pin !== confirm) {
    errorEl.textContent = "PIN-коды не совпадают";
    return;
  }

  try {
    const salt = generatePinSalt();
    const hash = await hashPin(pin, salt);
    localStorage.setItem(pinStorageKeyFor(currentUser.uid), JSON.stringify({ hash, salt }));
    $("settings-pin-new").value = "";
    $("settings-pin-confirm").value = "";
    updatePinStatusLabel();
    successEl.textContent = "PIN сохранён для этого браузера";
  } catch (err) {
    errorEl.textContent = "Не удалось сохранить PIN: " + (err.message || "");
  }
});

$("btn-clear-pin").addEventListener("click", () => {
  if (!currentUser) return;
  localStorage.removeItem(pinStorageKeyFor(currentUser.uid));
  $("settings-pin-new").value = "";
  $("settings-pin-confirm").value = "";
  $("settings-pin-error").textContent = "";
  $("settings-pin-success").textContent = "PIN отключён";
  updatePinStatusLabel();
});

/* ------------------------------------------------------------------ */
/* Автоудаление аккаунта (users/{uid}.autoDeleteEnabled/autoDeleteDays)  */
/* ------------------------------------------------------------------ */

$("settings-autodelete-days").addEventListener("input", (e) => {
  $("settings-autodelete-days-value").textContent = e.target.value;
});

$("btn-save-autodelete").addEventListener("click", async () => {
  const errorEl = $("settings-autodelete-error");
  const successEl = $("settings-autodelete-success");
  errorEl.textContent = "";
  successEl.textContent = "";

  const enabled = $("settings-autodelete-toggle").checked;
  const days = parseInt($("settings-autodelete-days").value, 10) || 30;

  try {
    await updateDoc(doc(db, "users", currentUser.uid), {
      autoDeleteEnabled: enabled,
      autoDeleteDays: days,
      lastActiveAt: Date.now(),
    });
    successEl.textContent = enabled
      ? `Аккаунт будет удалён после ${days} дней неактивности`
      : "Автоудаление отключено";
  } catch (err) {
    errorEl.textContent = "Не удалось сохранить: " + (err.message || "");
  }
});

/* ------------------------------------------------------------------ */
/* Уведомления                                                          */
/* ------------------------------------------------------------------ */

$("settings-notifications-toggle").addEventListener("change", async (e) => {
  if (e.target.checked) {
    const granted = await ensureNotificationPermission();
    e.target.checked = granted;
    if (!granted) {
      $("settings-notifications-hint").textContent = "Разрешение на уведомления не выдано.";
    }
  } else {
    notificationsEnabled = false;
  }
});


/* ------------------------------------------------------------------ */
/* НОВОЕ (FAQ-бот поддержки, веб): кнопочная панель в чате поддержки —  */
/* зеркало Android-версии (SupportFaq.kt + SupportFaqBotPanel). Данные  */
/* берутся из window.SUPPORT_FAQ (faq-data.js, подключён до app.js).    */
/* Логика не меняет существующие функции: читает activeChatId /         */
/* activeChatData / isAdmin и переключает CSS-классы faq-open /         */
/* faq-collapsed на #chat-active. Пока FAQ открыт — поле ввода скрыто   */
/* (CSS), «Связаться с поддержкой» сворачивает панель и возвращает      */
/* обычное поле ввода.                                                  */
/* ------------------------------------------------------------------ */

(function () {
  const FAQ = window.SUPPORT_FAQ;
  if (!FAQ) return;

  const panel = $("support-faq-panel");
  const reopenBtn = $("btn-faq-reopen");
  if (!panel || !reopenBtn) return;

  // Экраны бота: разделы -> вопросы -> ответ, плюс «Нет нужного вопроса?».
  let faqView = { screen: "sections" };
  let faqCollapsed = false;
  let lastFaqChatId = null;

  const findSection = (id) => FAQ.sections.find((s) => s.id === id) || null;

  function renderFaqPanel() {
    const view = faqView;
    let title = "Чем помочь?";
    let showBack = false;
    let body = "";

    if (view.screen === "questions") {
      const section = findSection(view.sectionId);
      title = section ? section.title : "Вопросы";
      showBack = true;
      body = (section ? section.questions : []).map((q) =>
        `<button type="button" class="faq-item" data-faq-question="${esc(q.id)}">
           <span class="faq-item-title">${esc(q.question)}</span>
           <span class="faq-item-chevron">›</span>
         </button>`
      ).join("");
    } else if (view.screen === "answer") {
      const section = findSection(view.sectionId);
      const q = section ? section.questions.find((x) => x.id === view.questionId) : null;
      title = section ? section.title : "Ответ";
      showBack = true;
      if (q) {
        body = `
        <div class="faq-answer-question">${esc(q.question)}</div>
        <div class="faq-answer-text">${esc(q.answer)}</div>
        <div class="faq-answer-actions">
          <button type="button" class="btn-secondary" data-faq-back>← Назад</button>
          <button type="button" class="btn-primary" data-faq-contact>Связаться с поддержкой</button>
        </div>`;
      }
    } else if (view.screen === "other") {
      title = "Нет нужного вопроса?";
      showBack = true;
      body = `
        <div class="faq-other">
          <div class="faq-other-title">Не нашли ответ среди готовых вопросов?</div>
          <div class="faq-other-desc">Опишите вашу проблему своими словами — оператор поддержки ответит вам в этом же чате.</div>
          <button type="button" class="btn-primary faq-contact-btn" data-faq-contact>Связаться с поддержкой</button>
        </div>`;
    } else {
      body = FAQ.sections.map((s) =>
        `<button type="button" class="faq-item" data-faq-section="${esc(s.id)}">
           <span class="faq-item-emoji">${esc(s.emoji)}</span>
           <span class="faq-item-title">${esc(s.title)}</span>
           <span class="faq-item-chevron">›</span>
         </button>`
      ).join("") + `
        <div class="faq-other">
          <div class="faq-other-title">Нет нужного вопроса?</div>
          <div class="faq-other-desc">Не нашли ответ среди готовых вопросов? Напишите оператору — ответим в этом же чате.</div>
          <button type="button" class="btn-primary faq-contact-btn" data-faq-open-other>Написать оператору</button>
        </div>`;
    }

    panel.innerHTML = `
      <div class="faq-header">
        ${showBack ? '<button type="button" class="icon-btn faq-back-btn" data-faq-back title="Назад">←</button>' : ""}
        <div class="faq-header-title">${esc(title)}</div>
        <button type="button" class="icon-btn faq-close-btn" data-faq-close title="Свернуть и писать оператору">✕</button>
      </div>
      <div class="faq-body">${body}</div>`;
  }

  function updateFaqState() {
    const chatActive = $("chat-active");
    if (!chatActive) return;
    const isSupport = !!(
      typeof activeChatId !== "undefined" && activeChatId &&
      typeof activeChatData !== "undefined" && activeChatData &&
      activeChatData.type === "SUPPORT" &&
      typeof isAdmin !== "undefined" && !isAdmin
    );
    // При (пере)открытии чата поддержки FAQ снова развёрнут и показывает разделы.
    if (isSupport && activeChatId !== lastFaqChatId) {
      lastFaqChatId = activeChatId;
      faqCollapsed = false;
      faqView = { screen: "sections" };
      renderFaqPanel();
    }
    if (!isSupport && lastFaqChatId) lastFaqChatId = null;
    chatActive.classList.toggle("faq-open", isSupport && !faqCollapsed);
    chatActive.classList.toggle("faq-collapsed", isSupport && faqCollapsed);
  }

  function collapseFaq() {
    faqCollapsed = true;
    updateFaqState();
    const input = $("message-input");
    if (input) input.focus();
  }

  // Делегирование кликов: панель перерисовывается innerHTML, слушатель один.
  panel.addEventListener("click", (e) => {
    if (e.target.closest("[data-faq-close]") || e.target.closest("[data-faq-contact]")) {
      collapseFaq();
      return;
    }
    if (e.target.closest("[data-faq-open-other]")) {
      faqView = { screen: "other" };
      renderFaqPanel();
      return;
    }
    const sectionBtn = e.target.closest("[data-faq-section]");
    if (sectionBtn) {
      faqView = { screen: "questions", sectionId: sectionBtn.dataset.faqSection };
      renderFaqPanel();
      return;
    }
    const questionBtn = e.target.closest("[data-faq-question]");
    if (questionBtn) {
      faqView = { screen: "answer", sectionId: faqView.sectionId, questionId: questionBtn.dataset.faqQuestion };
      renderFaqPanel();
      return;
    }
    if (e.target.closest("[data-faq-back]")) {
      if (faqView.screen === "answer") faqView = { screen: "questions", sectionId: faqView.sectionId };
      else faqView = { screen: "sections" };
      renderFaqPanel();
    }
  });

  reopenBtn.addEventListener("click", () => {
    faqCollapsed = false;
    faqView = { screen: "sections" };
    renderFaqPanel();
    updateFaqState();
  });

  // Мгновенное обновление при открытии чата — обёртка над openChat;
  // интервал ниже — страховка для остальных переходов (кнопка «Назад» и т.п.).
  try {
    if (typeof openChat === "function") {
      const originalOpenChat = openChat;
      openChat = function (chatId) {
        const result = originalOpenChat(chatId);
        updateFaqState();
        return result;
      };
    }
  } catch (e) { /* если openChat нельзя переприсвоить — достаточно интервала */ }

  renderFaqPanel();
  updateFaqState();
  setInterval(updateFaqState, 300);
})();
