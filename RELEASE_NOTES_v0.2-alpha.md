# StealthMesh v0.2-alpha

StealthMesh v0.2-alpha adds physically verified private peer-to-peer text
conversations to the reliability-first Nearby Mesh foundation introduced in
v0.1-alpha.

## What is included

- Automatic nearby Bluetooth Low Energy discovery
- Offline public Nearby Mesh text messaging
- Nearby-person cards that open private conversations
- Private text protected by the inherited Noise secure-session implementation
- Real session presentation for establishing, encrypted, reconnecting,
  unavailable, and error states
- Duplicate-free visible delivery in the tested public and private scenarios
- Identity, connection, secure-session, and messaging recovery across a tested
  app process termination/relaunch
- A thin StealthMesh repository, ViewModel, immutable state, and Compose shell

## Verification

The release was exercised on two authorized physical Android phones in a
controlled BLE-only topology with synthetic messages. Public and private text
passed in both directions before and after process termination. The private UI
reported encrypted only when both endpoints held established Noise sessions;
public/private timelines remained separate; peer identity was preserved; and
the prior sequential-session contamination sequence did not reproduce.

The focused Checkpoint 02 + 03 suite contains 21 passing tests. Raw device
identifiers, logs, captures, and Mesh Lab evidence are intentionally excluded
from this repository and release.

## Alpha limitations

- Developer/debug alpha build; not a Play Store production release
- Not independently security audited and not claimed to provide anonymity or
  perfect confidentiality
- Physical validation covers a controlled two-phone BLE topology, not broad OEM
  diversity, endurance, or multi-hop behavior
- Private StealthMesh validation currently covers text, not every inherited
  attachment or media path
- Wi-Fi Aware acceleration is not claimed as a completed StealthMesh feature
- The reserved premium Gemini UI is not integrated
- Release signing, full hardening, battery tuning, and wider compatibility work
  remain future tasks

## Licensing and upstream

StealthMesh retains substantial networking and protocol code from
[permissionlesstech/bitchat-android](https://github.com/permissionlesstech/bitchat-android).
The upstream pin and attribution are recorded in `UPSTREAM_BASE.md`. This
repository preserves the GNU General Public License version 3 text in
`LICENSE.md` and treats the retained code as GPL-3.0-covered.
