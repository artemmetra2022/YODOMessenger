// Local emulator only. Cipher strings here test shape/access rules, not cryptography.
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, deleteField, deleteDoc, serverTimestamp, collection, query, where, getDocs, writeBatch } = require('firebase/firestore');
async function main() {
  const env = await initializeTestEnvironment({ projectId: 'demo-yodo-private-security',
    firestore: { host: '127.0.0.1', port: 8088,
      rules: fs.readFileSync(process.env.YODO_RULES_PATH || path.resolve(__dirname, '../../firestore.rules'), 'utf8') } });
  let passed = 0;
  const test = async (name, fn) => { await fn(); passed++; console.log('PASS ' + name); };
  const alice = env.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
  const bob = env.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
  const eve = env.authenticatedContext('eve', { email: 'eve@example.com' }).firestore();
  const messageRef = (db, id = 'cipher', chat = 'private') => doc(db, `chats/${chat}/messages/${id}`);
  const scheduledRef = (db, id = 'pending') => doc(db, `chats/private/scheduledMessages/${id}`);
  const cipher = () => ({ senderId: 'alice', timestamp: 1, status: 'SENT', text: '', encrypted: true,
    encryptedPayloadVersion: 2, encByUid: { alice: 'ciphertext-for-alice', bob: 'ciphertext-for-bob' } });
  try {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      for (const [id, type] of [['private', 'PRIVATE'], ['group', 'GROUP'], ['saved_alice', 'PRIVATE']]) {
        await setDoc(doc(db, `chats/${id}`), { type, participantIds: ['alice', 'bob'], createdBy: 'alice', lastMessage: '', lastMessagePlain: 'legacy secret' });
      }
      await setDoc(messageRef(db, 'legacy'), { senderId: 'alice', text: 'legacy text', timestamp: 1 });
      await setDoc(messageRef(db, 'legacy-cipher'), { senderId: 'alice', text: '', timestamp: 1, encrypted: true,
        encByUid: { alice: 'old-a', bob: 'old-b' } });
    });
    await test('v2 encrypted personal message allowed', () => assertSucceeds(setDoc(messageRef(alice), cipher())));
    await test('plaintext personal send rejected', () => assertFails(setDoc(messageRef(alice, 'plain'), { senderId: 'alice', text: 'SECRET' })));
    await test('plaintext caption rejected', () => assertFails(setDoc(messageRef(alice, 'caption'), { senderId: 'alice', text: 'SECRET', imageBase64: 'image' })));
    await test('plaintext reply rejected', () => assertFails(setDoc(messageRef(alice, 'reply'), { ...cipher(), replyToText: 'SECRET' })));
    await test('plaintext reply name rejected', () => assertFails(setDoc(messageRef(alice, 'replyname'), { ...cipher(), replyToSenderName: 'SECRET' })));
    await test('transient plaintext preview rejected', () => assertFails(setDoc(messageRef(alice, 'preview'), { ...cipher(), _plainPreview: 'SECRET' })));
    await test('legacy encrypted new send rejected until client updates', () => assertFails(setDoc(messageRef(alice, 'old-client'), { ...cipher(), encryptedPayloadVersion: 1 })));
    await test('missing recipient cipher rejected', () => assertFails(setDoc(messageRef(alice, 'missing'), { ...cipher(), encByUid: { alice: 'a' } })));
    await test('extra recipient cipher rejected', () => assertFails(setDoc(messageRef(alice, 'extra'), { ...cipher(), encByUid: { alice: 'a', bob: 'b', eve: 'e' } })));
    await test('non-string recipient cipher rejected', () => assertFails(setDoc(messageRef(alice, 'bad'), { ...cipher(), encByUid: { alice: 'a', bob: 3 } })));
    await test('outsider cannot create encrypted personal message', () => assertFails(setDoc(messageRef(eve, 'outsider'), { ...cipher(), senderId: 'eve' })));
    await test('other member can read ciphertext', () => assertSucceeds(getDoc(messageRef(bob))));
    await test('encrypted edit allowed', () => assertSucceeds(updateDoc(messageRef(alice), { encByUid: { alice: 'new-a', bob: 'new-b' }, isEdited: true })));
    await test('encrypted edit cannot add plaintext', () => assertFails(updateDoc(messageRef(alice), { text: 'SECRET', isEdited: true })));
    await test('encrypted edit cannot add quote', () => assertFails(updateDoc(messageRef(alice), { replyToText: 'SECRET' })));
    await test('sender identity cannot change', () => assertFails(updateDoc(messageRef(alice), { senderId: 'bob' })));
    await test('legacy text can upgrade to encrypted', () => assertSucceeds(updateDoc(messageRef(alice, 'legacy'), { text: '', encrypted: true, encryptedPayloadVersion: 2, encByUid: { alice: 'a', bob: 'b' } })));
    await test('legacy ciphertext metadata update still allowed', () => assertSucceeds(updateDoc(messageRef(alice, 'legacy-cipher'), { status: 'READ' })));
    await test('legacy ciphertext soft deletion still allowed', () => assertSucceeds(updateDoc(messageRef(alice, 'legacy-cipher'), { isDeleted: true, text: '' })));
    await test('legacy preview deletion allowed', () => assertSucceeds(updateDoc(doc(alice, 'chats/private'), { lastMessagePlain: deleteField(), lastMessage: 'Личное сообщение' })));
    await test('plaintext preview rewrite rejected', () => assertFails(updateDoc(doc(alice, 'chats/private'), { lastMessagePlain: 'SECRET' })));
    await test('plaintext main preview rejected', () => assertFails(updateDoc(doc(alice, 'chats/private'), { lastMessage: 'SECRET' })));
    await test('generic encrypted preview allowed', () => assertSucceeds(updateDoc(doc(alice, 'chats/private'), { lastMessage: '🔒 Сообщение' })));
    await test('private type cannot be downgraded', () => assertFails(updateDoc(doc(alice, 'chats/private'), { type: 'GROUP' })));
    await test('private recipients cannot be replaced', () => assertFails(updateDoc(doc(alice, 'chats/private'), { participantIds: ['alice', 'eve'] })));
    await test('new chat cannot store plaintext preview', () => assertFails(setDoc(doc(alice, 'chats/new-private'), { type: 'PRIVATE', createdBy: 'alice', participantIds: ['alice', 'bob'], lastMessagePlain: 'SECRET' })));
    await test('new personal chat with generic preview allowed', () => assertSucceeds(setDoc(doc(alice, 'chats/new-private'), { type: 'PRIVATE', createdBy: 'alice', participantIds: ['alice', 'bob'], lastMessage: '' })));
    await test('encrypted scheduled message allowed', () => assertSucceeds(setDoc(scheduledRef(alice), { ...cipher(), scheduledFor: 100, createdAt: 1 })));
    await test('plaintext scheduled message rejected', () => assertFails(setDoc(scheduledRef(alice, 'plain'), { senderId: 'alice', text: 'SECRET', scheduledFor: 100 })));
    await test('scheduled edit cannot add plaintext', () => assertFails(updateDoc(scheduledRef(alice), { text: 'SECRET' })));
    await test('another member cannot read private schedule', () => assertFails(getDoc(scheduledRef(bob))));
    await test('another member cannot modify private schedule', () => assertFails(updateDoc(scheduledRef(bob), { scheduledFor: 200 })));
    await test('owner can query schedules', () => assertSucceeds(getDocs(query(collection(alice, 'chats/private/scheduledMessages'), where('senderId', '==', 'alice')))));
    await test('owner can cancel schedule', () => assertSucceeds(deleteDoc(scheduledRef(alice))));
    await test('group plaintext preserved as explicit non-E2EE scope', () => assertSucceeds(setDoc(messageRef(alice, 'plain', 'group'), { senderId: 'alice', text: 'Group text' })));
    await test('saved messages preserved as explicit non-E2EE scope', () => assertSucceeds(setDoc(messageRef(alice, 'plain', 'saved_alice'), { senderId: 'alice', text: 'Saved text' })));
    await test('unprotected media without caption remains supported', () => assertSucceeds(setDoc(messageRef(alice, 'media'), { senderId: 'alice', imageBase64: 'image', text: '' })));
    await test('outsider cannot change message reaction in private chat', () => assertFails(updateDoc(messageRef(eve), { reactions: { ok: ['eve'] } })));
    await test('atomic encrypted send and generic preview allowed', async () => {
      const batch = writeBatch(alice);
      batch.set(messageRef(alice, 'batch'), cipher());
      batch.update(doc(alice, 'chats/private'), { lastMessage: '🔒 Сообщение', lastMessagePlain: deleteField(),
        lastMessageId: 'batch', lastMessageTimestamp: 2, lastMessageSenderId: 'alice', lastMessageStatus: 'SENT' });
      await assertSucceeds(batch.commit());
    });
    console.log(`OK ${passed} private-text security rule tests`);
  } finally { await env.cleanup(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
