'use strict';
const {test,before,after,beforeEach}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,writeBatch,collection,query,where,getDocs}=require('firebase/firestore');
let env;
before(async()=>{
 if(!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run npm test in integration-tests; a local emulator is required.');
 env=await initializeTestEnvironment({projectId:'demo-trojan-ada-riya',firestore:{rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});
});
after(async()=>{if(env) await env.cleanup();});
beforeEach(async()=>{await env.clearFirestore();});
const dbFor=uid=>env.authenticatedContext(uid,{email:`${uid}@usc.edu`}).firestore();
async function register(uid,id='1234567890',nickname='Hailey') {
 const db=dbFor(uid),batch=writeBatch(db);
 batch.set(doc(db,`users/${uid}`),{uid,fullName:'Hailey Tien',nickname,uscEmail:`${uid}@usc.edu`,uscId:id,graduationYear:new Date().getUTCFullYear()+4});
 batch.set(doc(db,`publicProfiles/${uid}`),{displayName:nickname,fullName:'Hailey Tien'});
 batch.set(doc(db,`uscIdReservations/${id}`),{uid});
 await batch.commit();return db;
}
function ride(uid='alice') {
 return {title:'Airport',origin:{address:'USC',latitude:34.02,longitude:-118.28},
 destination:{address:'LAX',latitude:33.94,longitude:-118.4},departureTime:new Date(Date.now()+86400000),delayedDepartureTime:null,
 organizerId:uid,participantIds:[uid],participants:[{userId:uid,displayName:'Hailey',acceptedAt:new Date()}],
 maxRiders:3,expectedCostCents:3000,note:null,activityStatus:'ON_TIME',capacityStatus:'OPEN'};
}
test('registration atomically creates private profile, public profile and ID reservation',async()=>{
 const db=await register('alice');
 assert.equal((await getDoc(doc(db,'users/alice'))).get('nickname'),'Hailey');
 assert.equal((await getDoc(doc(db,'publicProfiles/alice'))).get('displayName'),'Hailey');
 await assertFails(getDoc(doc(db,'uscIdReservations/1234567890')));
});
test('duplicate USC ID cannot create partial profile documents',async()=>{
 await register('alice');await assert.rejects(register('bob'));
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();assert.equal((await getDoc(doc(db,'users/bob'))).exists(),false);
  assert.equal((await getDoc(doc(db,'publicProfiles/bob'))).exists(),false);
 });
});
test('nickname update keeps private and public names consistent',async()=>{
 const db=await register('alice');const batch=writeBatch(db);
 batch.update(doc(db,'users/alice'),{nickname:'Hails'});
 batch.update(doc(db,'publicProfiles/alice'),{displayName:'Hails'});await assertSucceeds(batch.commit());
 assert.equal((await getDoc(doc(db,'users/alice'))).get('nickname'),'Hails');
});
test('nickname cannot change in only the private profile',async()=>{
 const db=await register('alice');await assertFails(updateDoc(doc(db,'users/alice'),{nickname:'Unsynced'}));
});
test('another student cannot read private account details',async()=>{
 await register('alice');await assertFails(getDoc(doc(dbFor('bob'),'users/alice')));
 await assertSucceeds(getDoc(doc(dbFor('bob'),'publicProfiles/alice')));
});
test('student creates a ride under their real UID and sees it in organized rides',async()=>{
 const db=await register('alice');await assertSucceeds(setDoc(doc(db,'rides/airport'),ride()));
 const found=await getDocs(query(collection(db,'rides'),where('organizerId','==','alice')));
 assert.equal(found.size,1);assert.equal(found.docs[0].get('participants')[0].displayName,'Hailey');
});
test('signed-out and forged-organizer ride creation are denied',async()=>{
 await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(),'rides/airport'),ride()));
 await assertFails(setDoc(doc(dbFor('bob'),'rides/airport'),ride('alice')));
});
test('organizer can persist a delay through the full-document ride repository write',async()=>{
 const db=await register('alice');const original=ride();await setDoc(doc(db,'rides/airport'),original);
 await assertSucceeds(setDoc(doc(db,'rides/airport'),{...original,activityStatus:'DELAYED',delayedDepartureTime:new Date(Date.now()+172800000)}));
 assert.equal((await getDoc(doc(db,'rides/airport'))).get('activityStatus'),'DELAYED');
});
test('nonorganizer cannot update ride status',async()=>{
 const db=await register('alice');await setDoc(doc(db,'rides/airport'),ride());
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/airport'),{activityStatus:'DELAYED'}));
});
test('posted route, time, capacity and cost remain fixed',async()=>{
 const db=await register('alice');await setDoc(doc(db,'rides/airport'),ride());
 for(const change of [{title:'Changed'},{departureTime:new Date()},{maxRiders:6},{expectedCostCents:1},{origin:{address:'Elsewhere'}}])
  await assertFails(updateDoc(doc(db,'rides/airport'),change));
});
test('direct cancellation and reversal of server cancellation are denied',async()=>{
 const db=await register('alice');await setDoc(doc(db,'rides/airport'),ride());
 await assertFails(updateDoc(doc(db,'rides/airport'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'rides/airport'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
 await assertFails(updateDoc(doc(db,'rides/airport'),{activityStatus:'ON_TIME',capacityStatus:'OPEN'}));
});
test('public ride sharing preserves public identity and can be read by another student',async()=>{
 const db=await register('alice');await setDoc(doc(db,'rides/airport'),ride());
 await assertSucceeds(updateDoc(doc(db,'publicProfiles/alice'),{sharedRideIds:['airport']}));
 const shared=await getDoc(doc(dbFor('bob'),'publicProfiles/alice'));
 assert.deepEqual(shared.get('sharedRideIds'),['airport']);
 await assertSucceeds(updateDoc(doc(db,'publicProfiles/alice'),{sharedRideIds:[]}));
});
test('history query can load the signed-in users own request records',async()=>{
 const db=await register('alice');
 const results=await assertSucceeds(getDocs(query(collection(db,'joinRequests'),where('userId','==','alice'))));
 assert.equal(results.size,0);
});

test('search query reads on-time and delayed rides from another organizer',async()=>{
 const db=await register('alice');
 await setDoc(doc(db,'rides/on-time'),ride());
 await setDoc(doc(db,'rides/delayed'),ride());
 await updateDoc(doc(db,'rides/delayed'),{activityStatus:'DELAYED',delayedDepartureTime:new Date(Date.now()+172800000)});
 await setDoc(doc(db,'rides/cancelled'),ride());
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'rides/cancelled'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
 const search=query(collection(dbFor('bob'),'rides'),where('activityStatus','in',['ON_TIME','DELAYED']));
 const found=await assertSucceeds(getDocs(search));
 assert.deepEqual(found.docs.map(d=>d.id).sort(),['delayed','on-time']);
 const delayed=found.docs.find(d=>d.id==='delayed');
 assert.ok(delayed.get('delayedDepartureTime').toMillis()>delayed.get('departureTime').toMillis());
});

test('signed-out users cannot run the active-ride search query',async()=>{
 const db=await register('alice');await setDoc(doc(db,'rides/airport'),ride());
 const search=query(collection(env.unauthenticatedContext().firestore(),'rides'),where('activityStatus','in',['ON_TIME','DELAYED']));
 await assertFails(getDocs(search));
});

async function invalidRegistration(change) {
 const db=dbFor('bad'),batch=writeBatch(db);
 const profile={uid:'bad',fullName:'Student',nickname:'Student',uscEmail:'bad@usc.edu',uscId:'9999999999',graduationYear:new Date().getUTCFullYear()+1,...change};
 batch.set(doc(db,'users/bad'),profile);
 batch.set(doc(db,'publicProfiles/bad'),{displayName:profile.nickname,fullName:profile.fullName});
 batch.set(doc(db,'uscIdReservations/9999999999'),{uid:'bad'});
 await assertFails(batch.commit());
}
test('database rejects invalid student fields even if client validation is bypassed',async()=>{
 for(const change of [{uscEmail:'bad@gmail.com'},{graduationYear:1900},{graduationYear:new Date().getUTCFullYear()+7},{fullName:''},{fullName:'   '},{fullName:'\t'},{nickname:''}]) await invalidRegistration(change);
});
test('database rejects invalid ride cost, endpoints and timing',async()=>{
 const db=await register('alice');
 for(const change of [{expectedCostCents:-1},{expectedCostCents:0},{title:' '},{origin:{address:'USC',latitude:100,longitude:0}},{departureTime:new Date(0)},{delayedDepartureTime:new Date(Date.now()+1000)},{costShares:{alice:{amountCents:1}}}])
  await assertFails(setDoc(doc(db,'rides/invalid'),{...ride(),...change}));
 await setDoc(doc(db,'rides/valid'),ride());
 for(const delayedDepartureTime of [null,new Date(0),new Date(Date.now()+1000),'tomorrow'])
  await assertFails(updateDoc(doc(db,'rides/valid'),{activityStatus:'DELAYED',delayedDepartureTime}));
});

test('private history is limited to members until a member explicitly shares it',async()=>{
 const db=await register('alice');
 await register('bob','2222222222','Bob');
 await setDoc(doc(db,'rides/history'),{...ride(),participantIds:['alice']});
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'rides/history'),{activityStatus:'COMPLETED',capacityStatus:'NOT_APPLICABLE'}));
 await assertSucceeds(getDoc(doc(db,'rides/history')));
 const bob=dbFor('bob');
 await assertFails(getDoc(doc(bob,'rides/history')));
 await assertFails(getDocs(collection(bob,'rides')));
 // A stranger's sharing preference cannot publish somebody else's private history.
 await updateDoc(doc(bob,'publicProfiles/bob'),{sharedRideIds:['history']});
 await assertFails(getDoc(doc(bob,'rides/history')));
 await updateDoc(doc(db,'publicProfiles/alice'),{sharedRideIds:['history']});
 await assertSucceeds(getDoc(doc(bob,'rides/history')));
 await updateDoc(doc(db,'publicProfiles/alice'),{sharedRideIds:[]});
 await assertFails(getDoc(doc(bob,'rides/history')));
});
test('privacy permits scoped membership, history and monthly-summary queries',async()=>{
 const db=await register('alice');
 await setDoc(doc(db,'rides/airport'),{...ride(),participantIds:['alice']});
 const {or}=require('firebase/firestore');
 const membership=query(collection(db,'rides'),or(where('activityStatus','in',['ON_TIME','DELAYED']),where('organizerId','==','alice'),where('participantIds','array-contains','alice')));
 assert.equal((await assertSucceeds(getDocs(membership))).size,1);
 await assertSucceeds(getDocs(query(collection(db,'rides'),or(where('organizerId','==','alice'),where('participantIds','array-contains','alice')))));
 await assertSucceeds(getDocs(query(collection(db,'rides'),where('participantIds','array-contains','alice'),where('activityStatus','==','COMPLETED'))));
});

