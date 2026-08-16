# Turtur MVP — discussion and locked decisions

This document records **what we are building first** and **why**. Arguments that lost are kept so we do not relitigate them mid-build.

**App name:** Turtur (*Streptopelia turtur*, the turtle dove). The metaphor stays pigeons and lofts; the product on the icon is Turtur.

Audience: festivals, trekkers, hikers. Not protest tooling as the product story. Privacy still matters; reuniting at camp is the pitch.

## Goal of the MVP

Prove **camp mail**: sealed 1:1 and flock announcements that survive hours of people being out of range, then land when the flock sits down together.

If that loop is not delightful, we do not add radios, platforms, or media.

## Locked decisions

### 1. Android only

**Lock:** MVP clients are Android.

**Why:** Background BLE and a spare “drybag phone” are realistic on Android. iOS background mesh is hostile and would fake a product that dies when the screen locks.

**Rejected:** Dual-platform from day one; iOS foreground-only companion in v1.

### 2. No servers, no skybridge

**Lock:** Zero networked backend. Discovery and transfer are Bluetooth Low Energy between phones.

**Why:** The product *is* pocket-to-pocket. A relay API would make us look like a slow messenger with a bird skin, and it changes the threat model.

**Rejected:** Optional cloud mailbox, Firebase, WebRTC, Wi-Fi-only mesh.

### 3. Trip-first onboarding

**Lock:** First screen is **Start a trip** or **Join a trip** (QR). Identity is a **trip loft**, not a standing social graph.

**Why:** Both festivals and hikes are episodic. A disposable flock QR on a paper card in the cooler matches how these people already organize. Persistent “friends” come later.

**Rejected:** Address book + invite links; phone-number login; always-on personal loft as the home screen.

### 4. Default habitat is Wild; Crowd is a toggle

**Lock:** Default radio cadence is **Wild** (slow advertise, stingy scan). User may switch the trip to **Crowd** (faster, heavier battery) for festivals. No automatic density switching.

**Why:** Hikers punish battery. False auto-Crowd on a ridge is worse than a slightly sleepy festival until someone toggles.

**Rejected:** Auto-detect habitat; two separate apps.

### 5. Known birds only

**Lock:** Only **members of this trip** store, carry, and exchange pigeons for the trip.

**Why:** Passing-stranger courier is powerful (and coming later) but explodes routing, inbox spam, and “who is in my pocket?” UX. Festival density makes open epidemic flooding dangerous.

**Rejected:** Open loft / carry-for-anyone in MVP; Wi-Fi Direct swarm.

### 6. Three compose types; no media

**Lock:**

- **Note** — 1:1, ≤ 500 characters
- **Meetup** — flock-wide, place string + local time
- **Okay** — flock-wide enum: `fine` | `delayed` | `stay_put`

**Why:** These cover split/reunite, camp mail, and stragglers. Photos blow BLE budgets and battery. Voice needs UX we do not have.

**Rejected:** Freeform group chat threads; images; voice notes; live location.

### 7. Flock cap 12

**Lock:** A trip accepts at most 12 lofts.

**Why:** Broadcast is “one sealed copy per member.” 12 keeps packets and screens honest. Festival mega-crews can run two trips.

**Rejected:** Unbounded groups; MLS in v1.

### 8. Crypto: identity keys + sealed boxes (no ratchet yet)

**Lock:** Each loft has a long-term identity keypair created at trip start/join. Notes use a **sealed box to the recipient’s public key**. Meetup/Okay are N sealed boxes, one per other member. Relays see only envelope metadata (dest id, ttl, size bin, priority).

Join QR carries: trip id, trip name, creator’s intro, and enough to add the new public key to the membership list (see `flow.md`). Membership updates are signed by an existing member.

**Why:** Delay-tolerant 1:1 does not need Double Ratchet for an MVP of 12 friends. Sealed boxes are auditable and match store-and-forward. Forward secrecy is a planned molt upgrade.

**Rejected:** Single shared trip symmetric key (any member, or a stolen backup, reads everyone’s 1:1 mail). Phone-number identity. Server-distributed keys.

### 9. Honest delivery states; no typing/read theatre

**Lock:** UI states: `sealed` → `on_the_wing` → `landed` (only if an ACK pigeon is created and eventually returns) → `returned` (TTL died and a copy made it home) → `lost`. Default: sender sees **on the wing**, not “read.”

**Why:** Delay is the product. Fake checkmarks would train people to panic.

**ACK lock:** ACK all successful decrypts. ACK means **landed on device**, not that the human opened Arrivals.

### 10. Sync model: opportunistic + Feed the flock

**Lock:** Phones exchange while Turtur is allowed to use BLE and a flock peer is in range. **Feed the flock** forces an immediate anti-entropy burst (camp / kitchen / van).

**Why:** Hikers already do “airplane all day, radios at camp.” The button makes that ritual.

**Rejected:** Always-on aggressive flooding; scheduled alarms as the only sync.

### 11. TTL and return-to-sender (simple)

**Lock:** Every pigeon has a TTL: Wild default **48 hours**, Crowd default **8 hours**. User may pick 4h / 24h / 72h. When TTL expires on a holder, if that holder is the sender, mark `returned`; else drop ciphertext. Best-effort `returned` if a copy still exists on the sender.

**Why:** Full return-routing of expired mail through the flock is extra complexity. MVP must not hoard dead packets.

### 12. No maps GPS in the envelope

**Lock:** Meetup place is **typed text** (“red banner”, “Hut 3”, “south lot”). Turtur does not attach coordinates.

**Why:** GPS in a default festival/hike packet is a stalking and seizure problem. Names of places are how these groups already talk.

### 13. One trip at a time

**Lock:** A device holds at most one live trip loft. Start/join is blocked until the current trip molts.

**Why:** Radio, roster, and UX stay obvious. Multi-trip comes later.

## Implementation sketch (binding for v1)

- Language: Kotlin
- UI: Jetpack Compose
- Min BLE: advertising + GATT for envelope sync
- Storage: local encrypted prefs + envelope files, keyed by dest + TTL
- Envelope (public): `trip_id`, `dest_loft_id`, `priority`, `ttl_unix`, `size_bin`, `ciphertext`
- Payload types (inside ciphertext): `note`, `meetup`, `okay`, `ack`, `roster_update`
- Padding: pack ciphertext into bins (~512 B and ~2 KiB) so notes and okays do not scream their type by length
- UX home: Arrivals / On the wing / Flock (in-range count, last feed, battery hint) / compose tiles
- Package: `dev.turtur`

## Success metrics (qualitative)

1. Three-phone airplane-mode carry works without a developer explaining BLE.
2. A tired user can send Okay in two taps.
3. Feed the flock at a table visibly moves mail.
4. End trip leaves no trivial plaintext trip archive on disk (keys + arrivals wiped; OS backups documented in `flaw.md`).
