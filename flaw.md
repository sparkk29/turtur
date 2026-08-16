# Turtur — flaws, threats, and warnings

This is not a guarantee of safety. Turtur hides **letter contents** from relays and from us (there is no server). It does **not** make you invisible, unfindable, or rescue-ready.

## What we actually protect (MVP)

- **Confidentiality of payloads** from devices that are not the recipient (sealed box). Other flock members cannot read your 1:1 Notes.
- **No company message graph.** We do not operate a backend.
- **No phone numbers** in the protocol.

## What we do not protect

### Radios are visible

BLE advertising shows **a Turtur device is here**. A nearby observer can count phones, times, and movement of radios. Crowd habitat makes this louder. Do not claim stealth, anti-surveillance, or “off-grid anonymity.”

### Metadata on the envelope

Relays in the flock see `trip_id`, destination loft id, TTL, size bin, and priority. Pattern of **who mail is for** leaks inside the flock.

### The flock is a trust circle for routing, not for 1:1 content

Members help carry. A malicious member can:

- drop or delay pigeons (denial of delivery)
- keep copies of ciphertext until TTL
- refuse roster updates
- flood the pocket with junk (until we cap)

They should **not** be able to decrypt Notes to someone else.

Meetup/Okay are intended for everyone; do not put secrets there.

### BLE is not a hardened tunnel

Treat radio authentication as **best-effort flock proof**, not as a well-funded RF adversary defeating you. Replay, spam, and battery-drain (radio flooding) are real on the festival field.

### Seizure and backups

If the phone is unlocked, the attacker has the loft. OS backups, screenshots of QR, and notifications on a lock screen can leak arrivals. **End trip** wipes Turtur’s store; it cannot wipe screenshots, notifications already shown, or forensic copies.

**Duress / decoy loft is not in MVP.** Assume seized phone = seized trip.

### Split-brain molt

**End trip** is local. Others keep the trip. There is no global delete. That is confusing and is also the only honest design without a server.

### No support for “airplane mode but Turtur still works”

If Bluetooth is off, pigeons do not fly. Copy must not imply otherwise.

### Location

We avoid GPS in packets. Typed meetup names can still be identifying (“Maya’s van”). Notifications default to hidden content on the lock screen.

### Not a safety device

Okay ≠ SOS. Turtur is not a PLB, Garmin inReach, or ranger radio. If someone is lost or injured, Turtur may never deliver. **Do not market emergency use.** Delay and loss are normal.

Festival extra: stolen phones, photographed flock QR on a cooler — late strangers might join until the cap if you leave the QR up. Treat the QR like a house key while the trip is live.

### Crypto limitations (MVP)

- No forward secrecy: steal keys today, decrypt captured ciphertext from earlier in the trip (copies that still exist, or logs someone kept).
- No post-compromise healing.
- Creator QR bootstrap: whoever can show that QR can try to join until full; protect it like a ticket wristband.
- Clock-skew on TTL: wrong device time can burn mail early or keep it too long. Set time roughly right before the ridge.

### Scale and battery

Crowd + many envelopes will drain batteries and congest BLE. 12-person cap avoids a rave-wide mesh fantasy. We will still fail if everyone hammers Feed continuously.

### Legal / ethics

People may try to use Turtur where radios are restricted, or to evade lawful intercept. The app is a **camp-mail tool**. We do not provide stealth guidance. Operators of events should know Bluetooth devices are still devices on their site.

## Warnings to show in-product (MVP copy)

On first trip create:

> Turtur does not use the internet. Notes are sealed for the recipient. Nearby Bluetooth devices can still see that Turtur is on. This is not an emergency beacon. Mail can be late or never land.

On Okay:

> This goes to your flock, not rescue services.

On End trip:

> This only molts this phone.

On Crowd toggle:

> Faster flight, worse battery, easier to notice.

## Incident assumptions

If a gameable bug shows up, fail **closed**: refuse join, refuse carry, do not downgrade to plaintext. Never add a “debug unencrypted” switch in a release build.
