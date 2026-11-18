'use strict';
// BB06–BB10 runner: seeds isolated local fixtures and runs RideManagementBlackBoxTest.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore,Timestamp}=require('firebase-admin/firestore');
if(!process.env.FIRESTORE_EMULATOR_HOST||!process.env.FIREBASE_AUTH_EMULATOR_HOST||process.env.GCLOUD_PROJECT!=='demo-trojan-device')
 throw new Error('Only isolated demo-trojan-device emulators are supported.');
const root=process.env.TROJAN_DEVICE_BUILD;
if(!root)throw new Error('Use npm run test:ride:device.');
const project=JSON.parse(fs.readFileSync(path.join(root,'app/google-services.json'))).project_info.project_id;
if(project!=='demo-trojan-device')throw new Error('Refusing a live-project Android build.');
const app=initializeApp({projectId:project}),db=getFirestore(),auth=getAuth();
const sdk=process.env.ANDROID_HOME||path.join(os.homedir(),'Library/Android/sdk');
const adb=process.env.ANDROID_ADB||path.join(sdk,'platform-tools/adb');
const device=process.env.ANDROID_SERIAL||'emulator-5554';
const HOUR=3600000,MINUTE=60000;
const USC={address:'University of Southern California, Los Angeles',latitude:34.0224,longitude:-118.2851};
const LAX={address:'Los Angeles International Airport, Los Angeles',latitude:33.9416,longitude:-118.4085};
let sequence=0;

/** Tomorrow at 10:00 in Los Angeles, as epoch milliseconds. */
function tomorrowTenAmLosAngeles() {
 const parts=Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:'America/Los_Angeles',year:'numeric',month:'2-digit',day:'2-digit'})
  .formatToParts(new Date(Date.now()+24*HOUR)).map(p=>[p.type,p.value]));
 // Find the UTC instant that shows 10:00 in Los Angeles on that date (PST or PDT).
 for(const offset of [8,7]){
  const t=Date.UTC(+parts.year,+parts.month-1,+parts.day,10+offset);
  const hour=new Intl.DateTimeFormat('en-US',{timeZone:'America/Los_Angeles',hour:'numeric',hourCycle:'h23'}).format(new Date(t));
  if(+hour===10)return t;
 }
 throw new Error('Could not compute 10:00 in Los Angeles');
}

function rideDoc(title,ownerId,ownerName,departureMillis) {
 const now=Timestamp.now(),people=[{userId:ownerId,displayName:ownerName,acceptedAt:now}];
 return {title,origin:USC,destination:LAX,departureTime:Timestamp.fromMillis(departureMillis),delayedDepartureTime:null,
  maxRiders:4,expectedCostCents:4000,note:'Isolated Espresso fixture',organizerId:ownerId,participants:people,
  participantIds:[ownerId],activityStatus:'ON_TIME',capacityStatus:'OPEN',actualCostCents:null,costShares:null,completedAt:null};
}

async function seed(scenario) {
 if(!Number.isInteger(scenario)||scenario<6||scenario>10)throw new Error('Unknown scenario');
 const suffix=Date.now().toString(36)+'-'+(++sequence);
 const email=`organizer-${suffix}@usc.edu`,password='LocalTest123!',name=`Organizer BB${String(scenario).padStart(2,'0')}`;
 const user=await auth.createUser({email,password});
 const uscId=String(7000000000+Math.floor(Math.random()*999999999)).slice(0,10);
 const batch=db.batch();
 batch.set(db.doc('users/'+user.uid),{uid:user.uid,nickname:name,fullName:name,uscEmail:email,uscId,graduationYear:2028});
 batch.set(db.doc('publicProfiles/'+user.uid),{displayName:name,fullName:name,overdueRideIds:[],sharedRideIds:[]});
 const fixture={email,password,uid:user.uid,name,suffix};
 if(scenario===9){
  const id=`ride-bb09-${suffix}`,departure=tomorrowTenAmLosAngeles();
  batch.set(db.doc('rides/'+id),rideDoc(`BB09 delay ${suffix}`,user.uid,name,departure));
  Object.assign(fixture,{rideId:id,departureMillis:departure});
 }
 if(scenario===10){
  // Just outside and just inside the three-hour cutoff, allowing for the time the UI takes.
  const allowed=`ride-bb10-allowed-${suffix}`,blocked=`ride-bb10-blocked-${suffix}`;
  batch.set(db.doc('rides/'+allowed),rideDoc(`BB10 allowed ${suffix}`,user.uid,name,Date.now()+3*HOUR+5*MINUTE));
  batch.set(db.doc('rides/'+blocked),rideDoc(`BB10 blocked ${suffix}`,user.uid,name,Date.now()+3*HOUR-MINUTE));
  Object.assign(fixture,{allowedRideId:allowed,blockedRideId:blocked});
 }
 await batch.commit();
 return fixture;
}

const server=http.createServer(async(req,res)=>{
 try {
  if(req.method!=='POST'||req.url!=='/seed'){res.writeHead(404).end();return;}
  let body='';for await(const part of req){body+=part;if(body.length>1024)throw new Error('Oversized fixture request');}
  const fixture=await seed(JSON.parse(body).scenario);
  res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify(fixture));
 } catch(error){console.error('Ride fixture:',error.message);res.writeHead(400).end('Fixture failed');}
});
function adbSync(args){const r=spawnSync(adb,['-s',device,...args],{encoding:'utf8'});process.stdout.write(r.stdout||'');if(r.status!==0)throw new Error(r.stderr||'Android setup failed');}

(async()=>{
 await new Promise(resolve=>server.listen(8767,'127.0.0.1',resolve));
 adbSync(['install','-r',path.join(root,'app/build/outputs/apk/debug/app-debug.apk')]);
 adbSync(['install','-r',path.join(root,'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]);
 // Clear only the isolated test app; the normal app's accounts and data stay intact.
 adbSync(['shell','pm','clear','com.example.trojanrideshare.integrationtest']);
 const child=spawn(adb,['-s',device,'shell','am','instrument','-w','-e','class',
  'com.example.trojanrideshare.RideManagementBlackBoxTest',
  'com.example.trojanrideshare.integrationtest.test/androidx.test.runner.AndroidJUnitRunner']);
 let output='';child.stdout.on('data',d=>{output+=d;process.stdout.write(d);});child.stderr.pipe(process.stderr);
 const timer=setTimeout(()=>child.kill('SIGTERM'),900000);
 let code;try{code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('close',resolve);});}finally{clearTimeout(timer);}
 const report=path.resolve(__dirname,'../../../app/build/reports/ride-management');fs.mkdirSync(report,{recursive:true});
 fs.writeFileSync(path.join(report,'espresso.txt'),output);
 if(code!==0||!/OK \(5 tests\)/.test(output)||/FAILURES!!!|AssumptionViolated|INSTRUMENTATION_FAILED/.test(output))
  throw new Error('Ride management Espresso suite did not pass all five tests.');
 console.log('All five ride management Espresso tests passed. Report: '+path.join(report,'espresso.txt'));
})().catch(e=>{console.error(e.message);process.exitCode=1;}).finally(async()=>{await new Promise(resolve=>server.close(resolve));await deleteApp(app);});
