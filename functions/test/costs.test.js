 'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {costTransition}=require('../costs');
const ts=n=>({toMillis:()=>n});
const base={organizerId:'owner',activityStatus:'ON_TIME',departureTime:ts(100),participants:[{userId:'owner'},{userId:'alice'},{userId:'bob'}]};
const run=extra=>costTransition({ride:base,action:'complete',uid:'owner',now:ts(101),totalCents:1000,...extra});
test('equal split preserves every cent and records server completion time',()=>{
 const result=run();assert.equal(result.actualCostCents,1000);
 assert.deepEqual(Object.values(result.costShares).map(s=>s.amountCents),[334,333,333]);
 assert.deepEqual(result.participantIds,['owner','alice','bob']);assert.equal(result.completedAt.toMillis(),101);
 assert.equal(base.activityStatus,'ON_TIME');
});
test('custom shares must exactly cover all participants and total',()=>{
 assert.equal(run({customShares:{owner:0,alice:400,bob:600}}).costShares.owner.amountCents,0);
 for(const customShares of [{owner:500,alice:500},{owner:0,alice:400,bob:500},{owner:0,alice:400,bob:600,stranger:0},{owner:-1,alice:401,bob:600}])
  assert.throws(()=>run({customShares}),/share/);
});
test('rejects unauthenticated, nonorganizer, early or repeated completion',()=>{
 assert.throws(()=>run({uid:null}),/log in/);assert.throws(()=>run({uid:'alice'}),/organizer/);
 assert.throws(()=>run({now:ts(99)}),/before departure/);
 assert.throws(()=>run({ride:{...base,activityStatus:'COMPLETED'}}),/already/);
 assert.throws(()=>run({ride:{...base,delayedDepartureTime:ts(200)}}),/before departure/);
});
test('rejects invalid cents and duplicate participants',()=>{
 for(const totalCents of [0,-1,1.2,NaN,Infinity,Number.MAX_SAFE_INTEGER+1]) assert.throws(()=>run({totalCents}),/positive total/);
 assert.throws(()=>run({ride:{...base,participants:[{userId:'owner'},{userId:'owner'}]}}),/participants/);
});
test('flagging boundary uses server time and paying clears the flag',()=>{
 const completed={...base,...run()};
 const before={ride:completed,action:'flag',uid:'owner',riderId:'alice',now:ts(101+7*86400000-1)};
 assert.throws(()=>run(before),/seven days/);
 const flagged=run({...before,now:ts(101+7*86400000)});
 assert.equal(flagged.costShares.alice.flagged,true);
 const paid=run({ride:{...completed,...flagged},action:'paid',uid:'alice',riderId:'alice'});
 assert.equal(paid.costShares.alice.paid,true);assert.equal(paid.costShares.alice.flagged,false);
 assert.equal(completed.costShares.alice.paid,false);
});
