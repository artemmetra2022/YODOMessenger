// Administrative migration. DRY RUN by default. Never logs identifiers or message content.
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore, FieldPath, FieldValue } = require('firebase-admin/firestore');
const projectArg = process.argv.find(arg => arg.startsWith('--project='));
if (!projectArg || !projectArg.slice(10)) {
  console.error('Specify --project=YOUR_FIREBASE_PROJECT. Default: dry run. Add --apply to change documents.');
  process.exit(1);
}
const projectId = projectArg.slice(10);
const apply = process.argv.includes('--apply');
initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();
async function main() {
  let cursor = null, scanned = 0, eligible = 0, updated = 0;
  while (true) {
    let query = db.collection('chats').where('type', '==', 'PRIVATE').orderBy(FieldPath.documentId()).limit(200);
    if (cursor) query = query.startAfter(cursor);
    const page = await query.get();
    if (page.empty) break;
    const batch = db.batch(); let batchCount = 0;
    for (const doc of page.docs) {
      if (doc.id.startsWith('saved_') || doc.id.startsWith('support_')) continue;
      scanned++;
      const data = doc.data();
      const hasPlainField = Object.hasOwn(data, 'lastMessagePlain');
      const needsGeneric = typeof data.lastMessage === 'string' &&
        !['', '🔒 Сообщение', 'Личное сообщение'].includes(data.lastMessage);
      if (!hasPlainField && !needsGeneric) continue;
      eligible++;
      if (apply) {
        batch.update(doc.ref, { lastMessagePlain: FieldValue.delete(),
          lastMessage: data.lastMessage ? 'Личное сообщение' : '' });
        batchCount++;
      }
    }
    if (apply && batchCount) { await batch.commit(); updated += batchCount; }
    cursor = page.docs[page.docs.length - 1];
  }
  console.log(JSON.stringify({ mode: apply ? 'APPLY' : 'DRY_RUN', scanned, eligible, updated }));
}
main().catch(error => {
  console.error('Cleanup failed; no document content logged. Error code:', error.code || 'unknown');
  process.exitCode = 1;
});
