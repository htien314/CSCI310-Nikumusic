# TrojanRideShare

Android app (Java, XML layouts, Jetpack Navigation, Firebase Auth + Cloud Firestore) for USC students to coordinate shared rides.

## Setup
1. Clone the repo and open the folder in Android Studio.
2. `app/google-services.json` is already in the repo (it connects the app to our Firebase project). Ask Ada for Firebase console access if you don't have it yet.
3. Places API key (for location search): add `MAPS_API_KEY=your_key` to `local.properties`. That file is git-ignored, so never commit keys.
4. Sync Gradle, then run on an emulator (min SDK 26).

## Project structure (`app/src/main/java/com/example/trojanrideshare`)
| Package | What goes there |
|---|---|
| `model/` | Firestore data objects (Ride, Location, Participant, User, JoinRequest, CostShare, enums) |
| `data/` | Repository interfaces + Firestore implementations |
| `service/` | Business rules and services |
| `ui/<feature>/` | Fragments + ViewModels for each feature (e.g. `ui/ride`, `ui/search`, `ui/account`, `ui/membership`, `ui/cost`) |
| `ui/common/` | Shared UI: `UiState`, `LoadingErrorView`, `PlaceholderFragment` |
| `util/` | `Callback`, `Clock`, `Constants` |

## Conventions
- **Navigation:** all screens are destinations in `res/navigation/nav_graph.xml`. Each one starts as a `PlaceholderFragment`; replace its `android:name` with your Fragment class when your screen is ready. Pass rides between screens with the `rideId` argument.
- **Async results:** repositories and services report back through `Callback<T>`.
- **Time:** store Firestore `Timestamp`s (UTC); display in `Constants.DISPLAY_ZONE` (Los Angeles). Get "now" from a `Clock`.
- **Money:** store integer cents (`long`), never `double`.
- **Max riders includes the organizer** (2–6).

## Git workflow
- `main` stays buildable. Create a branch per person/feature, e.g. `ada/ride-management`.
- Open small pull requests; at least one teammate reviews before merging.
- Don't edit another member's service or screen without checking with them.

## Owners
| Area | Owner |
|---|---|
| Shared setup, rides (create, details, organizer controls) | Ada |
| Account, profile, ride history | Riya |
| Ride search + locations | Nikhil |
| Join requests + membership | Hailey |
| Cost split, payments, monthly summary | Estelle |
