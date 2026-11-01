'use strict';

class MembershipError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
const fail = (message, code = 'failed-precondition') => { throw new MembershipError(code, message); };
const isFinal = ride => ['COMPLETED', 'CANCELLED'].includes(ride.activityStatus);
const member = (ride, uid) => ride.participants.some(p => p.userId === uid);
const millis = timestamp => timestamp.toMillis();

/** Pure business rules. The caller supplies a fresh transaction snapshot and trusted server time. */
function transition({ride, requests, action, uid, targetId, message = '', displayName, now}) {
  if (!uid) fail('Please log in first.', 'unauthenticated');
  if (!ride) fail('Ride not found.', 'not-found');
  if (!Array.isArray(ride.participants) || !Number.isInteger(ride.maxRiders)
      || ride.maxRiders < 2 || ride.maxRiders > 6 || !['ON_TIME', 'DELAYED', 'COMPLETED', 'CANCELLED'].includes(ride.activityStatus)) {
    fail('This ride has invalid membership data.');
  }
  // Work on copies: failed operations must not mutate the caller's snapshot.
  ride = {...ride, participants: ride.participants.map(p => ({...p}))};
  requests = requests.map(r => ({...r}));
  const changed = [];
  const own = requests.find(r => r.userId === uid);
  const target = requests.find(r => r.id === targetId);
  const setStatus = (request, status) => {
    request.status = status; request.updatedAt = now; changed.push(request);
  };
  const requireActive = () => { if (isFinal(ride)) fail('This ride is cancelled or completed.'); };
  const requireOrganizer = () => { if (ride.organizerId !== uid) fail('Only the current organizer can do this.', 'permission-denied'); };

  switch (action) {
    case 'request':
      requireActive();
      if (ride.organizerId === uid) fail('You cannot request your own ride.');
      if (member(ride, uid)) fail('You are already participating in this ride.');
      if (ride.participants.length >= ride.maxRiders) fail('This ride is full.');
      if (own?.status === 'REJECTED') fail('You were previously rejected from this ride.');
      if (own?.status === 'PENDING') fail('You already have a pending request.');
      if (typeof message !== 'string' || message.length > 1000) fail('Use a message of at most 1,000 characters.', 'invalid-argument');
      // A cancelled pending request may be submitted again; a rejected request may not.
      changed.push({id: targetId, rideId: ride.id, userId: uid, displayName,
        message: message.trim(), status: 'PENDING', createdAt: now, updatedAt: now});
      break;
    case 'cancel':
      if (!own || own.status !== 'PENDING') fail('There is no pending request to cancel.');
      setStatus(own, 'CANCELLED');
      break;
    case 'accept':
    case 'reject':
      requireOrganizer(); requireActive();
      if (!target || target.status !== 'PENDING') fail('This request is no longer pending.');
      if (action === 'reject') { setStatus(target, 'REJECTED'); break; }
      if (ride.participants.length >= ride.maxRiders) fail('This ride is already full.');
      if (member(ride, target.userId)) fail('This rider is already participating.');
      // Array append is the definitive acceptance order, including equal timestamps.
      ride.participants.push({userId: target.userId, displayName: target.displayName, acceptedAt: now});
      setStatus(target, 'ACCEPTED');
      break;
    case 'leave': {
      requireActive();
      if (!member(ride, uid)) fail('You are not a participant in this ride.');
      if (!ride.departureTime || millis(ride.departureTime) - millis(now) < 3 * 60 * 60 * 1000) {
        fail('You can leave only at least three hours before the original departure.');
      }
      ride.participants = ride.participants.filter(p => p.userId !== uid);
      if (ride.organizerId === uid) {
        if (!ride.participants.length) {
          ride.activityStatus = 'CANCELLED';
        } else {
          // Latest acceptedAt wins; array order breaks timestamp ties deterministically.
          let successor = ride.participants[0];
          if (!successor.acceptedAt) fail('A participant is missing their acceptance time.');
          for (const participant of ride.participants) {
            if (!participant.acceptedAt) fail('A participant is missing their acceptance time.');
            if (millis(participant.acceptedAt) >= millis(successor.acceptedAt)) successor = participant;
          }
          ride.organizerId = successor.userId;
        }
      }
      break;
    }
    default: fail('Unknown membership action.', 'invalid-argument');
  }
  ride.capacityStatus = isFinal(ride) ? 'NOT_APPLICABLE'
    : ride.participants.length >= ride.maxRiders ? 'FULL' : 'OPEN';
  // The full transition rejects every pending request in the SAME transaction.
  if (ride.capacityStatus === 'FULL' || ride.activityStatus === 'CANCELLED') {
    for (const request of requests) if (request.status === 'PENDING') setStatus(request, 'REJECTED');
  }
  return {ride, changed};
}
module.exports = {transition, MembershipError};
