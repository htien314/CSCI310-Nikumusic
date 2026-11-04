'use strict';
const {test, before, after, beforeEach} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const enabled = Boolean(process.env.FIRESTORE_EMULATOR_HOST);
if (!enabled) {
  test('Firestore integration (run with emulators:exec)', {skip:true}, () => {});
} else {
  process.env.GCLOUD_PROJECT = 'demo-trojan-membership';
  const {getFirestore, Timestamp} = require('firebase-admin/firestore');
  const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
  const {doc, getDoc, setDoc, updateDoc, collection, query, where, getDocs} = require('firebase/firestore');
  const {membership, rideCosts, registerProfile} = require('../index');
  const db = getFirestore();
  let env;
  const invoke = (uid, data) => membership.run({auth: uid ? {uid, token:{name:uid}} : null, data});
  before(async () => {
    env = await initializeTestEnvironment({projectId:'demo-trojan-membership',
      firestore:{rules:fs.readFileSync('../firestore.rules','utf8')}});
  });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await db.doc('rides/ride').set({title:'Airport', organizerId:'owner', maxRiders:2,
      participants:[{userId:'owner', displayName:'Owner', acceptedAt:Timestamp.fromMillis(1)}],
      activityStatus:'ON_TIME', capacityStatus:'OPEN', departureTime:Timestamp.fromMillis(Date.now()+86400000)});
  });
  const cost = (uid,data) => rideCosts.run({auth:uid?{uid,token:{}}:null,data:{rideId:'ride',...data}});
  const submit = uid => invoke(uid, {action:'request', rideId:'ride'});
  async function requestFor(uid) {
    return (await db.collection('joinRequests').where('userId','==',uid).get()).docs[0];
  }
  test('two simultaneous approvals for one seat cannot overbook', async () => {
    await Promise.all([submit('alice'), submit('bob')]);
    const [a,b] = await Promise.all([requestFor('alice'),requestFor('bob')]);
    const results = await Promise.allSettled([invoke('owner',{action:'accept',rideId:'ride',targetId:a.id}),
      invoke('owner',{action:'accept',rideId:'ride',targetId:b.id})]);
    assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
    const ride = (await db.doc('rides/ride').get()).data();
    assert.equal(ride.participants.length, 2); assert.equal(ride.capacityStatus,'FULL');
    const requests = (await db.collection('joinRequests').get()).docs.map(d=>d.data());
    assert.equal(requests.filter(r=>r.status==='ACCEPTED').length,1);
    assert.equal(requests.filter(r=>r.status==='REJECTED').length,1);
    assert.equal(requests.filter(r=>r.status==='PENDING').length,0);
  });
  test('simultaneous duplicate submissions produce one request', async () => {
    const results = await Promise.allSettled([submit('alice'),submit('alice')]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    assert.equal((await db.collection('joinRequests').get()).size,1);
  });
  test('submission racing last acceptance is rejected or auto-rejected', async () => {
    await submit('alice');const a = await requestFor('alice');
    await Promise.allSettled([invoke('owner',{action:'accept',rideId:'ride',targetId:a.id}), submit('bob')]);
    assert.equal((await db.collection('joinRequests').where('status','==','PENDING').get()).size,0);
    assert.equal((await db.doc('rides/ride').get()).data().participants.length,2);
  });
  test('failed unauthorized approval changes neither request nor ride', async () => {
    await submit('alice');const a = await requestFor('alice');
    await assert.rejects(invoke('stranger',{action:'accept',rideId:'ride',targetId:a.id}),/current organizer/);
    assert.equal((await a.ref.get()).get('status'),'PENDING');
    assert.equal((await db.doc('rides/ride').get()).get('participants').length,1);
  });
  test('cannot approve a request belonging to a different ride', async () => {
    await db.doc('joinRequests/foreign').set({rideId:'other',userId:'alice',status:'PENDING'});
    await assert.rejects(invoke('owner',{action:'accept',rideId:'ride',targetId:'foreign'}),/no longer pending/);
  });
  test('server ignores forged user ID and time', async () => {
    await invoke('alice',{action:'request',rideId:'ride',uid:'owner',now:0});
    const a=await requestFor('alice');assert.equal(a.get('userId'),'alice');
    assert.ok(a.get('createdAt').toMillis() > Date.now()-60000);
    await assert.rejects(invoke(null,{action:'request',rideId:'ride'}),/log in/);
  });
  test('owner transfer revokes old organizer authority and permits new organizer', async () => {
    await db.doc('rides/ride').update({maxRiders:3});
    await submit('alice');const a=await requestFor('alice');
    await invoke('owner',{action:'accept',rideId:'ride',targetId:a.id});
    await submit('bob');const b=await requestFor('bob');
    await invoke('owner',{action:'leave',rideId:'ride'});
    await assert.rejects(invoke('owner',{action:'accept',rideId:'ride',targetId:b.id}),/current organizer/);
    await invoke('alice',{action:'accept',rideId:'ride',targetId:b.id});
    assert.equal((await db.doc('rides/ride').get()).get('organizerId'),'alice');
  });
  test('rules block direct request writes and participant forgery', async () => {
    const alice = env.authenticatedContext('alice').firestore();
    const owner = env.authenticatedContext('owner').firestore();
    await assertFails(setDoc(doc(alice,'joinRequests/forged'),{userId:'alice',rideId:'ride',status:'ACCEPTED'}));
    await assertFails(updateDoc(doc(owner,'rides/ride'),{participants:[{userId:'owner'},{userId:'alice'}]}));
    await assertFails(updateDoc(doc(owner,'rides/ride'),{organizerId:'alice'}));
    await assertFails(updateDoc(doc(owner,'rides/ride'),{departureTime:new Date()}));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'rides/ride')));
  });
  test('rules allow own-request and current-organizer inbox queries, deny strangers', async () => {
    await submit('alice'); const a=await requestFor('alice');
    const alice=env.authenticatedContext('alice').firestore(), owner=env.authenticatedContext('owner').firestore();
    const stranger=env.authenticatedContext('stranger').firestore();
    await assertSucceeds(getDocs(query(collection(alice,'joinRequests'),where('userId','==','alice'))));
    await assertSucceeds(getDocs(query(collection(owner,'joinRequests'),where('rideId','==','ride'))));
    await assertFails(getDoc(doc(stranger,`joinRequests/${a.id}`)));
  });
  test('rules permit Ada status-only update but protect finished rides and cutoff', async () => {
    const owner=env.authenticatedContext('owner').firestore();
    await assertSucceeds(updateDoc(doc(owner,'rides/ride'),{activityStatus:'DELAYED',delayedDepartureTime:new Date(Date.now()+172800000)}));
    await assertFails(updateDoc(doc(owner,'rides/ride'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
    await invoke('owner',{action:'cancelRide',rideId:'ride'});
    await assertFails(updateDoc(doc(owner,'rides/ride'),{activityStatus:'ON_TIME',capacityStatus:'OPEN'}));
    await db.doc('rides/ride').update({activityStatus:'ON_TIME',capacityStatus:'OPEN',departureTime:Timestamp.fromMillis(Date.now()+1000)});
    await assertFails(updateDoc(doc(owner,'rides/ride'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
  });

  test('cancellation closes pending requests atomically and synchronizes history IDs', async () => {
    await submit('alice');
    await invoke('owner',{action:'cancelRide',rideId:'ride'});
    assert.equal((await requestFor('alice')).get('status'),'REJECTED');
    const ride=(await db.doc('rides/ride').get()).data();
    assert.equal(ride.activityStatus,'CANCELLED');
    assert.deepEqual(ride.participantIds,['owner']);
  });
  test('membership, completion, payments and public flags share consistent records', async () => {
    await db.doc('rides/ride').update({maxRiders:3});
    await db.doc('publicProfiles/alice').set({displayName:'Alice',fullName:'Alice Student',sharedRideIds:['ride']});
    await submit('alice');const a=await requestFor('alice');
    await invoke('owner',{action:'accept',rideId:'ride',targetId:a.id});
    let ride=(await db.doc('rides/ride').get()).data();
    assert.deepEqual(ride.participantIds,['owner','alice']);
    await submit('bob');
    await assert.rejects(cost('owner',{action:'complete',totalCents:1001}),/before departure/);
    await db.doc('rides/ride').update({departureTime:Timestamp.fromMillis(Date.now()-1000)});
    await assert.rejects(cost('stranger',{action:'complete',totalCents:1001}),/Only the organizer/);
    await cost('owner',{action:'complete',totalCents:1001});
    ride=(await db.doc('rides/ride').get()).data();
    assert.equal(ride.activityStatus,'COMPLETED');
    assert.equal(ride.costShares.owner.amountCents+ride.costShares.alice.amountCents,1001);
    assert.equal((await requestFor('bob')).get('status'),'REJECTED');
    await assert.rejects(cost('owner',{action:'paid',riderId:'alice'}),/only your own/);
    await assert.rejects(cost('owner',{action:'flag',riderId:'alice'}),/seven days/);
    await db.doc('rides/ride').update({completedAt:Timestamp.fromMillis(Date.now()-8*86400000)});
    await cost('owner',{action:'flag',riderId:'alice'});
    let profile=(await db.doc('publicProfiles/alice').get()).data();
    assert.deepEqual(profile.overdueRideIds,['ride']);assert.equal(profile.displayName,'Alice');
    const client=env.authenticatedContext('alice').firestore();
    await assertFails(updateDoc(doc(client,'publicProfiles/alice'),{overdueRideIds:[]}));
    await cost('alice',{action:'paid',riderId:'alice'});
    profile=(await db.doc('publicProfiles/alice').get()).data();
    assert.deepEqual(profile.overdueRideIds,[]);assert.deepEqual(profile.sharedRideIds,['ride']);
    ride=(await db.doc('rides/ride').get()).data();
    assert.equal(ride.costShares.alice.paid,true);assert.equal(ride.costShares.alice.flagged,false);
    await assert.rejects(cost('owner',{action:'flag',riderId:'alice'}),/Paid riders/);
    await assertFails(updateDoc(doc(client,'rides/ride'),{actualCostCents:1}));
    await assertFails(updateDoc(doc(client,'rides/ride'),{costShares:{}}));
  });
  test('concurrent completions record costs once and concurrent payments preserve each share', async () => {
    await db.doc('rides/ride').update({departureTime:Timestamp.fromMillis(Date.now()-1000),
      participants:[{userId:'owner'},{userId:'alice'}]});
    const results=await Promise.allSettled([cost('owner',{action:'complete',totalCents:1000}),cost('owner',{action:'complete',totalCents:2000})]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    await Promise.all([cost('owner',{action:'paid',riderId:'owner'}),cost('alice',{action:'paid',riderId:'alice'})]);
    const ride=(await db.doc('rides/ride').get()).data();
    assert.equal(ride.costShares.owner.paid,true);assert.equal(ride.costShares.alice.paid,true);
  });
  test('late requests and approvals cannot join a departed ride', async () => {
    await submit('alice');const a=await requestFor('alice');
    await db.doc('rides/ride').update({departureTime:Timestamp.fromMillis(Date.now()-1)});
    await assert.rejects(submit('bob'),/depart/);
    await assert.rejects(invoke('owner',{action:'accept',rideId:'ride',targetId:a.id}),/depart/);
    assert.equal((await a.ref.get()).get('status'),'PENDING');
  });

  const register=(uid,data)=>registerProfile.run({auth:{uid,token:{email:`${uid}@usc.edu`}},data:{fullName:'Student',nickname:'Student',uscEmail:`${uid}@usc.edu`,uscId:'8000000001',graduationYear:new Date().getUTCFullYear()+1,...data}});
  test('trusted registration rejects invalid profiles and forged email without partial records',async()=>{
    for(const data of [{fullName:' '},{nickname:''},{uscEmail:'other@usc.edu'},{uscEmail:'alice@gmail.com'},{uscId:'123'},{graduationYear:1900}])
      await assert.rejects(register('alice',data),/valid graduation year/);
    assert.equal((await db.doc('users/alice').get()).exists,false);
    assert.equal((await db.doc('uscIdReservations/8000000001').get()).exists,false);
  });
  test('concurrent registration reserves a USC ID once without partial profiles',async()=>{
    const results=await Promise.allSettled([register('alice',{}),register('bob',{})]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    assert.equal((await db.collection('users').get()).size,1);
    assert.equal((await db.collection('publicProfiles').get()).size,1);
    assert.equal((await db.collection('uscIdReservations').get()).size,1);
    assert.match(results.find(r=>r.status==='rejected').reason.message,/USC ID is already registered/);
  });
}
