'use strict';
// The app's own membership, completion and payment writes (MembershipTransactions and
// FirebaseCostGateway) against firestore.rules, with no Cloud Functions involved.
const {test,before,after,beforeEach}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,writeBatch,serverTimestamp,Timestamp,collection,query,where,getDocs}=require('firebase/firestore');

let env;
before(async()=>{
 if(!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run npm test in integration-tests; a local emulator is required.');
 env=await initializeTestEnvironment({projectId:'demo-trojan-client-membership',firestore:{rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});
});
after(async()=>{if(env) await env.cleanup();});
beforeEach(async()=>{await env.clearFirestore();});

const HOUR=3600000,DAY=24*HOUR;
const dbFor=uid=>env.authenticatedContext(uid,{email:`${uid}@usc.edu`}).firestore();
const nick=uid=>uid[0].toUpperCase()+uid.slice(1);
const reqId=(rideId,uid)=>`${rideId}_${uid}`;
const person=(uid,offset=0)=>({userId:uid,displayName:nick(uid),acceptedAt:Timestamp.fromMillis(Date.now()-DAY+offset)});

async function seed(fn){await env.withSecurityRulesDisabled(c=>fn(c.firestore()));}
async function users(...uids){
 await seed(async db=>{for(const uid of uids){
  await setDoc(doc(db,`users/${uid}`),{uid,fullName:nick(uid),nickname:nick(uid),uscEmail:`${uid}@usc.edu`,uscId:'1',graduationYear:2028});
  await setDoc(doc(db,`publicProfiles/${uid}`),{displayName:nick(uid),fullName:nick(uid)});
 }});
}
async function seedRide(id,{organizer='alice',members=['alice'],departure=Date.now()+DAY,maxRiders=3,...extra}={}){
 await seed(db=>setDoc(doc(db,`rides/${id}`),{title:'Airport',
  origin:{address:'USC',latitude:34.02,longitude:-118.28},destination:{address:'LAX',latitude:33.94,longitude:-118.4},
  departureTime:Timestamp.fromMillis(departure),delayedDepartureTime:null,maxRiders,expectedCostCents:3000,note:null,
  organizerId:organizer,participants:members.map((m,i)=>person(m,i)),participantIds:members,
  activityStatus:'ON_TIME',capacityStatus:members.length>=maxRiders?'FULL':'OPEN',...extra}));
}
const submission=(rideId,uid,message='')=>({rideId,userId:uid,displayName:nick(uid),message,status:'PENDING',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
async function seedRequest(rideId,uid,status='PENDING'){
 await seed(db=>setDoc(doc(db,`joinRequests/${reqId(rideId,uid)}`),{rideId,userId:uid,displayName:nick(uid),message:'',status,
  createdAt:Timestamp.now(),updatedAt:Timestamp.now()}));
}
async function rideData(id){let data;await seed(async db=>{data=(await getDoc(doc(db,`rides/${id}`))).data();});return data;}

// Mirrors MembershipTransactions for accept: append the rider, update capacity, accept the request.
function acceptBatch(db,rideId,ride,uid,closeOthers=[]){
 const participants=[...ride.participants,{userId:uid,displayName:nick(uid),acceptedAt:Timestamp.now()}];
 const batch=writeBatch(db);
 batch.update(doc(db,`rides/${rideId}`),{participants,participantIds:participants.map(p=>p.userId),
  organizerId:ride.organizerId,activityStatus:ride.activityStatus,capacityStatus:participants.length>=ride.maxRiders?'FULL':'OPEN'});
 batch.update(doc(db,`joinRequests/${reqId(rideId,uid)}`),{status:'ACCEPTED',updatedAt:serverTimestamp()});
 for(const other of closeOthers) batch.update(doc(db,`joinRequests/${reqId(rideId,other)}`),{status:'REJECTED',closedReason:'RIDE_FULL',updatedAt:serverTimestamp()});
 return batch;
}
function leaveUpdate(ride,uid,{organizerId=ride.organizerId}={}){
 const participants=ride.participants.filter(p=>p.userId!==uid);
 return {participants,participantIds:participants.map(p=>p.userId),organizerId,
  activityStatus:participants.length?ride.activityStatus:'CANCELLED',capacityStatus:participants.length?'OPEN':'NOT_APPLICABLE'};
}
function shares(amounts){return Object.fromEntries(Object.entries(amounts).map(([id,c])=>[id,{riderId:id,amountCents:c,paid:false,flagged:false}]));}
const completion=(amounts,total)=>({activityStatus:'COMPLETED',capacityStatus:'NOT_APPLICABLE',actualCostCents:total,
 completedAt:serverTimestamp(),participantIds:Object.keys(amounts),costShares:shares(amounts)});

// ---- requests ----

test('a student sends a pending request under their own ID with an optional message',async()=>{
 await users('alice','bob');await seedRide('r');
 const db=dbFor('bob');
 await assertSucceeds(setDoc(doc(db,`joinRequests/${reqId('r','bob')}`),submission('r','bob','Can I join?')));
 assert.equal((await getDoc(doc(db,`joinRequests/${reqId('r','bob')}`))).get('status'),'PENDING');
 // The organizer sees it in their inbox.
 const inbox=await getDocs(query(collection(dbFor('alice'),'joinRequests'),where('rideId','==','r')));
 assert.equal(inbox.size,1);
});

test('forged, own-ride, full, finished, joined and departed requests are denied',async()=>{
 await users('alice','bob','carol');await seedRide('r');
 const bob=dbFor('bob');
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('r','carol')}`),submission('r','carol')));
 await assertFails(setDoc(doc(bob,`joinRequests/other-id`),submission('r','bob')));
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('r','bob')}`),{...submission('r','bob'),displayName:'Someone else'}));
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('r','bob')}`),{...submission('r','bob'),status:'ACCEPTED'}));
 await assertFails(setDoc(doc(dbFor('alice'),`joinRequests/${reqId('r','alice')}`),submission('r','alice')));
 await seedRide('full',{members:['alice','carol'],maxRiders:2});
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('full','bob')}`),submission('full','bob')));
 await seedRide('done',{activityStatus:'CANCELLED',capacityStatus:'NOT_APPLICABLE'});
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('done','bob')}`),submission('done','bob')));
 await seedRide('joined',{members:['alice','bob']});
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('joined','bob')}`),submission('joined','bob')));
 await seedRide('left',{departure:Date.now()-HOUR});
 await assertFails(setDoc(doc(bob,`joinRequests/${reqId('left','bob')}`),submission('left','bob')));
});

