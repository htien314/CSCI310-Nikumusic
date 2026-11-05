# Nikhil: ride search and locations

## Scope delivered

- `SearchCriteria`, `SearchService`, `SearchResult`, `RideSortOrder`, and the Search tab (`ui/search`).
- `LocationGateway` contract and its Android Geocoder adapter, the shared `LocationPickerView`, and `util/Distance`.
- Search by pickup AND destination. A ride matches only if its origin is within the radius of the pickup and its destination is within the radius of the destination (UR-3.3). The radius boundary counts as inside.
- Filters: date, departure-time window (earliest and/or latest, can run past midnight), and minimum open seats (Any includes Full rides).
- Sorting: closest then earliest (default), closest, earliest first, latest first. Proximity = origin distance + destination distance (UR-3.7).
- Before a search, the tab lists upcoming rides with open seats, earliest first ("browse").
- Results leave out: Cancelled/Completed rides, rides whose current departure time has passed, rides with unreadable locations, and rides the signed-in user organizes or has already joined. Delayed rides with seats still show (UR-3.5), using their delayed time.
- Results stay current. Both the browse list and search results listen to Firestore, so a ride that fills up, is delayed, cancelled or newly posted updates on screen without a refresh.
- Each result opens Ada's ride details on tap, and has a Request to Join button that opens Hailey's request screen with the ride ID.

## Units and matching (team decision)

All radii are in **miles**. `Distance.UNIT`, `Distance.withinRadius(a, b, radiusMiles)`, and `Location.distanceMilesTo(other)` are the shared versions. Distance is the straight-line (haversine) distance, not driving distance. Search accepts 0 < radius ≤ 100 miles; the screen defaults to 5.

## Location contract (for Ada and Riya)

`LocationGateway` (in `service/`):

- `lookup(query, callback)`: best single match as a `Location` (address, latitude, longitude), or an error whose message can be shown as is.
- `suggest(query, maxResults, callback)`: up to N matches, best first, so the user can choose. Has a default implementation, so existing fakes keep compiling.

