# Full app integration

Branch: `integration/full-app`, built on `integration/ada-riya-nikhil` (`7a2c7c8`), with Hailey’s membership branch and Estelle’s cost branch merged with their histories preserved. Main has not been changed.

## Connected behavior

- Firebase accounts and real user profiles flow into ride creation, search, membership and participants’ public profiles. Logout remains near the top of My Profile.
- Ride details link to requests/membership; membership links back to ride details, completion and payments. Monthly summaries are reachable from the rides hub and payment tracking.
- The `membership` callable handles requests, approvals, rejection, departures, organizer transfer and cancellation. All membership changes also synchronize `participantIds`. Requesting and approval stop at the current departure; cancellation/departure use the original three-hour cutoff.
- Cancelling closes all pending requests in the same transaction. Filling the final seat also rejects other pending requests.
- The new `rideCosts` callable authoritatively completes rides, splits actual costs, marks a rider’s own share paid, and lets the organizer flag unpaid shares after seven days. It validates verified identity, server time and current participants. Completion closes pending requests atomically. Direct client writes to membership and cost fields remain denied.
- Payment flags are reflected in public profiles as `overdueRideIds`, updated atomically with payments. Paying clears that ride’s flag and preserves other flags, profile names and sharing preferences.
- History shows actual assigned shares, paid/unpaid status and participant count. It supports text, status, participant, pickup/destination radius and date order filters. Extra filters collapse so results remain visible on smaller screens. Location radii use Nikhil’s shared distance logic and miles.
- Monthly summaries use the scheduled departure month in Los Angeles time, even when costs are recorded in a later month. Details, history and payment screens reload when reopened.

## Verified locally

- Android debug build and lint passed; 218 Java unit tests passed, including history filter/payment display and cross-month summary regressions.
- Startup smoke check passed on the Pixel 4 / API 33 Android emulator: APK installation preserved the existing sign-in, MainActivity opened My Profile with Log Out visible, and the crash log was empty. The emulator is left running.
- 36 backend unit tests passed.
- 14 Firestore/callable integration tests passed, covering concurrent last-seat approvals, duplicate submissions, organizer transfer, cancellation cleanup, joining/completing/paying/flagging, concurrent completion/payment, late requests and direct-write denial.
- 15 account/profile/search Firestore integration tests passed.
- The tests use disposable local demo projects and do not alter the shared Firebase database. Deliberate permission-denied logs are expected in permission tests.

Backend tests execute the callable handlers against Firestore, rather than a deployed network endpoint. They do not establish that the shared Firebase deployment is ready or replace a complete multi-account Android demo.

## Required Firebase setup before the full live flow works

Review and deploy the reconciled `firestore.rules`, `firestore.indexes.json`, and both callables (`membership` and `rideCosts`) to the existing shared project, `trojanrideshare-672ad`. The default Android build connects to that project. This integration has not deployed anything, created another project, or changed billing. A project owner must confirm Cloud Functions availability and current deployed rules before deployment.

From a Firebase-authorized owner’s environment after review:

```sh
cd functions
npm ci
cd ..
functions/node_modules/.bin/firebase deploy --project trojanrideshare-672ad --only functions:membership,firestore:rules,firestore:indexes
```

`functions:membership` selects the codebase named `membership` in `firebase.json`; that codebase exports BOTH callables. Use Node 22 for backend deployment. Do not deploy the new rules separately ahead of the callables: direct ride cancellation now requires the membership backend, and costs require the cost backend.

For local Android development, run Auth, Firestore and Functions emulators for the Android-configured project, then build with `-PuseFirebaseEmulator=true`. Only that explicit build option switches all three SDKs to `10.0.2.2` on ports 9099, 8080 and 5001. The normal build keeps live-project configuration. Android emulator accounts/data are separate from live accounts/data.

## Repeatable checks

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
node --test functions/test/membership.test.js functions/test/costs.test.js
cd functions && npm run test:emulator
# From repo root, after the preceding emulator has shut down:
cd integration-tests && npm test
```

## Remaining full-device demo

1. With the required backend deployed (or local emulators running), register three distinct USC users. Verify duplicate USC IDs are rejected without partial profiles.
2. Organizer creates a ride. Other students find it through search, view its details/profile, and submit requests. Accept the last seat and confirm automatic rejection of other pending requests on both accounts.
3. Verify pending withdrawal, leave/reopened seats, and latest-accepted organizer transfer before the original three-hour cutoff. Confirm late controls cannot bypass the server cutoff.
4. Cancel a different ride with pending requests; confirm all accounts refresh consistently and history shows cancellation.
5. Complete a departed ride using equal and custom cost splits. Confirm exact cent totals, actual shares in history, and the scheduled-month summary.
6. Sign in as each rider to mark their own share paid. Use test data to verify seven-day overdue flags, public profile flags and clearing flags on payment. Payment status is a record in the app; no financial transfer is implemented.
7. Test logout/login, back navigation, rotation, app restart and a network interruption. Confirm no prior account’s private profile or stale controls remain visible.

No full-device, multi-account demo or live-project end-to-end result is claimed until the above checks are performed.
