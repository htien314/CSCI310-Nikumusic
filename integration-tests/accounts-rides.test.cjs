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
const dbFor=uid=>env.authenticatedContext(uid).firestore();
async function register(uid,id='1234567890',nickname='Hailey') {
 const db=dbFor(uid),batch=writeBatch(db);
 batch.set(doc(db,`users/${uid}`),{uid,fullName:'Hailey Tien',nickname,uscEmail:`${uid}@usc.edu`,uscId:id,graduationYear:2030});
 batch.set(doc(db,`publicProfiles/${uid}`),{displayName:nickname,fullName:'Hailey Tien'});
 batch.set(doc(db,`uscIdReservations/${id}`),{uid});
 await batch.commit();return db;
}
function ride(uid='alice') {
 return {title:'Airport',origin:{address:'USC',latitude:34.02,longitude:-118.28},
 destination:{address:'LAX',latitude:33.94,longitude:-118.4},departureTime:new Date(Date.now()+86400000),delayedDepartureTime:null,
 organizerId:uid,participants:[{userId:uid,displayName:'Hailey',acceptedAt:new Date()}],
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
