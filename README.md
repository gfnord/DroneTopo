# DroneTopo — Matrice 4TD GPS → CalTopo Shared Locations

A minimal Android app that runs **on the DJI RC Plus 2** controller bundled with the
**DJI Matrice 4TD** aircraft. It reads the aircraft's live GPS position via the
**DJI Mobile SDK V5 (MSDK 5.x, pinned to 5.18.0)** and reports each fix to a **CalTopo
Team** as a live beacon on the **Shared Locations** overlay (default every 5 s).

The first version is deliberately minimal and robust: **no camera, no gimbal, no flight
control.** Just: register the SDK, confirm aircraft connection, subscribe to the GPS
location key, and report the latest position to CalTopo every tick.

---

## Target hardware & SDK facts

| Item | Value |
|---|---|
| Aircraft | DJI Matrice 4TD (Matrice 4D Enterprise Series — on the MSDK V5 supported-product list) |
| Controller / install target | DJI RC Plus 2 (Android, **arm64-v8a** only) |
| SDK | DJI Mobile SDK **V5**, aircraft package set, version **5.18.0** |
| Toolchain | AGP **8.7.0** / Kotlin **2.1.0** / Gradle **8.9** / NDK **21.4.7075529** / `compileSdk=35` / `targetSdk=35` / `minSdk=24` |

> Do **not** use MSDK V4 — it does not support this airframe.

---

## ⚠️ Critical runtime gotcha — force-stop DJI Pilot 2 before launching

When a third-party MSDK app launches on the RC Plus 2, the official **DJI Pilot 2** app
must be **force-stopped** or it will hold the aircraft link and your third-party app will
not connect.

**Before every flight test:**

1. On the controller, open **Settings → Apps → DJI Pilot 2 → Force stop**
   (or use the notification tray shortcut on some firmware versions).
2. Confirm DJI Pilot 2 is no longer running.
3. Launch **DroneTopo GPS**.
4. Watch the *SDK phase* line: it should progress
   `INITIALIZING → REGISTERING → REGISTERED`, then `READY` once the aircraft is powered on
   and linked.

---

## Registering your DJI App Key

The DJI SDK requires an App Key tied to your package name.

1. Create a developer account at <https://developer.dji.com>.
2. Register a new app at <https://developer.dji.com/user/apps>.
3. **The package name bound to the App Key MUST EXACTLY match `applicationId`.**
   This project uses `applicationId = "br.com.nobrega.m4td.gps"`. Either:
   - register the key against `br.com.nobrega.m4td.gps`, or
   - change `applicationId` in `app/build.gradle` (and `namespace` to match) **before**
     registering, and update the `applicationId` in your DJI developer app.
4. Copy the generated **App Key** string.

Internet access is required for first-time registration. After the first successful
registration the SDK caches it locally on the controller.

---

## Injecting the App Key, signing material, and CalTopo defaults

Secrets are NEVER committed. There are three supported locations for properties; pick
one:

| Location | When to use | Committed? |
|---|---|---|
| `~/.gradle/gradle.properties` | Recommended — user-global secrets stay out of all repos | No |
| `<repo>/gradle.properties` (after editing the committed copy) | Quick project-local override | No (it would be — keep it ignored) |
| `<repo>/local.properties` | Project-local; mainly for `sdk.dir` | No (already in `.gitignore`) |

**Minimum required properties (overwrite the blanks in `gradle.properties`):**

```properties
# DJI App Key (the bound package name must equal applicationId).
AIRCRAFT_API_KEY=abcdef0123456789...

# Signing keystore. The DJI sample signs BOTH debug and release with a release
# keystore so signed builds install/run cleanly on the controller. Generate one:
#   keytool -genkey -v -keystore keystore/m4td.jks -keyalg RSA -keysize 2048 \
#           -validity 10000 -alias m4td
STORE_FILE=../keystore/m4td.jks
STORE_PASSWORD=...
KEY_ALIAS=m4td
KEY_PASSWORD=...
```

**CalTopo defaults** (the connect key is a shared secret — leave blank in
`gradle.properties` and enter it via the in-app **Settings** screen instead. Safe
non-secret defaults are already committed in `gradle.properties`):

```properties
DEFAULT_CALTOPO_BASE_URL=https://caltopo.com
DEFAULT_CALTOPO_CONNECT_KEY=
DEFAULT_DEVICE_ID=M4TD-01
DEFAULT_CALTOPO_REPORT_INTERVAL_SECONDS=5
DEFAULT_SKIP_INVALID_FIXES=true
```

The connect key (shared secret) lives only in SharedPreferences via the in-app Settings
screen — it is never written to a committed file.

---

## Building the APK

