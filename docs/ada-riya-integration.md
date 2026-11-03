# Ada + Riya integration

Branch: `integration/ada-riya`, based on `main`.

Merged source heads:
- Ada: `ce542c7955d68cf84d11603e790c895173e02411`
- Riya: `5cf1c8d9ecafb8dc82c7f6c414ba1eb8c9fc9b80`

## Connected behavior

The app starts on Profile, where users can register or log in. Create Ride uses the real Firebase session and loads the student's private profile through a repository adapter before saving the organizer UID and nickname. There is no debug-user fallback. A signed-out submit opens Log In. Profile rides link to Ada's details, and details link to Riya's participant/public-profile flow. Returning to the profile refreshes the ride list.

The Gradle merge preserves Ada's ViewModel test dependency and Riya's RecyclerView dependency. The duplicate BuildConfig block was removed. The debug emulator domain explicitly sets includeSubdomains=false so Android lint succeeds.

## Verification

From the project root:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
npm ci --prefix integration-tests
npm test --prefix integration-tests
```

The database tests start a local Firestore emulator under `demo-trojan-ada-riya`; they never use the live team database. They cover atomic registration, duplicate USC-ID rejection without partial profiles, nickname consistency, private/public access, real-organizer ride creation and queries, persisted status updates, ownership restrictions, immutable ride metadata, cancellation, sharing and history-request query permissions. Java tests cover the profile-to-ride identity flow and signup validation boundaries, in addition to Ada's existing suite.

## Manual check after team review

With the team's reviewed Firebase rules available in the selected environment:
1. Register a USC test student, leaving nickname blank, then verify the profile defaults to the full name.
2. Create a future ride with valid origin/destination. Verify the organizer's UID and displayed name match the student.
3. Return to Profile, open the saved ride, update its status, and reopen it to confirm persistence.
4. Open participant profiles. Toggle the ride's public sharing on Profile and check visibility using another account.
5. Log out, try Create Ride and confirm that it opens Log In rather than using a test identity.
6. Log in as another student and confirm that organizer controls are unavailable on the first student's ride.

Android device/UI and live-project end-to-end verification are still required. No Firebase rules or services have been deployed by this integration.

## Next integration stages

Merge search, membership and cost functionality separately after this foundation is reviewed. Their screens remain placeholders on this branch. Keep Riya's private/public profile and USC-ID reservation rules when bringing in membership; preserve membership's functions/index configuration. Reconcile shared RideRepository changes when bringing in search and cost. Cost/payment writes still need their own backend authorization. History's remaining rubric fields/filters, overdue profile indicators, and the scheduled-month summary correction remain later work.

Verified results: 66 Android unit tests passed, debug APK assembled, Android lint completed successfully, and 13 Firestore emulator integration tests passed. The Firebase CLI is development-only test tooling and currently reports upstream transitive dependency advisories. No production dependency overrides or Firebase deployment changes were made.
