'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore,Timestamp}=require('firebase-admin/firestore');
if(!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.GCLOUD_PROJECT!=='demo-trojan-device') throw new Error('Only isolated demo-trojan-device emulators are supported.');
const root=process.env.TROJAN_DEVICE_BUILD;
if(!root) throw new Error('Use npm run test:membership:device.');
const project=JSON.parse(fs.readFileSync(path.join(root,'app/google-services.json'))).project_info.project_id;
if(project!=='demo-trojan-device')throw new Error('Refusing a live-project Android build.');
const app=initializeApp({projectId:project}),db=getFirestore(),auth=getAuth();
const sdk=process.env.ANDROID_HOME||path.join(os.homedir(),'Library/Android/sdk');
const adb=process.env.ANDROID_ADB||path.join(sdk,'platform-tools/adb');
const device=process.env.ANDROID_SERIAL||'emulator-5554';
let sequence=0;
async function seed(scenario) {
 if(!Number.isInteger(scenario)||scenario<16||scenario>20)throw new Error('Unknown scenario');
 const suffix=Date.now().toString(36)+'-'+(++sequence);
 const ownerEmail=`owner-${suffix}@usc.edu`,riderEmail=`rider-${suffix}@usc.edu`,password='LocalTest123!';
 const owner=await auth.createUser({email:ownerEmail,password}),rider=await auth.createUser({email:riderEmail,password});
 const ownerName=`Organizer ${scenario}`,riderName=`Rider ${scenario}`;
 const rideId=`membership-bb${scenario}-${suffix}`,title=`Membership BB${scenario} Leavey to LAX`;
 const now=Timestamp.now(),people=[{userId:owner.uid,displayName:ownerName,acceptedAt:now}];
 if(scenario===20)people.push({userId:'existing-member-'+suffix,displayName:'Existing rider',acceptedAt:now});
 const ride={title,origin:{address:'USC Leavey Library',latitude:34.0217,longitude:-118.2828},
 destination:{address:'Los Angeles International Airport (LAX)',latitude:33.9416,longitude:-118.4085},
 departureTime:Timestamp.fromMillis(Date.now()+86400000),delayedDepartureTime:null,maxRiders:scenario===20?2:3,
 expectedCostCents:1000,note:'Isolated Espresso fixture',organizerId:owner.uid,participants:people,
 participantIds:people.map(p=>p.userId),activityStatus:'ON_TIME',capacityStatus:scenario===20?'FULL':'OPEN',
 actualCostCents:null,costShares:null,completedAt:null};
 const batch=db.batch();
 for(const [user,name] of [[owner,ownerName],[rider,riderName]]) {
  batch.set(db.doc('users/'+user.uid),{uid:user.uid,nickname:name,fullName:name,uscEmail:user.email,uscId:'7000000000',graduationYear:2027});
  batch.set(db.doc('publicProfiles/'+user.uid),{displayName:name,fullName:name,overdueRideIds:[],sharedRideIds:[]});
 }
 batch.set(db.doc('rides/'+rideId),ride);
 if(scenario!==16)batch.set(db.doc('joinRequests/'+rideId+'_'+rider.uid),{rideId,userId:rider.uid,displayName:riderName,
 message:'Please pick me up at Leavey.',status:'PENDING',createdAt:now,updatedAt:now});
 await batch.commit();
 return {rideId,title,ownerEmail,riderEmail,password,riderId:rider.uid,riderName};
}
const server=http.createServer(async(req,res)=>{
 try {
  if(req.method!=='POST'||req.url!=='/seed'){res.writeHead(404).end();return;}
  let body='';for await(const part of req){body+=part;if(body.length>1024)throw new Error('Oversized fixture request');}
  const fixture=await seed(JSON.parse(body).scenario);
  res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify(fixture));
 } catch(error){console.error('Membership fixture:',error.message);res.writeHead(400).end('Fixture failed');}
});
function adbSync(args){const r=spawnSync(adb,['-s',device,...args],{encoding:'utf8'});process.stdout.write(r.stdout||'');if(r.status!==0)throw new Error(r.stderr||'Android setup failed');}
(async()=>{
 await new Promise(resolve=>server.listen(8766,'127.0.0.1',resolve));
 adbSync(['install','-r',path.join(root,'app/build/outputs/apk/debug/app-debug.apk')]);
 adbSync(['install','-r',path.join(root,'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]);
 // Clear only the isolated test app; normal app accounts and data stay intact.
 adbSync(['shell','pm','clear','com.example.trojanrideshare.integrationtest']);
 const child=spawn(adb,['-s',device,'shell','am','instrument','-w','-e','class',
 'com.example.trojanrideshare.MembershipBlackBoxTest',
 'com.example.trojanrideshare.integrationtest.test/androidx.test.runner.AndroidJUnitRunner']);
 let output='';child.stdout.on('data',d=>{output+=d;process.stdout.write(d);});child.stderr.pipe(process.stderr);
 const timer=setTimeout(()=>child.kill('SIGTERM'),600000);
 let code;try{code=await new Promise((resolve,reject)=>{child.on('error',reject);child.on('close',resolve);});}finally{clearTimeout(timer);}
 const report=path.resolve(__dirname,'../../../app/build/reports/membership');fs.mkdirSync(report,{recursive:true});
 fs.writeFileSync(path.join(report,'espresso.txt'),output);
 if(code!==0||!/OK \(5 tests\)/.test(output)||/FAILURES!!!|AssumptionViolated|INSTRUMENTATION_FAILED/.test(output))throw new Error('Membership Espresso suite did not pass all five tests.');
 console.log('All five membership Espresso tests passed. Report: '+path.join(report,'espresso.txt'));
})().catch(e=>{console.error(e.message);process.exitCode=1;}).finally(async()=>{await new Promise(resolve=>server.close(resolve));await deleteApp(app);});
