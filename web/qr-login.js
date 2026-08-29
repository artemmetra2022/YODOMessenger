/**
 * YODO Messenger — вход по QR-коду (веб-часть).
 *
 * Протокол (полное описание — в firestore.rules, match /qrLogins/{sessionId}):
 *  1. Генерируем ОДНОРАЗОВУЮ пару ключей ECDH (WebCrypto, P-256) прямо в браузере.
 *     Приватный ключ НИКОГДА никуда не отправляется — существует только в памяти
 *     этой вкладки и удаляется при закрытии экрана.
 *  2. Создаём документ qrLogins/{sessionId} с нашим публичным ключом и статусом
 *     "pending", рисуем QR с URL yodo://qrlogin/{sessionId}?pk={base64url(pk)}.
 *  3. Слушаем документ через onSnapshot. Приложение на телефоне (уже авторизовано)
 *     сканирует QR, пользователь подтверждает вход, телефон шифрует свои сохранённые
 *     email+пароль под нашим публичным ключом (ECDH + HKDF-SHA256 + AES-256-GCM) и
 *     переводит документ в статус "approved" с полем encryptedPayload.
 *  4. Как только видим "approved" — расшифровываем полезную нагрузку нашим приватным
 *     ключом, логинимся через signInWithEmailAndPassword, удаляем документ.
 *
 * Формат encryptedPayload (совпадает с тем, что генерирует QrLoginCrypto.kt на
 * Android): base64( ephemeralPublicKey(65 байт, несжатая точка P-256) || iv(12 байт)
 * || ciphertext+GCM-тег ).
 */

const HKDF_INFO = new TextEncoder().encode("yodo-qrlogin-v1");
const QR_LOGIN_TTL_MS = 5 * 60 * 1000; // документ считаем протухшим через 5 минут

