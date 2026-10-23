# Push arrival notifications

On the registered applications screen, enable **Show system notification when a
push arrives** for each desired application. It is off by default. Registrations
belonging to the same package share the switch, including when channel IDs change.
Grant Sunup notification permission if prompted. The reminder uses the separate
**App push arrival reminders** channel, whose sound can be changed in Android's
notification settings. Disabling the switch also removes that application's
current reminder.

The notification reports a UnifiedPush transport arrival, not a chat message or
unread count. Sunup never decrypts or inspects the payload. Tapping opens the
registered application, or Sunup if that application has no launch activity.
Multiple arrivals update one dismissible notification per package. The displayed
count resets after dismissal; alerts are limited to once per 30 seconds while the
notification remains active. The active system notification holds the count and
last alert time, so recreating Sunup's process does not reset an existing reminder.
These counts are separate from the existing registration delivery counters.

## Implementation

- `api/ServerConnection.kt`: only decoded `ServerMessage.Notification` frames enqueue
  a reminder, immediately before the existing forwarding and ACK path. No change
  to nonempty payload bytes, library registration logic, forwarding or ACK policy.
  Missing data is treated as an empty wake-up push. Optional notification fields
  have serialization defaults so empty wake-up frames can be accepted.
- `arrival/PushArrivalNotifier.kt`: uses the shared coroutine IO pool with serialized
  notification work. Neither package lookup, SQLite nor NotificationManager runs
  on the WebSocket callback. Exceptions are contained in this optional work.
  No target-app response or business-server request is involved. No extra network
  connection, dedicated thread, polling job or service is added.
- `arrival/ArrivalStore.kt`: separate SQLite database `push_arrival.db`, leaving the
  UnifiedPush library's registration schema untouched. The package-name primary key
  is independent of connector/channel IDs. Each read queries disk; each setting
  change commits before the UI updates. Final notification posting is serialized
  with disable/removal writes, followed by cancellation after a disable. The `:ui` process and main service process
  do not share a preference cache. Settings survive process recreation and reboot.
  Unregistering keeps preferences for later re-registration; full package removal
  clears the preference and notification, while package replacement does not.
- Reminder deduplication uses server URL, UAID, channel ID and transport version,
  retained for at most 24 hours and 512 identities on disk. It does not compare
  ciphertext, merge independent channel IDs, or suppress forwarding/ACKs. This is
  bounded best-effort reminder deduplication; sufficiently old replays can remind
  again. No payload is stored in the new database.
- `activities/SunupUi.kt`, `MainScreen.kt`, `RegistrationList.kt`: narrow local
  adaptations of Apache-2.0 UnifiedPush UI 0.7.6. That dependency has no registration
  row extension point. They preserve its navigation, selection/deletion, counts,
  endpoint copying, permissions and settings components. `ArrivalSettings.kt` adds
  switches and notification permission/channel guidance. Existing navigation and
  icon artifacts are declared directly, using the dependency's existing versions.
- `values/strings.xml` and `values-zh-rCN/strings.xml`: English and Simplified Chinese.
  `app/lint.xml` explicitly permits English fallback for these resources and the
  existing upstream privacy text; other lint checks remain enabled.

## Build and GitHub Actions

