# Ada + Riya + Nikhil integration

Branch: `integration/ada-riya-nikhil`. This is a separate branch; no changes to main or teammates' feature branches.

## Sources and scope

- Existing Ada/Riya integration: `158edc6a9e1993bdb53f25c01f9fb830202e41b8`.
- Nikhil search/locations: `d6afb5b1e067d00088ee798b65791af47fdbaf17`.
- Merge commit: `70705f3` (both source heads are parents).
- All source histories and existing screens are preserved. Hailey's membership and Estelle's cost/payment branches are deliberately deferred at the user's request.

## Integration behavior

Profile remains the entry point. Authenticated users can create geocoded rides and find other users' rides on Search. Results open Ada's details, which link to Riya's participant/public profiles. Search applies both endpoint radii in miles, date/time and seat filters, current delayed times, proximity and time sorting, and Firestore listeners.

Search now restarts for a changed Firebase UID when returning from Profile, while retaining the same user's active search across navigation/rotation. This fixes stale own/joined-ride exclusion after account switching and retries a previous signed-out permission failure after login. Pending geocoder callbacks are invalidated when the search ViewModel is cleared, so they cannot create an orphaned database listener.

The README now describes the actual Geocoder adapter: no Places key is required; use a Google APIs or Google Play emulator image.

## Firestore review after the merge

`firestore.rules` and `firebase.json` are identical to the Ada/Riya integration. Nikhil's read query uses `activityStatus in [ON_TIME, DELAYED]`, covered by the existing authenticated ride reads; no broader read/write permission or composite index was introduced.

The emulator tests confirm cross-account search returns On-Time/Delayed rides and excludes Cancelled rides, and signed-out search fails. Existing tests continue to cover private profiles, public identity consistency, USC-ID reservations, organizer-only writes, immutable posted fields and final cancellation.

This is a compatibility check, not a complete security audit. For example, current ride reads allow any authenticated user to read ride documents; opt-in public profile sharing controls the profile presentation, not document-level access to all ride data. Registration field checks are more extensive in application code than in the current backend rules. Membership writes remain denied until that feature's backend is integrated.

Live deployed Firebase rules have **not** been retrieved or changed. The Computer Use tool reported permissions were not granted when attempting browser access. Compare the live rules with this branch before a live demo; an emulator pass does not prove the deployed project uses the same rules. No live test accounts or ride data were created.

## Verification

- Android unit tests: **131 passed**, zero failures/errors/skips (including three new session/lifecycle regressions).
- `:app:assembleDebug`: passed; debug APK generated.
- `:app:lintDebug`: passed with warnings, zero errors. Existing warnings include hardcoded strings and dependency recommendations.
- Firestore emulator tests: **15 passed**, zero failures, under the isolated demo project `demo-trojan-ada-riya`.
- No teammate files deleted relative to either integration source.
- Android device/emulator UI, real geocoding availability and live multi-account flows still require manual verification.

Standard checks:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
npm ci --prefix integration-tests
npm test --prefix integration-tests
```

On a restricted workstation, point `ANDROID_USER_HOME` at a writable scratch directory for debug signing. Firebase CLI may need writable `XDG_CONFIG_HOME` and `FIREBASE_EMULATORS_PATH` directories; using scratch directories resolved the test launcher's post-test configuration error here. These are local environment settings, not application changes.

## Part 2.3 readiness and remaining work

This branch is an integration milestone, not a claim that the full demo checklist is complete.

| Area | Present here | Still needed |
| --- | --- | --- |
| Account/profile | Registration/login/logout, display-name editing, private/public profile navigation and shared rides | Live multi-account verification and backend validation review |
| Ride management | Create/details, geocoded endpoints, capacity validation, organizer status/delay/cancel controls | Full/open membership transitions and completion depend on deferred features |
| Search | Both endpoint radii, date/time/seat filtering, earliest/latest/proximity ordering, live candidate updates | Device checks; Request to Join still opens Hailey's placeholder |
| Membership | Existing navigation entry points retained | Merge Hailey's request/inbox/approval/leave/ownership-transfer implementation and backend authorization |
| Costs/payments/monthly | Existing navigation placeholders retained | Merge Estelle's completion/splits/payment/flags/summary implementation and backend authorization |
| History | Completed/cancelled lists, keyword/status filtering, oldest/newest sorting | Endpoint radius and participant filters, participant counts, assigned actual shares and paid status; current history displays expected cost |
| Submission | Existing design and implementation references | Final implementation PDF, complete-project ZIP and demo video after full integration |

Manual handoff: sign up/login, post a future USC-to-LAX ride, find it from another account with both search endpoints, change its delay as organizer and verify the other account's results, open ride details and public profiles, switch accounts and confirm own rides are excluded for the new account, reopen the app, then verify persistence. Test with both a matching pair and an out-of-radius destination. Membership and costs are not part of this branch's working demo.
