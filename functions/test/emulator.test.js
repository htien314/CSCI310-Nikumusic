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
  const {membership} = require('../index');
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
    await assertSucceeds(updateDoc(doc(owner,'rides/ride'),{activityStatus:'DELAYED'}));
    await assertSucceeds(updateDoc(doc(owner,'rides/ride'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
    await assertFails(updateDoc(doc(owner,'rides/ride'),{activityStatus:'ON_TIME',capacityStatus:'OPEN'}));
    await db.doc('rides/ride').update({activityStatus:'ON_TIME',capacityStatus:'OPEN',departureTime:Timestamp.fromMillis(Date.now()+1000)});
    await assertFails(updateDoc(doc(owner,'rides/ride'),{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'}));
  });
}
