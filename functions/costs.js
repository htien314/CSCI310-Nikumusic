'use strict';
const {MembershipError} = require('./membership');
const fail = (message, code = 'failed-precondition') => { throw new MembershipError(code, message); };
const cents = value => Number.isSafeInteger(value) && value >= 0;
/** Trusted cost rules. Inputs are a current transactional ride and authenticated actor. */
function costTransition({ride, action, uid, totalCents, customShares, riderId, now}) {
  if (!uid) fail('Please log in first.', 'unauthenticated');
  if (!ride) fail('Ride not found.', 'not-found');
  if (action === 'complete') {
    if (ride.organizerId !== uid) fail('Only the organizer can complete this ride.', 'permission-denied');
    if (!['ON_TIME', 'DELAYED'].includes(ride.activityStatus)) fail('This ride is already cancelled or completed.');
    const departure = ride.delayedDepartureTime || ride.departureTime;
    if (!departure || now.toMillis() < departure.toMillis()) fail('The ride cannot be completed before departure.');
    if (!cents(totalCents) || totalCents === 0) fail('Enter a positive total in whole cents.', 'invalid-argument');
    const ids = (ride.participants || []).map(p => p.userId);
    if (!ids.length || ids.some(id => typeof id !== 'string' || !id) || new Set(ids).size !== ids.length || !ids.includes(uid)) fail('Ride participants are invalid.');
    let amounts;
    if (customShares != null) {
      if (typeof customShares !== 'object' || Array.isArray(customShares)
          || Object.keys(customShares).length !== ids.length || ids.some(id => !Object.hasOwn(customShares, id) || !cents(customShares[id]))
          || ids.reduce((sum, id) => sum + customShares[id], 0) !== totalCents) fail('Provide a nonnegative share for every participant totaling the actual cost.', 'invalid-argument');
      amounts = customShares;
    } else {
      const base = Math.floor(totalCents / ids.length), remainder = totalCents % ids.length;
      amounts = Object.fromEntries(ids.map((id,i) => [id, base + (i < remainder ? 1 : 0)]));
    }
    return {actualCostCents: totalCents, completedAt: now, activityStatus: 'COMPLETED', capacityStatus: 'NOT_APPLICABLE',
      participantIds: ids, costShares: Object.fromEntries(ids.map(id => [id, {riderId:id, amountCents:amounts[id], paid:false, flagged:false}]))};
  }
  if (ride.activityStatus !== 'COMPLETED' || !ride.completedAt || !ride.costShares) fail('Complete this ride first.');
  if (!riderId || !Object.hasOwn(ride.costShares, riderId)) fail('This rider has no cost share.', 'not-found');
  const shares = Object.fromEntries(Object.entries(ride.costShares).map(([id,s])=>[id,{...s}]));
  const share = shares[riderId];
  if (action === 'paid') {
    if (riderId !== uid) fail('You may mark only your own share Paid.', 'permission-denied');
    if (share.paid) fail('This share is already Paid.');
    share.paid = true; share.flagged = false;
  } else if (action === 'flag') {
    if (ride.organizerId !== uid) fail('Only the organizer may flag unpaid riders.', 'permission-denied');
    if (share.paid) fail('Paid riders cannot be flagged.');
    if (now.toMillis() - ride.completedAt.toMillis() < 7*86400000) fail('Flagging is available seven days after cost recording.');
    share.flagged = true;
  } else fail('Unknown cost action.', 'invalid-argument');
  return {costShares:shares};
}
module.exports = {costTransition};
