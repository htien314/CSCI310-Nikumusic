'use strict';
// Review-only by default. A project owner must explicitly pass --apply to write.
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const args=process.argv.slice(2),projectAt=args.indexOf('--project');
if (projectAt<0 || !args[projectAt+1]) throw new Error('Specify --project PROJECT_ID. Add --apply only after reviewing the dry run.');
const projectId=args[projectAt+1],apply=args.includes('--apply');
const app=initializeApp({projectId});
(async()=>{
 const snapshot=await getFirestore().collection('rides').get();
 let required=0,updated=0,invalid=0;
 for(const doc of snapshot.docs) {
  const data=doc.data(),people=data.participants;
  if(!Array.isArray(people) || people.length>6 || people.some(p=>typeof p.userId!=='string'||!p.userId)) {invalid++;continue;}
  const ids=people.map(p=>p.userId);
  if(new Set(ids).size!==ids.length) {invalid++;continue;}
  if(JSON.stringify(data.participantIds)===JSON.stringify(ids)) continue;
  required++;
  if(apply) {
   // Refuse stale updates when a join/leave races this migration.
   await doc.ref.update({participantIds:ids},{lastUpdateTime:doc.updateTime});updated++;
  }
 }
 console.log(JSON.stringify({projectId,mode:apply?'apply':'dry-run',scanned:snapshot.size,required,updated,invalid}));
 if(invalid) process.exitCode=1;
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(()=>deleteApp(app));
