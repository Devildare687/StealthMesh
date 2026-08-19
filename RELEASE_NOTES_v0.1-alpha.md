# StealthMesh v0.1-alpha

StealthMesh v0.1-alpha is a developer alpha of the reliability-first Nearby
Mesh text path. It keeps the proven upstream Bluetooth mesh implementation and
adds a focused StealthMesh state, repository, ViewModel, and Compose chat shell.

## What works

- Automatic nearby Bluetooth LE discovery and direct local links
- Public Nearby Mesh text in both directions without Internet access
- Nearby-peer, mesh-status, deterministic timeline, and message-composer UI
- Duplicate-free visible messages in the bounded physically tested scenarios
- Automatic direct-link recovery and identity preservation after app relaunch

## Verification

The integrated build passed the focused Checkpoint 02 test set and debug APK
build. Two physical Android phones verified mutual discovery, bidirectional
Compose-based text, exactly-once visible delivery in the tested scenarios,
process restart/recovery, peer disappearance, and reconnection. Identifiers and
raw device evidence remain private.

## Current limitations

This is not a production security-audited release. Private StealthMesh
conversations, the reserved premium UI, Wi-Fi Aware optimization, broader OEM
coverage, multi-hop physical validation, and final hardening remain future work.
An inherited Windows/Robolectric test backlog is documented separately in
[TEST_REPORT.md](TEST_REPORT.md).

## Roadmap

The next planned product work is private-conversation/session validation,
followed by premium UI integration. Optional transport acceleration and broader
hardening are later goals; no dates are promised.

## License and attribution

StealthMesh retains substantial code from
[permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android)
and preserves the upstream GPLv3 license text and attribution. See
[UPSTREAM_BASE.md](UPSTREAM_BASE.md) for the source pin and licensing note.
