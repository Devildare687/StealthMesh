# StealthMesh architecture

## Pinned upstream baseline

StealthMesh starts from `permissionlesstech/bitchat-android` at commit
`09b481f1ef5852ed50edce987dd719bd9588b125`. Checkpoint 00 intentionally makes
no production-code or build-configuration changes.

## Current StealthMesh product flow

```text
StealthMeshScreen (Compose)
            |
            v
StealthMeshViewModel
            |
            v
AppStateStealthMeshRepository -------> MeshService send API
            |                                  |
            v                                  v
       AppStateStore <--------------- existing mesh services
                                               |
                                               v
                                         Bluetooth LE
```

- `StealthMeshScreen` renders immutable `StealthMeshUiState` and forwards draft
  and send actions.
- `StealthMeshViewModel` combines repository state with saved public/private
  drafts and the selected private-conversation target.
- `AppStateStealthMeshRepository` maps peers, direct links, nicknames, signal
  strength, public/private messages, and real Noise session state from
  `AppStateStore` and `MeshService` into product models. Public and private
  sends use the retained `MeshService` boundary.
- `AppStateStore` continues to bridge the compatibility-sensitive transport
  implementation into observable application state.

## Retained transport data flow

```text
MainActivity / ChatViewModel                 MeshForegroundService
              |                                      |
              +------------ MeshServiceHolder -------+
                                   |
                           UnifiedMeshService
                          /                  \
              BluetoothMeshService      WifiAwareMeshService
              BLE scan/GATT/client      discovery/network/socket
                          \                  /
                           TransportBridgeService
                         TTL + duplicate suppression
                                   |
             packet processing / fragmentation / relay / store-forward
                                   |
                    Noise sessions + packet signatures
                                   |
                       MeshDelegate -> UI/repository
```

- `MeshForegroundService` owns background lifetime and notification behavior.
- `MeshServiceHolder` ensures process-wide BLE and unified-service instances and
  one shared gossip manager.
- `UnifiedMeshService` is the feature-facing selector. BLE is the canonical
  broadcast origin when enabled; addressed traffic prefers a transport with an
  established Noise session.
- `BluetoothMeshService` coordinates BLE discovery/GATT, peers, fragments,
  packet processing, security, relay, and store-forward components.
- `WifiAwareMeshService` adapts Wi-Fi Aware discovery and sockets to the shared
  `MeshCore` and `MeshTransport` contracts.
- `TransportBridgeService` bridges registered transports, decrements relay TTL,
  and suppresses duplicates without changing the binary protocol.
- `MessageRouter` currently chooses an authenticated local mesh route, a Nostr
  fallback, or a bounded retry queue. Nostr is outside the StealthMesh v0.2
  golden path and will be isolated only after local transport behavior is
  protected by tests.
- `NoiseEncryptionService` / `NoiseSessionManager` establish per-peer Noise
  sessions. Static identity and Ed25519 signing keys are persisted through
  Android encrypted preferences.
- `BinaryProtocol`, `BitchatPacket`, `MessageType`, fragmentation, TTL, peer-ID
  derivation, Noise identity binding, signing, verification, duplicate
  suppression, and store-forward rules form the compatibility-sensitive core.

## Reliability-first checkpoints

0. **Checkpoint 00 — upstream build baseline:** pin and document the untouched
   upstream source and toolchain.
1. **Checkpoint 01 — golden local text slice:** protect protocol boundaries,
   retain BLE discovery/GATT/relay behavior, and prove two-phone local text and
   restart recovery without redesigning transport state machines.
2. **Checkpoint 02 — reliable chat domain:** add the thin StealthMesh state,
   repository, ViewModel, and functional Compose chat shell, then revalidate the
   two-phone BLE Golden Path through the integrated product surface.
3. **Checkpoint 03 — private StealthMesh chat:** expose the inherited per-peer
   Noise state through the thin product layer, add isolated private text
   conversations, and physically validate exactly-once delivery and recovery.

Later checkpoints remain paused for the v0.2-alpha release preparation.
Optional transport acceleration, premium UI integration, broader private-media
coverage, and additional hardening are roadmap items rather than current
product claims.

The untracked `ui/` design drop is reserved for Checkpoint 05 and is deliberately
untouched at this baseline.

## Protected BLE Golden Path

Checkpoint 01 establishes the following physically verified contract as a
regression gate:

1. Two physical phones start the debug app with required runtime permissions.
2. With Wi-Fi disabled, BLE advertising/scanning produces mutual discovery and
   a direct usable link.
3. Public text is delivered exactly once in both directions.
4. After one app process is killed and relaunched, its identity persists, the
   direct BLE link recovers without a host-issued connect command, and
   exactly-once text still works in both directions.
5. The Compose UI state reports the same one-peer connected state as the
   debug-only mesh state probe.

The observable contract—not incidental implementation detail—is frozen.
Changes to BLE discovery/GATT, peer lifecycle, message admission/deduplication,
packet processing/relay, foreground lifecycle, or UI peer-state propagation
must keep `ble_golden_path` green on two physical phones. The ADB command and
message-count probes remain debug-only.
