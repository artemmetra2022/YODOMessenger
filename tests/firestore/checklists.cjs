// Local emulator only; never points at a production Firebase project.
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, serverTimestamp, getDoc, runTransaction } = require('firebase/firestore');

async function main() {
  const env = await initializeTestEnvironment({
    projectId: 'demo-yodo-checklists',
    firestore: { host: '127.0.0.1', port: 8088,
      rules: fs.readFileSync(process.env.YODO_RULES_PATH || path.resolve(__dirname, '../../firestore.rules'), 'utf8') }
  });
  let passed = 0;
  const test = async (name, fn) => { await fn(); passed++; console.log('PASS ' + name); };
  const alice = env.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
  const bob = env.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
  const outsider = env.authenticatedContext('eve', { email: 'eve@example.com' }).firestore();
  const base = () => ({ senderId: 'alice', timestamp: 1, status: 'SENT', text: '', checklistTitle: 'Tasks',
    checklistItems: ['A', 'B'], checklistCheckedBy: {} });
  const ref = (db, id = 'list', chat = 'group') => doc(db, `chats/${chat}/messages/${id}`);
  const check = (index, uid, checked = true) => ({ [`checklistCheckedBy.${index}`]: checked ? uid : '',
    checklistLastItemIndex: index, checklistUpdatedBy: uid, checklistUpdatedAt: serverTimestamp() });
  try {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      for (const [id, type] of [['group', 'GROUP'], ['private', 'PRIVATE'], ['channel', 'CHANNEL']]) {
        await setDoc(doc(db, `chats/${id}`), { type, participantIds: ['alice', 'bob'], createdBy: 'alice', bannedUserIds: [] });
      }
      await setDoc(doc(db, 'chats/group/topics/closed'), { isClosed: true });
      await setDoc(doc(db, 'chats/group/topics/open'), { isClosed: false });
      await setDoc(ref(db, 'expired'), { ...base(), expiresAt: 1 });
      await setDoc(ref(db, 'deleted'), { ...base(), isDeleted: true });
      await setDoc(ref(db, 'closed'), { ...base(), topicId: 'closed' });
      await setDoc(ref(db, 'plain'), { senderId: 'alice', text: 'Hello', timestamp: 1 });
      await setDoc(ref(db, 'mine-outside'), { ...base(), senderId: 'eve' });
    });
    await test('member creates checklist', () => assertSucceeds(setDoc(ref(alice), base())));
    await test('other member reads checklist', () => assertSucceeds(getDoc(ref(bob))));
    await test('other member completes item', () => assertSucceeds(updateDoc(ref(bob), check(0, 'bob'))));
    await test('author completes another item', () => assertSucceeds(updateDoc(ref(alice), check(1, 'alice'))));
    await test('both completions preserved', async () => {
      const data = (await getDoc(ref(alice))).data();
      if (data.checklistCheckedBy['0'] !== 'bob' || data.checklistCheckedBy['1'] !== 'alice') throw Error('Lost completion');
    });
    await test('member unchecks another member item', () => assertSucceeds(updateDoc(ref(alice), check(0, 'alice', false))));
    await test('outsider cannot read', () => assertFails(getDoc(ref(outsider))));
    await test('outsider cannot create', () => assertFails(setDoc(ref(outsider, 'outside'), { ...base(), senderId: 'eve' })));
    await test('outsider cannot complete', () => assertFails(updateDoc(ref(outsider), check(0, 'eve'))));
    await test('non-member original author cannot complete', () => assertFails(updateDoc(ref(outsider, 'mine-outside'), check(0, 'eve'))));
    await test('private chats do not accept checklists', () => assertFails(setDoc(ref(alice, 'list', 'private'), base())));
    await test('channels do not accept checklists', () => assertFails(setDoc(ref(alice, 'list', 'channel'), base())));
    await test('empty checklist rejected', () => assertFails(setDoc(ref(alice, 'empty'), { ...base(), checklistItems: [] })));
    await test('more than 20 items rejected', () => assertFails(setDoc(ref(alice, 'long'), { ...base(), checklistItems: Array(21).fill('A') })));
    await test('invalid item type rejected', () => assertFails(setDoc(ref(alice, 'badtype'), { ...base(), checklistItems: ['A', 3] })));
    await test('oversized item rejected', () => assertFails(setDoc(ref(alice, 'big'), { ...base(), checklistItems: ['A'.repeat(201)] })));
    await test('pre-filled actor rejected', () => assertFails(setDoc(ref(alice, 'prefilled'), { ...base(), checklistCheckedBy: { '0': 'bob' } })));
    await test('author cannot edit immutable title', () => assertFails(updateDoc(ref(alice), { checklistTitle: 'Changed' })));
    await test('author cannot edit immutable items', () => assertFails(updateDoc(ref(alice), { checklistItems: ['Changed'] })));
    await test('cannot fake completion actor', () => assertFails(updateDoc(ref(alice), { ...check(0, 'alice'), 'checklistCheckedBy.0': 'bob' })));
    await test('cannot change two items at once', () => assertFails(updateDoc(ref(bob), { ...check(0, 'bob'), 'checklistCheckedBy.1': '' })));
    await test('out of range item rejected', () => assertFails(updateDoc(ref(alice), check(2, 'alice'))));
    await test('cannot fake last actor', () => assertFails(updateDoc(ref(alice), { ...check(0, 'alice'), checklistUpdatedBy: 'bob' })));
    await test('cannot fake timestamp', () => assertFails(updateDoc(ref(alice), { ...check(0, 'alice'), checklistUpdatedAt: 123 })));
    await test('expired checklist cannot be changed', () => assertFails(updateDoc(ref(bob, 'expired'), check(0, 'bob'))));
    await test('deleted checklist cannot be changed', () => assertFails(updateDoc(ref(bob, 'deleted'), check(0, 'bob'))));
    await test('closed forum checklist cannot be changed', () => assertFails(updateDoc(ref(bob, 'closed'), check(0, 'bob'))));
    await test('closed forum cannot accept new checklist', () => assertFails(setDoc(ref(alice, 'closed-new'), { ...base(), topicId: 'closed' })));
    await test('open forum accepts checklist', () => assertSucceeds(setDoc(ref(alice, 'open-new'), { ...base(), topicId: 'open' })));
    await test('author can soft delete checklist', () => assertSucceeds(updateDoc(ref(alice), { isDeleted: true, text: '' })));
    await test('ordinary author text edit still works', () => assertSucceeds(updateDoc(ref(alice, 'plain'), { text: 'Edited' })));
    await test('ordinary message reaction still works', () => assertSucceeds(updateDoc(ref(bob, 'plain'), { reactions: { ok: ['bob'] } })));
    await test('checklist fields cannot be added to existing plain message', () => assertFails(updateDoc(ref(alice, 'plain'), { checklistTitle: 'Injected' })));
    await test('concurrent transactions preserve different items', async () => {
      await setDoc(ref(alice, 'concurrent'), base());
      const complete = async (db, index, uid) => runTransaction(db, async txn => {
        const target = ref(db, 'concurrent'); await txn.get(target); txn.update(target, check(index, uid));
      });
      await Promise.all([complete(alice, 0, 'alice'), complete(bob, 1, 'bob')]);
      const data = (await getDoc(ref(alice, 'concurrent'))).data();
      if (data.checklistCheckedBy['0'] !== 'alice' || data.checklistCheckedBy['1'] !== 'bob') throw Error('Lost concurrent update');
    });
    console.log(`OK ${passed} Firestore tests`);
  } finally { await env.cleanup(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
