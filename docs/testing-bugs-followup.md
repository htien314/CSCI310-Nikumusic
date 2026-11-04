# Team testing bug log — integration follow-up

Source: [Testing Bugs](https://docs.google.com/document/d/15FRAEj1lNkPOT482A4AX6HrOdofcXNFCT6Is_tNKEbs/edit). Read November 3, 2026. That document describes `integration/ada-riya-nikhil`; the work below is on `integration/full-app`. The shared Google Doc has not been edited.

| Bug-log items | Current implementation and evidence |
| --- | --- |
| 1–2: Profile/login navigation and old auth screens | Profile selection/reselection returns to the profile root. Successful authentication uses the same stack-clearing helper. Three Android device regression tests pass. |
| 3: Student profile validation | Database rules reject non-USC email, invalid graduation years and blank names/nicknames. The trusted registration function validates fields and authenticated email too. Local rule/callable tests pass. |
| 4: Ride validation | Database rejects nonpositive estimates, invalid endpoints, past departures, forged initial cost records and invalid delay times. Valid organizer delay still passes. |
| 5: Public-sharing privacy | Completed/cancelled rides are restricted to members/organizer unless a participant explicitly shares the ride. Arbitrary strangers cannot publish another ride by adding its ID to their own sharing list. Sharing and revoking access pass both rule and SDK endpoint tests. Legacy participant IDs must be backfilled before live deployment. |
| 6–10: Requests, My Rides, membership, completion and payments | Integrated across all teammates’ code. Local multi-account SDK workflow and the main three-account Android screen workflow pass. |
| 11: Public payment information | Profiles display overdue flag counts; backend flag/payment transactions keep them consistent. Local endpoint and Android screen tests verify setting and clearing flags. |
| 12–13: Actual history shares, counts and filters | History uses assigned cost/payment state and participant count, with participant and geographic filters. Java regression tests pass. |
| 14: Monthly Summary | Real destination and repository are connected; scheduled-month, assigned-cost and organizer/joined calculations have passing unit tests. |
| 15: Duplicate USC-ID message | New authenticated `registerProfile` transaction returns “This USC ID is already registered.” Reservations remain private, and conflicts leave no partial profiles. SDK and concurrent transaction tests pass. Deploy this new function before testing new registrations on the live app. |

The passing local checks do not establish live deployment readiness. Still required for live use: owner review/deployment of rules, indexes and all three functions and legacy-ID dry run/backfill. The main local Android multi-account flow, real geocoding and process restart now pass; advanced UI scenarios and device-level network interruption remain follow-up checks. The local SDK test does verify sign-out/sign-in persistence and sharing revocation against the local database.

Detailed commands and deployment order: [full integration notes](full-app-integration.md).

## Full Android follow-up

The main three-account Android form/navigation flow and process-restart persistence now pass against local Firebase emulators, including real USC/LAX geocoding. A cancellation/privacy edge found during that test was fixed: rejected students now see their own request status after the private ride disappears from their accessible ride list. The team chose to keep Spark and use the [local demo workflow](local-device-testing.md); live deployment remains deferred.
