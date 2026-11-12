/** RFC 9180 base-mode HPKE, Tink X25519/HKDF-SHA256/AES-256-GCM public keysets.
 * Send-only: never creates/replaces users.publicKey and never exports phone secrets.
 * WebCrypto performs all cryptographic primitives. Unknown suites fail closed.
 */
const te = new TextEncoder();
const bytes = (s) => te.encode(s);
export const concat = (...xs) => {
  const r = new Uint8Array(xs.reduce((n, x) => n + x.length, 0));
  let p = 0;
  for (const x of xs) {
    r.set(x, p);
    p += x.length;
  }
  return r;
};
export const unbase64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
export const base64 = (b) => {
  let s = "";
  for (let i = 0; i < b.length; i += 8192)
    s += String.fromCharCode(...b.subarray(i, i + 8192));
  return btoa(s);
};
const u16 = (n) => new Uint8Array([n >> 8, n & 255]);
const kemSuite = concat(bytes("KEM"), u16(32));
const hpkeSuite = concat(bytes("HPKE"), u16(32), u16(1), u16(2));
async function hmac(k, m) {
  const key = await crypto.subtle.importKey(
    "raw",
    k.length ? k : new Uint8Array(32),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, m));
}
async function expand(prk, info, len) {
  let t = new Uint8Array(),
    r = new Uint8Array();
  for (let i = 1; r.length < len; i++) {
    t = await hmac(prk, concat(t, info, new Uint8Array([i])));
    r = concat(r, t);
  }
  return r.slice(0, len);
}
const extract = (suite, salt, label, ikm) =>
  hmac(salt, concat(bytes("HPKE-v1"), suite, bytes(label), ikm));
const labeledExpand = (suite, prk, label, info, len) =>
  expand(
    prk,
    concat(u16(len), bytes("HPKE-v1"), suite, bytes(label), info),
    len,
  );
function fields(b) {
  const result = new Map();
  let p = 0;
  const varint = () => {
    let v = 0,
      shift = 0;
    while (p < b.length && shift < 49) {
      const c = b[p++];
      v += (c & 127) * 2 ** shift;
      if (!(c & 128)) return v;
      shift += 7;
    }
    throw new Error("Повреждённый ключ");
  };
  while (p < b.length) {
    const tag = varint(),
      n = tag >>> 3,
      wire = tag & 7;
    if (!n || result.has(n)) throw new Error("Некорректный ключ");
    if (wire === 0) result.set(n, varint());
    else if (wire === 2) {
      const len = varint();
      if (p + len > b.length) throw new Error("Повреждённый ключ");
      result.set(n, b.slice(p, p + len));
      p += len;
    } else throw new Error("Неподдерживаемый ключ");
  }
  return result;
}
export function parsePublicKey(encoded) {
  if (typeof encoded !== "string" || encoded.length > 30000)
    throw new Error(
      "Нет ключа телефона. Откройте обновлённое Android-приложение.",
    );
  const set = JSON.parse(new TextDecoder().decode(unbase64(encoded)));
  const row = set.key?.find(
    (k) => k.keyId === set.primaryKeyId && k.status === "ENABLED",
  );
  if (
    !row ||
    !Number.isInteger(row.keyId) ||
    row.keyId < 1 ||
    row.keyId > 0xffffffff ||
    !["TINK", "RAW"].includes(row.outputPrefixType) ||
    row.keyData?.typeUrl !==
      "type.googleapis.com/google.crypto.tink.HpkePublicKey" ||
    row.keyData.keyMaterialType !== "ASYMMETRIC_PUBLIC"
  )
    throw new Error("Неподдерживаемый ключ телефона");
  const f = fields(unbase64(row.keyData.value));
  const params = fields(f.get(2) || new Uint8Array());
  if (
    (f.get(1) || 0) !== 0 ||
    params.get(1) !== 1 ||
    params.get(2) !== 1 ||
    params.get(3) !== 2 ||
    f.get(3)?.length !== 32
  )
    throw new Error("Неподдерживаемый набор шифрования");
  const id = row.keyId;
  const prefix =
    row.outputPrefixType === "RAW"
      ? new Uint8Array()
      : new Uint8Array([
          1,
          (id >>> 24) & 255,
          (id >>> 16) & 255,
          (id >>> 8) & 255,
          id & 255,
        ]);
  return { publicKey: f.get(3), prefix };
}
export async function seal(publicKey, plaintext, info = bytes("yodo-e2e-v1")) {
  let pair;
  try {
    pair = await crypto.subtle.generateKey({ name: "X25519" }, true, [
      "deriveBits",
    ]);
  } catch (e) {
    if (e.name === "NotSupportedError")
      throw new Error(
        "Браузер не поддерживает X25519. Обновите браузер; открытый текст не отправлен.",
      );
    throw e;
  }
  const enc = new Uint8Array(
    await crypto.subtle.exportKey("raw", pair.publicKey),
  );
  const peer = await crypto.subtle.importKey(
    "raw",
    publicKey,
    { name: "X25519" },
    false,
    [],
  );
  const dh = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "X25519", public: peer },
      pair.privateKey,
      256,
    ),
  );
  const eae = await extract(kemSuite, new Uint8Array(), "eae_prk", dh);
  const shared = await labeledExpand(
    kemSuite,
    eae,
    "shared_secret",
    concat(enc, publicKey),
    32,
  );
  const pskHash = await extract(
    hpkeSuite,
    new Uint8Array(),
    "psk_id_hash",
    new Uint8Array(),
  );
  const infoHash = await extract(
    hpkeSuite,
    new Uint8Array(),
    "info_hash",
    info,
  );
  const context = concat(new Uint8Array([0]), pskHash, infoHash);
  const secret = await extract(hpkeSuite, shared, "secret", new Uint8Array());
  const keyBytes = await labeledExpand(hpkeSuite, secret, "key", context, 32);
  const nonce = await labeledExpand(
    hpkeSuite,
    secret,
    "base_nonce",
    context,
    12,
  );
  const key = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "AES-GCM" },
    false,
    ["encrypt"],
  );
  const cipher = new Uint8Array(
    await crypto.subtle.encrypt(
      {
        name: "AES-GCM",
        iv: nonce,
        additionalData: new Uint8Array(),
        tagLength: 128,
      },
      key,
      bytes(plaintext),
    ),
  );
  return concat(enc, cipher);
}
export async function encryptToTink(encoded, plaintext) {
  const k = parsePublicKey(encoded);
  return base64(concat(k.prefix, await seal(k.publicKey, plaintext)));
}
function varint(n) {
  const a = [];
  do {
    const x = n % 128;
    n = Math.floor(n / 128);
    a.push(x | (n ? 128 : 0));
  } while (n);
  return new Uint8Array(a);
}
const vi = (field, n) =>
  n ? concat(varint(field * 8), varint(n)) : new Uint8Array();
