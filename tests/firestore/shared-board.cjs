// Isolated Firestore emulator only. Never targets production.
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, deleteDoc, serverTimestamp, collection, getDocs, writeBatch, query, orderBy, documentId, limit, startAfter } = require('firebase/firestore');
async function main() {
 const env = await initializeTestEnvironment({ projectId:'demo-yodo-shared-board', firestore:{host:'127.0.0.1',port:8088,rules:fs.readFileSync(process.env.YODO_RULES_PATH||path.resolve(__dirname,'../../firestore.rules'),'utf8')} });
 let passed=0;
 const test=async(name,fn)=>{await fn();passed++;console.log('PASS '+name)};
 const alice=env.authenticatedContext('alice',{email:'alice@example.com'}).firestore();
 const bob=env.authenticatedContext('bob',{email:'bob@example.com'}).firestore();
 const eve=env.authenticatedContext('eve',{email:'eve@example.com'}).firestore();
 const admin=env.authenticatedContext('admin',{email:'artemmetra2022spb@gmail.com'}).firestore();
 const meta=db=>doc(db,'chats/group/sharedBoard/meta');
 const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
 const object=(db,n)=>doc(db,`chats/group/sharedBoardObjects/${id(n)}`);
 const change=fields=>({...fields,updatedAt:serverTimestamp(),updatedBy:'alice'});
 const payload=gen=>({json:'{}',authorUid:'alice',at:serverTimestamp(),...(gen===undefined?{}:{generation:gen})});
 const token=id(999);
 try {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(ctx=>setDoc(doc(ctx.firestore(),'chats/group'),{type:'GROUP',participantIds:['alice','bob'],createdBy:'alice'}));
  await test('legacy board can be created',()=>assertSucceeds(setDoc(meta(alice),{title:'Общая доска',width:1200,height:900,grid:'SQUARES',createdBy:'alice',updatedBy:'alice',createdAt:serverTimestamp(),updatedAt:serverTimestamp()})));
  await test('legacy generation-zero stroke accepted',()=>assertSucceeds(setDoc(object(alice,1),payload())));
  await test('participant reads board',()=>assertSucceeds(getDoc(meta(bob))));
  await test('outsider cannot read',()=>assertFails(getDoc(meta(eve))));
  await test('outsider cannot clear',()=>assertFails(updateDoc(meta(eve),{generation:1,clearing:true,updatedAt:serverTimestamp(),updatedBy:'eve'})));
  await test('clear advances generation and locks writes',()=>assertSucceeds(updateDoc(meta(alice),change({generation:1,clearing:true,deleted:false,resetToken:token}))));
  await test('new strokes blocked while clearing',()=>assertFails(setDoc(object(alice,2),payload(1))));
  await test('old stroke physically removed',()=>assertSucceeds(deleteDoc(object(bob,1))));
  await test('complete clear',()=>assertSucceeds(updateDoc(meta(alice),change({clearing:false}))));
  await test('stale offline stroke rejected',()=>assertFails(setDoc(object(alice,3),payload())));
  await test('wrong generation rejected',()=>assertFails(setDoc(object(alice,3),payload(0))));
  await test('new generation stroke accepted',()=>assertSucceeds(setDoc(object(alice,3),payload(1))));
  await test('generation rollback rejected',()=>assertFails(updateDoc(meta(alice),change({generation:0}))));
  await test('delete board sets tombstone',()=>assertSucceeds(updateDoc(meta(alice),change({generation:2,clearing:true,deleted:true,resetToken:id(998)}))));
  await test('deleted board blocks strokes',()=>assertFails(setDoc(object(alice,4),payload(2))));
  await test('recreation blocked until physical cleanup',()=>assertFails(updateDoc(meta(alice),change({generation:3,clearing:false,deleted:false}))));
  await test('cleanup can delete remaining objects',()=>assertSucceeds(deleteDoc(object(alice,3))));
  await test('deletion completed',()=>assertSucceeds(updateDoc(meta(alice),change({clearing:false}))));
  await test('cannot erase tombstone',()=>assertFails(deleteDoc(meta(alice))));
  await test('explicit recreation advances generation',()=>assertSucceeds(updateDoc(meta(alice),change({generation:3,deleted:false,clearing:false}))));
  await test('old generation remains invalid after recreation',()=>assertFails(setDoc(object(alice,5),payload(1))));
  await test('current generation accepted after recreation',()=>assertSucceeds(setDoc(object(alice,5),payload(3))));
  await test('bulk cleanup removes objects beyond preview limit',async()=>{
    await env.withSecurityRulesDisabled(async ctx=>{
      const db=ctx.firestore();const b=writeBatch(db);
      for(let n=10;n<335;n++)b.set(object(db,n),{json:'{}',authorUid:'alice',at:new Date(),generation:3});
      await b.commit();
    });
    await assertSucceeds(updateDoc(meta(alice),change({generation:4,clearing:true,deleted:false,resetToken:id(997)})));
    let after=null,removed=0;
    while(true){
      const base=collection(alice,'chats/group/sharedBoardObjects');
      const q=after?query(base,orderBy(documentId()),startAfter(after),limit(100)):query(base,orderBy(documentId()),limit(100));
      const page=await getDocs(q);if(page.empty)break;
      for(let i=0;i<page.docs.length;i+=10){const b=writeBatch(alice);page.docs.slice(i,i+10).forEach(d=>b.delete(d.ref));await assertSucceeds(b.commit());}
      removed+=page.size;after=page.docs.at(-1);
    }
    if(removed!==326)throw new Error(`expected 326 deleted, got ${removed}`);
    await assertSucceeds(updateDoc(meta(alice),change({clearing:false})));
    if(!(await getDocs(collection(alice,'chats/group/sharedBoardObjects'))).empty)throw new Error('objects survived');
  });
  const settings=db=>doc(db,'config/appSettings');
  await test('admin can set realtime sync',()=>assertSucceeds(setDoc(settings(admin),{boardSyncIntervalMs:0})));
  await test('admin can set max interval',()=>assertSucceeds(updateDoc(settings(admin),{boardSyncIntervalMs:5000})));
  for(const invalid of [-1,5001,1.5,'1000'])await test(`invalid interval ${JSON.stringify(invalid)} rejected`,()=>assertFails(updateDoc(settings(admin),{boardSyncIntervalMs:invalid})));
  await test('ordinary participant cannot change interval',()=>assertFails(setDoc(settings(alice),{boardSyncIntervalMs:1000})));
  await test('users read interval',()=>assertSucceeds(getDoc(settings(bob))));
  console.log(`OK ${passed} shared-board rule tests`);
 }finally{await env.cleanup()}
}
main().catch(error=>{console.error(error);process.exitCode=1});