test('a rider cancels a pending request and may resubmit, but a rejected request stays rejected',async()=>{
 await users('alice','bob');await seedRide('r');await seedRequest('r','bob');
 const bob=dbFor('bob'),ref=doc(bob,`joinRequests/${reqId('r','bob')}`);
 await assertSucceeds(updateDoc(ref,{status:'CANCELLED',updatedAt:serverTimestamp()}));
 await assertSucceeds(setDoc(ref,submission('r','bob')));
 await assertSucceeds(updateDoc(doc(dbFor('alice'),`joinRequests/${reqId('r','bob')}`),{status:'REJECTED',updatedAt:serverTimestamp()}));
 await assertFails(setDoc(ref,submission('r','bob')));
 await assertFails(updateDoc(ref,{status:'PENDING',updatedAt:serverTimestamp()}));
});

test('a rider who left may request again, but not while still in the ride',async()=>{
 await users('alice','bob');await seedRide('r',{members:['alice','bob']});await seedRequest('r','bob','ACCEPTED');
 const bob=dbFor('bob'),ref=doc(bob,`joinRequests/${reqId('r','bob')}`);
 await assertFails(setDoc(ref,submission('r','bob')));
 await updateDoc(doc(bob,'rides/r'),leaveUpdate(await rideData('r'),'bob'));
 await assertSucceeds(setDoc(ref,submission('r','bob')));
});