function base64UrlEncode(bytes) {
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64Decode(str) {
  const bin = atob(str.replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function randomSessionId() {
  const bytes = new Uint8Array(18);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

/** Экспортирует публичный ключ WebCrypto в формате "raw" (несжатая точка P-256, 65 байт). */
async function exportRawPublicKey(publicKey) {
  const raw = await crypto.subtle.exportKey("raw", publicKey);
  return new Uint8Array(raw);
}

/** ECDH(нашПриватный, чужойПубличный-эфемерный) → HKDF-SHA256 → 256-битный AES-ключ. */
async function deriveAesKey(ourPrivateKey, theirPublicRawBytes) {
  const theirPublicKey = await crypto.subtle.importKey(
    "raw",
    theirPublicRawBytes,
    { name: "ECDH", namedCurve: "P-256" },
    false,
    []
  );
  const sharedSecretBits = await crypto.subtle.deriveBits(
    { name: "ECDH", public: theirPublicKey },
    ourPrivateKey,
    256
  );
  const hkdfKey = await crypto.subtle.importKey(
    "raw",
    sharedSecretBits,
    "HKDF",
    false,
    ["deriveKey"]
  );
  return crypto.subtle.deriveKey(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: new Uint8Array(32), // "без соли" в HKDF = hashLen нулевых байт (совместимо с Android)
      info: HKDF_INFO,
    },
    hkdfKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["decrypt"]
  );
}

/** Расшифровывает encryptedPayload (base64) нашим приватным ключом. Возвращает {email, password}. */
async function decryptPayload(ourPrivateKey, encryptedPayloadBase64) {
  const all = base64Decode(encryptedPayloadBase64);
  const ephemeralPublicRaw = all.slice(0, 65);
  const iv = all.slice(65, 65 + 12);
  const ciphertext = all.slice(65 + 12);

  const aesKey = await deriveAesKey(ourPrivateKey, ephemeralPublicRaw);
  const plaintextBuf = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv, tagLength: 128 },
    aesKey,
    ciphertext
  );
  const json = new TextDecoder().decode(plaintextBuf);
  return JSON.parse(json);
}

/**
 * Инициализирует экран входа по QR.
 * @param deps.auth        - Firebase Auth instance
 * @param deps.db          - Firestore instance (уже modular SDK функции переданы отдельно)
 * @param deps.firestoreFns - { doc, setDoc, onSnapshot, deleteDoc, serverTimestamp }
 * @param deps.signInWithEmailAndPassword - функция из firebase-auth SDK
 * @param deps.showScreen  - переключение экранов ("screen-login" / "screen-qr-login" / ...)
 * @param deps.$           - document.getElementById helper
 * @param deps.onError     - (message) => void, показать ошибку на экране логина
 */
export function initQrLogin({
  auth,
  db,
  firestoreFns,
  signInWithEmailAndPassword,
  showScreen,
  $,
  onError,
}) {
  const { doc, setDoc, onSnapshot, deleteDoc, serverTimestamp } = firestoreFns;

  let unsubscribe = null;
  let currentSessionId = null;
  let currentPrivateKey = null;
  let cancelled = false;

  function setStatus(text, kind) {
    const el = $("qr-login-status");
    el.textContent = text;
    el.className = "qr-login-status" + (kind ? " qr-login-" + kind : "");
  }

  function cleanup() {
    cancelled = true;
    if (unsubscribe) {
      unsubscribe();
      unsubscribe = null;
    }
    if (currentSessionId) {
      // Не блокируем закрытие экрана ожиданием сети — просто "выстрелили и забыли".
      deleteDoc(doc(db, "qrLogins", currentSessionId)).catch(() => {});
    }
    currentSessionId = null;
    currentPrivateKey = null;
  }

  async function drawQrCode(text) {
    const wrap = $("qr-login-canvas-wrap");
    wrap.innerHTML = "";
    const canvas = document.createElement("canvas");
    wrap.appendChild(canvas);
    // Библиотека QRCode (davidshimjs, UMD) регистрирует глобальный window.QRCode.
    // eslint-disable-next-line no-undef
    await new Promise((resolve, reject) => {
      try {
        new QRCode(wrap, {
          text,
          width: 200,
          height: 200,
          correctLevel: QRCode.CorrectLevel.M,
        });
        resolve();
      } catch (e) {
        reject(e);
      }
    });
  }

  async function startSession() {
    cancelled = false;
    setStatus("Создаём код…");

    const keyPair = await crypto.subtle.generateKey(
      { name: "ECDH", namedCurve: "P-256" },
      true,
      ["deriveBits", "deriveKey"]
    );
    currentPrivateKey = keyPair.privateKey;
    const publicRaw = await exportRawPublicKey(keyPair.publicKey);
    const publicKeyB64 = base64UrlEncode(publicRaw);

    const sessionId = randomSessionId();
    currentSessionId = sessionId;

    await setDoc(doc(db, "qrLogins", sessionId), {
      publicKey: publicKeyB64,
      status: "pending",
      createdAt: serverTimestamp(),
    });

    if (cancelled) return; // экран уже закрыли, пока ждали сеть

    const qrContent = `yodo://qrlogin/${sessionId}?pk=${publicKeyB64}`;
    await drawQrCode(qrContent);
    setStatus("Ожидание сканирования…");

    const createdAtLocal = Date.now();
    unsubscribe = onSnapshot(
      doc(db, "qrLogins", sessionId),
      async (snap) => {
        if (cancelled || sessionId !== currentSessionId) return;
        if (!snap.exists()) return;
        const data = snap.data();

        if (Date.now() - createdAtLocal > QR_LOGIN_TTL_MS) {
          setStatus("Код устарел. Обновите страницу, чтобы получить новый.", "error");
          cleanup();
          return;
        }

        if (data.status === "approved" && data.encryptedPayload) {
          setStatus("Подтверждено на телефоне, выполняем вход…", "approved");
          try {
            const { email, password } = await decryptPayload(
              currentPrivateKey,
              data.encryptedPayload
            );
            // Останавливаем слушатель ДО удаления документа — иначе собственное
            // deleteDoc() ниже прилетит нам же в этот колбэк как "документ пропал".
            if (unsubscribe) {
              unsubscribe();
              unsubscribe = null;
            }
            await signInWithEmailAndPassword(auth, email, password);
            deleteDoc(doc(db, "qrLogins", sessionId)).catch(() => {});
            currentSessionId = null;
            currentPrivateKey = null;
            // Дальше onAuthStateChanged в app.js сам переключит экран на чат-лист.
          } catch (e) {
            setStatus("Не удалось войти. Попробуйте отсканировать код ещё раз.", "error");
          }
        }
      },
      () => {
        setStatus("Ошибка соединения. Обновите страницу.", "error");
      }
    );
  }

  $("btn-show-qr-login").addEventListener("click", () => {
    showScreen("screen-qr-login");
    startSession().catch((e) => {
      console.error("QR login init failed", e);
      setStatus("Не удалось создать QR-код. Обновите страницу.", "error");
    });
  });

  $("btn-cancel-qr-login").addEventListener("click", () => {
    cleanup();
    showScreen("screen-login");
  });
}
