# Turtur MVP — flows

All flows assume Android, BLE, one trip at a time, known-birds only.

## Happy path: half-day hike (north star)

1. At the trailhead, Alex taps **Start a trip**, names it “Lost Sierra”, habitat **Wild**.
2. Blair and Chris scan Alex’s **flock QR**. Three lofts, one roster.
3. Everyone enables Turtur; phones pair as flock (BLE).
4. On the trail they split. Airplane mode is fine only if Bluetooth stays allowed.
5. Alex sends Chris a **Note**: “Taking high pass, 2h late.” Pigeon sits **on the wing** on Alex, then copies onto Blair when they pass at a saddle (Feed or opportunistic).
6. Chris is never near Alex. At the hut kitchen they **Feed the flock**. Chris gets the note. Alex may later get an ACK if Chris’s device creates one and Blair carries it back.
7. Chris sends **Meetup**: “bridge / 08:00.” Copies for Alex and Blair.
8. Sunday, Alex taps **End trip**. Lofts molt.

## Flow: Start a trip

```
Launch
  → Start a trip
  → Trip name
  → Habitat: Wild (default) | Crowd
  → Create loft (identity keypair, trip id)
  → Home: show flock QR + “waiting for birds”
  → Share QR (screen or already-printed card)
```

Rules:

- Creator is member 1.
- **End trip** is local: it molts this device only.
- QR payload is short: trip id, name, habitat hint, creator intro. Exact encoding must survive a bright-sun screenshot.

## Flow: Join a trip

```
Launch
  → Join a trip
  → Camera scans flock QR
  → Show trip name + creator band
  → Confirm join
  → Create my loft keys
  → Roster update pigeon(s) queued to existing members
  → Home
```

Rules:

- If flock is already 12, refuse.
- New device must sync roster from someone in range soon; until then it can only talk to members it has keys for (at least the creator from the QR).
- Joining mid-trip: get current public keys before sending 1:1 notes to others. Until roster is fresh, compose to unknown members is disabled.

## Flow: Compose Note (1:1)

```
Home → Note
  → Pick recipient (name + band from roster)
  → Text (max 500)
  → Optional TTL override
  → Release
  → Status: on the wing
```

Packaging: sealed box to recipient pk. Envelope dest = that loft. Store locally; replicate to other **trip** devices that do not already hold it, until dest has ACK’d or TTL ends.

## Flow: Compose Meetup (flock)

```
Home → Meetup
  → Place (short text)
  → Time (local clock, no timezone magic)
  → Release
```

Packaging: N sealed boxes, one per other member. Same `meetup_id` inside so UI can dedupe.

## Flow: Compose Okay (flock)

```
Home → Okay
  → Fine | Delayed | Stay put
  → Release
```

Two taps after opening Okay. Highest priority in the sync queue. Same N-copy sealing as Meetup.

## Flow: Carry and feed (the product)

```
Whenever Turtur may use BLE and a flock peer is in range:
  → Advertise compact trip presence (trip id + loft id)
  → Authenticate as flock members (key proof)
  → Anti-entropy: offer envelope ids I hold that you want
  → Transfer ciphertext only
```

**Feed the flock** button:

```
User taps Feed
  → Immediate scan + sync for ~30–90 seconds
  → Show: peers found, pigeons given, pigeons taken
  → Haptic on first arrival
```

Out of range is not an error. Home shows last feed time and **N on the wing**.

## Flow: Arrivals

```
Pigeon decrypts on this device
  → Arrivals list (newest first)
  → Types visually distinct: Note / Meetup / Okay
  → Queue ACK pigeon to sender
```

ACK means **landed on device**.

## Flow: On the wing (sender)

Shows pigeons this loft created that are not landed/returned/lost. Copy count is optional (“held on 2 devices”) only if we know; otherwise omit rather than lie.

## Flow: End trip (molt)

```
Settings → End trip
  → Warn: arrivals and keys will be wiped on this phone
  → Confirm
  → Wipe keys, roster, envelopes, arrivals
  → Back to Start/Join
```

MVP **does not** network-broadcast “trip dissolved.” Other phones keep working until they molt too. Documented in `flaw.md`.

## Flow: Habitat toggle

Trip setting: Wild ↔ Crowd. Changes advertise/scan cadence and **default TTL for new pigeons**. Already-flying pigeons keep their original TTL.

## Flow: App permissions (first run)

Bluetooth, notifications (arrivals), camera (QR join). No location permission in MVP (and do not sneak-use it).

## Error / empty states (required copy)

| Situation | Copy direction |
| --- | --- |
| Zero peers | “No flock in range. That’s normal on the trail. Feed at camp.” |
| Join but not in BLE of anyone | “You’re in the trip. Keys will finish when you stand near another bird.” |
| BLE off / permission denied | “Turtur needs Bluetooth to fly.” |
| TTL dead | “This pigeon never made it home.” |
| Flock full | “This trip is full (12).” |
| Airplane mode | Turtur cannot fly until Bluetooth is allowed. |

## Screen list (MVP)

1. Start or join
2. Create trip / scan QR
3. Home (Arrivals preview, On the wing count, Feed, compose tiles, flock in-range)
4. Compose Note / Meetup / Okay
5. Arrivals detail
6. On the wing list
7. Flock roster + QR for late joiners
8. Trip settings (habitat, end trip, battery hint)
