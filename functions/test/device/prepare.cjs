'use strict';
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {execFileSync,spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'../../..');
function prepare() {
 const target=fs.mkdtempSync(path.join(os.tmpdir(),'trojan-device-'));
 const tracked=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],{cwd:repo,encoding:'utf8'}).split('\0').filter(Boolean);
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
 // An opt-in fixture build isolates UI/backend verification from an unavailable
 // external geocoder. It is generated only in this disposable demo-project copy.
 if (process.env.TROJAN_TEST_LOCATIONS === 'fixture') {
  edit('app/src/main/java/com/example/trojanrideshare/data/GeocoderLocationGateway.java', () => `package com.example.trojanrideshare.data;
import android.content.Context;
import com.example.trojanrideshare.BuildConfig;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.FirebaseApp;
public class GeocoderLocationGateway implements LocationGateway {
 public GeocoderLocationGateway(Context context) {
  if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATOR ||
      !"demo-trojan-device".equals(FirebaseApp.getInstance().getOptions().getProjectId()))
   throw new IllegalStateException("Location fixtures are restricted to disposable local UI tests.");
 }
 public void lookup(String query, Callback<Location> callback) {
  String text = query == null ? "" : query.trim().toLowerCase(java.util.Locale.US);
  if (text.equals("usc") || text.equals("university of southern california, los angeles"))
   callback.onSuccess(new Location("University of Southern California, Los Angeles",34.0224,-118.2851));
  else if (text.equals("lax") || text.equals("los angeles international airport, los angeles"))
   callback.onSuccess(new Location("Los Angeles International Airport, Los Angeles",33.9416,-118.4085));
  else callback.onError(new IllegalArgumentException("This local UI demo supports only USC and LAX. Production uses real geocoding."));
 }
}`);
  edit('app/src/main/res/layout/activity_main.xml', text => {
   text = text.replace('app:layout_constraintTop_toTopOf="parent"', 'app:layout_constraintTop_toBottomOf="@id/demoEnvironmentBanner"');
   return text.replace('    <androidx.fragment.app.FragmentContainerView', `<TextView android:id="@+id/demoEnvironmentBanner" android:layout_width="0dp" android:layout_height="wrap_content"
android:text="LOCAL DEMO · FIXED USC/LAX LOCATIONS" android:textSize="10sp" android:gravity="center" android:padding="6dp"
android:textColor="@color/brand_on_container" android:background="@color/brand_container"
app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" />
    <androidx.fragment.app.FragmentContainerView`);
  });
  console.log('EXPLICIT FIXTURE MODE: this run verifies Android UI/backend behavior, not the external geocoder.');
 }
 const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||path.join(os.homedir(),'Library/Android/sdk');
 if(!fs.existsSync(sdk))throw new Error('Set ANDROID_HOME to the installed Android SDK.');
 fs.writeFileSync(path.join(target,'local.properties'),`sdk.dir=${sdk}\n`);
 fs.chmodSync(path.join(target,'gradlew'),0o755);
 const build=spawnSync(path.join(target,'gradlew'),['-p',target,'-PuseFirebaseEmulator=true',':app:assembleDebug',':app:assembleDebugAndroidTest','--console=plain'],{cwd:target,stdio:'inherit'});
 if(build.status!==0)throw new Error('The isolated Android test build failed.');
 return target;
}
module.exports={prepare,repo};
