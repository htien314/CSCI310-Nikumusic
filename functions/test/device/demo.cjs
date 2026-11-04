'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'../../..');
const record=path.join(repo,'.firebase/device-verification.json');
if(!fs.existsSync(record))throw new Error('Run npm run test:device successfully first.');
const {snapshot,passed}=JSON.parse(fs.readFileSync(record,'utf8'));
if(!passed||!fs.existsSync(path.join(snapshot,'firebase-export-metadata.json')))throw new Error('A passing device test and exported demo data are required.');
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||path.join(os.homedir(),'Library/Android/sdk');
const adb=process.env.ANDROID_ADB||path.join(sdk,'platform-tools/adb');
const device=process.env.ANDROID_SERIAL||'emulator-5554';
const child=spawn(path.join(repo,'functions/node_modules/.bin/firebase'),['emulators:start','--project','demo-trojan-device','--only','auth,firestore,functions','--import',snapshot,'--export-on-exit',snapshot],{cwd:repo,env:process.env});
let opened=false;
child.stdout.on('data',data=>{
 process.stdout.write(data);
 if(!opened && data.toString().includes('All emulators ready')) {
  opened=true;
  spawnSync(adb,['-s',device,'shell','am','start','-f','0x10008000','-n','com.example.trojanrideshare.integrationtest/com.example.trojanrideshare.MainActivity'],{stdio:'inherit'});
  console.log('Local-only test accounts: owner-device@usc.edu, alice-device@usc.edu, bob-device@usc.edu. Password for all: LocalTest123!');
 }
});
child.stderr.pipe(process.stderr);
child.on('error',e=>{console.error(e.message);process.exitCode=1;});
child.on('close',code=>{process.exitCode=code||0;});
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>child.kill(signal));