test('only the organizer rejects, and a rider cannot accept themselves',async()=>{
 await users('alice','bob','carol');await seedRide('r');await seedRequest('r','bob');
 await assertFails(updateDoc(doc(dbFor('carol'),`joinRequests/${reqId('r','bob')}`),{status:'REJECTED',updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(dbFor('bob'),`joinRequests/${reqId('r','bob')}`),{status:'ACCEPTED',updatedAt:serverTimestamp()}));
});

// ---- accepting and capacity ----

test('the organizer accepts a request: the rider joins and the request is accepted together',async()=>{
 await users('alice','bob');await seedRide('r');await seedRequest('r','bob');
 const alice=dbFor('alice');
 await assertSucceeds(acceptBatch(alice,'r',await rideData('r'),'bob').commit());
 const ride=await rideData('r');
 assert.deepEqual(ride.participantIds,['alice','bob']);assert.equal(ride.capacityStatus,'OPEN');
});

test('accepting without the request, for someone else, or as a non-organizer is denied',async()=>{
 await users('alice','bob','carol');await seedRide('r');await seedRequest('r','bob');
 const ride=await rideData('r');
 const alice=dbFor('alice'),alone=writeBatch(alice);
 const participants=[...ride.participants,{userId:'bob',displayName:'Bob',acceptedAt:Timestamp.now()}];
 alone.update(doc(alice,'rides/r'),{participants,participantIds:['alice','bob'],capacityStatus:'OPEN'});
 await assertFails(alone.commit());
 await assertFails(acceptBatch(dbFor('alice'),'r',ride,'carol').commit());
 await assertFails(acceptBatch(dbFor('carol'),'r',ride,'bob').commit());
});

test('filling the last seat makes the ride Full and closes the other pending requests',async()=>{
 await users('alice','bob','carol','dan');await seedRide('r',{members:['alice','dan']});
 await seedRequest('r','bob');await seedRequest('r','carol');
 await assertSucceeds(acceptBatch(dbFor('alice'),'r',await rideData('r'),'bob',['carol']).commit());
 const ride=await rideData('r');assert.equal(ride.capacityStatus,'FULL');
 assert.equal((await getDoc(doc(dbFor('carol'),`joinRequests/${reqId('r','carol')}`))).get('closedReason'),'RIDE_FULL');
});

test('approvals stop at departure, including a delayed departure',async()=>{
 await users('alice','bob');
 await seedRide('r',{departure:Date.now()-2*HOUR,activityStatus:'DELAYED',delayedDepartureTime:Timestamp.fromMillis(Date.now()-HOUR)});
 await seedRequest('r','bob');
 await assertFails(acceptBatch(dbFor('alice'),'r',await rideData('r'),'bob').commit());
});

// ---- leaving ----

test('a rider leaves at least three hours ahead; ownership and status stay the same',async()=>{
 await users('alice','bob');await seedRide('r',{members:['alice','bob'],activityStatus:'DELAYED',
  delayedDepartureTime:Timestamp.fromMillis(Date.now()+2*DAY)});
 const ride=await rideData('r');
 await assertSucceeds(updateDoc(doc(dbFor('bob'),'rides/r'),leaveUpdate(ride,'bob')));
 const after=await rideData('r');assert.equal(after.organizerId,'alice');assert.equal(after.activityStatus,'DELAYED');
});

test('leaving within three hours is blocked, for riders and the organizer',async()=>{
 await users('alice','bob');await seedRide('r',{members:['alice','bob'],departure:Date.now()+2*HOUR});
 const ride=await rideData('r');
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/r'),leaveUpdate(ride,'bob')));
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),leaveUpdate(ride,'alice',{organizerId:'bob'})));
});

test('a rider cannot remove someone else',async()=>{
 await users('alice','bob','carol');await seedRide('r',{members:['alice','bob','carol']});
 const ride=await rideData('r');
 const forged={...leaveUpdate(ride,'carol'),participantIds:['alice','carol']};
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/r'),forged));
});

test('an organizer leaving riders behind hands the ride to the latest-joined rider',async()=>{
 await users('alice','bob','carol');await seedRide('r',{members:['alice','bob','carol']});
 const ride=await rideData('r');
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),leaveUpdate(ride,'alice',{organizerId:'bob'})));
 await assertSucceeds(updateDoc(doc(dbFor('alice'),'rides/r'),leaveUpdate(ride,'alice',{organizerId:'carol'})));
 assert.equal((await rideData('r')).organizerId,'carol');
});

test('a solo organizer leaving cancels the ride and closes pending requests',async()=>{
 await users('alice','bob');await seedRide('r');await seedRequest('r','bob');
 const alice=dbFor('alice'),batch=writeBatch(alice);
 batch.update(doc(alice,'rides/r'),leaveUpdate(await rideData('r'),'alice'));
 batch.update(doc(alice,`joinRequests/${reqId('r','bob')}`),{status:'REJECTED',closedReason:'RIDE_CANCELLED',updatedAt:serverTimestamp()});
 await assertSucceeds(batch.commit());
 const ride=await rideData('r');assert.equal(ride.activityStatus,'CANCELLED');assert.equal(ride.capacityStatus,'NOT_APPLICABLE');
 // The rejected requester still sees why, without reading the now-private ride.
 assert.equal((await getDoc(doc(dbFor('bob'),`joinRequests/${reqId('r','bob')}`))).get('closedReason'),'RIDE_CANCELLED');
});

// ---- completion and payments ----

