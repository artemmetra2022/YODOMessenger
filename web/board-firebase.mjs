import { encodeSharedObject, decodeSharedObject } from "./board-model.mjs";
/** Firebase is injected: the editor can be tested offline with the same interface. */
export function sharedBoardApi(db, auth, f) {
  const metaRef = (id) => f.doc(db, "chats", id, "sharedBoard", "meta");
  const objectRef = (id, oid) =>
    f.doc(db, "chats", id, "sharedBoardObjects", oid);
  const who = () => {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error("Войдите в аккаунт");
    return uid;
  };
  function allowed(m, g) {
    if (!m || m.deleted || m.clearing || (m.generation || 0) !== g)
      throw new Error(
        "Доска очищена, удалена или ещё загружается. Повторите действие.",
      );
  }
  return {
    watchShared(id, onMeta, onObjects, onError) {
      let active = true;
      const fail = (e) => {
        if (active) onError(e);
      };
      const a = f.onSnapshot(
        metaRef(id),
        (s) => {
          if (active) onMeta(s.exists() ? s.data() : null);
        },
        fail,
      );
      const b = f.onSnapshot(
        f.collection(db, "chats", id, "sharedBoardObjects"),
        (s) => {
          if (active) onObjects(s.docs.map((d) => ({ ...d.data(), id: d.id })));
        },
        fail,
      );
      return () => {
        active = false;
        a();
        b();
      };
    },
    async createShared(id) {
      const uid = who();
      return f.runTransaction(db, async (tx) => {
        const ref = metaRef(id),
          s = await tx.get(ref);
        if (who() !== uid) throw new Error("Аккаунт изменился");
        if (s.exists() && !s.data().deleted) return s.data();
        if (s.exists()) {
          const old = s.data();
          if (old.clearing) throw new Error("Доска ещё очищается");
          const m = {
            ...old,
            deleted: false,
            clearing: false,
            generation: (old.generation || 0) + 1,
            updatedBy: uid,
            updatedAt: f.serverTimestamp(),
          };
          tx.update(ref, {
            deleted: false,
            clearing: false,
            generation: m.generation,
            updatedBy: uid,
            updatedAt: m.updatedAt,
          });
          return m;
        }
        const m = {
          title: "Общая доска чата",
          width: 1200,
          height: 900,
          grid: "SQUARES",
          createdBy: uid,
          createdAt: f.serverTimestamp(),
          updatedAt: f.serverTimestamp(),
          updatedBy: uid,
          generation: 0,
          deleted: false,
          clearing: false,
        };
        tx.set(ref, m);
        return m;
      });
    },
    async setSharedGrid(id, grid) {
      const uid = who();
      await f.runTransaction(db, async (tx) => {
        const s = await tx.get(metaRef(id));
        allowed(s.exists() ? s.data() : null, s.data()?.generation || 0);
        if (who() !== uid) throw new Error("Аккаунт изменился");
        tx.update(metaRef(id), {
          grid,
          updatedAt: f.serverTimestamp(),
          updatedBy: uid,
        });
      });
    },
    async changeShared(id, generation, adds, removes) {
      // One gesture adds/deletes one immutable object; undo is conditional on its exact JSON.
      if (adds.length + removes.length > 8)
        throw new Error("Слишком много изменений за один раз");
      if (!adds.length && !removes.length) return;
      const uid = who();
      const encoded = adds.map((o) => encodeSharedObject(o));
      await f.runTransaction(db, async (tx) => {
        const m = await tx.get(metaRef(id));
        allowed(m.exists() ? m.data() : null, generation);
        const refs = [...adds, ...removes].map((o) => objectRef(id, o.id));
        const docs = [];
        for (const ref of refs) docs.push(await tx.get(ref));
        if (who() !== uid) throw new Error("Аккаунт изменился");
        adds.forEach((o, i) => {
          if (docs[i].exists())
            throw new Error("Объект уже восстановлен другим участником");
        });
        removes.forEach((o, i) => {
          const d = docs[adds.length + i];
          if (
            !d.exists() ||
            (d.data().generation || 0) !== generation ||
            JSON.stringify(decodeSharedObject(d.data().json, o.id)) !==
              JSON.stringify(o)
          )
            throw new Error("Объект уже изменён другим участником");
        });
        adds.forEach((o, i) =>
          tx.set(objectRef(id, o.id), {
            json: encoded[i],
            authorUid: uid,
            at: f.serverTimestamp(),
            generation,
          }),
        );
        removes.forEach((o) => tx.delete(objectRef(id, o.id)));
        tx.update(metaRef(id), {
          updatedAt: f.serverTimestamp(),
          updatedBy: uid,
        });
      });
    },
  };
}
