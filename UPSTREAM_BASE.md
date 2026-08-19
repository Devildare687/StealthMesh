# Upstream base

## Source pin

- Repository: `https://github.com/permissionlesstech/bitchat-android.git`
- Branch at import: `main`
- Commit: `09b481f1ef5852ed50edce987dd719bd9588b125`
- Upstream commit date: 2026-08-17
- Baseline established: 2026-08-19
- Local development branch: `stealthmesh-reboot`
- Upstream remote name: `upstream`

Checkpoint 00 is the untouched upstream source plus StealthMesh planning and
verification documents. No Kotlin, resource, manifest, Gradle, or supplied UI
source was changed.

## Toolchain pin

- JDK: 21.0.11 (`.java-version`)
- Gradle: 9.6.1 with wrapper SHA-256 validation
- Android Gradle Plugin: 9.3.1
- Kotlin: 2.4.10
- compile/target SDK: 37
- build tools: 37.0.0
- minimum SDK: 26

## Retained upstream modules and boundaries

- Android phone app and Wear OS modules
- BLE discovery, GATT client/server, connection tracking, packet broadcast, and
  multi-hop relay
- optional Wi-Fi Aware transport and BLE/Wi-Fi bridge
- binary packet protocol, fragmentation/reassembly, TTL, deduplication, gossip
  sync, and store-forward
- Noise sessions, identity/signing keys, verification, and encrypted persistence
- foreground-service lifecycle, boot integration, repository/storage layer, and
  the existing Compose UI
- Nostr/geohash/Tor, hotspot sharing, file/media, and voice code remain present
  at this checkpoint so the baseline stays reviewable

## Features intentionally disabled

None at Checkpoint 00. This is an upstream-equivalent baseline. Non-v0.1
surfaces will be isolated in a later checkpoint only after the golden local text
path is protected.

## License and attribution

The upstream `LICENSE.md` contains the GNU General Public License version 3
text. The upstream `README.md` simultaneously says the project is public domain;
those statements conflict. Until upstream clarifies that discrepancy,
StealthMesh treats the imported code as GPL-3.0-covered, preserves the original
license and history, and must retain source/notice obligations for distribution.
This note records repository evidence and is not legal advice.
