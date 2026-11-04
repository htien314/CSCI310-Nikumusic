# Running on the free Spark plan (no Cloud Functions)

Branch: `spark-client-backend`, built on `riya-full-ui-1`. The team's version with Cloud Functions stays on `riya-full-ui-1` for comparison.

## Why

Cloud Functions can only be deployed on the paid Blaze plan. The team kept the shared project `trojanrideshare-672ad` on Spark, so the three callables (`registerProfile`, `membership`, `rideCosts`) were never deployed, and the normal app could not register, join, leave or complete rides against the shared database.

## What changed

The same business rules now run inside the app as Firestore transactions, and `firestore.rules` checks every write. Nothing calls Cloud Functions any more.

| Was a Cloud Function | Now | Files |
| --- | --- | --- |
| `registerProfile` | One batch writes the private profile, public profile and USC ID reservation. A taken USC ID makes the reservation write, and the batch, fail: "This USC ID is already registered." | `data/UserRepository.java` |
| `membership` | `MembershipTransition` (Java port of `functions/membership.js`) decides the outcome; `MembershipTransactions` reads the ride and requests and writes the result in one transaction. Ada's Cancel ride button uses it too. | `service/MembershipTransition.java`, `data/MembershipTransactions.java`, `data/FirebaseJoinRequestRepository.java`, `data/FirestoreRideRepository.java` |
| `rideCosts` | `CostTransition` (Java port of `functions/costs.js`) with a transaction in `FirebaseCostGateway`, including closing pending requests on completion and the public-profile overdue flag. | `service/CostTransition.java`, `data/FirebaseCostGateway.java` |

Join request documents are now named `{rideId}_{userId}`, so the rules can tell whose request a document is.

## What the rules enforce (server time, not the phone's clock)

- Requests: only under your own ID, for an active ride before departure, not your own, not full, not already joined; a rejected request can't be resubmitted; after cancelling or leaving, a rider may ask again.
- Only the current organizer accepts or rejects. Accepting must append exactly that requester and accept their request in the same write; capacity must become Full/Open correctly.
- Leaving: only yourself, at least three hours before the original departure. An organizer leaving others behind must hand the ride to the latest-joined rider; a solo organizer leaving cancels it. Cancelling the whole ride also needs three hours.
- Completion: organizer only, after the current departure, positive actual cost, `completedAt` stamped by the server, one non-negative share per participant adding up exactly to the total.
- Payments: a rider marks only their own share Paid, once; the organizer flags unpaid shares only seven days after completion. The public profile's `overdueRideIds` can only gain a ride that was just flagged and lose one that was just paid.

Not enforceable in rules, enforced by the app only: auto-rejecting the other pending requests when a ride fills or completes (rules can't sweep other documents). A modified app could skip it; the requests would just stay Pending.

## Checks

- `integration-tests`: 41 rules tests pass (the team's 21, one updated for organizer cancellation, plus 20 for the app-side flows in `client-membership-costs.test.cjs`).
- `npm run test:device --prefix functions`: all 10 Android phases pass with real geocoding (three accounts, last seat, leaving and rejoining, completion, flags, payments, history, monthly summary, cancellation, custom splits, restart, navigation). The script still starts the functions emulator, but the app never calls it.
- Android unit tests: 240 pass, including `MembershipTransitionTest` and `CostTransitionTest`.
- `functions/` tests still describe the old callables and are not used by this branch.

## Going live on the shared project (Spark is enough)

1. Delete the leftover test rides (titles ending in "delete me") in the Firebase console.
2. Fill in `participantIds` on the remaining older rides, so their members keep access under the new rules.
3. A project owner logs in to the Firebase CLI and deploys only rules and indexes:

```sh
functions/node_modules/.bin/firebase deploy --project trojanrideshare-672ad --only firestore:rules,firestore:indexes
```

Deploy the rules and the new app together: older builds that still call the functions will fail on register, join and complete either way.
