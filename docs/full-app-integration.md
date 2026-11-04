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
- 16 Firestore/callable integration tests passed, covering concurrent last-seat approvals, duplicate submissions, organizer transfer, cancellation cleanup, joining/completing/paying/flagging, concurrent completion/payment, late requests and direct-write denial.
- 19 account/profile/search/privacy Firestore integration tests passed.
- Three Android device navigation regressions passed, reproducing Profile → Log In → Search → Profile, Profile reselection, and clearing login/signup navigation history.
- One complete multi-account workflow passed through the real Firebase JavaScript SDK and local Auth, Firestore and Functions HTTP endpoints. It covers signup, duplicate ID errors, search, requests, last-seat rejection, leaving, organizer transfer, completion, flags, paying, sign-out/sign-in persistence, cancellation and share/revoke privacy. The migration dry run and explicit apply path were also checked against local data.
- The tests use disposable local demo projects and do not alter the shared Firebase database. Deliberate permission-denied logs are expected in permission tests.

The network workflow uses local emulator endpoints, not the shared Firebase deployment. Android device navigation is verified separately; the full local Android workflow is now verified separately through the main forms, including real geocoding, three accounts, cancellation and a full process restart. Advanced custom-split UI and device-level network interruption remain separate checks. The host used Node 24 for local tests; deployment remains configured for Node 22.

## Full Android device verification

On November 3, 2026, `npm run test:device` passed both phases against isolated demo emulators: the complete three-account form/navigation workflow, and sign-in/request persistence after force-stopping and restarting the app. Real USC/LAX geocoding, requests, last-seat rejection, leaving/rejoining, actual-cost completion, flags, payment, history, monthly summary and cancellation were exercised through Android screens.

This found and fixed a privacy/navigation edge: a rejected student could see “Loading ride” indefinitely after cancellation made the ride private. The app now displays the student's own request status and recent rejected/cancelled request updates without relaxing ride read permissions. Android unit tests, normal debug build and lint also passed after the fix.

## Local demo decision

The team chose to retain Spark and continue local testing without billing. Use [the isolated Android device workflow and demo](local-device-testing.md). The live setup was audited read-only; no live deployment, migration, or billing change was performed. Live readiness remains a separate checkpoint.

## Required Firebase setup before the full live flow works

Review and deploy the reconciled `firestore.rules`, `firestore.indexes.json`, and all three callables (`membership`, `rideCosts` and `registerProfile`) to the existing shared project, `trojanrideshare-672ad`. The default Android build connects to that project. This integration has not deployed anything, created another project, or changed billing. A project owner must confirm Cloud Functions availability and current deployed rules before deployment.

From a Firebase-authorized owner’s environment after review:

```sh
cd functions
npm ci
cd ..
functions/node_modules/.bin/firebase deploy --project trojanrideshare-672ad --only functions:membership,firestore:rules,firestore:indexes
```

`functions:membership` selects the codebase named `membership` in `firebase.json`; that codebase exports all three callables. Use Node 22 for backend deployment. Before enabling the new privacy rules, review the legacy-data migration described below. Do not deploy the new rules separately ahead of the callables: direct ride cancellation now requires the membership backend, costs require the cost backend, and new registration requires `registerProfile`. This new registration dependency is required even if account creation worked on an earlier build.

For local Android development, run Auth, Firestore and Functions emulators for the Android-configured project, then build with `-PuseFirebaseEmulator=true`. Only that explicit build option switches all three SDKs to `10.0.2.2` on ports 9099, 8080 and 5001. The normal build keeps live-project configuration. Android emulator accounts/data are separate from live accounts/data.

## Legacy participant IDs and privacy

Completed/cancelled ride reads now require current membership/organizer ownership or an explicit share by a current participant. Active ride search remains available to signed-in students. The membership hub and history use scoped queries instead of reading the entire collection. Sharing controls publish the ride document to other signed-in students; removing every participant’s share revokes future server reads for nonmembers.

A project owner must backfill missing `participantIds` in older rides BEFORE deploying the new privacy rules, so existing members do not disappear from membership/history queries. Review the dry-run report first, investigate invalid records, then explicitly apply the repair in the authorized project:

```sh
node functions/scripts/backfill-participant-ids.js --project trojanrideshare-672ad
# Only after reviewing the dry run:
node functions/scripts/backfill-participant-ids.js --project trojanrideshare-672ad --apply
```

The script defaults to read-only, synchronizes only IDs derived from current participants, and rejects stale writes if a ride changes concurrently. No live migration has been run.

See [testing bug-log follow-up](testing-bugs-followup.md) for the status of each issue in the team’s Google Doc.

## Repeatable checks

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
node --test functions/test/membership.test.js functions/test/costs.test.js
cd functions && npm run test:emulator
# Separately, from functions/, once the previous emulators have stopped:
npm run test:network
# From repo root, after the preceding emulator has shut down:
cd integration-tests && npm test
```

## Manual/demo follow-up checks

1. With the required backend deployed (or local emulators running), register three distinct USC users. Verify duplicate USC IDs are rejected without partial profiles.
2. Organizer creates a ride. Other students find it through search, view its details/profile, and submit requests. Accept the last seat and confirm automatic rejection of other pending requests on both accounts.
3. Verify pending withdrawal, leave/reopened seats, and latest-accepted organizer transfer before the original three-hour cutoff. Confirm late controls cannot bypass the server cutoff.
4. Cancel a different ride with pending requests; confirm all accounts refresh consistently and history shows cancellation.
5. Complete a departed ride using equal and custom cost splits. Confirm exact cent totals, actual shares in history, and the scheduled-month summary.
6. Sign in as each rider to mark their own share paid. Use test data to verify seven-day overdue flags, public profile flags and clearing flags on payment. Payment status is a record in the app; no financial transfer is implemented.
7. Test logout/login, back navigation, rotation, app restart and a network interruption. Confirm no prior account’s private profile or stale controls remain visible.

The main multi-account Android flow above is verified locally. No live-project end-to-end result is claimed. Continue with advanced custom-split UI, concurrent physical devices and device-level network interruption as needed for the team demo.