test('direct profile registration cannot forge the authenticated email or add fields',async()=>{
 const db=dbFor('alice');
 for(const change of [{uscEmail:'forged@usc.edu'},{role:'admin'}]) {
  const batch=writeBatch(db),data={uid:'alice',fullName:'Alice',nickname:'Alice',uscEmail:'alice@usc.edu',uscId:'8888888888',graduationYear:new Date().getUTCFullYear(),...change};
  batch.set(doc(db,'users/alice'),data);
  batch.set(doc(db,'publicProfiles/alice'),{fullName:'Alice',displayName:'Alice'});
  batch.set(doc(db,'uscIdReservations/8888888888'),{uid:'alice'});
  await assertFails(batch.commit());
 }
});

test('ride creation rejects forged or malformed participant and metadata contracts',async()=>{
 const db=await register('alice');
 for(const change of [{participants:[{userId:'alice',displayName:'Forged',acceptedAt:new Date()}]},
  {participants:[{userId:'alice',displayName:42,acceptedAt:'bad'}]}, {note:42}, {extra:'field'}, {participantIds:[]}])
  await assertFails(setDoc(doc(db,'rides/malformed'),{...ride(),...change}));
 const missing=ride();delete missing.participantIds;
 await assertFails(setDoc(doc(db,'rides/malformed'),missing));
 // Valid addresses need not fit the 100-character nickname limit.
 await assertSucceeds(setDoc(doc(db,'rides/long-address'),{...ride(),origin:{...ride().origin,address:'A'.repeat(150)}}));
});
