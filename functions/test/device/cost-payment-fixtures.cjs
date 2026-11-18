'use strict';

const {randomUUID} = require('node:crypto');
const {getAuth} = require('firebase-admin/auth');
const {Timestamp} = require('firebase-admin/firestore');

async function seedCostPaymentFixture(app, db, scenario) {
  const runId = randomUUID();
  const now = new Date();
  const email = `cost-${runId}@usc.edu`;
  const {uid} = await getAuth(app).createUser({email, password: 'LocalTest123!'});
  const owner = {userId: uid, displayName: 'Cost Organizer'};
  const otherRiders = ['Cost Alice', 'Cost Bob', 'Cost Carol'].map((displayName, index) => ({
    userId: `cost-${runId}-${index}`, displayName
  }));

  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), {
    uid, fullName: owner.displayName, nickname: owner.displayName, uscEmail: email,
    uscId: '8000000021', graduationYear: now.getUTCFullYear() + 1
  });
  batch.set(db.doc(`publicProfiles/${uid}`), {
    fullName: owner.displayName, displayName: owner.displayName
  });

  const departedAt = new Date(now.getTime() - 3_600_000);
  function addRide(label, riders, departure, amounts) {
    const ref = db.collection('rides').doc();
    const completed = amounts !== undefined;
    const data = {
      title: `Device test costs ${label}`,
      origin: {address: 'University of Southern California', latitude: 34.0224, longitude: -118.2851},
      destination: {address: 'Los Angeles International Airport', latitude: 33.9416, longitude: -118.4085},
      organizerId: riders[0].userId,
      participantIds: riders.map(rider => rider.userId),
      participants: riders.map(rider => ({
        ...rider, acceptedAt: Timestamp.fromMillis(departure.getTime() - 3_600_000)
      })),
      departureTime: Timestamp.fromDate(departure),
      expectedCostCents: 4000,
      maxRiders: riders.length,
      activityStatus: completed ? 'COMPLETED' : 'ON_TIME',
      capacityStatus: completed ? 'NOT_APPLICABLE' : 'FULL'
    };
    if (completed) {
      data.actualCostCents = amounts.reduce((total, amount) => total + amount, 0);
      data.completedAt = Timestamp.now();
      data.costShares = Object.fromEntries(riders.map(({userId}, index) => [
        userId, {riderId: userId, amountCents: amounts[index], paid: false, flagged: false}
      ]));
    }
    batch.set(ref, data);
    return ref.id;
  }

  let rideId;
  switch (scenario) {
    case 'bb21':
      rideId = addRide('even split', [owner, ...otherRiders], departedAt);
      break;
    case 'bb22':
      rideId = addRide('remainder', [owner, ...otherRiders.slice(0, 2)], departedAt);
      break;
    case 'bb23':
      rideId = addRide('negative total', [owner, otherRiders[0]], departedAt);
      break;
    case 'bb24':
      rideId = addRide('payment', [owner, otherRiders[0]], departedAt, [500, 500]);
      break;
    case 'bb25': {
      // UTC noon on day one falls inside the same calendar month in Los Angeles.
      const [year, month] = new Intl.DateTimeFormat('sv-SE', {
        timeZone: 'America/Los_Angeles', year: 'numeric', month: '2-digit'
      }).format(now).split('-').map(Number);
      const thisMonth = new Date(Date.UTC(year, month - 1, 1, 12));
      const lastMonth = new Date(Date.UTC(year, month - 2, 1, 12));
      rideId = addRide('this month first', [owner, otherRiders[0]], thisMonth, [1000, 300]);
      addRide('this month second', [owner, otherRiders[0]], thisMonth, [500, 400]);
      addRide('last month', [owner, otherRiders[0]], lastMonth, [700, 200]);
      break;
    }
    default:
      throw new Error('Unknown cost and payment scenario');
  }
  await batch.commit();
  return {email, rideId};
}

module.exports = {seedCostPaymentFixture};