test('the organizer completes after departure with an equal split totaling the cost',async()=>{
 await users('alice','bob','carol');await seedRide('r',{members:['alice','bob','carol'],departure:Date.now()-HOUR});
 await assertSucceeds(updateDoc(doc(dbFor('alice'),'rides/r'),completion({alice:334,bob:333,carol:333},1000)));
 const ride=await rideData('r');assert.equal(ride.activityStatus,'COMPLETED');assert.equal(ride.costShares.bob.amountCents,333);
});

test('a solo organizer bears the whole cost, and a custom $0.00 share is allowed',async()=>{
 await users('alice','bob');await seedRide('solo',{departure:Date.now()-HOUR});
 await assertSucceeds(updateDoc(doc(dbFor('alice'),'rides/solo'),completion({alice:1250},1250)));
 await seedRide('pair',{members:['alice','bob'],departure:Date.now()-HOUR});
 await assertSucceeds(updateDoc(doc(dbFor('alice'),'rides/pair'),completion({alice:1000,bob:0},1000)));
});

test('completion before departure, by a rider, with a wrong total or missing share is denied',async()=>{
 await users('alice','bob');await seedRide('early',{members:['alice','bob']});
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/early'),completion({alice:500,bob:500},1000)));
 await seedRide('r',{members:['alice','bob'],departure:Date.now()-HOUR});
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/r'),completion({alice:500,bob:500},1000)));
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),completion({alice:500,bob:400},1000)));
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),completion({alice:1000},1000)));
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),{...completion({alice:500,bob:500},1000),completedAt:Timestamp.fromMillis(Date.now()-30*DAY)}));
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/r'),completion({alice:1000,bob:0},0)));
});

async function completedRide(id,{completedDaysAgo=1,bobPaid=false}={}){
 const costShares=shares({alice:500,bob:500});
 if(bobPaid) costShares.bob.paid=true;
 await seedRide(id,{members:['alice','bob'],departure:Date.now()-(completedDaysAgo+1)*DAY,activityStatus:'COMPLETED',
  capacityStatus:'NOT_APPLICABLE',actualCostCents:1000,completedAt:Timestamp.fromMillis(Date.now()-completedDaysAgo*DAY),costShares});
}

test('a rider marks only their own share Paid, and Paid cannot be undone',async()=>{
 await users('alice','bob');await completedRide('r');
 const paid=shares({alice:500,bob:500});paid.bob.paid=true;
 const othersPaid=shares({alice:500,bob:500});othersPaid.alice.paid=true;
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/r'),{costShares:othersPaid}));
 await assertSucceeds(updateDoc(doc(dbFor('bob'),'rides/r'),{costShares:paid}));
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/r'),{costShares:shares({alice:500,bob:500})}));
});

test('the organizer flags an unpaid share only after seven days, never a Paid one',async()=>{
 await users('alice','bob');await completedRide('recent');
 const flagged=shares({alice:500,bob:500});flagged.bob.flagged=true;
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/recent'),{costShares:flagged}));
 await completedRide('old',{completedDaysAgo:8});
 await assertFails(updateDoc(doc(dbFor('bob'),'rides/old'),{costShares:flagged}));
 await assertSucceeds(updateDoc(doc(dbFor('alice'),'rides/old'),{costShares:flagged}));
 await completedRide('paid',{completedDaysAgo:8,bobPaid:true});
 const flagPaid=shares({alice:500,bob:500});flagPaid.bob.paid=true;flagPaid.bob.flagged=true;
 await assertFails(updateDoc(doc(dbFor('alice'),'rides/paid'),{costShares:flagPaid}));
});

test('a flag shows on the public profile, and paying removes it',async()=>{
 await users('alice','bob');await completedRide('r',{completedDaysAgo:8});
 const alice=dbFor('alice'),flagged=shares({alice:500,bob:500});flagged.bob.flagged=true;
 // Adding the ride to the profile without flagging the share is denied.
 await assertFails(updateDoc(doc(alice,'publicProfiles/bob'),{overdueRideIds:['r']}));
 const flag=writeBatch(alice);
 flag.update(doc(alice,'rides/r'),{costShares:flagged});
 flag.update(doc(alice,'publicProfiles/bob'),{overdueRideIds:['r']});
 await assertSucceeds(flag.commit());
 // Bob cannot simply clear the flag; paying clears it.
 const bob=dbFor('bob');
 await assertFails(updateDoc(doc(bob,'publicProfiles/bob'),{overdueRideIds:[]}));
 const paid=shares({alice:500,bob:500});paid.bob.paid=true;
 const pay=writeBatch(bob);
 pay.update(doc(bob,'rides/r'),{costShares:paid});
 pay.update(doc(bob,'publicProfiles/bob'),{overdueRideIds:[]});
 await assertSucceeds(pay.commit());
});
