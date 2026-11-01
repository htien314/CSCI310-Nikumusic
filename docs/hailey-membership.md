# Hailey: membership and join requests

## Scope delivered

- `JoinRequest`, `JoinRequestRepository`, Firebase adapter, `MembershipService`, ViewModel, and membership screen.
- Request with optional message, request timestamp, rider pending list, pending cancellation, organizer inbox, accept/reject, and participant display.
- Prevent requests to own/full/final rides, existing membership, duplicate pending requests, and prior rejection.
- Atomic approval, automatic rejection of all remaining pending requests at capacity, and capacity reopening after a departure.
- Leave at **at least three hours** before the **original** departure (exactly three hours is allowed); never leave cancelled/completed rides.
- Transfer organizer ownership to the latest accepted remaining participant; use participant array order to break equal timestamp ties. Cancel when the last organizer leaves. Preserve On-Time/Delayed on other departures.
- Shared listeners refresh membership, request state, and organizer authority across signed-in accounts.

## Branch dependency

This feature branch starts from `ada/ride-management` at `ce542c7` because the Ride, Participant, activity/capacity enums and shared ride UI have not been merged into main yet. The membership diff changes none of Ada's services or screens. Merge Ada's branch first, then retarget this feature PR to main. Riya's authentication must also be integrated for real sign-in; this feature never uses a debug identity.

## Implementation changes from Project 2.2

The existing Android flow remains Fragment -> ViewModel -> MembershipService -> JoinRequestRepository. Read listeners use Firestore. Membership writes call one Firebase callable named `membership` in `us-central1`, implemented within the **same shared Firebase project**, rather than performing security-sensitive state changes on the Android client.

The callable verifies Firebase authentication, obtains the user ID from the verified token, obtains the display name from the stored profile, and applies `functions/membership.js` inside an Admin SDK Firestore transaction. It reads the ride, pending requests, and relevant existing request before writing. Every operation writes the same ride, so concurrent submissions/approvals/departures serialize and retried transactions reevaluate current data and server time. A failed transaction changes neither requests nor participants. Only membership-owned ride fields are patched; route, departure, notes, and costs remain intact.

Rationale: client button checks cannot protect the participant list from a modified app, and device clocks cannot authoritatively enforce the cutoff. Firestore rules deny direct client membership/request writes. This adds a Cloud Functions dependency and a deployment step to the design; confirm the shared Firebase project supports Functions before the team deploys. The demo/local emulator requires no production deployment or live data.

## Data contract

Existing Ada ride fields are reused: `organizerId`, `participants` (ordered list of `{userId, displayName, acceptedAt}`), `maxRiders` including organizer, immutable `departureTime`, `activityStatus` (`ON_TIME`, `DELAYED`, `CANCELLED`, `COMPLETED`), and `capacityStatus` (`OPEN`, `FULL`, `NOT_APPLICABLE`). Participant count derives from the array; no second count field can drift.

`joinRequests/{sha256(JSON.stringify([rideId, uid]))}` stores:

| Field | Meaning |
| --- | --- |
| `rideId`, `userId` | Exact ride and authenticated requester |
| `displayName` | Nickname/full name snapshot at submission |
| `message` | Optional trimmed text, at most 1,000 characters |
| `status` | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| `createdAt`, `updatedAt` | Server Firestore timestamps |

`CANCELLED` is a design refinement to distinguish rider withdrawal from organizer rejection. A withdrawn pending request may be resubmitted; a rejected request remains blocked even after a seat reopens. A rider who leaves may request again unless previously rejected. This interprets the existing rule as blocking current membership, rather than a permanent ban on every formerly accepted rider. Confirm this interpretation with the team if the instructor requires otherwise.

## Handoffs

- **Ada:** existing models are consumed unchanged. `firestore.rules` permits existing status-only transactions and guards original departure/participant fields. Do not deploy looser overlapping ride write rules: Firestore combines matching allows with OR, which would bypass membership protection.
- **Nikhil:** open `R.id.action_global_requestsInbox` with a Bundle containing `Constants.ARG_RIDE_ID`. It presents the request action, current participants, request status, and organizer-only inbox. A simple open-ride entry list also lives in the membership hub so the feature remains reachable before search is connected.
- **Riya:** `myRidesFragment` now implements the My Rides & Requests tab. Supply FirebaseAuth sign-in and profiles at `users/{uid}` with nickname/fullName. The feature listens to authentication changes and discards old-account data/callbacks. Read queries and mutations use the signed-in identity. Reconcile the baseline self-only user rules with the public-profile and USC-ID uniqueness rules in Riya's work before deployment.
- **Estelle:** accepted participants and their order are in the ride's array. Completion must read the current ride, save the final shares, and mark it `COMPLETED` in a single transaction. The baseline rules intentionally deny direct completion/cost writes until that atomic completion contract and its rules are integrated. Membership cannot change after completion.

## Local checks

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
cd functions
npm ci
npm test
npm run test:emulator
```

The emulator command uses `demo-trojan-membership`, never the shared production project. It tests the actual callable handler against Firestore plus the included rules. Expected permission-denied logs come from tests that deliberately attempt unauthorized writes.

Results: 64 Android unit tests passed (8 new membership tests plus Ada's existing suite), debug APK assembled, Android lint completed successfully with no membership-file findings, 31 backend rule tests passed, and 10 Firestore emulator tests passed. Coverage includes two concurrent approvals for one seat, simultaneous duplicate requests, request/approval races, cross-ride requests, forged identity/time, organizer transfer, and direct write denial. These checks do not claim Android device/UI or live-project end-to-end verification.

## Team deployment and demo checklist

The shared project's existing live rules are not available in this repository. The included `firestore.rules` is a tested baseline for this branch, **not a verified replacement for teammates' unpublished rules**. Reconcile authentication/public-profile/cost contracts before deploying it. In particular, an existing broad write allow must be removed, not merely supplemented by a deny.

After team review, an authorized project owner can install the Firebase CLI, select the existing shared project explicitly, and deploy the membership function, indexes and reconciled rules. Use Node 22 for the backend. Do not create a second Firebase project/backend or enable project billing as part of this feature review.

Then verify with multiple signed-in accounts: create ride -> request from two riders -> organizer accepts last seat -> other requests rejected -> accepted rider leaves -> seat reopens -> organizer leaves -> latest accepted participant gains controls -> complete with costs -> no further joins/leaves. Confirm state after restart, retry after network loss, and document the actual integration outcome. No full-team end-to-end result is claimed yet.

Dependency audit after updating the new server libraries: no high/critical production advisories; four moderate transitive production advisories remain in upstream `google-gax`/glob tooling dependencies. The Firebase CLI development toolchain also reports upstream advisories. No forced downgrade or unverified major dependency override was applied.
