'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {transition} = require('../membership');
const stamp = value => ({toMillis: () => value});
const now = stamp(1000000);
function ride(extra = {}) {
  return {id: 'ride', organizerId: 'owner', activityStatus: 'ON_TIME', capacityStatus: 'OPEN',
    maxRiders: 3, departureTime: stamp(now.toMillis() + 10800000),
    participants: [{userId: 'owner', displayName: 'Organizer', acceptedAt: stamp(1)}], ...extra};
}
function request(userId = 'rider', extra = {}) {
  return {id: userId, userId, rideId: 'ride', displayName: userId, status: 'PENDING', ...extra};
}
function run(extra = {}) {
  return transition({ride: ride(), requests: [], action: 'request', uid: 'rider', targetId: 'rider', displayName: 'Rider', now, ...extra});
}
function denied(extra, regex) { assert.throws(() => run(extra), regex); }

test('request stores identity, optional message, pending state and server timestamp', () => {
  const result = run({message: '  Hello  '});
  assert.equal(result.changed[0].message, 'Hello'); assert.equal(result.changed[0].status, 'PENDING');
  assert.equal(result.changed[0].createdAt, now); assert.equal(result.ride.participants.length, 1);
});
for (const state of ['COMPLETED', 'CANCELLED']) {
  test(`request cannot join ${state}`, () => denied({ride: ride({activityStatus: state})}, /cancelled or completed/));
  test(`cannot leave ${state}`, () => denied({ride: ride({activityStatus: state}), action: 'leave', uid: 'owner'}, /cancelled or completed/));
}
test('requires authentication', () => denied({uid: null}, /log in/));
test('missing ride', () => denied({ride: null}, /not found/));
test('rejects own ride', () => denied({uid: 'owner'}, /own ride/));
test('rejects current member', () => denied({ride: ride({participants: [{userId:'rider'}]})}, /already participating/));
test('rejects full ride', () => denied({ride: ride({maxRiders: 2, participants: [{userId:'owner'}, {userId:'other'}]})}, /full/));
test('rejects duplicate pending request', () => denied({requests: [request()]}, /already have/));
test('prior rejection permanently prevents resubmission even if a seat reopens', () => denied({requests: [request('rider', {status:'REJECTED'})]}, /previously rejected/));
test('cancel then resubmit works without adding a participant', () => {
  const cancelled = run({action:'cancel', requests:[request()]});
  assert.equal(cancelled.changed[0].status, 'CANCELLED');
  assert.equal(run({requests:cancelled.changed}).changed[0].status, 'PENDING');
});
test('cannot cancel accepted request', () => denied({action:'cancel', requests:[request('rider', {status:'ACCEPTED'})]}, /no pending/));
test('message length and type validation', () => {
  denied({message: 'x'.repeat(1001)}, /1,000/); denied({message: 17}, /1,000/);
  assert.equal(run({message:'x'.repeat(1000)}).changed[0].message.length, 1000);
});
for (const action of ['accept', 'reject']) {
  test(`${action} requires current organizer`, () => denied({action, requests:[request()]}, /current organizer/));
  test(`${action} requires pending request`, () => denied({action, uid:'owner', requests:[request('rider', {status:'ACCEPTED'})]}, /no longer pending/));
}
test('approval appends one participant with acceptance time', () => {
  const result = run({action:'accept', uid:'owner', requests:[request()]});
  assert.equal(result.ride.participants.length, 2); assert.equal(result.ride.participants[1].acceptedAt, now);
  assert.equal(result.changed[0].status, 'ACCEPTED');
});
test('duplicate approval does not add a participant', () => {
  const first = run({action:'accept', uid:'owner', requests:[request()]});
  denied({ride:first.ride, action:'accept', uid:'owner', requests:first.changed}, /no longer pending/);
});
test('reject does not add a participant', () => {
  const result = run({action:'reject', uid:'owner', requests:[request()]});
  assert.equal(result.ride.participants.length, 1); assert.equal(result.changed[0].status, 'REJECTED');
});
test('last-seat acceptance rejects EVERY other pending request atomically', () => {
  const result = run({ride:ride({maxRiders:2}), action:'accept', uid:'owner', requests:[request(), request('other'), request('third')]});
  assert.equal(result.ride.capacityStatus, 'FULL'); assert.equal(result.changed.length, 3);
  assert.equal(result.changed.filter(r => r.status === 'REJECTED').length, 2);
});
test('stale last-seat approval cannot overbook after another acceptance', () => {
  const first = run({ride:ride({maxRiders:2}), action:'accept', uid:'owner', requests:[request(), request('other')]});
  denied({ride:first.ride, action:'accept', uid:'owner', targetId:'other', requests:first.changed}, /no longer pending/);
});
test('exactly three hours allows leave, one millisecond less forbids leave', () => {
  assert.equal(run({action:'leave', uid:'owner'}).ride.activityStatus, 'CANCELLED');
  denied({action:'leave', uid:'owner', now:stamp(now.toMillis()+1)}, /three hours/);
});
test('delayed departure does not extend original cutoff', () => denied({action:'leave', uid:'owner',
  ride:ride({activityStatus:'DELAYED', departureTime:stamp(now.toMillis()+1), delayedDepartureTime:stamp(now.toMillis()+99999999)})}, /three hours/));
test('rider leaves full ride and capacity reopens, preserving delayed status', () => {
  const result = run({action:'leave', ride:ride({maxRiders:2, activityStatus:'DELAYED', participants:[{userId:'owner'}, {userId:'rider'}]})});
  assert.equal(result.ride.capacityStatus, 'OPEN'); assert.equal(result.ride.activityStatus, 'DELAYED');
});
test('organizer transfers to latest accepted participant, with array order as tie-break', () => {
  const result = run({action:'leave', uid:'owner', ride:ride({participants:[
    {userId:'owner', acceptedAt:stamp(1)}, {userId:'a', acceptedAt:stamp(200)},
    {userId:'b', acceptedAt:stamp(100)}, {userId:'c', acceptedAt:stamp(200)}], maxRiders:6})});
  assert.equal(result.ride.organizerId, 'c'); assert.equal(result.ride.activityStatus, 'ON_TIME');
});
test('last organizer departure cancels and rejects pending requests', () => {
  const result = run({action:'leave', uid:'owner', requests:[request(), request('other')]});
  assert.equal(result.ride.activityStatus, 'CANCELLED'); assert.equal(result.ride.capacityStatus, 'NOT_APPLICABLE');
  assert.equal(result.ride.participants.length, 0); assert.ok(result.changed.every(r => r.status === 'REJECTED'));
});
test('nonmember cannot leave', () => denied({action:'leave'}, /not a participant/));
test('failures preserve input snapshots', () => {
  const original = ride(); const pending = [request()];
  denied({ride:original, requests:pending, action:'accept'}, /current organizer/);
  assert.equal(original.participants.length, 1); assert.equal(pending[0].status, 'PENDING');
});