You need Android Studio (Koopa/Ladybug or newer) with:
- Android SDK platform 35
- Android Build-Tools 35.x
- NDK 21.4.7075529
- Kotlin plugin 2.1.0 (bundled)

```bash
# 1. From a fresh clone, bootstrap the Gradle wrapper (one-time):
gradle wrapper --gradle-version=8.9
#   (any system gradle >= 7 will do for this one-time bootstrap)

# 2. Configure secrets (see previous section).

# 3. Build the debug APK:
./gradlew :app:assembleDebug
#   -> app/build/outputs/apk/debug/app-debug.apk

# Or a signed release APK (requires signing config in gradle.properties):
./gradlew :app:assembleRelease
#   -> app/build/outputs/apk/release/app-release.apk
```

If you skip the wrapper bootstrap and open the project in Android Studio, the IDE will
offer to do it for you on first sync.

---

## Sideload onto the RC Plus 2

The RC Plus 2 is an Android device. Install the APK like any sideloaded Android app:

```bash
# Connect the controller to your computer via USB, enable USB debugging / ADB on it.
adb devices                       # confirm it shows up
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then on the controller:

1. **Force-stop DJI Pilot 2** (see above).
2. Launch **DroneTopo GPS** from the app drawer.
3. Open **Settings** and configure:
   - **Connect key** (shared secret from CalTopo Trackable Devices)
   - **Device ID** (combined with the connect key into the on-map call sign)
   - **Base URL** = `https://caltopo.com` (or staging override)
   - **Report interval (seconds)** (default 5)
   - **Skip invalid fixes** (recommended — drops `(0,0)` / NaN)
4. Power on the aircraft, wait for `READY` on the main screen.
5. Tap **Start streaming**. The notification tray shows an ongoing "Reporting as
   {call sign}" notification; this is the foreground service keeping the stream alive
   while the screen is dimmed or the app is backgrounded.

---

## CalTopo integration — Shared Locations beacon

Each valid fix is reported to CalTopo's Team **Shared Locations** overlay via the unsigned
connect-key position endpoint. The drone appears as a live, colored dot on any team map
that has the Shared Locations overlay enabled.

### Endpoint

```
GET {baseUrl}/api/v1/position/report/{CONNECT_KEY}?id={DEVICE_ID}&lat={LAT}&lng={LNG}
```

- `{CONNECT_KEY}` — the path segment. Either a key you typed (e.g. `SARTRUCKS`) or,
  preferably, the auto-generated key from **Team Admin → Trackable Devices → Create New
  Access URL** (harder to spoof).
- `id`, `lat`, `lng` are the only documented parameters. CalTopo timestamps each report
  **on receipt**, so no client timestamp is sent. `lng` (not `lon`) is required by
  CalTopo's API.
- The on-map **call sign** is composed by CalTopo as `{CONNECT_KEY}-{DEVICE_ID}`
  (e.g. `SARTRUCKS-M4TD-01`). The app does not send the call sign; it only echoes it in
  the UI so the operator can confirm it matches the Trackable Device.
- Worked example: `https://caltopo.com/api/v1/position/report/SARTRUCKS?id=14&lat=36.47375&lng=-118.85302`
- HTTP 200 = success. Non-200 is treated as a failure (counted as `fail` in the UI, not
  retried with backoff — see Reporting semantics below).

**Beyond `id`/`lat`/`lng`, nothing else is sent.** Altitude/heading/speed params are
**not** part of CalTopo's documented position-report API for custom devices, and altitude
is not shown for custom devices on the overlay. If you need those, treat any extra params
as experimental and verify against a live response before relying on them.

### Reporting semantics (latest-wins, no queue)

A SAR map must not show stale drone positions, so this is **not** a queue:

- A `@Volatile` holder keeps the most recent `LocationFix` from the aircraft.
- A coroutine ticker fires every `reportIntervalSeconds` (default **5 s**) and reports the
  current value of the holder.
- On failure, the fix is **dropped**. The next tick sends the then-current position
  rather than backfilling the missed one.
- Throttle range: 3–10 s is sane for a moving drone (CalTopo draws straight lines between
  consecutive reports, so faster reporting = smoother track). Every report is a round
  trip; do not push below 1 Hz.
- The reporter runs off the main thread inside the foreground `StreamingService`; network
  loss never crashes or blocks the GPS listener.

### Locale-safe decimal formatting (critical)

`lat`/`lng` are formatted with `Locale.US` and `%.6f`, guaranteeing a `.` decimal
separator regardless of the controller's locale. A comma-decimal locale (most of Europe)
would otherwise silently corrupt the request (`lat=49,323750` is not a valid query
parameter and CalTopo would either reject or mis-parse it). This is the single most common
bug class for GET-position-report integrations; the unit test in
`CalTopoPositionReporter` should cover it explicitly if you add tests.

