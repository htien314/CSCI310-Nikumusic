'use strict';
const http=require('node:http');
const path=require('node:path');
const os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore,Timestamp}=require('firebase-admin/firestore');
const {seedCostPaymentFixture}=require('./cost-payment-fixtures.cjs');
if(!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.GCLOUD_PROJECT!=='demo-trojan-device') throw new Error('Run only through the demo-trojan-device Firebase emulators.');
const app=initializeApp({projectId:'demo-trojan-device'}),db=getFirestore();
const costPaymentsOnly = process.env.TROJAN_DEVICE_SUITE === 'cost-payments';
const costPaymentTests = [
  ['bb21_completesFortyDollarRideWithFourEqualShares', 'BB21 passed'],
  ['bb22_displaysRemainderOnFirstRidersShare', 'BB22 passed'],
  ['bb23_rejectsNegativeFinalCost', 'BB23 passed'],
  ['bb24_recordsOwnPaymentAndShowsItAfterReopening', 'BB24 passed'],
  ['bb25_showsAssignedSharesForSelectedMonth', 'BB25 passed']
];
const root=process.env.TROJAN_DEVICE_BUILD;
if(!root) throw new Error('Set TROJAN_DEVICE_BUILD to the isolated demo-project Android build.');
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||path.join(os.homedir(),'Library/Android/sdk');
const adb=process.env.ANDROID_ADB||path.join(sdk,'platform-tools/adb');
const device=process.env.ANDROID_SERIAL || 'emulator-5554';
const run=args=>{const r=spawnSync(adb,['-s',device,...args],{encoding:'utf8'});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw new Error('Android command failed.');return r.stdout||'';};
const server=http.createServer(async(req,res)=>{
 try {
  if(req.method!=='POST'||!['/fixture','/cost-payment-fixture','/search-fixture'].includes(req.url)) {res.writeHead(404).end();return;}
  let body='';for await(const part of req){body+=part;if(body.length>(req.url==='/search-fixture'?16384:4096))throw new Error('Request too large');}
  const data = JSON.parse(body);
  if (req.url === '/cost-payment-fixture') {
   if (!costPaymentsOnly || !/^bb2[1-5]$/.test(data.scenario)) throw new Error('Invalid cost fixture request');
   const fixture = await seedCostPaymentFixture(app, db, data.scenario);
   res.writeHead(200, {'Content-Type': 'application/json'}).end(JSON.stringify(fixture));
   return;
  }
  if (req.url === '/search-fixture') {
   await searchFixture(data);
   res.writeHead(200).end('ok');
   return;
  }
  const {rideId,action}=data;
  if(typeof rideId!=='string'||!rideId||rideId.includes('/'))throw new Error('Invalid test ride');
  const ref=db.doc(`rides/${rideId}`),ride=await ref.get();
  if(!ride.exists||!String(ride.get('title')).startsWith('Device test'))throw new Error('Only isolated test fixtures may be changed');
  if(action==='depart')await ref.update({departureTime:Timestamp.fromMillis(Date.now()-1000)});
  else if(action==='overdue')await ref.update({completedAt:Timestamp.fromMillis(Date.now()-8*86400000)});
  else throw new Error('Invalid fixture action');
  res.writeHead(200).end('ok');
 } catch(error){console.error('Fixture error:',error.message);res.writeHead(400).end('Test fixture failed');}
});
// Search black-box tests (SearchLocationBlackBoxTest): rides posted by another user, written with
// the admin SDK so each test starts from known data. Only bbsearch-* rides are touched.
const SEARCH_ORGANIZER='bbsearch-organizer';
async function searchFixture({action,rides}){
 const existing=await db.collection('rides').where('organizerId','==',SEARCH_ORGANIZER).get();
 if(action==='reset'){await Promise.all(existing.docs.map(d=>d.ref.delete()));return;}
 if(action!=='seed'||!Array.isArray(rides)||rides.length>10)throw new Error('Invalid search fixture');
 for(const r of rides){
  if(typeof r.id!=='string'||!/^bbsearch-[a-z0-9-]+$/.test(r.id)||!String(r.title).startsWith('BB'))throw new Error('Invalid search ride');
  const maxRiders=Number(r.maxRiders),riders=Number(r.riders);
  if(!(maxRiders>=2&&maxRiders<=6&&riders>=1&&riders<=maxRiders))throw new Error('Invalid search ride capacity');
  const acceptedAt=Timestamp.fromMillis(Date.now()-86400000);
  const participants=[{userId:SEARCH_ORGANIZER,displayName:'Search Seeder',acceptedAt}];
  for(let i=1;i<riders;i++)participants.push({userId:`bbsearch-rider-${i}`,displayName:`Seeded Rider ${i}`,acceptedAt});
  await db.doc(`rides/${r.id}`).set({title:r.title,
   origin:{address:r.originAddress,latitude:r.originLat,longitude:r.originLng},
   destination:{address:r.destAddress,latitude:r.destLat,longitude:r.destLng},
   departureTime:Timestamp.fromMillis(r.departureMillis),delayedDepartureTime:null,maxRiders,expectedCostCents:4000,note:null,
   organizerId:SEARCH_ORGANIZER,participants,participantIds:participants.map(p=>p.userId),
   activityStatus:'ON_TIME',capacityStatus:riders>=maxRiders?'FULL':'OPEN',actualCostCents:null,completedAt:null,costShares:null});
 }
}
(async()=>{
 await new Promise(resolve=>server.listen(8765,'127.0.0.1',resolve));
 run(['install','-r',path.join(root,'app/build/outputs/apk/debug/app-debug.apk')]);
 run(['install','-r',path.join(root,'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]);
 run(['shell','pm','clear','com.example.trojanrideshare.integrationtest']);
 const localNetworkPermission = 'android.permission.ACCESS_LOCAL_NETWORK';
 const permissionList = run(['shell','pm','list','permissions']);
 if (permissionList.split(/\r?\n/).includes('permission:' + localNetworkPermission)) {
  run(['shell','pm','grant','com.example.trojanrideshare.integrationtest',localNetworkPermission]);
 }
 async function instrument(method, checkpoint, className = "FullAppEmulatorTest") {
  const child=spawn(adb,['-s',device,'shell','am','instrument','-w','-e','locationMode',process.env.TROJAN_TEST_LOCATIONS || 'real','-e','class',`com.example.trojanrideshare.${className}#${method}`,'com.example.trojanrideshare.integrationtest.test/androidx.test.runner.AndroidJUnitRunner']);
  let output='';child.stdout.on('data',d=>{output+=d;process.stdout.write(d);});child.stderr.pipe(process.stderr);
  const timer=setTimeout(()=>child.kill('SIGTERM'),600000);
  let code;
  try {code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('close',resolve);});}
  finally {clearTimeout(timer);}
  if(code!==0 || !/OK \(1 test\)/.test(output) || (checkpoint && !output.includes(checkpoint)))throw new Error(`Android workflow phase did not pass: ${method}`);
 }
 if (costPaymentsOnly) {
  for (const [method, checkpoint] of costPaymentTests) {
   await instrument(method, checkpoint);
  }
  console.log('All five cost and payment Espresso tests passed.');
  return;
 }
 await instrument('multipleAccountsThroughRealAndroidScreens','Cancellation closed the pending request across accounts');
 run(['shell','am','force-stop','com.example.trojanrideshare.integrationtest']);
 await instrument('accountAndRequestsSurviveProcessRestart','Sign-in and server request state survived a full app process restart');
 await instrument('customSplitUsesNamesAndValidatesEveryAmount','Custom split names, zero share, invalid amounts and exact total verified');
 for (const [className,method] of [
  ['AccountNavigationTest','profileLoginSearchProfileAlwaysShowsProfileRoot'],
  ['AccountNavigationTest','reselectingProfileFromLoginReturnsToProfile'],
  ['AccountNavigationTest','authenticationReturnRemovesLoginAndSignupFromBackStack'],
  ['AccountNavigationTest','profileReturnPreservesSearchForm'],
  ['AccountNavigationTest','childScreensOfferAnAccessibleBackButton'],
  ['LocationPickerViewTest','slowLookupCannotOverwriteEditedText'],
  ['LocationPickerViewTest','formResolveRejectsAnAnswerForAnEditedLocation'],
  ['SearchLocationBlackBoxTest','bb11_findMatchingRideAndOpenDetails'],
  ['SearchLocationBlackBoxTest','bb12_bothRouteEndpointsMustMatch'],
  ['SearchLocationBlackBoxTest','bb13_searchRadiusBoundaries'],
  ['SearchLocationBlackBoxTest','bb14_filterByTimeWindowAndOpenSeats'],
  ['SearchLocationBlackBoxTest','bb15_sortSearchResults']])
  await instrument(method,null,className);
 console.log('Full Android form/navigation workflow passed against demo emulators. Location mode: '+(process.env.TROJAN_TEST_LOCATIONS || 'real')+'.');
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(async()=>{
 server.close();await deleteApp(app);
 if (!costPaymentsOnly) spawnSync(adb,['-s',device,'shell','am','start','-n','com.example.trojanrideshare/.MainActivity'],{stdio:'ignore'});
});
