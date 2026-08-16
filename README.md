# turtur

Delay-tolerant, end-to-end sealed messaging for **festivals, trekkers, and hikers**.

*Turtur* is Latin for the turtle dove (*Streptopelia turtur*). There is no chat server. Your phone is a loft. Other phones in your **trip flock** are the coops a pigeon can land in. A letter moves when people physically come into Bluetooth range — at camp, a hut kitchen, a trailhead, or a festival meeting point.

Turtur wins when WhatsApp has no tower.

**Tagline:** Camp mail that flies in pockets.

## Who it is for

- Festival crews that split across stages and reunite at camp
- Hut-to-hut trekkers who can wait until evening
- Weekend hikers who miss a junction or straggle to the car

## What the MVP does

- Start or join a **trip** by scanning a QR (no phone numbers)
- Send a **Note** to one person in the flock
- Send a **Meetup** (place + time) to everyone
- Send **Okay** (I'm fine / delayed / stay put) to everyone
- **Store-and-forward**: if the recipient is not nearby, another flock member carries the sealed pigeon until they are
- **Feed the flock**: a manual burst sync when you are in camp
- **End trip**: the loft molts (local trip data is wiped)

## What the MVP does not do

No internet relay, no iOS, no photos, no live GPS, no public nearby chat, no mountain-rescue SOS, no carrying mail for strangers.

## Vocabulary

| Thing | Meaning |
| --- | --- |
| Loft | This device's trip identity + keys |
| Flock / trip | The bonded group (max 12) |
| Pigeon | One sealed packet in transit |
| On the wing | Not delivered yet; sitting in someone's pocket |
| Arrivals | Mail that decrypted on this device |
| Feed the flock | Sync with phones in range now |
| Molt | End trip and wipe |

## Magic demo (success criteria)

Three Android phones. Airplane mode (Bluetooth on). A writes to C. B walks between them. At dinner, C has the letter. No cell, no Wi-Fi network, no server.

## Repo

- Decisions: [`discussion.md`](discussion.md)
- Screens and flows: [`flow.md`](flow.md)
- Threats and warnings: [`flaw.md`](flaw.md)
- Deferred work: [`futureimplementation.md`](futureimplementation.md)

The Turtur brand is a **turtle dove**, not a cartoon pigeon. Icon: copper band on dusk paper. Android app lives at the repo root (`dev.turtur`).

Open in Android Studio (JDK 17). Assemble:

```
./gradlew :app:assembleDebug
```
