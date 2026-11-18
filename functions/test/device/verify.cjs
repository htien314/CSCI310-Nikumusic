'use strict';
const fs=require('node:fs'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const {prepare,repo}=require('./prepare.cjs');
const build=prepare();
const costPaymentsOnly = process.env.TROJAN_DEVICE_SUITE === 'cost-payments';
const snapshot = path.join(repo, '.firebase', `device-demo-${Date.now()}`);
const args = ['emulators:exec', '--project', 'demo-trojan-device', '--only', 'auth,firestore,functions'];
if (!costPaymentsOnly) {
  fs.mkdirSync(path.dirname(snapshot), {recursive: true});
  args.push('--export-on-exit', snapshot);
}

const firebase = path.join(repo, 'functions/node_modules/.bin/firebase');
const result = spawnSync(firebase, [...args, 'node functions/test/device/run.cjs'], {
  cwd: repo, stdio: 'inherit', env: {...process.env, TROJAN_DEVICE_BUILD: build}
});
if (!costPaymentsOnly) {
  fs.writeFileSync(path.join(repo, '.firebase/device-verification.json'), JSON.stringify({
    build, snapshot, locationMode: process.env.TROJAN_TEST_LOCATIONS === 'fixture' ? 'fixture' : 'real',
    passed: result.status === 0
  }, null, 2));
}
process.exitCode=result.status===0?0:1;
