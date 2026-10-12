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

If the phone is unlocked, the attacker has the loft. OS backups, screenshots of QR, and notifications on a lock screen can leak arrivals. **End trip** wipes Turtur’s store; it cannot wipe screenshots, notifications already shown, or forensic copies. End trip also **keeps** the separate guardian store on purpose: your SOS key, emergency contacts, and heard beacons from the last 72 hours.

**Duress / decoy loft is not in MVP.** Assume seized phone = seized trip.

### Split-brain molt

**End trip** is local. Others keep the trip. There is no global delete. That is confusing and is also the only honest design without a server.

### No support for “airplane mode but Turtur still works”

If Bluetooth is off, pigeons do not fly. Copy must not imply otherwise.

### Location

Mail pigeons never carry GPS. Typed meetup names can still be identifying (“Maya’s van”). Notifications default to hidden content on the lock screen. The **SOS beacon is the exception**: it broadcasts your GPS fix in the clear on purpose, and SOS alerts are visible on the lock screen.

### Best-effort SOS

Okay ≠ SOS. The SOS beacon is a separate, opt-in tool. It is **not** a PLB, Garmin inReach, satellite SOS, or ranger radio, and it never replaces calling 112. Market it as "might help someone nearby find you", never as rescue.

- **No reach to phones without Turtur.** Android and iOS do not let an app alert phones that don't have it installed. We do not spoof Fast Pair, Apple proximity pop-ups, or Find My adverts.
- **Range is Bluetooth range.** That is tens of metres on most phones and a few hundred on phones with LE Coded PHY. Terrain, bodies, and pockets cut it further. If no Turtur phone passes, nobody hears you.
- **Location and phone numbers are public to listeners.** SOS records are signed but not encrypted: any Turtur phone in range (and anyone running a BLE scanner with our UUID) can read your name, GPS fix, battery, and up to three emergency contact numbers. The SOS screen warns about this before it starts.
- **Anyone can forge an unverified SOS.** Signatures only prove a record was not altered and that later updates come from the same key. A stranger's beacon is labelled *unverified*. Only contacts whose guardian card you scanned, or SOS pigeons that arrive signed through your trip, show as *verified*. Expect pranks and mistakes; rescuers should still approach carefully.
- **Replay is limited, not prevented.** Listeners reject the same or a lower sequence number for a beacon they have seen. A recorded beacon replayed to someone who never heard the original will still alert them.
- **The SMS bridge depends on a human.** The person carrying your SOS must tap Send in their own SMS app once they have signal. Turtur cannot send SMS by itself (no `SEND_SMS`), cannot confirm delivery, and the cellular-signal nudge only fires when the phone sees mobile data, not voice-only service.
- **Battery.** The beacon advertises about once a second and takes a GPS fix every 10 minutes. Expect roughly a day or more on a healthy battery; cold weather and a weak battery shorten this a lot. Screen off helps.
- **Background listening has gaps.** The Good Samaritan listener uses a system-filtered background scan. Some Android 10–11 phones and aggressive battery savers only deliver results while Turtur's loft service is running. Full-screen alerts can be blocked on Android 14 and later unless the user allows them.
- **GPS may be stale or missing.** Inside a gully or under a canopy the fix can be old or absent; the record says when it was taken and how accurate it was.

Delay and loss are normal for mail. For an SOS, assume it may never be heard.

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

> Turtur does not use the internet. Notes are sealed for the recipient. Nearby Bluetooth devices can still see that Turtur is on. Mail can be late or never land. The SOS beacon is best effort: it only reaches Turtur phones in Bluetooth range, so call emergency services first if you have any signal.

On Okay:

> This goes to your flock, not rescue services.

On SOS (before starting):

> If you have any mobile signal, call emergency services first. Turtur only reaches Turtur phones within Bluetooth range.
>
> While the beacon is on, any Turtur phone in range can read your name, GPS position, and the phone numbers of your emergency contacts.

On a stranger's SOS:

> Unverified Turtur SOS. Could be a mistake or a prank; still worth a careful look.

On End trip:

> This only molts this phone.

On Crowd toggle:

> Faster flight, worse battery, easier to notice.

## Incident assumptions

If a gameable bug shows up, fail **closed**: refuse join, refuse carry, do not downgrade to plaintext. Never add a “debug unencrypted” switch in a release build.
