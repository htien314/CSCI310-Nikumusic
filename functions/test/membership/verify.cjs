'use strict';
// Reuse the existing isolated build: a separate application ID and demo Firebase project.
const {spawnSync}=require('node:child_process');
const path=require('node:path');
const {prepare,repo}=require('../device/prepare.cjs');
const build=prepare();
const result=spawnSync(path.join(repo,'functions/node_modules/.bin/firebase'),[
 'emulators:exec','--project','demo-trojan-device','--only','auth,firestore',
 'node functions/test/membership/run.cjs'
],{cwd:repo,stdio:'inherit',env:{...process.env,TROJAN_DEVICE_BUILD:build}});
process.exitCode=result.status===0?0:1;