### CalTopo-side setup

Two equivalent ways to register the beacon (the endpoint shape is identical):

1. **Manual (Add Other Device).**
   Team Admin → Trackable Devices → *Add Other Device*. Set Call Sign
   `{CONNECT_KEY}-{DEVICE_ID}`, label, color, Save.

2. **Connect key (recommended).**
   Team Admin → Trackable Devices → *Create New Access URL* → copy the generated connect
   key into the app's Settings screen. On first report the device auto-appears in
   Trackable Devices; edit its label/color there.

Then on any team map, enable the **Shared Locations** overlay (Map Overlays → Realtime
Data). The drone appears as a colored dot; clicking it shows coords + last update time,
and you can *Record to Map* to convert the beacon into a saved live track.

### Connect key is a shared secret

Anyone who learns the connect key can post positions under the resulting call sign.
Treat it like a password:

- Store it only via the in-app **Settings** screen (SharedPreferences, never committed
  to VCS).
- The app does **not** implement the signed Team API (`/api/v1/acct/...`,
  `/api/v1/map/...`) — that requires a service-account credential and HMAC-SHA256 and is
  not needed for live position beacons.
- For staging or self-test, override `DEFAULT_CALTOPO_BASE_URL` in `gradle.properties`.

### Dry-run before live GPS

Before wiring to live aircraft GPS, dry-run the URL from the controller's browser or via
`adb shell`:

```bash
adb shell 'curl -sS "https://caltopo.com/api/v1/position/report/SARTRUCKS?id=14&lat=36.47375&lng=-118.85302"'
```

Open the team map with Shared Locations enabled and confirm the dot appears at the
hardcoded lat/lng before relying on live data.

---

## Architecture

```
App (Application)
 +- owns DjiSdkManager (singleton; SDKManager callback lives here, NEVER on an Activity)
 |   +- owns AircraftLocationRepository (KeyManager listeners, isolates DJI types)
 |   +- exposes state: StateFlow<SdkState>, latestFix: StateFlow<LocationFix?>
 |
 +- MainActivity + MainViewModel
 |   +- observes SdkState, latestFix, ReportState; renders the status panel
 |   +- starts/stops StreamingService
 |
 +- StreamingService (foreground service, specialUse)
 |   +- owns CalTopoPositionReporter (OkHttp GET, latest-wins)
 |   +- @Volatile latestFix holder + coroutine ticker every reportIntervalSeconds
 |   +- ongoing notification + stop action
 |
 +- SettingsActivity
     +- SharedPreferences-backed form for CalTopo base URL / connect key / device id / interval / skip-invalid
```

**Reporting semantics in `CalTopoPositionReporter` / `StreamingService`:**

- Latest-wins: only the most recent valid fix is sent on each tick.
- No queue, no backfill: on failure the fix is dropped; the next tick sends the current position.
- Throttle: `reportIntervalSeconds` (default 5 s); 3–10 s recommended.
- `lat`/`lng` formatted with `Locale.US` + `%.6f` (never a comma decimal).
- Network loss never throws into the GPS listener.
- HTTPS is required by default. See `network_security_config.xml` if you must allow a
  self-signed cert for a specific on-prem host.

---

## Field-test checklist

1. **App Key & signing**
   - [ ] App Key bound to `br.com.nobrega.m4td.gps` on developer.dji.com
   - [ ] `gradle.properties` (`~/.gradle/gradle.properties`) has `AIRCRAFT_API_KEY`
   - [ ] `gradle.properties` has `STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
   - [ ] `./gradlew :app:assembleRelease` produces a signed APK
2. **Install on RC Plus 2**
   - [ ] APK sideloaded (`adb install -r ...`)
   - [ ] **DJI Pilot 2 force-stopped** before launch
3. **First launch (registration)**
   - [ ] Internet available on the controller (needed for first-time registration)
   - [ ] Status panel shows `INITIALIZING -> REGISTERING -> REGISTERED`
   - [ ] If `REGISTRATION FAILED`: check App Key, package-name match, internet
4. **Aircraft connection**
   - [ ] Aircraft powered on, RC linked
   - [ ] Status shows `READY` and `Connected (productId=...)`
5. **CalTopo-side setup (one-time)**
   - [ ] Team Admin → Trackable Devices → *Create New Access URL* (recommended)
     OR *Add Other Device* with Call Sign `{CONNECT_KEY}-{DEVICE_ID}`
   - [ ] Copy the connect key into the in-app Settings screen
   - [ ] Dry-run the URL from `adb shell` curl and confirm the dot appears on the team map
6. **In-app config**
   - [ ] Connect key set (treated as a secret — only stored in Settings, not in VCS)
   - [ ] Device ID set (e.g. `M4TD-01`)
   - [ ] Base URL = `https://caltopo.com` (or staging override)
   - [ ] Report interval = 5 s (3–10 s acceptable)
   - [ ] Skip invalid fixes ON
   - [ ] Call sign line shows `{CONNECT_KEY}-{DEVICE_ID}` matching the Trackable Device
