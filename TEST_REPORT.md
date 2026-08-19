# Test report

## Checkpoint 00 — untouched upstream baseline

Date: 2026-08-19

Source: `09b481f1ef5852ed50edce987dd719bd9588b125`

Host: Windows, JDK 21.0.11, Android SDK/API 37

### Automated results

| Check | Result | Evidence |
|---|---|---|
| `gradlew.bat assembleDebug --no-daemon` | PASS | Phone ABI/universal APKs and Wear debug APK produced |
| `gradlew.bat test --no-daemon` | FAIL (upstream baseline) | 594 app tests observed; 25 failed and 3 skipped after the Windows Robolectric dependency-path workaround; Wear unit tests passed |
| Android SDK/JDK preflight | PASS | Exact JDK, platform, build-tools, and platform-tools pins available |
| `adb devices -l` | NO DEVICES | Physical acceptance matrix unavailable |

The first test run had 26 failures because Robolectric encoded the space in the
Windows user-profile path while resolving its runtime Android jars. Supplying the
same downloaded jars from a space-free offline dependency directory removed that
host-specific resolver failure. The remaining baseline failures were:

- one `NostrDirectMessageHandlerTest` timeout;
- direct SQLite-open failures in `ConversationDatabaseTest` on this Windows
  Robolectric host; and
- downstream repository/incoming/media assertions after database setup failed.

Changing Robolectric between native and legacy SQLite modes did not remove the
database-open failures. No production code was modified to mask them. The
upstream CI definition runs unit tests on Ubuntu, so these Windows results do not
establish the pinned commit's Linux CI status.

### Debug artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app-arm64-v8a-debug.apk` | 44,993,943 | `5AD7740167ED6E3A9C1D927BE9253CFDB0C3804E3B445CE9F4972C350864BC4A` |
| `app-armeabi-v7a-debug.apk` | 41,814,021 | `42902D505E906906512CDD7F5AFCD6A8B707A4AE1F7076FCC03AAA42FBE0963D` |
| `app-universal-debug.apk` | 76,927,591 | `AAA4490224140D17583C8B28CF07B24AC771C11ABA519DBBAEC0294C11CA4C0A` |
| `app-x86_64-debug.apk` | 46,861,720 | `84AEE57475904D351FF2B3750A7086F86B49DEA4A30B0209AF457AB118FC094A` |
| `app-x86-debug.apk` | 46,926,141 | `E69319F82D4516D40832AB7E84E7D3FDCCB8D135A46AA9D7F0B1C69E59CD555F` |
| `wear-debug.apk` | 75,687,592 | `B52163CD0C39726AEFEA2CBE396F1CA3A890FAD5CAD5B50282FDB846ECE48F94` |

### Physical-device matrix

`NOT PHYSICALLY VERIFIED` — no Android device or emulator was attached. BLE
scan/advertise, GATT connect, two-device text delivery, multi-hop relay, Wi-Fi
Aware promotion/fallback, reconnect, background operation, restart, permissions,
and OEM-specific behavior remain open acceptance work. This report makes no
claim about those scenarios.

## Checkpoint 01 — BLE Golden Path

Date: 2026-08-20

Build: current-tree ARM64 debug APK

Topology: two authorized physical Android phones, phone A ↔ phone B. Device
selectors, hardware identifiers, peer IDs, addresses, and raw logs are excluded
from this report.

### Results

| Check | Result | Remote assertion |
|---|---|---|
| ADB preflight | PASS | Exactly two authorized physical API 36 phones; no emulator |
| `:app:assembleDebug` | PASS | Debug APK compiled with the pinned toolchain |
| Controlled-peer inspection | PASS | Each phone discovered only the other lab participant |
| `ble_golden_path` | PASS | Wi-Fi disabled; mutual BLE discovery/direct state; text A→B and B→A exactly once |
| Automatic recovery | PASS | After phone B process death/relaunch, identity persisted and direct BLE recovered without an explicit connect command |
| Post-recovery text | PASS | Text A→B and B→A again arrived exactly once |
| Live UI semantics | PASS | Both foreground UIs reported one connected peer, matching one direct peer in mesh state |
| `dm` | PASS | Noise handshake and encrypted content-matched round trips in both directions |
| `session_recovery` (clean isolated run) | PASS | Identity/session recovery and bidirectional encrypted DMs after process death |
| Temporary device settings restoration | PASS | Bluetooth, Wi-Fi, stay-awake, screen timeout, and lock-screen settings match recorded originals |
| Release-gate Python tests | PASS | 15 tests |

The first `session_recovery` attempt followed `ble_golden_path` and `dm` on an
evolving device state and timed out on the first recovered A→B encrypted DM,
despite both sides reporting established sessions. Its failure evidence was
preserved locally. A clean setup followed by an isolated `session_recovery`
run passed. This is recorded as cross-scenario state contamination to revisit
during Checkpoint 03; it does not weaken the clean BLE public-text recovery
assertions above.

The focused scenario uses unique synthetic messages and a bounded five-second
post-receipt observation window to assert duplicate absence. The UI check uses
accessibility semantics and the debug mesh-state probe; no screenshots were
exported.

Private raw evidence remains in OS temporary storage and is uncommitted. This
debug-harness result does not cover three-hop relay, Wi-Fi Aware, permission
denial, doze/endurance, release APK behavior, OEM diversity, or cross-client
compatibility.

## Checkpoint 02 — reliable chat domain

Date: 2026-08-20

Baseline: `checkpoint-01-ble-golden-path`

Build: Checkpoint 02 ARM64 debug APK with the StealthMesh state, repository,
ViewModel, and Compose chat shell integrated. The production BLE/mesh core was
not redesigned or refactored for this checkpoint.

### Automated results

| Check | Result | Evidence |
|---|---|---|
| Focused StealthMesh and Checkpoint 01 tests | PASS | 11 tests |
| `:app:assembleDebug` | PASS | Debug APKs produced |
| New `stealthmesh` package lint inspection | PASS | No finding in the new package |
| Full inherited unit suite (recorded once) | FAIL (inherited baseline) | 605 tests observed; 26 failed and 3 skipped |

The full-suite failures were outside the new StealthMesh package and remained
concentrated in the previously documented Windows/Robolectric SQLite,
persistence, media, and Nostr-timeout areas. No production workaround was added
to hide those inherited failures.

### Physical integration results

| Check | Result | Public assertion |
|---|---|---|
| Mutual BLE discovery and direct links | PASS | Each phone presented the other nearby participant |
| Real Compose send A → B and B → A | PASS | Synthetic text appeared once on each receiving phone |
| Local/remote timeline attribution | PASS | Compose message bubbles identified sent and received text correctly |
| Process death and relaunch | PASS | Identity persisted and the direct link recovered automatically |
| Post-recovery text A → B and B → A | PASS | Synthetic text again appeared exactly once in each tested direction |
| Peer disappearance | PASS | The remaining phone returned to the no-peer mesh-active state after the retained stale-peer policy elapsed |
| Reconnection | PASS | Both phones returned to one nearby peer after relaunch |
| Temporary settings restoration | PASS | Test-only Bluetooth, Wi-Fi, awake, timeout, and lock settings were restored |

The physical run used two authorized Android phones and synthetic messages.
Hardware identifiers, ADB selectors, peer IDs, network metadata, screenshots,
and raw logs are excluded. Raw evidence remains local and untracked.

This checkpoint does not establish exhaustive multi-hop behavior, broad OEM
compatibility, Wi-Fi Aware acceleration, a completed private-chat product flow,
release-signing readiness, or an independent security audit.
