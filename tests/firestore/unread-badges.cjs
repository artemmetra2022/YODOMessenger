// Local emulator only. Badge clearing is deliberately independent of public read receipts.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,writeBatch,runTransaction,increment,serverTimestamp}=require('firebase/firestore');
async function main(){
 const env=await initializeTestEnvironment({projectId:'demo-yodo-unread-badges',firestore:{host:'127.0.0.1',port:8088,rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 const alice=env.authenticatedContext('alice',{email:'alice@example.com'}).firestore();
 const bob=env.authenticatedContext('bob',{email:'bob@example.com'}).firestore();
 const eve=env.authenticatedContext('eve',{email:'eve@example.com'}).firestore();
 const chat=(db,id='group')=>doc(db,'chats/'+id),msg=(db,id,scope='group')=>doc(db,`chats/${scope}/messages/${id}`),topic=(db,id)=>doc(db,'chats/group/topics/'+id);
 const count=async(ref,uid='bob')=>(await getDoc(ref)).data().unreadCounts?.[uid]||0;
 const tail=(d,uid)=>({count:d.unreadCounts?.[uid]||0,id:d.lastMessageId,time:d.lastMessageTimestamp||0,sender:d.lastMessageSenderId});
 async function clear(db,uid,ids,topicId=null,chatId='group',afterRead=null){return runTransaction(db,async tx=>{
   const ch=await tx.get(chat(db,chatId)),tr=topicId?topic(db,topicId):null,t=tr?await tx.get(tr):null;
   const target=t||ch,d=tail(target.data(),uid);if(!d.count)return true;
   let matches=ids.includes(d.id);
   if(!d.id){const docs=[];for(const id of ids.slice(-8))docs.push(await tx.get(msg(db,id,chatId)));
      matches=docs.some(s=>s.exists()&&d.time>0&&s.data().timestamp===d.time&&(!d.sender||s.data().senderId===d.sender)&&(!topicId||s.data().topicId===topicId));}
   if(afterRead)await afterRead();if(!matches)return false;
   if(tr){tx.update(tr,{['unreadCounts.'+uid]:0});tx.update(chat(db,chatId),{['unreadCounts.'+uid]:Math.max(0,(ch.data().unreadCounts?.[uid]||0)-d.count)})}
   else tx.update(chat(db,chatId),{['unreadCounts.'+uid]:0});return true;
 });}
 async function send(id,time,topicId=null){const batch=writeBatch(alice),d={senderId:'alice',text:'test',timestamp:time,status:'SENT',recipientIds:['bob'],deliveredTo:[],readBy:[],sentAt:serverTimestamp(),...(topicId?{topicId}:{})};
   batch.set(msg(alice,id),d);const preview={lastMessage:'test',lastMessageId:id,lastMessageTimestamp:time,lastMessageSenderId:'alice','unreadCounts.bob':increment(1)};
   batch.update(chat(alice),preview);if(topicId)batch.update(topic(alice,topicId),preview);await batch.commit();}
 let passed=0;const test=async(name,fn)=>{await fn();console.log('PASS '+name);passed++};
 try{
  await env.clearFirestore();await env.withSecurityRulesDisabled(async ctx=>{
   const db=ctx.firestore();await setDoc(chat(db),{type:'GROUP',participantIds:['alice','bob'],createdBy:'bob',adminIds:[],unreadCounts:{alice:4,bob:0}});
   for(const id of ['one','two'])await setDoc(topic(db,id),{title:id,unreadCounts:{bob:0}});
   await setDoc(chat(db,'yodo_official_channel'),{type:'CHANNEL',createdBy:'admin',participantIds:['bob'],unreadCounts:{bob:3,alice:2},lastMessageId:'post',lastMessageTimestamp:100});
   await setDoc(chat(db,'legacy'),{type:'GROUP',createdBy:'alice',participantIds:['alice','bob'],unreadCounts:{bob:2},lastMessageTimestamp:100,lastMessageSenderId:'alice'});
   await setDoc(msg(db,'legacy-tail','legacy'),{senderId:'alice',text:'old',timestamp:100,status:'SENT'});
  });
  await test('first incoming message increments badge',async()=>{await send('m1',1);assert.equal(await count(chat(bob)),1)});
  await test('burst keeps exact unread count',async()=>{await send('m2',2);await send('m3',3);assert.equal(await count(chat(bob)),3)});
  await test('viewing history does not clear unseen tail',async()=>{assert.equal(await clear(bob,'bob',['m1','m2']),false);assert.equal(await count(chat(bob)),3)});
  await test('badge clears without sending public read receipt',async()=>{await assertSucceeds(clear(bob,'bob',['m3']));assert.equal(await count(chat(bob)),0);assert.equal((await getDoc(msg(bob,'m3'))).data().status,'SENT')});
  await test('clearing badge preserves other user counter',async()=>{assert.equal(await count(chat(bob),'alice'),4)});
  await test('new incoming after a clear restores badge',async()=>{await send('m4',4);assert.equal(await count(chat(bob)),1)});
  await test('repeated read does not subtract from newer messages',async()=>{assert.equal(await clear(bob,'bob',['m3']),false);assert.equal(await count(chat(bob)),1)});
  await test('receipt failure cannot prevent a separate badge clear',async()=>{await assertFails(updateDoc(msg(alice,'m4'),{status:'READ'}));await assertSucceeds(clear(bob,'bob',['m4']));assert.equal(await count(chat(bob)),0)});
  await test('non-admin topic sender can atomically update lastMessageId',async()=>{await assertSucceeds(send('t1',10,'one'));assert.equal((await getDoc(topic(bob,'one'))).data().lastMessageId,'t1')});
  await test('another topic adds to group count',async()=>{await send('t2',11,'two');await send('t3',12,'two');assert.equal(await count(chat(bob)),3)});
  await test('reading one topic clears it and keeps other topic unread',async()=>{await assertSucceeds(clear(bob,'bob',['t1'],'one'));assert.equal(await count(topic(bob,'one')),0);assert.equal(await count(chat(bob)),2);assert.equal(await count(topic(bob,'two')),2)});
  await test('repeated topic read does not subtract twice',async()=>{await clear(bob,'bob',['t1'],'one');assert.equal(await count(chat(bob)),2)});
  await test('topic history cannot clear unread tail',async()=>{assert.equal(await clear(bob,'bob',['t2'],'two'),false);assert.equal(await count(chat(bob)),2)});
  await test('reading final topic clears group badge',async()=>{await clear(bob,'bob',['t3'],'two');assert.equal(await count(chat(bob)),0)});
  await test('legacy missing lastMessageId clears with verified visible document',async()=>{await assertSucceeds(clear(bob,'bob',['legacy-tail'],null,'legacy'));assert.equal(await count(chat(bob,'legacy')),0)});
  await test('official channel reader may reset own badge',async()=>{await assertSucceeds(clear(bob,'bob',['post'],null,'yodo_official_channel'));assert.equal(await count(chat(bob,'yodo_official_channel')),0)});
  await test('official reader cannot reset another user badge',()=>assertFails(updateDoc(chat(bob,'yodo_official_channel'),{'unreadCounts.alice':0})));
  await test('official reader cannot edit channel while clearing badge',()=>assertFails(updateDoc(chat(bob,'yodo_official_channel'),{'unreadCounts.bob':0,title:'forged'})));
  await test('official reader cannot invent unread badge',()=>assertFails(updateDoc(chat(bob,'yodo_official_channel'),{'unreadCounts.bob':99})));
  await test('outsider cannot clear private group counter',()=>assertFails(updateDoc(chat(eve),{'unreadCounts.bob':0})));
  await test('concurrent new tail is not erased by stale clear',async()=>{
   await send('race-old',20);let first=true;
   const cleared=await clear(bob,'bob',['race-old'],null,'group',async()=>{if(first){first=false;await send('race-new',21)}});
   assert.equal(cleared,false);assert.equal(await count(chat(bob)),2);
  });
  console.log(`${passed} unread badge tests passed`);
 }finally{await env.cleanup()}
}
main().catch(e=>{console.error(e);process.exit(1)});
