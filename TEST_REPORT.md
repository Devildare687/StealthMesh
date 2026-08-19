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