const ld = (field, b) => concat(varint(field * 8 + 2), varint(b.length), b);
/** The exact Keyset protobuf bytes hashed by Android PublicKeyIdentity. */
export function publicKeysetBytes(encoded) {
  parsePublicKey(encoded);
  const set = JSON.parse(new TextDecoder().decode(unbase64(encoded)));
  const enums = {
    status: { ENABLED: 1, DISABLED: 2, DESTROYED: 3 },
    prefix: { TINK: 1, LEGACY: 2, RAW: 3, CRUNCHY: 4 },
    material: {
      SYMMETRIC: 1,
      ASYMMETRIC_PRIVATE: 2,
      ASYMMETRIC_PUBLIC: 3,
      REMOTE: 4,
    },
  };
  return concat(
    vi(1, set.primaryKeyId),
    ...set.key.map((k) => {
      const d = k.keyData;
      const kd = d
        ? concat(
            ld(1, bytes(d.typeUrl)),
            ld(2, unbase64(d.value)),
            vi(3, enums.material[d.keyMaterialType] || 0),
          )
        : null;
      return ld(
        2,
        concat(
          kd ? ld(1, kd) : new Uint8Array(),
          vi(2, enums.status[k.status] || 0),
          vi(3, k.keyId),
          vi(4, enums.prefix[k.outputPrefixType] || 0),
        ),
      );
    }),
  );
}
export async function keyFingerprint(encoded) {
  return [
    ...new Uint8Array(
      await crypto.subtle.digest("SHA-256", publicKeysetBytes(encoded)),
    ),
  ]
    .map((x) => x.toString(16).padStart(2, "0"))
    .join("")
    .toUpperCase();
}
export function isPrivate(chatId, chat) {
  return (
    chat?.type === "PRIVATE" &&
    !chatId.startsWith("saved_") &&
    !chatId.startsWith("support_")
  );
}
export async function protectPrivateData(
  chatId,
  chat,
  owner,
  data,
  readKey,
  checkTrust,
) {
  if (
    !chat?.type ||
    !["PRIVATE", "GROUP", "CHANNEL", "SUPPORT"].includes(chat.type)
  )
    throw new Error("Не удалось подтвердить тип чата");
  if (!isPrivate(chatId, chat)) return data;
  const ids = [...new Set(chat.participantIds || [])];
  if (ids.length !== 2 || !ids.includes(owner))
    throw new Error("Не удалось подтвердить участников личного чата");
  if (!data.text && !data.replyToText && !data.replyToSenderName) return data;
  const keys = await Promise.all(ids.map(readKey));
  for (let i = 0; i < ids.length; i++) await checkTrust(ids[i], keys[i]);
  const payload = { text: data.text || "" };
  if (data.replyToText != null) payload.replyText = data.replyToText;
  if (data.replyToSenderName != null)
    payload.replySenderName = data.replyToSenderName;
  const ciphers = await Promise.all(
    keys.map((key) => encryptToTink(key, JSON.stringify(payload))),
  );
  const result = {
    ...data,
    text: "",
    encrypted: true,
    encryptedPayloadVersion: 2,
    encByUid: Object.fromEntries(ids.map((uid, i) => [uid, ciphers[i]])),
  };
  delete result.replyToText;
  delete result.replyToSenderName;
  return result;
}
