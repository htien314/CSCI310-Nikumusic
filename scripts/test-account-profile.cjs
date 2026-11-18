'use strict';
// Builds a disposable APK from this branch and runs BB01–BB05 against demo emulators.
// The normal application ID, google-services.json and installed app are preserved.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {execFileSync, spawnSync} = require('node:child_process');
const repo = path.resolve(__dirname, '..');
const project = 'demo-trojan-account-profile';
const applicationId = 'com.example.trojanrideshare.accountprofiletest';
const testClass = 'com.example.trojanrideshare.AccountProfileBlackBoxTest';
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), 'Library/Android/sdk');
const adb = process.env.ANDROID_ADB || path.join(sdk, 'platform-tools/adb');
const device = process.env.ANDROID_SERIAL || 'emulator-5554';
function run(command, args, options = {}) {
    const result = spawnSync(command, args, {cwd: repo, encoding: 'utf8', stdio: 'inherit', ...options});
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${path.basename(command)} failed (${result.status})`);
    return result;
}
function instrument() {
    if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST
            || process.env.GCLOUD_PROJECT !== project) throw new Error('Requires the isolated demo emulator session.');
    const build = process.env.ACCOUNT_PROFILE_BUILD;
    if (!build || !fs.existsSync(path.join(build, 'app/build/outputs/apk/debug/app-debug.apk'))) {
        throw new Error('Missing disposable test build. Run this script without --instrument first.');
    }
    run(adb, ['-s', device, 'install', '-r', path.join(build, 'app/build/outputs/apk/debug/app-debug.apk')]);
    run(adb, ['-s', device, 'install', '-r', path.join(build, 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]);
    run(adb, ['-s', device, 'shell', 'pm', 'clear', applicationId]);
    const result = run(adb, ['-s', device, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', testClass,
        `${applicationId}.test/androidx.test.runner.AndroidJUnitRunner`], {stdio: 'pipe', timeout: 300_000});
    process.stdout.write(result.stdout || ''); process.stderr.write(result.stderr || '');
    const outputDir = path.join(repo, '.firebase/account-profile');
    fs.mkdirSync(outputDir, {recursive: true});
    fs.writeFileSync(path.join(outputDir, 'espresso.txt'), (result.stdout || '') + (result.stderr || ''));
    if (!/OK \(5 tests\)/.test(result.stdout) || /FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=/.test(result.stdout)) {
        throw new Error('All five Espresso tests must pass; inspect .firebase/account-profile/espresso.txt');
    }
}
function prepareAndRun() {
    run(adb, ['-s', device, 'shell', 'getprop', 'sys.boot_completed']);
    const build = fs.mkdtempSync(path.join(os.tmpdir(), 'trojan-account-profile-'));
    const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        {cwd: repo, encoding: 'utf8'}).split('\0').filter(Boolean);
    for (const relative of files) {
        const from = path.join(repo, relative);
        if (!fs.existsSync(from) || !fs.statSync(from).isFile()) continue;
        const to = path.join(build, relative);
        fs.mkdirSync(path.dirname(to), {recursive: true}); fs.copyFileSync(from, to);
    }
    const edit = (file, change) => {
        const target = path.join(build, file);
        fs.writeFileSync(target, change(fs.readFileSync(target, 'utf8')));
    };
    edit('app/build.gradle.kts', text => text.replace('applicationId = "com.example.trojanrideshare"', `applicationId = "${applicationId}"`));
    edit('app/google-services.json', text => {
        const json = JSON.parse(text);
        json.project_info.project_id = project;
        for (const client of json.client) client.client_info.android_client_info.package_name = applicationId;
        return JSON.stringify(json, null, 2);
    });
    edit('app/src/main/AndroidManifest.xml', text => text.replace('android:allowBackup="true"',
        'android:allowBackup="false" android:usesCleartextTraffic="true"'));
    edit('app/src/main/res/values/strings.xml', text => text.replace(/<string name="app_name">[^<]*<\/string>/,
        '<string name="app_name">TrojanRideShare Account Tests</string>'));
    fs.writeFileSync(path.join(build, 'local.properties'), `sdk.dir=${sdk}\n`);
    fs.chmodSync(path.join(build, 'gradlew'), 0o755);
    run(path.join(build, 'gradlew'), ['-p', build, '-PuseFirebaseEmulator=true', ':app:assembleDebug',
        ':app:assembleDebugAndroidTest', '--console=plain'], {cwd: build});
    // Use only Auth and Firestore: these account/profile scenarios do not need Functions.
    fs.writeFileSync(path.join(build, 'account-profile.firebase.json'), JSON.stringify({
        firestore: {rules: path.join(repo, 'firestore.rules')},
        emulators: {auth: {port: 9099}, firestore: {port: 8080}, ui: {enabled: false}, singleProjectMode: true}
    }, null, 2));
    const firebase = process.env.FIREBASE_CLI || 'firebase';
    const command = `${JSON.stringify(process.execPath)} ${JSON.stringify(__filename)} --instrument`;
    console.log(`Disposable build: ${build}\nDemo Firebase project: ${project}`);
    run(firebase, ['emulators:exec', '--project', project, '--config', path.join(build, 'account-profile.firebase.json'),
        '--only', 'auth,firestore', command], {env: {...process.env, ACCOUNT_PROFILE_BUILD: build}});
}
try {
    if (process.argv.includes('--instrument')) instrument(); else prepareAndRun();
} catch (error) {
    console.error(error.message); process.exitCode = 1;
}
