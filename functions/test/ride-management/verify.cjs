'use strict';
// Builds the isolated test app (separate application ID, demo Firebase project) and runs
// BB06–BB10 against local Auth and Firestore emulators. Locations use the fixed USC/LAX
// fixtures by default so results don't depend on an external geocoder; set
// TROJAN_TEST_LOCATIONS=real to use the device's real geocoder instead.
const {spawnSync}=require('node:child_process');
const path=require('node:path');
process.env.TROJAN_TEST_LOCATIONS=process.env.TROJAN_TEST_LOCATIONS||'fixture';
const {prepare,repo}=require('../device/prepare.cjs');
const build=prepare();
const result=spawnSync(path.join(repo,'functions/node_modules/.bin/firebase'),[
 'emulators:exec','--project','demo-trojan-device','--only','auth,firestore',
 'node functions/test/ride-management/run.cjs'
],{cwd:repo,stdio:'inherit',env:{...process.env,TROJAN_DEVICE_BUILD:build}});
process.exitCode=result.status===0?0:1;
