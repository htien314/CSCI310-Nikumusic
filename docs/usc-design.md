# USC design branch

`design/usc-polish` is based on `integration/full-app` at `09e667c`.

The redesign uses cardinal and gold, cream surfaces, coordinated dark-mode colors, custom vector navigation icons, rounded cards, persistent field labels, password visibility controls and a visible back button. Search groups date/time/seat/sort filters in an expandable section; all filters remain available and retain their values. Existing view IDs, click handlers and feature destinations are preserved. Dynamically created membership, participant and payment controls follow the same theme.

Android 17 local demos now declare the debug local-network permission and request it only when emulator mode is enabled. Allow the system prompt to connect to the local Firebase backend. The normal live build does not request local-network access.

## Verification

- 218 Android unit tests passed.
- 36 backend unit tests passed.
- 16 callable/Firestore integration tests passed on isolated port 8180.
- 19 account/profile/search/privacy Firestore tests passed.
- Six Android device checks passed: three existing account-navigation regressions and three new checks covering screen inflation, access to every expanded search filter and header-back preservation of entered account data. The screen test exports review PNGs to the app's external files directory under `design-review`.
- Debug APK build and lint passed; lint retains warnings.
- Every original layout ID remains present, no source files are removed, and backend code, Firestore rules/indexes and Firebase configuration are identical to the base branch.

Database verification used the demo project `demo-trojan-membership`. An initial check used a different project name while the backend tests hardcode that name, which invalidated test isolation. Rerunning with the correct project name passed all 16 backend and 19 account/privacy cases. No changes to backend code or rules were needed.

## Demo checklist handoff

All five feature areas remain connected: accounts/public profiles; ride creation/details and status changes; search and membership; completion/custom and equal costs, payments/flags; history and monthly summaries. Tests cover these underlying validation, authorization, cutoff, concurrency, split, payment and summary rules. This design pass has not rerun the entire multi-account Android workflow or manually reviewed every screen. Do those before claiming complete demo readiness or rubric coverage.

Check the main flows on a small screen and at large font scale: login/register/logout, post/search/request/approve, departure and ownership transfer, complete/split/pay/flag, history filters, monthly summaries and opt-in public sharing. Check invalid input, empty results, rotation and network interruption. The precise seven-day flag threshold, original three-hour cutoff, immutable ride data and exact no-activity message are unchanged.

The local demo remains separate from the live Firebase project. The live deployment and migration prerequisites documented in `full-app-integration.md` still apply. A grade cannot be guaranteed by a redesign or automated test results.
