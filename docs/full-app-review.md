# Full integration and UI review — November 3, 2026

Reviewed base: `origin/integration/full-app`, commit `09e667c`. Changes are on the separate `riya-full-ui-1` branch. The earlier integration checkout and its uncommitted work were preserved. No Firebase deployment or live data migration was performed.

## Current automated results

| Check | Result |
| --- | --- |
| Debug APK, Java tests, Android lint | Build passed; 220 unit tests passed; lint has warnings, no errors |
| Backend unit tests | 37 passed; emulator-only entry skipped in this command and exercised separately |
| Firestore account/profile/search/privacy | 21 passed |
| Membership/cost transaction integration | 16 passed |
| Real Firebase JS SDK against local Auth/Firestore/Functions HTTP endpoints | 1 complete multi-account lifecycle passed |
| Android emulator | 10 methods passed: 3 multi-account/custom-split/restart workflows, 5 navigation checks, 2 stale-location checks |

The current real-geocoding Android attempt failed during ride creation: Google's Android provider returned `DEADLINE_EXCEEDED`. The passing Android run explicitly uses **fixed USC/LAX test locations**, only in a separate debug APK with a visible banner. It does not verify real geocoding or live Firebase. The default APK still uses the real geocoder.

## Copy-and-paste issue list

1. FIXED — Returning to Profile could restore a stale login/signup screen. Profile now opens a fresh account root while preserving other tabs' saved forms. Files: `ui/auth/AccountNavigation.java`, `MainActivity.java`. Five device navigation checks cover the behavior and the new accessible back button.
2. FIXED — An old location lookup could overwrite edited text or submit coordinates for an earlier address. Editing, resetting and detaching now invalidate pending responses; stale form resolutions return an error. File: `ui/common/LocationPickerView.java`. Two device regressions pass.
3. FIXED — Custom cost split controls showed Firebase UIDs instead of participant names. Fields and equal-split previews now show names, while backend keys remain UIDs. File: `ui/ride/CompleteRideFragment.java`.
4. FIXED — Money entry could accept exponent notation or decimals beyond cents. A shared strict parser accepts plain dollars/cents; totals must be positive; an individual custom share may be zero. The UI blocks mismatched totals. Files: `util/MoneyParser.java`, `ui/ride/CompleteRideFragment.java`. Unit and device checks pass.
5. FIXED — Automatic rejection did not explain cancellation/full/completed closure. Backend requests now carry `closedReason`; the requester sees the reason without receiving access to the private ride. Files: `model/JoinRequest.java`, `ui/membership/MembershipFragment.java`, `functions/membership.js`, `functions/index.js`.
6. FIXED — Nickname/name limits and ride title/note/address validation were inconsistent across screens and rules. Client validation now fails early, and rules require the organizer's actual nickname, explicit initial participant IDs and allowed fields. Files: `service/RegistrationValidator.java`, `service/AuthService.java`, `service/RideService.java`, `data/UserRepository.java`, `firestore.rules`.
7. FIXED — Payment reloads and monthly summaries could leave old values/controls visible during a new request or account transition. Payment rows and summary totals now clear during loading; summaries refresh on return and reject old-account callbacks. Files: `ui/ride/PaymentTrackingFragment.java`, `ui/ride/MonthlySummaryFragment.java`.
8. REDESIGNED — Inconsistent colors, spacing, inputs, action hierarchy, icons and dense search/results. A shared emerald/mint theme, day/night palettes, car illustration, rounded cards, readable ride summaries, collapsible search filters and a labeled bottom bar now span authentication, profile, search, creation, membership, participants, details, completion, payments, history and summary screens. Shared resources: `res/values/themes.xml`, `res/values/colors.xml`, `res/values-night/colors.xml`, `ui/common/UiStyle.java` and the fragment layouts.
9. OPEN — Real Android geocoding timed out on the emulator. File: `data/GeocoderLocationGateway.java`. Retest USC/LAX lookups with a functioning provider/network and on a physical device; do not treat fixture results as a fix for address lookup.
10. OPEN — The default APK needs the integrated Firebase backend and matching rules/indexes in the shared project. Local success does not make the live project ready. Review all three callables and the legacy participant-ID migration before an authorized deployment. See `docs/full-app-integration.md`.
11. OPEN — University email format is validated, but institutional identity/email ownership is not verified. Rules allow signed-in accounts to read active rides/public profiles; membership callables do not universally require a registered USC profile. If access must be limited to verified USC students, add a verification/profile gate across rules and backend together. See `docs/firestore-review.json`.
12. FOLLOW-UP — Android lint still reports 199 warnings, mainly hardcoded/display text and localization, plus dependency/version and autofill suggestions. No lint errors. Device-level network interruption and concurrent physical-device testing remain unverified.

All Java paths above are relative to `app/src/main/java/com/example/trojanrideshare/`; Android resources are under `app/src/main/`. Backend/rules/docs paths are relative to the repository root.

## UI direction

The supplied reference informed the white/mint surfaces, emerald actions, shared-car illustration, compact identity/route cards and restrained navigation. Displayed ride costs, names and availability come from actual app data; no fabricated ratings or vehicle data were added. Light/dark color resources provide semantic text/surface colors, and child destinations expose a labeled back control. Manual light-theme review on Pixel 4/API 33 checked Profile, welcome/sign-in, Search, ride creation, My Rides cards, membership and ride details. Dark theme, large-font settings and additional screen sizes still need visual review.

## Repeat locally

See [local device testing](local-device-testing.md). Run the Firebase suites sequentially because they use the same emulator ports. Device workflow:

```sh
TROJAN_TEST_LOCATIONS=fixture npm run test:device --prefix functions
npm run demo:local --prefix functions
```

The demo uses `com.example.trojanrideshare.integrationtest`, `demo-trojan-device`, disposable local accounts and separate saved data. It does not change the normal app's saved account or the shared Firebase database.