Use JDK 25, the checked-in Gradle wrapper, Android platform `platforms;android-37.0`
and SDK build tools (AGP also installs its default build tools 36.0.0).

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Its existing upstream
application ID is `org.unifiedpush.distributor.sunup.debug` and its label is
`Sunup-dbg`. It can be installed alongside the official release; select/register
this distributor in Forkgram or your other UnifiedPush application. A new debug
signing key (including a fresh GitHub runner's key) cannot update a previous debug
APK signed with a different key. Use a consistent release key for repeatable updates.

`.github/workflows/android.yml` checks feature branches, master/main and pull
requests and uploads the Debug APK and test/lint reports as workflow artifacts.
No signing secrets are needed for that job. To enable a manually requested signed
release, upload these **repository Actions Secrets** (never commit the keystore):

| Secret | Value |
| --- | --- |
| `SUNUP_KEYSTORE_BASE64` | Base64 encoding of your JKS/PKCS12 signing keystore |
| `SUNUP_STORE_PASSWORD` | Keystore password |
| `SUNUP_KEY_ALIAS` | Signing alias |
| `SUNUP_KEY_PASSWORD` | Key password |

Run **Android build → Run workflow** and enable `signed_release`. The signed job
runs only on explicit dispatch after the checks pass, decodes the keystore into
runner temporary storage and removes it on exit. Missing secrets fail explicitly.
Without this option the signed job is skipped. No automatic publishing occurs.
For local release builds set `SUNUP_KEYSTORE_PATH` and the three password/alias
environment variables; `assembleRelease` without them produces an unsigned APK.

Release keeps the upstream application ID `org.unifiedpush.distributor.sunup`.
Your signing key cannot overwrite the official APK unless it matches its signing
certificate. Uninstalling the official distributor removes its local registrations;
re-register clients after installing your build. The certificate hash earlier in
the upstream README describes the official release, not this fork's APKs.

## Verification scope

The JVM tests use Robolectric and local simulated WebSocket frames. They cover
persistence across independent SQLite helpers and reopening the database, defaults,
per-package isolation, bounded transport-identity deduplication, aggregation and
alert policy, NotificationManager posting/clearing, disabled notifications, and
payload-byte forwarding/ACKs with the option off/on and notifications denied.
They do not send a push through a real Autopush server or run Forkgram.

Before relying on this feature, verify on real devices:

1. Check the switch and denied/granted notification permission on Android 13+;
   disable the individual channel too. Check Android 7 notification behavior.
2. Register two apps, multiple registrations of one app, and turn on only one
   package. Send distinct and repeated transport frames. Check one notification,
   exact arrival count, sound throttling, swipe dismissal and tap behavior.
3. Change the option while the service is running in the other process; force
   recreate the UI/service and reboot. Check persisted values. The local tests
   use independent connections but are not a real Android multiprocess/reboot test.
4. Unregister/re-register and update/uninstall the client. Check missing icons and
   apps without launcher activities, and ensure foreground service notifications
   remain separate.
5. With a real Forkgram registration, send a real push while Telegram connectivity
   is unavailable, then repeat with normal connectivity. Confirm Sunup actually
   receives a valid notification frame in both cases; check its independent
   reminder and normal client delivery/synchronization. Sunup cannot make a server
   emit pushes or restore Telegram connectivity. Mainland China operation without
   a proxy has not been tested here and is not guaranteed.

No connected Android device or real Forkgram/Telegram test account is supplied in
this environment. These device/network checks remain unverified.

### Local results on 2026-10-23

- `./gradlew --offline ktlintCheck lintDebug assembleDebug compileDebugUnitTestKotlin`:
  passed using downloaded/cached dependencies, with normal dependency verification.
  All test source compiles. Existing upstream lint warnings remain; no lint errors.
- `testDebugUnitTest`: the two `ArrivalPolicyTest` methods passed. The other three
  test classes failed during Robolectric initialization because downloading
  `org.robolectric:android-all-instrumented:13-robolectric-9030017-i7` returned
  HTTP 503. Their Android behavior assertions have **not** been executed/verified.
  The fixture and forwarding tests are local simulations even when they run.
- Debug APK signing verified with `apksigner`; application ID confirmed as
  `org.unifiedpush.distributor.sunup.debug`, min SDK 24 and target SDK 37.
- Workflow YAML and XML resources parse successfully. A remote Actions run and
  the optional signed release job have not been verified in this environment.

## Changed file index (relative to imported upstream)

| Files | Purpose |
| --- | --- |
| `arrival/ArrivalStore.kt` | Durable package settings, bounded deduplication and post/disable serialization |
| `arrival/ArrivalPolicy.kt` | Aggregation and 30-second alert policy |
| `arrival/PushArrivalNotifier.kt` | Asynchronous independent system notifications, permission checks, app icons and launch fallback |
| `arrival/ArrivalPackageReceiver.kt` | Clear settings and notifications on full uninstall |
| `activities/MainActivity.kt` | Use the local UI adapter |
| `activities/SunupUi.kt`, `MainScreen.kt`, `RegistrationList.kt` | Adapt existing UI to insert per-app controls while reusing library components |
| `activities/ArrivalSettings.kt` | Shared package switch state, SQLite IO and permission guidance |
| `api/ServerConnection.kt`, `api/data/ServerMessage.kt` | Enqueue reminders only for actual pushes; support empty wake-up frames |
| `app/src/main/AndroidManifest.xml` | Declare uninstall receiver and legacy vibration permission |
| `app/src/main/res/values/strings.xml`, `values-zh-rCN/strings.xml` | English and Simplified Chinese feature text |
| `app/src/test/.../ArrivalPolicyTest.kt`, `ArrivalStoreTest.kt`, `PushArrivalNotifierTest.kt`, `PushDeliveryTest.kt` | Policy, storage, system notification and simulated forwarding tests |
| `app/build.gradle.kts` | Unit-test configuration, existing UI artifact declarations and optional environment-based release signing |
| `gradle/libs.versions.toml`, `gradle/verification-metadata.xml` | Dependency aliases and checksum metadata; retain upstream verification rules |
| `app/lint.xml` | Resource-specific missing-translation fallback allowances |
| `.github/workflows/android.yml` | Automatic checks/Debug artifacts and opt-in signed release build |
| `.gitignore` | Exclude Kotlin session files and common keystore names |
| `README.md`, `docs/push-arrival-notifications.md` | Usage, implementation, signing and verification scope |

Kotlin paths above are under `app/src/main/java/org/unifiedpush/distributor/sunup/`
unless a full path is given. Upstream `LICENSE` and its declarations are retained.
