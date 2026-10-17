// Emulator only: receipts, concurrent reads, burst writes and idempotent retry contract.
const fs=require('node:fs'), path=require('node:path'), assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,updateDoc,getDoc,getDocs,collection,runTransaction,serverTimestamp,increment}=require('firebase/firestore');
async function main(){
 const env=await initializeTestEnvironment({projectId:'demo-yodo-receipts',firestore:{host:'127.0.0.1',port:8088,rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 const a=env.authenticatedContext('alice',{email:'alice@example.com'}).firestore(), b=env.authenticatedContext('bob',{email:'bob@example.com'}).firestore(), c=env.authenticatedContext('carol',{email:'carol@example.com'}).firestore(), e=env.authenticatedContext('eve',{email:'eve@example.com'}).firestore();
 const ref=(db,id)=>doc(db,'chats/group/messages/'+id),chat=db=>doc(db,'chats/group');
 let passed=0;const test=async(name,fn)=>{await fn();console.log('PASS '+name);passed++};
 const payload=()=>({senderId:'alice',text:'test',timestamp:Date.now(),status:'SENT',recipientIds:['bob','carol'],deliveredTo:[],readBy:[],sentAt:serverTimestamp()});
 async function receipt(db,id,uid,read,attempt=0){try{return await runTransaction(db,async tx=>{
   const ch=await tx.get(chat(db)),m=await tx.get(ref(db,id)),d=m.data();
   const delivered=[...new Set([...d.deliveredTo,uid])],readers=[...new Set([...d.readBy,...(read?[uid]:[])])];
   const status=d.recipientIds.every(x=>readers.includes(x))?'READ':d.recipientIds.every(x=>delivered.includes(x))?'DELIVERED':'SENT';
   tx.update(ref(db,id),{deliveredTo:delivered,readBy:readers,status});
   if(ch.data().lastMessageId===id && ch.data().lastMessageStatus!==status)tx.update(chat(db),{lastMessageStatus:status});
 });}catch(err){if(attempt>=3||!['permission-denied','aborted'].includes(err.code))throw err;await new Promise(resolve=>setTimeout(resolve,150*(attempt+1)));return receipt(db,id,uid,read,attempt+1);}}
 async function send(id){return runTransaction(a,async tx=>{
   await tx.get(chat(a));const m=await tx.get(ref(a,id));if(m.exists())return;
   tx.set(ref(a,id),payload());tx.update(chat(a),{lastMessageId:id,lastMessageStatus:'SENT',lastMessageSenderId:'alice',lastMessage:'test','unreadCounts.bob':increment(1),'unreadCounts.carol':increment(1)});
 });}
 try{
 await env.clearFirestore();await env.withSecurityRulesDisabled(ctx=>setDoc(chat(ctx.firestore()),{type:'GROUP',participantIds:['alice','bob','carol'],createdBy:'alice',unreadCounts:{bob:0,carol:0}}));
 await test('create modern message',()=>assertSucceeds(send('m1')));
 await test('cannot forge delivered receipt on creation',()=>assertFails(setDoc(ref(a,'forged'),{...payload(),deliveredTo:['bob']})));
 await test('cannot omit a group recipient',()=>assertFails(setDoc(ref(a,'missing'),{...payload(),recipientIds:['bob']})));
 await test('cannot invent recipient',()=>assertFails(setDoc(ref(a,'extra'),{...payload(),recipientIds:['bob','carol','eve']})));
 await test('sender cannot mark own message read',()=>assertFails(updateDoc(ref(a,'m1'),{deliveredTo:['bob','carol'],readBy:['bob','carol'],status:'READ'})));
 await test('outsider cannot acknowledge',()=>assertFails(updateDoc(ref(e,'m1'),{deliveredTo:['eve']})));
 await test('recipient cannot forge another recipient',()=>assertFails(updateDoc(ref(b,'m1'),{deliveredTo:['bob','carol'],status:'DELIVERED'})));
 await test('first delivery does not claim all delivered',async()=>{await assertSucceeds(receipt(b,'m1','bob',false));assert.equal((await getDoc(ref(a,'m1'))).data().status,'SENT')});
 await test('first read does not claim all read',async()=>{await assertSucceeds(receipt(b,'m1','bob',true));assert.equal((await getDoc(ref(a,'m1'))).data().status,'SENT')});
 await test('all delivered means delivered',async()=>{await assertSucceeds(receipt(c,'m1','carol',false));assert.equal((await getDoc(ref(a,'m1'))).data().status,'DELIVERED')});
 await test('all read means read',async()=>{await assertSucceeds(receipt(c,'m1','carol',true));assert.equal((await getDoc(ref(a,'m1'))).data().status,'READ')});
 await test('late delivery cannot downgrade read',()=>assertFails(updateDoc(ref(b,'m1'),{status:'DELIVERED'})));
 await test('cannot erase receipt',()=>assertFails(updateDoc(ref(b,'m1'),{readBy:['carol'],status:'DELIVERED'})));
 await test('recipient set immutable',()=>assertFails(updateDoc(ref(a,'m1'),{recipientIds:['bob']})));
 await test('server timestamp immutable',()=>assertFails(updateDoc(ref(a,'m1'),{sentAt:serverTimestamp()})));
 await test('ordinary edit keeps receipts',()=>assertSucceeds(updateDoc(ref(a,'m1'),{text:'edited',isEdited:true})));
 await test('idempotent retry does not duplicate or increment unread',async()=>{await assertSucceeds(send('m1'));assert.equal((await getDoc(chat(a))).data().unreadCounts.bob,1);assert.equal((await getDocs(collection(a,'chats/group/messages'))).size,1)});
 await test('burst writes retain every message and exact unread count',async()=>{for(let i=2;i<=9;i++)await assertSucceeds(send('m'+i));assert.equal((await getDocs(collection(a,'chats/group/messages'))).size,9);assert.equal((await getDoc(chat(a))).data().unreadCounts.bob,9)});
 await test('old receipt cannot change newer chat preview',async()=>{await receipt(b,'m2','bob',true);assert.equal((await getDoc(chat(a))).data().lastMessageId,'m9');assert.equal((await getDoc(chat(a))).data().lastMessageStatus,'SENT')});
 await test('concurrent readers preserve both receipts',async()=>{await Promise.all([receipt(b,'m9','bob',true),receipt(c,'m9','carol',true)]);assert.equal((await getDoc(ref(a,'m9'))).data().status,'READ');assert.deepEqual(new Set((await getDoc(ref(a,'m9'))).data().readBy),new Set(['bob','carol']))});
 await test('eight-message receipt transaction fits rules budget',()=>assertSucceeds(runTransaction(b,async tx=>{
   await tx.get(chat(b));const docs=[];for(let i=2;i<=9;i++)docs.push(await tx.get(ref(b,'m'+i)));
   for(const m of docs){const d=m.data(),delivered=[...new Set([...d.deliveredTo,'bob'])],readers=[...new Set([...d.readBy,'bob'])];
     const status=d.recipientIds.every(x=>readers.includes(x))?'READ':d.recipientIds.every(x=>delivered.includes(x))?'DELIVERED':'SENT';
     if(d.readBy.includes('bob'))continue;
     tx.update(m.ref,{deliveredTo:delivered,readBy:readers,status});
   }
 })));
 await test('unread clear does not erase concurrently arrived tail',async()=>{
   await runTransaction(b,async tx=>{const snap=await tx.get(chat(b));if(['m1','m2'].includes(snap.data().lastMessageId))tx.update(chat(b),{'unreadCounts.bob':0})});
   assert.equal((await getDoc(chat(b))).data().unreadCounts.bob,9);
 });
 await test('displayed current tail safely clears badge',async()=>{
   await runTransaction(b,async tx=>{const snap=await tx.get(chat(b));if(['m9'].includes(snap.data().lastMessageId))tx.update(chat(b),{'unreadCounts.bob':0})});
   assert.equal((await getDoc(chat(b))).data().unreadCounts.bob,0);
 });
 await test('legacy messages remain compatible',()=>assertSucceeds(setDoc(ref(a,'old'),{senderId:'alice',text:'legacy',timestamp:1,status:'SENT'})));
 await test('legacy sent to read allowed',()=>assertSucceeds(updateDoc(ref(b,'old'),{status:'READ'})));
 await test('legacy read cannot regress',()=>assertFails(updateDoc(ref(b,'old'),{status:'DELIVERED'})));
 console.log(`${passed} message receipt tests passed`);
 }finally{await env.cleanup()}
}
main().catch(e=>{console.error(e);process.exit(1)});
