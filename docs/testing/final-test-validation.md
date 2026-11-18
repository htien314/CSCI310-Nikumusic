# Combined team test branch — validation

Validated on November 18, 2026 with JDK 21, Node 22 and an Android API 36 emulator.

## Included branches

| Branch | Source commit |
| --- | --- |
| `estelle-test` | `0d64675b1c9193f831429b29d42121fe82b251d0` |
| `nikhil-test` | `7173cb913793903650185a335f42d391e7d0f11e` |
| `ada/ride-management-tests` | `53546178ad7c128344c7f0ae41defc80dbb2a396` |
| `hailey/membership-tests-16-20` | `b1d085aa4747da37229f4541be1b44a7eca0078a` |
| `riya-feature-1-tests` | `6726bc48cdb6e543096b169de9b6dcde6b81f6d3` |

Base: `main` at `a86750d20c87d8663094c15dc13e29c7fa0307a9`.

## Integration changes

Preserved all three device fixture endpoints (general workflow, cost/payment and search), both npm device commands, and both JaCoCo report tasks. The device runner grants local network permission only on Android versions that expose it. The long workflow closes the keyboard after entering fields so taps reach the intended controls.

The only change under `app/src/main` relative to the base is Riya’s package-private injectable `AuthService` constructor. The public constructor still supplies the same Firebase Auth and user repository instances. Firebase project configuration, Firestore rules and Cloud Functions implementation are unchanged.

## Results

| Check | Result |
| --- | --- |
| Local JUnit suite, including WB01–WB25 | 267 passed, 0 failed, 0 skipped |
| Debug app, release app and instrumentation APK builds | Passed |
| Android lint | 0 errors; 205 warnings remain |
| Both JaCoCo report tasks | Passed; this run used the entire unit suite |
| Functions unit tests | 37 passed; 1 emulator-only test skipped here and run separately below |
| Firestore integration tests | 16 passed, 0 skipped |
| Firebase SDK cross-account lifecycle | Passed |
| BB01–BB05 account/profile | 5 passed |
| BB06–BB10 ride management | 5 passed |
| BB11–BB15 search/location | 5 passed |
| BB16–BB20 membership | 5 passed |
| BB21–BB25 cost/payment | 5 passed |
| Full cross-account workflow, process restart, custom splits, navigation and location-field regressions | 10 passed |
| Real Android geocoding smoke check | USC and LAX resolved; unknown location rejected |

## Demo checklist evidence and limits

The unit, rules, SDK and device suites exercise registration/profile validation, ride creation and status changes, search/filter/sort rules, membership authorization and concurrent acceptance, completion and cost splitting, payment persistence, history and monthly summaries. The SDK lifecycle also checks profile sharing access and revocation.

Device workflows use disposable application IDs and local demo Firebase projects. Route-dependent device scenarios use explicit USC/LAX fixtures; the separate geocoding smoke check uses the real Android geocoder. Local port substitutions were made only in disposable validation copies to avoid other running emulators. No production Firebase data was used.

These automated results are regression evidence for the supplied demo checklist, not a claim that every checklist line was manually demonstrated or that production services are guaranteed available. Before presenting, manually rehearse the checklist on the intended demo setup, especially real route selection, time-sensitive rules, public/private profile visibility and empty-state displays. Existing lint warnings remain outside this merge’s scope.

## Reproducing checks

Use JDK 21, Node 22, the Android SDK and a running emulator. Install the functions dependencies first with `npm ci` in `functions`. Run emulator suites sequentially so their default ports do not conflict.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
./gradlew :app:lintDebug :app:rideManagementCoverage :app:accountProfileCoverage
npm --prefix functions test
npm --prefix functions run test:emulator
npm --prefix functions run test:network
TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:device
TROJAN_DEVICE_SUITE=cost-payments TROJAN_TEST_LOCATIONS=fixture npm --prefix functions run test:device
npm --prefix functions run test:ride:device
npm --prefix functions run test:membership:device
FIREBASE_CLI="$PWD/functions/node_modules/.bin/firebase" node scripts/test-account-profile.cjs
```

For individual white-box tests, filter `:app:testDebugUnitTest` with `--tests`. The account/profile suite has its own disposable app/project and runner. The generic device runner includes BB11–BB15 alongside the long workflow and navigation regressions. The separate real-geocoder smoke check was a validation-only test and is not part of the committed assignment suites.
