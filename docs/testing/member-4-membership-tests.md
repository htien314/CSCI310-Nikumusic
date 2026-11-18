# Member 4: requests and membership, scenarios 16–20

## Requirements and integrated behavior

Reviewed November 17, 2026 against Project 2.1, User Stories 3–4 (page 5) and Use Cases 3–4 (pages 7–9), and the scenario descriptions supplied for this assignment. Source: `310 Project 2.1 Nikumusic (1).pdf` supplied by the team.

- Submitting a request saves **PENDING**, not membership. The requester sees their status; the organizer sees their inbox.
- One request per ride and requester uses `{rideId}_{userId}`. A pending request cannot be duplicated.
- Only the current organizer accepts or rejects a pending request. **ACCEPTED** is this codebase's name for the assignment's “Approved.”
- Acceptance adds the requester once. Available seats equal `maxRiders - participants.size()`; **the organizer counts toward capacity**.
- Rejection leaves participants and available seats unchanged. A resolved request cannot be accepted or rejected again.
- Requests and acceptance require an active ride before its current departure. Full rides cannot accept more members.
- Filling the last seat automatically rejects other pending requests. BB20 therefore intentionally seeds a stale pending request on an already full ride; it tests the refusal branch rather than pretending that is the normal last-seat outcome.

The integrated implementation is `MembershipViewModel -> MembershipService -> FirebaseJoinRequestRepository -> MembershipTransactions -> MembershipTransition`, with Firestore rules validating writes. The original `docs/hailey-membership.md` describes an older Cloud Functions implementation; these tests target the current Spark-compatible Java transactions.

## The ten deliverables

| Scenario | Espresso black-box assertion | JUnit white-box assertion |
| --- | --- | --- |
| 16 Submit | Submit via the real dialog; requester sees pending, organizer sees inbox; ride unchanged. | Real ViewModel/service/transition with fake repository; exactly one pending save, unchanged participants, both listener projections. |
| 17 Duplicate | Existing pending status and no second request action; refresh still yields one unchanged request. | Existing-pending guard makes zero commits; no-request control makes one pending commit. |
| 18 Approve | Organizer clicks Accept; requester sees accepted; membership grows by one and seats fall by one. | Non-organizer denial is unchanged; organizer success inserts one participant, updates ACCEPTED and seat count. |
| 19 Reject | Organizer confirms Reject; requester sees rejected; ride and seats unchanged. | REJECTED without membership change; repeat reject and later accept both fail without writes. |
| 20 Capacity | Full ride with stale pending request; Accept reports full; complete ride/request snapshots unchanged. | One-seat approval succeeds, makes FULL and closes other pending requests; zero-seat attempt leaves every related record unchanged. |

Files:

- `app/src/androidTest/java/com/example/trojanrideshare/MembershipBlackBoxTest.java` — exactly five `@Test` methods, BB16–BB20.
- `app/src/test/java/com/example/trojanrideshare/MembershipWhiteBoxTest.java` — exactly five `@Test` methods, WB16–WB20.
- `functions/test/membership/verify.cjs` and `run.cjs` — reproducible isolated build, fixture setup and Espresso runner.

## Run the five JUnit tests

From the repository root:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.example.trojanrideshare.MembershipWhiteBoxTest'
```

On this Mac, set `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` if your terminal has no JDK configured.

Report: `app/build/reports/tests/testDebugUnitTest/index.html`. JUnit XML: `app/build/test-results/testDebugUnitTest/TEST-com.example.trojanrideshare.MembershipWhiteBoxTest.xml`.

## Run the five Espresso tests

1. Start a Google APIs/Google Play Android emulator (API 33 or later).
2. Install the existing backend development dependencies with `npm ci --prefix functions` if needed.
3. From the repository root:

```sh
npm run test:membership:device --prefix functions
```

For a second Android emulator, set its actual serial, for example:

```sh
ANDROID_SERIAL=emulator-5556 npm run test:membership:device --prefix functions
```

The runner uses the existing disposable-build helper to create a separate Android application ID, `com.example.trojanrideshare.integrationtest`, with Firebase project `demo-trojan-device` and `-PuseFirebaseEmulator=true`. It starts local Auth and Firestore emulators, seeds each scenario independently, installs the isolated APKs, and runs all five tests. Ports 8080, 9099 and fixture-server port 8766 must be available. Fixtures are local only and require no Google sign-in or billing.

The test class fails fast if run against a normal/live build. Running its green gutter icon directly in the normal Android Studio configuration will fail that guard; use the command above.

Output: `app/build/reports/membership/espresso.txt`. The runner accepts only `OK (5 tests)`; a skip or incomplete suite is not a passing result.

## Scope of the assertions

- Espresso drives the production membership screen's request, approval and rejection controls. Firebase sign-in and fixture creation are setup; registration and the login form are outside scenarios 16–20.
- BB17 proves the actual UI suppresses a duplicate submission, while WB17 executes the duplicate guard directly.
- JUnit uses a fixed clock and a fake repository that calls **production** `MembershipTransition`. It simulates persistence and listener delivery without reimplementing the decisions under test. It does not prove Firestore security rules or concurrency on its own.
- Espresso uses real Android Firebase transactions against the local emulator and repository rules. It does not claim live-Firebase or external-geocoding coverage.
- The five per-scenario `@Test` methods can contain positive and negative controls, as specified by the assignment. No extra counted test methods are needed.
- No product screen, live account, deployed rule or live ride is changed by the test setup.

## Executed results — November 17, 2026

- `MembershipWhiteBoxTest`: **5 tests, 0 failures, 0 errors, 0 skipped**.
- Full existing JUnit suite plus the new tests: **245 tests, 0 failures, 0 errors, 0 skipped**.
- `MembershipBlackBoxTest`: **OK (5 tests)**, executed on Pixel 4 API 33 (`emulator-5556`) with isolated Auth/Firestore emulators. Final suite time: 35.09 seconds.
- Both isolated Android app and test APK builds succeeded. JavaScript syntax checks and Git whitespace checks passed.
- The first Espresso run exposed a test-helper error: Refresh is outside the scroll container. The helper was corrected to click it directly, and the entire five-test suite passed on the next run. No product logic was altered to make the assertions pass.
- Results were validated against integrated commit `a86750d` plus these test additions. Application behavior and production Firebase configuration are unchanged.
