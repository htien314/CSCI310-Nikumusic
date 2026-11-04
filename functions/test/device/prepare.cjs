'use strict';
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {execFileSync,spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'../../..');
function prepare() {
 const target=fs.mkdtempSync(path.join(os.tmpdir(),'trojan-device-'));
 const tracked=execFileSync('git',['ls-files','-z'],{cwd:repo,encoding:'utf8'}).split('\0').filter(Boolean);
 for(const relative of tracked) {
  const from=path.join(repo,relative);if(!fs.existsSync(from)||!fs.statSync(from).isFile())continue;
  const to=path.join(target,relative);fs.mkdirSync(path.dirname(to),{recursive:true});fs.copyFileSync(from,to);
 }
 const test='app/src/androidTest/java/com/example/trojanrideshare/FullAppEmulatorTest.java';
 fs.mkdirSync(path.dirname(path.join(target,test)),{recursive:true});fs.copyFileSync(path.join(repo,test),path.join(target,test));
 const edit=(relative,fn)=>{const f=path.join(target,relative);fs.writeFileSync(f,fn(fs.readFileSync(f,'utf8')));};
 edit('app/build.gradle.kts',s=>s.replace('applicationId = "com.example.trojanrideshare"','applicationId = "com.example.trojanrideshare.integrationtest"'));
 edit('app/google-services.json',s=>{
  const data=JSON.parse(s);data.project_info.project_id='demo-trojan-device';
  for(const c of data.client)c.client_info.android_client_info.package_name='com.example.trojanrideshare.integrationtest';
  return JSON.stringify(data,null,2);
 });
 edit('app/src/main/AndroidManifest.xml',s=>s.replace('android:allowBackup="true"','android:allowBackup="false" android:usesCleartextTraffic="true"'));
 edit('app/src/main/res/values/strings.xml',s=>s.replace(/<string name="app_name">[^<]+<\/string>/,'<string name="app_name">TrojanRideShare Local Test</string>'));
 const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||path.join(os.homedir(),'Library/Android/sdk');
 if(!fs.existsSync(sdk))throw new Error('Set ANDROID_HOME to the installed Android SDK.');
 fs.writeFileSync(path.join(target,'local.properties'),`sdk.dir=${sdk}\n`);
 fs.chmodSync(path.join(target,'gradlew'),0o755);
 const build=spawnSync(path.join(target,'gradlew'),['-p',target,'-PuseFirebaseEmulator=true',':app:assembleDebug',':app:assembleDebugAndroidTest','--console=plain'],{cwd:target,stdio:'inherit'});
 if(build.status!==0)throw new Error('The isolated Android test build failed.');
 return target;
}
module.exports={prepare,repo};
