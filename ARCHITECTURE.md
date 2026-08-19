# StealthMesh architecture

## Pinned upstream baseline

StealthMesh starts from `permissionlesstech/bitchat-android` at commit
`09b481f1ef5852ed50edce987dd719bd9588b125`. Checkpoint 00 intentionally makes
no production-code or build-configuration changes.

## Current data flow

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
  fallback, or a bounded retry queue. Nostr is outside the StealthMesh v0.1
  golden path and will be isolated only after local transport behavior is
  protected by tests.
- `NoiseEncryptionService` / `NoiseSessionManager` establish per-peer Noise
  sessions. Static identity and Ed25519 signing keys are persisted through
  Android encrypted preferences.
- `BinaryProtocol`, `BitchatPacket`, `MessageType`, fragmentation, TTL, peer-ID
  derivation, Noise identity binding, signing, verification, duplicate
  suppression, and store-forward rules form the compatibility-sensitive core.

## Reliability-first implementation plan

1. **Checkpoint 01 — golden local text slice:** protect protocol boundaries with
   tests, retain BLE discovery/GATT/relay behavior, and prove two-peer local text
   delivery without redesigning transport state machines.
2. **Checkpoint 02 — capability and optional transport policy:** make Wi-Fi Aware
   an automatic capability-gated acceleration path with BLE fallback and a
   stable peer/session identity across transports.
3. **Checkpoint 03 — security and lifecycle hardening:** verify identity
   persistence, authenticated private messaging, deduplication, foreground
   lifecycle, permissions, reconnects, and process restart behavior.
4. **Checkpoint 04 — product-surface reduction:** isolate or disable Nostr,
   geohash, Tor, hotspot sharing, media/voice, and other non-v0.1 features
   without deleting compatibility-sensitive internals prematurely.
5. **Checkpoint 05 — UI integration:** port the reserved Gemini UI into native
   Compose only after the mesh core is green; UI state must consume stable
   service/repository contracts rather than own transport logic.
6. **Checkpoint 06 — release evidence:** run the full automated matrix, lint,
   assemble, and available multi-device tests; document every unavailable
   physical scenario as `NOT PHYSICALLY VERIFIED`.

The untracked `ui/` design drop is reserved for Checkpoint 05 and is deliberately
untouched at this baseline.
