'use strict';
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
const {createHash} = require('node:crypto');
const {transition, MembershipError} = require('./membership');
initializeApp();
const db = getFirestore();
const requestId = (rideId, uid) => createHash('sha256').update(JSON.stringify([rideId, uid])).digest('hex');
const validId = id => typeof id === 'string' && id.length > 0 && id.length <= 1500 && !id.includes('/');

exports.membership = onCall({region: 'us-central1'}, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please log in first.');
  const {action, rideId, targetId, message = ''} = request.data || {};
  const uid = request.auth.uid;
  if (!validId(rideId) || !['request', 'cancel', 'accept', 'reject', 'leave', 'cancelRide'].includes(action)) {
    throw new HttpsError('invalid-argument', 'Choose a ride and a valid action.');
  }
  if (['accept', 'reject'].includes(action) && !validId(targetId)) {
    throw new HttpsError('invalid-argument', 'Choose a request.');
  }
  try {
    await db.runTransaction(async transaction => {
      const rideRef = db.collection('rides').doc(rideId);
      const rideSnapshot = await transaction.get(rideRef);
      if (!rideSnapshot.exists) throw new MembershipError('not-found', 'Ride not found.');
      // Every operation reads and writes this ride, serializing submissions, approvals,
      // cancellations and departures. A retry rereads all rules and server time.
      const ride = {...rideSnapshot.data(), id: rideId};
      const collection = db.collection('joinRequests');
      const ownId = requestId(rideId, uid);
      const ownSnapshot = await transaction.get(collection.doc(ownId));
      const pending = await transaction.get(collection.where('rideId', '==', rideId).where('status', '==', 'PENDING'));
      const requests = new Map(pending.docs.map(doc => [doc.id, {...doc.data(), id: doc.id}]));
      if (ownSnapshot.exists) requests.set(ownId, {...ownSnapshot.data(), id: ownId});
      if (targetId && targetId !== ownId && !requests.has(targetId)) {
        const targetSnapshot = await transaction.get(collection.doc(targetId));
        if (targetSnapshot.exists && targetSnapshot.get('rideId') === rideId) {
          requests.set(targetId, {...targetSnapshot.data(), id: targetId});
        }
      }
      let displayName = request.auth.token.name || 'USC student';
      if (action === 'request') {
        const profile = await transaction.get(db.collection('users').doc(uid));
        displayName = profile.get('nickname') || profile.get('fullName') || displayName;
      }
      const result = transition({ride, requests: [...requests.values()], uid, action,
        targetId: action === 'request' ? ownId : targetId, message, displayName, now: Timestamp.now()});
      // Patch only membership-owned fields. Never overwrite route, time, notes or costs.
      transaction.update(rideRef, {
        participants: result.ride.participants,
        participantIds: result.ride.participants.map(p => p.userId),
        organizerId: result.ride.organizerId,
        activityStatus: result.ride.activityStatus,
        capacityStatus: result.ride.capacityStatus,
      });
      for (const item of result.changed) {
        const {id, ...data} = item;
        transaction.set(collection.doc(id), data);
      }
    });
    return {ok: true};
  } catch (error) {
    if (error instanceof MembershipError) throw new HttpsError(error.code, error.message);
    console.error('Membership transaction failed', error);
    throw new HttpsError('internal', 'The change could not be saved. Please refresh and try again.');
  }
});

const {costTransition} = require('./costs');
exports.rideCosts = onCall({region:'us-central1'}, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please log in first.');
  const {action,rideId,totalCents,customShares,riderId}=request.data || {};
  if (!validId(rideId) || !['complete','paid','flag'].includes(action)) throw new HttpsError('invalid-argument','Choose a ride and valid action.');
  try {
    await db.runTransaction(async tx => {
      const ref=db.collection('rides').doc(rideId), snapshot=await tx.get(ref);
      if (!snapshot.exists) throw new MembershipError('not-found','Ride not found.');
      const patch=costTransition({ride:snapshot.data(),action,uid:request.auth.uid,totalCents,customShares,riderId,now:Timestamp.now()});
      // Pending requests are closed atomically with completion, just like cancellation.
      const pending=action==='complete' ? await tx.get(db.collection('joinRequests').where('rideId','==',rideId).where('status','==','PENDING')) : null;
      let publicRef, overdue;
      if (action === 'paid' || action === 'flag') {
        publicRef=db.collection('publicProfiles').doc(riderId);
        const profile=await tx.get(publicRef);
        const existing=profile.exists && Array.isArray(profile.get('overdueRideIds')) ? profile.get('overdueRideIds') : [];
        overdue=existing.filter(id => id !== rideId);
        if (patch.costShares[riderId].flagged) overdue.push(rideId);
      }
      tx.update(ref,patch);
      if (publicRef) tx.set(publicRef,{overdueRideIds:overdue},{merge:true});
      if(pending) for(const doc of pending.docs) tx.update(doc.ref,{status:'REJECTED',updatedAt:patch.completedAt});
    });
    return {ok:true};
  } catch(error) {
    if(error instanceof MembershipError) throw new HttpsError(error.code,error.message);
    console.error('Cost transaction failed',error);
    throw new HttpsError('internal','The cost update could not be saved. Please try again.');
  }
});
