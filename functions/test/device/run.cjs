'use strict';
const http=require('node:http');
const path=require('node:path');
const os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore,Timestamp}=require('firebase-admin/firestore');
if(!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.GCLOUD_PROJECT!=='demo-trojan-device') throw new Error('Run only through the demo-trojan-device Firebase emulators.');
const app=initializeApp({projectId:'demo-trojan-device'}),db=getFirestore();
const root=process.env.TROJAN_DEVICE_BUILD;
if(!root) throw new Error('Set TROJAN_DEVICE_BUILD to the isolated demo-project Android build.');
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||path.join(os.homedir(),'Library/Android/sdk');
const adb=process.env.ANDROID_ADB||path.join(sdk,'platform-tools/adb');
const device=process.env.ANDROID_SERIAL || 'emulator-5554';
const run=args=>{const r=spawnSync(adb,['-s',device,...args],{encoding:'utf8'});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw new Error('Android command failed.');};
const server=http.createServer(async(req,res)=>{
 try {
  if(req.method!=='POST'||req.url!=='/fixture') {res.writeHead(404).end();return;}
  let body='';for await(const part of req){body+=part;if(body.length>4096)throw new Error('Request too large');}
  const {rideId,action}=JSON.parse(body);
  if(typeof rideId!=='string'||!rideId||rideId.includes('/'))throw new Error('Invalid test ride');
  const ref=db.doc(`rides/${rideId}`),ride=await ref.get();
  if(!ride.exists||!String(ride.get('title')).startsWith('Device test'))throw new Error('Only isolated test fixtures may be changed');
  if(action==='depart')await ref.update({departureTime:Timestamp.fromMillis(Date.now()-1000)});
  else if(action==='overdue')await ref.update({completedAt:Timestamp.fromMillis(Date.now()-8*86400000)});
  else throw new Error('Invalid fixture action');
  res.writeHead(200).end('ok');
 } catch(error){console.error('Fixture error:',error.message);res.writeHead(400).end('Test fixture failed');}
});
(async()=>{
 await new Promise(resolve=>server.listen(8765,'127.0.0.1',resolve));
 run(['install','-r',path.join(root,'app/build/outputs/apk/debug/app-debug.apk')]);
 run(['install','-r',path.join(root,'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]);
 run(['shell','pm','clear','com.example.trojanrideshare.integrationtest']);
 async function instrument(method, checkpoint, className = "FullAppEmulatorTest") {
  const child=spawn(adb,['-s',device,'shell','am','instrument','-w','-e','locationMode',process.env.TROJAN_TEST_LOCATIONS || 'real','-e','class',`com.example.trojanrideshare.${className}#${method}`,'com.example.trojanrideshare.integrationtest.test/androidx.test.runner.AndroidJUnitRunner']);
  let output='';child.stdout.on('data',d=>{output+=d;process.stdout.write(d);});child.stderr.pipe(process.stderr);
  const timer=setTimeout(()=>child.kill('SIGTERM'),600000);
  let code;
  try {code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('close',resolve);});}
  finally {clearTimeout(timer);}
  if(code!==0 || !/OK \(1 test\)/.test(output) || (checkpoint && !output.includes(checkpoint)))throw new Error(`Android workflow phase did not pass: ${method}`);
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
  ['LocationPickerViewTest','formResolveRejectsAnAnswerForAnEditedLocation']])
  await instrument(method,null,className);
 console.log('Full Android form/navigation workflow passed against demo emulators. Location mode: '+(process.env.TROJAN_TEST_LOCATIONS || 'real')+'.');
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(async()=>{
 server.close();await deleteApp(app);
 spawnSync(adb,['-s',device,'shell','am','start','-n','com.example.trojanrideshare/.MainActivity'],{stdio:'ignore'});
});
