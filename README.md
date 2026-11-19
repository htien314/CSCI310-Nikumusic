# TrojanRideShare

**CSCI 310 Project 2.4 · Team 03 (Nikumusic)**

Updated November 18, 2026 against merged `main` at `f1f61ff`.

TrojanRideShare is an Android app that helps USC students share rides with other students going the same way. Students can:

- post a ride
- search for rides near their own route
- request to join a ride
- manage who is in the ride
- split the final cost and track who has paid
- review their ride history and monthly spending

The app only coordinates rides. It does not book Uber or Lyft trips or move any money.

**Contents**

1. [Requirements](#1-requirements)
2. [One-time setup](#2-one-time-setup)
3. [Get the code and open it](#3-get-the-code-and-open-it)
4. [Build and run the app](#4-build-and-run-the-app)
5. [Rules the app enforces](#5-rules-the-app-enforces)
6. [Backend: Firebase](#6-backend-firebase)
7. [Running the tests](#7-running-the-tests)
8. [Offline demo with sample data](#8-offline-demo-with-sample-data)
9. [Troubleshooting](#9-troubleshooting)
10. [Project structure](#10-project-structure)

---

## 1. Requirements

| Requirement | Version we used | Needed for |
|---|---|---|
| macOS, Windows or Linux | Tested on macOS (Apple silicon) | Everything |
| Android Studio | A version compatible with the included Android Gradle Plugin 9.3.3 | Building and running the app |
| JDK | JDK 21 was used to launch the verified commands. The repository requests JDK 25 for the Gradle daemon through `gradle/gradle-daemon-jvm.properties`; Gradle can download that toolchain if needed. | Gradle |
| Android SDK Platform | 37 (compile and target SDK) | Building |
| Android device or emulator | App minimum: Android 8.0 (API 26). Use API 33 or later for the supplied device test suites. Combined team validation used API 36; membership was also rerun on Pixel 4 API 33. | Running and device tests |
| Emulator system image | **Google APIs** or **Google Play** | Location lookup (see the note below) |
| Internet connection | | Sign-in, saving data, address lookup |
| Node.js | 22, matching `functions/package.json` | Firebase test runners and local demo |
| Git | Installed and available in your terminal | Cloning and the device runners’ disposable source copies |

> **Why a Google image?** Origin, destination and search locations are turned into coordinates with Android's built-in Geocoder. AOSP images may not provide the geocoding service this app expects. If the geocoder times out, the app falls back to OpenStreetMap, but a Google image is the most reliable choice.

**No separate Maps/Places API key or billing upgrade is required.** The included `app/google-services.json` connects the normal app to the shared Firebase project. Firebase email/password authentication and Firestore provide the backend on the Spark plan.

The commands below use macOS/Linux shell syntax and were validated on macOS. For Windows Gradle commands, use `gradlew.bat`; POSIX environment-variable prefixes require a compatible shell or equivalent PowerShell environment settings. The Node-based device runners have not been separately validated on Windows.

---

## 2. One-time setup

### 2.1 Install Android Studio and the SDK

1. Download Android Studio from https://developer.android.com/studio and install it.
2. Open Android Studio. Go to **Settings** (on macOS, **Android Studio > Settings**), then **Languages & Frameworks > Android SDK**.
3. On the **SDK Platforms** tab, check **Android API 37**.
4. On the **SDK Tools** tab, make sure these are checked:
   - **Android SDK Build-Tools**
   - **Android Emulator**
   - **Android SDK Platform-Tools**
5. Click **Apply** and wait for the downloads to finish.

### 2.2 Create an emulator

1. Open **Tools > Device Manager**, then click **+ > Create Virtual Device**.
2. Choose a phone, for example **Pixel 6**, and click **Next**.
3. Choose a system image whose target says **Google APIs** or **Google Play**, with API 33 or newer. Click the download icon next to it if needed, then click **Next**.
4. Click **Finish**. Start the emulator with the ▶ button in Device Manager.

> **Using a physical phone instead:** enable **Developer options** (tap **Settings > About phone > Build number** seven times). Then turn on **USB debugging** and connect the phone by USB. It will show up in Android Studio's device list.

### 2.3 Install Node.js 22 (only for tests and the offline demo)

Install it from https://nodejs.org, or on macOS with Homebrew:

```bash
brew install node@22
```

---

## 3. Get the code and open it

**From the submitted ZIP:** unzip it and open the folder containing `settings.gradle.kts`, `gradlew`, `app/` and `functions/`. This is the project root.

The Node-based Espresso runners use Git to list the source files for their disposable build. If the ZIP has no `.git` directory, run `git init` once **inside this extracted project root** before using those runners. No commit or GitHub account is required. This step is unnecessary for a Git clone, normal app builds, or JUnit tests.

**From GitHub:** our final code is on the `main` branch.

```bash
git clone https://github.com/htien314/CSCI310-Nikumusic.git
cd CSCI310-Nikumusic
git switch main
```

**Open it in Android Studio:**

1. Choose **File > Open** and select the **project root folder**. Don't select the `app` subfolder.
2. Wait for **Gradle sync** to finish; the status bar at the bottom shows progress. The first sync downloads Gradle 9.5, the Android Gradle Plugin and all libraries, which can take several minutes.
3. If Android Studio says the SDK location is not set, click the suggested fix or choose your SDK folder. This creates `local.properties`, which stays on your computer and isn't committed.
4. If sync fails because of the JDK, see [Troubleshooting](#9-troubleshooting).

For command-line builds, set `JAVA_HOME` to an installed JDK when needed. This Mac used:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Those paths are examples for this Mac; use your own installed locations. Check that `java -version`, `git --version`, and (for device test runners) `node -v` work before continuing.

---

## 4. Build and run the app

### 4.1 From Android Studio (recommended)

1. Start your emulator (section 2.2) or connect your phone.
2. In the toolbar, choose the **app** run configuration and select your device.
3. Click **Run ▶** or press **Ctrl+R** (macOS) / **Shift+F10** (Windows/Linux).
4. The app installs and opens on the **Profile** tab. If no account is saved, choose **Log In** or **Register**.
5. For registration, enter a name, nickname, unused `@usc.edu` email, unique ten-digit USC ID, valid graduation year, and password. Use test information for a class demo. Then use **Create**, **Search**, **My Rides**, and **Profile** in the bottom navigation. Organizer actions are under the selected ride’s **Ride details and payments** screen; join/leave actions are under **Requests and membership**.

### 4.2 From the command line

Run these from the project root, with a device or emulator running.

macOS and Linux:
```bash
./gradlew :app:installDebug
```

Windows:
```bash
gradlew.bat :app:installDebug
```

Then open **TrojanRideShare** from the device's app drawer. To build an APK without installing it, run `./gradlew :app:assembleDebug`. The APK is saved at `app/build/outputs/apk/debug/app-debug.apk`.

### 4.3 Which database does it use?

The normal build connects to our shared Firebase project, `trojanrideshare-672ad`. Accounts and rides you create are saved in that shared project. Other signed-in users can discover active rides according to the app’s search filters and access rules; private profile fields are not public.

The supplied Node-based Espresso runners and local demo use disposable app copies with different application IDs and local Firebase emulators. They do not change the normal app’s account or live database. JUnit tests run locally without Firebase. Do not run the emulator-only Espresso classes directly against the normal app configuration; use the commands in section 7.

---

## 5. Rules the app enforces

| Rule | Limit |
|---|---|
| Email | Must be a `@usc.edu` address |
| USC ID | Exactly 10 digits, unique per account |
| Nickname | Not blank, at most 100 characters |
| Ride title | Required, at most 100 characters |
| Ride note | At most 2,000 characters |
| Maximum riders | 2 to 6, including the organizer |
| Departure | Must be in the future when the ride is created |
| Expected cost | Greater than $0 |
| Delay | The new time must be after the original time and in the future |
| Cancel or leave | At least 3 hours before the **original** departure |
| Search radius | More than 0 and at most 100 miles |
| Join requests | One pending request per student per ride; no new request after a rejection |
| Final cost | Greater than $0, in whole cents; custom shares must be non-negative and add up exactly to the total |
| Completion | Organizer only, at or after the current departure time |
| Payments | Each rider can mark only their own share as paid |

Money is stored as whole cents. Equal-split remainders are assigned in participant order. Times are stored as UTC timestamps and displayed in `America/Los_Angeles`.

The organizer counts toward capacity. Approval adds the requester and reduces available seats by one; when the last seat is filled, the app closes the other pending requests as rejected. At exactly three hours before the original departure, leaving/cancellation is allowed; with less than three hours remaining, it is blocked. Delaying a ride does not extend this cutoff.

Ride details provide delay-time updates, cancellation and completion. This version does not have a general Edit Ride form for changing the title, route, price or capacity after creation.

---

## 6. Backend: Firebase

- **Authentication:** Firebase Authentication with email and password.
- **Database:** Cloud Firestore. Registration writes the profile documents and USC-ID reservation in an atomic batch. Membership and cost changes use client-side Firestore transactions. The server evaluates `firestore.rules` to enforce write permissions, capacity and time constraints. The app therefore needs no deployed Cloud Functions for these workflows.
- **Legacy backend code:** `functions/` still contains the earlier callable implementation and its tests. Some local runners start the Functions emulator for that older test harness; this does not mean the current Android app needs a paid Functions deployment.
- **Configuration:** the normal app reads `app/google-services.json`, which targets the team’s already configured shared project. Local runners change only their disposable copies to `demo-` projects. Do not replace the shared Firebase configuration to run tests.

**For project owners only.** If `firestore.rules` or `firestore.indexes.json` changes, a project owner deploys them with:

```bash
functions/node_modules/.bin/firebase deploy --project trojanrideshare-672ad --only firestore:rules,firestore:indexes
```

Run `npm ci --prefix functions` first. This requires being logged in to the Firebase CLI as a project member.

---

## 7. Running the tests

The full description of every test, including its rationale, result and coverage, is in our testing document. Run all commands from the project root.

### 7.1 Install the test dependencies (once)

```bash
npm ci --prefix functions
```

### 7.2 White-box tests (JUnit)

The full merged suite contains 267 local unit tests, including WB01–WB25. No Android or Firebase emulator is needed. Node dependencies are not required for this JUnit command.

```bash
./gradlew :app:testDebugUnitTest
```

Results are in `app/build/reports/tests/testDebugUnitTest/index.html`. To run one group of tests only:

| Tests | Command |
|---|---|
| WB01–WB05 | `./gradlew :app:testDebugUnitTest --tests '*AccountProfileWhiteBoxTest'` |
| WB06–WB10 | `./gradlew :app:testDebugUnitTest --tests '*RideManagementWhiteBoxTest'` |
| WB11–WB15 | `./gradlew :app:testDebugUnitTest --tests '*SearchLocationWhiteBoxTest'` |
| WB16–WB20 | `./gradlew :app:testDebugUnitTest --tests '*MembershipWhiteBoxTest'` |
| WB21–WB25 | `./gradlew :app:testDebugUnitTest --tests '*RideCompletionTest.wb21_*' --tests '*CostTransitionTest.wb22_*' --tests '*RideCompletionTest.wb23_*' --tests '*PaymentTrackingTest.wb24_*' --tests '*MonthlySummaryServiceTest.wb25_*'` |

**Coverage (JaCoCo).** To measure branch and line coverage across the business logic of all five features:

```bash
./gradlew :app:testDebugUnitTest :app:teamCoverage --rerun-tasks
```

The HTML report is at `app/build/reports/jacoco/teamCoverage/html/index.html`. See [the measured team coverage](docs/testing/team-coverage.md) for aggregate counts. The scope is the service, model and utility packages plus UI ViewModels; Android screens and Firebase adapters are excluded, and Espresso coverage is not merged into this JVM report.

Run coverage without `--tests` filters. A filtered JUnit run replaces its execution data, so a later coverage report would describe only that subset unless the full suite is rerun.

### 7.3 Black-box tests (Espresso)

1. Start an emulator first (section 2.2).
2. Stop any other Firebase emulators that are running.
3. Confirm `npm ci --prefix functions` has completed, `JAVA_HOME` and the Android SDK are configured, and Git is installed (see ZIP instructions in section 3).
4. Run the commands below **one at a time**, waiting for each to finish.

Each command does the following:
- builds a separate test copy: account/profile uses `com.example.trojanrideshare.accountprofiletest`; the other suites use `com.example.trojanrideshare.integrationtest`
- starts local Firebase Auth and Firestore emulators
- adds the test data it needs
- runs the tests on the emulator

Your normal app, its signed-in account and the live database are never touched. Each suite takes about 2–6 minutes.

| Tests | Command | Expected result |
|---|---|---|
| BB01–BB05 | `FIREBASE_CLI="$PWD/functions/node_modules/.bin/firebase" node scripts/test-account-profile.cjs` | `OK (5 tests)` |
| BB06–BB10 | `TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:ride:device` | `OK (5 tests)` |
| BB11–BB15 | `TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:device` | `OK (1 test)` for each test. This suite also runs the full workflow and navigation tests. |
| BB16–BB20 | `npm run test:membership:device --prefix functions` | `OK (5 tests)` |
| BB21–BB25 | `TROJAN_DEVICE_SUITE=cost-payments TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:device` | `OK (1 test)` for each test |

- `TROJAN_TEST_LOCATIONS=fixture` makes "USC" and "LAX" always resolve to the same coordinates in the test build, so the results don't depend on the internet or on geocoding.
- The tests run on `emulator-5554` by default. Use the Android SDK’s `adb devices` to check the serial. To select another running emulator, prefix the command, for example `ANDROID_SERIAL=emulator-5556 npm run test:membership:device --prefix functions`.
- Ports 8080 and 9099 are shared by the local suites. Device fixture helpers also use ports 8765 (general workflow/cost/search), 8766 (membership), and 8767 (ride management). Keep them available and run suites sequentially.
- BB20 intentionally seeds a full ride with a stale pending request to test refusal. Ordinary last-seat approval automatically rejects the other pending requests.
- Membership results are written to `app/build/reports/membership/espresso.txt`; account/profile results go to `.firebase/account-profile/espresso.txt`. Other runners print their per-test results in the terminal.

### 7.4 Security rules and legacy backend tests

These checks supplement the Android assignment tests. The Functions tests describe the older callable implementation; Firestore rule tests also cover the current client transaction flows. Install the integration-test dependencies separately for the last command. Run emulator suites sequentially.

```bash
npm --prefix functions test
```

```bash
npm --prefix functions run test:emulator
```

```bash
npm --prefix integration-tests ci && npm --prefix integration-tests test
```

### 7.5 Recorded results — November 18, 2026

The combined team results below are recorded in [final test validation](docs/testing/final-test-validation.md) and [team coverage](docs/testing/team-coverage.md). They are historical validation evidence, not a guarantee of a fresh run on another machine.

| Suite | Result |
|---|---|
| JUnit | 267 passed, 0 failed |
| JaCoCo coverage (all five features) | 80.6% of branches (661/820), 90.1% of lines (1,195/1,326) |
| Espresso, BB01–BB25 | All 25 passed in combined team validation on Android API 36 |
| Functions unit tests | 37 passed, 1 emulator-only test skipped |
| Firestore emulator tests | 16 passed |

Separate verification on merged `main` at `f1f61ff`, November 18: Hailey’s five JUnit tests and five Espresso tests passed again using Pixel 4 API 33. The documented membership command ran successfully without source changes. The full 267-test suite and coverage figures above come from the combined team reports.

---

## 8. Offline demo with sample data

This is a **local-backend demo**, not a fully offline installation. The initial build requires dependency downloads; ordinary location lookup also needs a working provider/network. Fixture mode uses fixed USC/LAX coordinates so the prepared workflow can run without external geocoding.

To try the app with local accounts and rides, including a completed ride with costs and payments:

1. Run the full workflow/search command from section 7.3 once: `TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:device`. It exports the local dataset and records the disposable build path under `.firebase/`. The cost-only command does not create this export.
2. Start the demo:
   ```bash
   npm --prefix functions run demo:local
   ```
3. Keep that terminal open. The script opens **TrojanRideShare Local Test**. A fixture-mode build shows a **LOCAL DEMO · FIXED USC/LAX LOCATIONS** banner.
4. Sign in with a local-only account: `owner-device@usc.edu`, `alice-device@usc.edu`, or `bob-device@usc.edu`. All three use `LocalTest123!`. These belong only to the emulator dataset, not the shared Firebase project. Switch accounts using **Profile > Log Out**. The `.firebase/` export and temporary build directory must still exist on this machine.
5. To see the data, open the local Firebase dashboard at http://localhost:4000. Nothing here appears in the live Firebase console.
6. Press **Control-C** in the terminal to stop. The demo data is saved for next time.

---

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| Gradle reports an unsupported Java version or toolchain error | Configure a compatible installed JDK in Android Studio’s Gradle settings or set `JAVA_HOME` for terminal runs. The verified commands used a JDK 21 launcher; the repository separately requests a JDK 25 Gradle daemon. Allow the toolchain download or install the requested JDK locally. |
| "SDK location not found" | Let Android Studio create `local.properties`, or add the line `sdk.dir=/path/to/Android/sdk` to that file. |
| Build fails with "Failed to find target android-37" | Install **Android API 37** in the SDK Manager (section 2.1). |
| **Find location** says "Couldn't find that location." or keeps searching | Use a Google APIs or Google Play emulator image and check that the emulator has internet access. Try a more specific place name, such as "University of Southern California". |
| "This USC ID is already registered" | Each USC ID can be used only once, even if its account was deleted. Register with a different 10-digit ID. |
| The **Cancel ride** or **Leave ride** button is missing or refused | Confirm you are in the correct account and screen. Cancellation is organizer-only; leaving requires membership. Both require at least three hours before the original departure and an active ride. On My Rides, open **View ride** for Leave ride; choose **Ride details and payments** for organizer controls. |
| Can't complete a ride | Sign in as its organizer. Completion is available at or after the current departure, including a delayed time, and requires an actual total greater than $0. |
| Espresso tests cannot reach local Firebase on a newer Android image | Use the current debug build and accept any required local-network permission. The general workflow runner grants that permission when the Android version exposes it. If a standalone suite cannot reach the backend, rerun on the verified API 33 image. |
| Espresso tests fail with "device offline" or time out | Wait until the emulator has fully booted to its home screen, close other heavy apps, and run the suite again. |
| "Port 8080 (or 9099) is not open" or "port taken" | Another Firebase emulator is still running. Stop it with Control-C, or quit the leftover `java` process, then run the suite again. |
| `npm` commands fail | Check `node -v` and `npm -v`; Node 22 matches the backend configuration. Run commands from the project root and install dependencies with `npm ci --prefix functions`. |
| Device runner says “not a git repository” | From the extracted project root, run `git init` once. The runner uses Git only to list files for its disposable build. |
| Local demo says a passing device test or snapshot is missing | Run the full fixture workflow first. Keep its `.firebase/` dataset and temporary build directory; then run `npm --prefix functions run demo:local`. |
| Full ride does not appear in search | Submit an explicit search with the seat filter **Any (include full rides)**. Default browsing hides full rides, and search excludes rides you organize or have already joined. |

---

## 10. Project structure

```
.
├── app/
│   ├── google-services.json          Firebase configuration (included)
│   └── src/
│       ├── main/java/com/example/trojanrideshare/
│       │   ├── model/                Ride, Location, Participant, User, JoinRequest, CostShare, enums
│       │   ├── data/                 Repository interfaces and Firestore implementations
│       │   ├── service/              Business rules: rides, search, membership, costs, payments, summaries
│       │   ├── ui/                   Screens and ViewModels: account, ride, search, membership, cost, common
│       │   └── util/                 Callback, Clock, Constants
│       ├── main/res/                 Layouts, navigation graph, strings, themes
│       ├── test/                     JUnit (white-box) tests
│       └── androidTest/              Espresso (black-box) tests
├── functions/                        Local runners, Firebase CLI dependencies, and legacy callable implementation/tests
├── integration-tests/                Firestore security-rules tests
├── scripts/                          Account and profile device test runner
├── docs/                             Feature notes, local testing guide, test validation report
├── firestore.rules                   Firestore security rules
├── firestore.indexes.json            Firestore indexes
└── firebase.json                     Firebase emulator configuration
```

## Feature owners

| Feature | Team member | Assignment tests |
|---|---|---|
| Accounts and profiles | Riya | BB/WB01–05 |
| Ride creation and management | Ada | BB/WB06–10 |
| Search and locations | Nikhil | BB/WB11–15 |
| Requests and membership | Hailey | BB/WB16–20 |
| Costs, payments and summaries | Estelle | BB/WB21–25 |

Earlier integration notes under `docs/` describe historical checkpoints. For the current combined test status, use `docs/testing/final-test-validation.md`; for current backend architecture, use `docs/spark-client-backend.md`.
