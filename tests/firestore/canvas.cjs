// Local emulator only; does not test image decoding or encryption implementation.
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, deleteField } = require('firebase/firestore');
async function main() {
 const env = await initializeTestEnvironment({ projectId:'demo-yodo-canvas', firestore:{host:'127.0.0.1',port:8088,rules:fs.readFileSync(process.env.YODO_RULES_PATH||path.resolve(__dirname,'../../firestore.rules'),'utf8')} });
 let passed=0;
 const test=async(name,fn)=>{await fn();passed++;console.log('PASS '+name)};
 const alice=env.authenticatedContext('alice').firestore(),bob=env.authenticatedContext('bob').firestore(),eve=env.authenticatedContext('eve').firestore();
 const ref=(db,id,chat='group')=>doc(db,`chats/${chat}/messages/${id}`);
 const canvas=()=>({senderId:'alice',text:'🎨 Доска',imageBase64:'iVBORw0KGgo=',canvasVersion:1,canvasLocalId:'01234567-89ab-4cde-8123-456789abcdef'});
 try {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{for(const [id,type] of [['group','GROUP'],['private','PRIVATE']]) await setDoc(doc(ctx.firestore(),`chats/${id}`),{type,participantIds:['alice','bob'],createdBy:'alice'});});
  await test('group preview accepted',()=>assertSucceeds(setDoc(ref(alice,'ok'),canvas())));
  await test('participant reads PNG',()=>assertSucceeds(getDoc(ref(bob,'ok'))));
  await test('ordinary noncanvas media unchanged',()=>assertSucceeds(setDoc(ref(alice,'image'),{senderId:'alice',text:'',imageBase64:'image'})));
  await test('version without id rejected',()=>{const d=canvas();delete d.canvasLocalId;return assertFails(setDoc(ref(alice,'missingid'),d))});
  await test('id without version rejected',()=>{const d=canvas();delete d.canvasVersion;return assertFails(setDoc(ref(alice,'missingversion'),d))});
  await test('unknown version rejected',()=>assertFails(setDoc(ref(alice,'version'),{...canvas(),canvasVersion:2})));
  await test('path traversal reference rejected',()=>assertFails(setDoc(ref(alice,'path'),{...canvas(),canvasLocalId:'../../other'})));
  await test('empty PNG rejected',()=>assertFails(setDoc(ref(alice,'empty'),{...canvas(),imageBase64:''})));
  await test('missing PNG rejected',()=>{const d=canvas();delete d.imageBase64;return assertFails(setDoc(ref(alice,'nopng'),d))});
  await test('oversized PNG rejected',()=>assertFails(setDoc(ref(alice,'huge'),{...canvas(),imageBase64:'x'.repeat(650001)})));
  await test('view-once canvas rejected',()=>assertFails(setDoc(ref(alice,'once'),{...canvas(),isViewOnce:true})));
  await test('album cannot impersonate canvas',()=>assertFails(setDoc(ref(alice,'album'),{...canvas(),imagesBase64:['image']})));
  await test('reference immutable',()=>assertFails(updateDoc(ref(alice,'ok'),{canvasLocalId:'01234567-89ab-4cde-8123-456789abcdee'})));
  await test('version immutable',()=>assertFails(updateDoc(ref(alice,'ok'),{canvasVersion:2})));
  await test('ordinary message cannot acquire local reference',()=>assertFails(updateDoc(ref(alice,'image'),{canvasVersion:1,canvasLocalId:canvas().canvasLocalId})));
  await test('author can edit caption',()=>assertSucceeds(updateDoc(ref(alice,'ok'),{text:'Новое название',isEdited:true})));
  await test('private plaintext canvas caption rejected',()=>assertFails(setDoc(ref(alice,'plain','private'),canvas())));
  const encrypted={...canvas(),text:'',encrypted:true,encryptedPayloadVersion:2,encByUid:{alice:'cipher-a',bob:'cipher-b'}};
  await test('private encrypted caption accepted',()=>assertSucceeds(setDoc(ref(alice,'cipher','private'),encrypted)));
  await test('private outsider cannot read canvas',()=>assertFails(getDoc(ref(eve,'cipher','private'))));
  await test('canvas soft deletion remains allowed',()=>assertSucceeds(updateDoc(ref(alice,'ok'),{text:'',isDeleted:true,imageBase64:deleteField()})));
  console.log(`OK ${passed} canvas rule tests`);
 } finally {await env.cleanup()}
}
main().catch(error=>{console.error(error);process.exitCode=1});