`LocationPickerView` (in `ui/common/`) is the reusable input. The user types a place and taps the search icon (or the keyboard's search key). One match is selected right away; several matches show a "Which one did you mean?" list. The field shows "✓ Location found" or the error under the box.

```xml
<com.example.trojanrideshare.ui.common.LocationPickerView
    android:id="@+id/origin_picker"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/create_ride_origin" />
```

```java
originPicker.setGateway(new GeocoderLocationGateway(requireContext()));

// On submit: uses the chosen place, or looks up the typed text if nothing was chosen
originPicker.resolve(new Callback<Location>() { ... });
```

Give each picker its own `android:id`; it saves its own text and chosen place across rotation. `getSelectedLocation()` and `getText()` are available if you'd rather pass both to your ViewModel, as `SearchViewModel` does.

- **Ada:** Create Ride currently uses plain text fields and looks them up on submit, which still works. Swapping the two fields for pickers lets organizers confirm the exact place before posting. I haven't changed your screen.
- **Riya:** for history radius search, use a picker (or `lookup`) for the keyword location and `Distance.withinRadius(...)` against each ride's origin/destination, so history and ride search agree.

## API and configuration

The adapter is `GeocoderLocationGateway`, which uses Android's built-in `Geocoder`. On devices and emulators with Google Play services it calls Google's geocoding service.

- **No API key is needed.** The `MAPS_API_KEY` line in the README is only required if we switch to the Places SDK later.
- **Emulator:** a "Google APIs" or "Google Play" image is preferred. Google's provider can hang (`DEADLINE_EXCEEDED`) when Play services fails its device check, which happens on some emulator images.
- **Fallback:** if the Geocoder fails, takes longer than 8 seconds or finds nothing, `NominatimLocationGateway` asks OpenStreetMap's free Nominatim service instead (no key; identifying User-Agent; well under its one-request-per-second limit). After one timeout, the rest of that app run goes straight to OpenStreetMap. OpenStreetMap is less forgiving of misspellings than Google.
- Lookups are biased toward Southern California first, so "LAX" means the airport.
- Lookups run off the main thread. A failed Geocoder call is retried once.

To switch to Google Places later, write another `LocationGateway` implementation and swap it in the two ViewModel factories and the fragment. Nothing else depends on Geocoder.

## Error and empty-state behavior

| Situation | What the user sees |
| --- | --- |
| Pickup or destination blank | "Enter a pickup location and a destination." |
| Place not found | `Couldn't find "<text>".` on the search error banner (or under the picker) |
| No network during lookup | "Couldn't search for locations. Check your connection and try again." |
| Device has no geocoder | "Location search isn't available on this device. Use an emulator image with Google APIs." |
| Radius not a number / ≤ 0 / > 100 | "Enter a radius in miles." / "The radius has to be more than 0 miles." / "The radius can be at most 100 miles." |
| Firestore read denied (e.g. logged out) | "You don't have permission to see rides. Make sure you're logged in." |
| Firestore unreachable | "Couldn't load rides. Check your connection and try again." with Retry |
| No matches | "No rides match your search. Try a bigger radius or fewer filters." |
| Nothing to browse | "No upcoming rides with open seats right now." |
| A slow lookup finishes after a newer search started | Ignored, so old results never replace newer ones |
| A ride document can't be read | Skipped and logged; the rest of the list still loads |

## Data access changes

Search needs to query rides, so `RideRepository` (Ada's interface) gained two read-only methods. No existing method changed.

- `findActive(callback)`: one-time load of On-Time and Delayed rides.
- `listenToActive(callback)`: the same query as a live listener; returns a `Subscription` to cancel.

Both use `whereIn("activityStatus", ["ON_TIME", "DELAYED"])`, which needs no composite index. Firestore errors are translated to `SecurityException` (permission denied / unauthenticated) and `IOException` (unavailable / timeout) so `SearchService` doesn't depend on Firestore. Hailey's `firestore.rules` already allows any signed-in user to read rides; search needs nothing more.

The radius, date, time and seat checks run in `SearchService` after loading, because Firestore can't filter by distance. That's fine at class-project scale and keeps every rule unit-testable.

## Changes from the Project 2.2 design

| 2.2 design | Implementation | Why |
| --- | --- | --- |
| `SearchCriteria.radiusKm` | `radiusMiles` | US users think in miles; one unit for the whole app (plan section 7). |
| `openSeatsOnly: boolean` | `minSeats: int` (0 = any) | Lets a group of friends ask for 2+ seats. 1 is the old "open seats only". |
| `dateRange` | `date` + `earliestTime`/`latestTime` | Matches UR-3.4 (a date and a time window). |
| `search(criteria): List<Ride>` | `search`/`watch(criteria, Callback<List<SearchResult>>)` | Firestore is async; results carry the two distances shown on screen; `watch` keeps results current. |
| `LocationGateway.resolve(query)`, `distance(a, b)` | `lookup`, `suggest`; distance on `Location` / `Distance` | Distance needs no network, so it isn't a gateway call. `suggest` supports choosing between matches. |
| `RideRepository.search()` | `findActive()` + `listenToActive()` | Repository loads candidates; matching rules live in the service layer. |
| — | `watchUpcoming` (browse) and own-ride exclusion | Plan asks for browsing open rides posted by other users. |

## Tests

`SearchServiceTest` (38 tests), `SearchViewModelTest` (18), `DistanceTest` (3), and new `LocationTest` cases cover: both-radius requirement (origin-only and destination-only matches rejected), exact boundary distances on each end, reversed trips, malformed locations, status filtering, departed rides, delayed-time handling, own/joined ride exclusion, seat filter boundaries, LA-timezone date filter, inclusive and overnight time windows, all four sort orders and tie-breaks, live updates after cancel/fill, unsubscribe, permission and network errors, and stale lookups.

Run with:

```sh
./gradlew :app:testDebugUnitTest
```

These were run against the service, model and ViewModel code with stand-ins for Firebase/LiveData (101 tests passing, including Ada's existing suite). The fragment, picker view and Firestore adapter still need a real Android build and a manual check on an emulator, ideally with two signed-in accounts: post a ride on one, confirm it appears on the other's Search tab, fill or cancel it, and confirm the list updates.
