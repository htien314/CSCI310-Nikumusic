# Full Android testing without enabling billing

The team chose to keep the shared project on Spark and test the integrated backend locally. This verifies the full app against Firebase emulators; it is not a live Firebase deployment. Keep the live setup/deployment checklist open until that work is explicitly resumed.

## Run the automated device workflow

1. Start a Google APIs / Google Play Android emulator in Android Studio. The default device is `emulator-5554`; set `ANDROID_SERIAL` if using another.
2. Use JDK 21 and an installed Android SDK. Set `JAVA_HOME` and `ANDROID_HOME` when those are not already configured. Node 22 matches the backend configuration; the current machine also ran the tests with Node 24 and a runtime-version warning.
3. From the repository root:

```sh
cd functions
npm ci                 # only needed when dependencies are missing/outdated
npm run test:device
```

The script copies the current working-tree source, including new untracked source files into a temporary build directory, changes only that copy's Firebase project to `demo-trojan-device`, gives it the separate Android ID `com.example.trojanrideshare.integrationtest`, and enables emulator connections. The normal installed app and its saved account remain intact. The test itself refuses to run against a non-demo Firebase project.

It uses the actual Android signup/login and ride-creation forms, search, requests and approval, last-seat rejection, leaving/rejoining, completion, payment flags, marking a share paid, history, monthly summary and cancellation. A second phase force-stops the test app and verifies that its account and server request state survive restarting the process. A third phase checks custom splits: participant names, a zero share, invalid decimals, exponent input and exact totals. Seven focused navigation/location regressions follow.

The default location mode uses real Android geocoding. In the current review, Google's provider timed out during ride creation. To test the remaining workflow independently, explicitly select the fixed-location mode:

```sh
TROJAN_TEST_LOCATIONS=fixture npm run test:device
```

This substitutes only USC and LAX coordinates in the disposable demo build, refuses other place names, and displays **LOCAL DEMO · FIXED USC/LAX LOCATIONS** on screen. The substitution refuses non-debug/non-demo configurations. Production source and the normal APK retain the real geocoder. A passing fixture run does not verify real geocoding. Only the departure/overdue time fixtures are advanced through a local-only test helper; no live rides are edited.

Results and the exported disposable demo dataset are recorded under `.firebase/`, which is ignored by Git. The test builds and installs its own APKs and does not uninstall the normal app.

## Open the app for manual testing afterward

After a passing `test:device` run, from `functions/`:

```sh
npm run demo:local
```

Keep that terminal running. It restores the saved local Auth/Firestore dataset, starts both backend functions and registration, and opens **TrojanRideShare Local Test** in the emulator. The normal app still points to the shared Firebase project and requires the live backend for its full workflow.

Local test accounts (not real university accounts):

| Email | Password |
| --- | --- |
| `owner-device@usc.edu` | `LocalTest123!` |
| `alice-device@usc.edu` | `LocalTest123!` |
| `bob-device@usc.edu` | `LocalTest123!` |

Use Profile → Log Out to switch accounts. Owner and Alice participated in the completed airport ride; Bob has rejected requests, including the cancelled ride. New local accounts need a different ten-digit test USC ID; the seeded IDs are `6000000001`, `6000000002`, and `6000000003`.

The backend dashboard is [localhost:4000](http://localhost:4000). Accounts and rides created here appear in that local dashboard, not the live Firebase console. Stop the local backend with Control-C in the terminal; the demo data is saved on exit.

## Privacy regression covered

When an organizer cancels a ride, a rejected requester may no longer read the private ride document. The request screen now shows the student's own request status, and My Rides has recent rejected/cancelled request updates, instead of leaving the student on “Loading ride.” This preserves the private-ride access rules.

## Still outside this local checkpoint

Live deployment, billing decisions, repairing old live ride records, reviewing live security rules/indexes, testing multiple physical devices concurrently, and a full device-level network-interruption exercise remain separate work. Backend tests cover additional race conditions. The Android workflow now covers both equal and custom cost splits. Real geocoding must be retested on a working provider/network before the demo.