7. **Locale sanity (critical bug class)**
   - [ ] Set the controller to a comma-decimal locale (e.g. German)
   - [ ] Confirm the URL still uses `.` decimals (check `adb logcat` / OkHttp log)
8. **Streaming**
   - [ ] Tap **Start streaming**
   - [ ] Foreground notification appears ("Reporting as {call sign}")
   - [ ] CalTopo report line shows `HTTP 200 · ok=N fail=0`
   - [ ] Drone dot on the team map matches the aircraft's actual position
   - [ ] Disconnect aircraft -> report pauses; on reconnect the latest position is sent
     (no stale backfill from when the link was down)
   - [ ] Disable controller network -> failure count climbs; no crash; on reconnect the
     current position is sent (no queue replay)
9. **Background behavior**
   - [ ] Press power to dim the screen -> stream continues (foreground service)
   - [ ] Press Home to background the app -> stream continues
10. **Teardown**
    - [ ] Tap **Stop streaming** -> notification clears, listeners cancelled
    - [ ] Reboot the controller -> service does NOT auto-start (start manually)
11. **Payload sanity**
    - [ ] Spot-check the team map: drone position near the actual aircraft
    - [ ] Only `id`/`lat`/`lng` are sent (altitude is not displayed for custom devices)

---

## Verified API facts (MSDK V5 5.18.0)

All SDK identifiers below are confirmed against the official DJI V5 API reference and
sample code ([github.com/dji-sdk/Mobile-SDK-Android-V5](https://github.com/dji-sdk/Mobile-SDK-Android-V5))
— no guessing in the generated code:

| Identifier | Package | Returns / Notes |
|---|---|---|
| `SDKManager` | `dji.v5.manager` | `.getInstance().init(Context, SDKManagerCallback)` |
| `SDKManagerCallback` | `dji.v5.manager.interfaces` | 7 methods (init/register/connect/disconnect/productChanged/dbDownload) |
| `DJISDKInitEvent` | `dji.v5.common.register` | `INITIALIZE_COMPLETE` gates `registerApp()` |
| `IDJIError` | `dji.v5.common.error` | `.errorCode()`, `.description()` |
| `KeyManager` | `dji.v5.manager` | `.listen(key, holder, cb)`, `.cancelListen(holder)` |
| `KeyTools` | `dji.sdk.keyvalue.key` | `.createKey(FlightControllerKey.X)` |
| `FlightControllerKey` | `dji.sdk.keyvalue.key` | Holds the static key constants below |
| `KeyAircraftLocation3D` | as above | `LocationCoordinate3D` (`dji.sdk.keyvalue.value.common`) with `latitude`/`longitude`/`altitude` |
| `KeyGPSSatelliteCount` | as above | `Integer` |
| `KeyGPSSignalLevel` | as above | `GPSSignalLevel` enum (`dji.sdk.keyvalue.value.flightcontroller`): `LEVEL_1`..`LEVEL_5`, `LEVEL_10`, `UNKNOWN` |
| `Helper.install(this)` | `com.cySdkyc.clx.Helper` | Required in `Application.attachBaseContext()` |

**Altitude semantics:** `KeyAircraftLocation3D`'s altitude is **relative to the takeoff /
home point** (verified against the 5.18.0 API reference). The JSON payload therefore
labels `altitudeReference` as `"takeoff_relative"`. If you swap in an MSL or ellipsoidal
altitude key, update `AltitudeReference` in `AircraftLocationRepository.kt` accordingly.

The Maven repository declarations, `packagingOptions`, and toolchain versions in this
project are taken verbatim from the 5.18.0 official sample.

---

## Reference docs

- MSDK V5 tutorial: <https://developer.dji.com/doc/mobile-sdk-tutorial/en/>
- "Notice of Run MSDK" (Helper.install, maven repos, packaging): <https://developer.dji.com/doc/mobile-sdk-tutorial/en/quick-start/user-project-caution.html>
- `ISDKManager` API reference (init/register/connect): <https://developer.dji.com/api-reference-v5/android-api/Components/SDKManager/DJISDKManager.html>
- Official V5 sample (repo config, manifest, packaging): <https://github.com/dji-sdk/Mobile-SDK-Android-V5>
