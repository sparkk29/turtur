# Turtur — future implementation

Not in MVP. Do not start these until the three-phone camp-mail demo is boringly reliable.

## Near-term (v1.x)

- **iOS** foreground loft (screen-on sync only), with honest copy that background carry is Android’s job
- **Crushed photo** (one image, hard cap ~40–80 KB) on Notes
- **Named grounds** — van / hut / kitchen drop that is just a spare Android with a “this is a ground” flag (still known-birds)
- **Paper QR pigeon** — export a note as a QR (or QR sequence) for hut boards
- **Roster gossip robustness** — signed append-only membership, late join after creator is gone
- **True return-to-sender** — expired envelopes route back toward origin loft_id
- **Trip dissolve pigeon** — tell other devices to suggest molt
- **Multiple trips** at rest (one active radio trip at a time)

## Medium-term

- **Open courier** — strangers carry sealed envelopes; pocket-flock UI “set down this bird”; battery policy
- **Skybridge** — opt-in dumb encrypted mailbox when someone reaches LTE (van in town)
- **Dawn sync ritual** — scheduled camp burst + sound
- **Disposable festival loft** with printed gate QR, auto-molt timestamp
- **Group threads** beyond Meetup/Okay (still E2E)
- **Forward secrecy** — ratchets or frequent identity molt mid-trip
- **Padding / dummy pigeons** for Crowd habitat
- **Watch / lock-screen glance**: “Maya · meetup · kitchen · dusk”

## SOS follow-ups

The best-effort SOS beacon shipped (see `flaw.md` → Best-effort SOS). Next:

- **Check-in timer (dead-man switch)**: "start SOS if I don't check in by 17:00", for falls that leave someone unconscious
- **Optional internet bridge**: opt-in relay so a carrier with data can deliver the SOS without a manual SMS tap (breaks the no-server rule; needs its own threat model)
- **Relay re-broadcast** with hop count, so finders are never led to the carrier instead of the hiker
- **Compass needle** in the finder (sensor heading) instead of a bearing in degrees
- **Siren and strobe** on the victim's phone for people nearby without Turtur
- **Per-SOS encrypted note** to verified contacts, alongside the public position

## Later / maybe never

- Public nearby chat, discovery of strangers, dating
- Live GPS sharing
- Voice notes
- Wi-Fi Aware / Wi-Fi Direct / Meshtastic radio bridges
- Official mountain-rescue or 112 integration (liability, false confidence); the Turtur-to-Turtur SOS beacon is not this
- Desktop / web
- Phone-number identity

## Platform notes

Android courier-only device (no compose, huge battery/status) is a strong festival/trail accessory. Build it after personal loft + carry work, not before.
