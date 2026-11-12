/** Account-scoped encrypted browser library (non-exportable AES key in IndexedDB).
 * Does not provide protection from malicious same-origin JS / compromised browser.
 */
import { encodeBoard, decodeBoard } from "./board-model.mjs";
let opening;
function open() {
  return (opening ??= new Promise((resolve, reject) => {
    const r = indexedDB.open("yodo-web-boards", 1);
    r.onupgradeneeded = () => {
      r.result.createObjectStore("keys");
      r.result.createObjectStore("boards", { keyPath: "key" });
    };
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => {
      opening = null;
      reject(r.error);
    };
  }));
}
async function tx(store, mode, fn) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, mode),
      r = fn(t.objectStore(store));
    let value;
    r.onsuccess = () => {
      value = r.result;
    };
    t.oncomplete = () => resolve(value);
    t.onerror = () => reject(t.error);
    t.onabort = () =>
      reject(t.error || new Error("Не удалось сохранить доску"));
  });
}
async function key(owner) {
  let k = await tx("keys", "readonly", (s) => s.get(owner));
  if (k) return k;
  const next = await crypto.subtle.generateKey(
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction("keys", "readwrite"),
      s = t.objectStore("keys"),
      r = s.get(owner);
    let chosen;
    r.onsuccess = () => {
      chosen = r.result || next;
      if (!r.result) s.put(next, owner);
    };
    t.oncomplete = () => resolve(chosen);
    t.onerror = () => reject(t.error);
  });
}
export async function saveLocalBoard(owner, board) {
  if (!owner) throw new Error("Войдите в аккаунт");
  const k = await key(owner),
    id = owner + "/" + board.id,
    iv = crypto.getRandomValues(new Uint8Array(12)),
    data = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv, additionalData: new TextEncoder().encode(id) },
      k,
      new TextEncoder().encode(encodeBoard(board)),
    );
  await tx("boards", "readwrite", (s) =>
    s.put({ key: id, owner, id: board.id, updatedAt: Date.now(), iv, data }),
  );
}
export async function listLocalBoards(owner) {
  const rows = await tx("boards", "readonly", (s) =>
    s.getAll(IDBKeyRange.bound(owner + "/", owner + "/\uffff")),
  );
  const k = await key(owner);
  const out = [];
  for (const r of rows) {
    try {
      const plain = await crypto.subtle.decrypt(
        {
          name: "AES-GCM",
          iv: r.iv,
          additionalData: new TextEncoder().encode(r.key),
        },
        k,
        r.data,
      );
      out.push({
        board: decodeBoard(new TextDecoder().decode(plain)),
        updatedAt: r.updatedAt,
      });
    } catch {
      out.push({ id: r.id, error: true, updatedAt: r.updatedAt });
    }
  }
  return out.sort((a, b) => b.updatedAt - a.updatedAt);
}
export function deleteLocalBoard(owner, id) {
  return tx("boards", "readwrite", (s) => s.delete(owner + "/" + id));
}
