# Nexpense protocol and architecture

Status: protocol version 1. Nexpense is a trusted-group ledger, not a payment system.

## Identity and security

A group is identified by the public key derived from a shared Nostr secret key. The
shared key signs every event and is the only access-control boundary. Acting members
are encrypted application-level UUIDs, not Nostr identities. Any holder of the key can
act or delete as any member. The acting-member selection is local UI state that is
cleared on every app launch, so each session starts from an explicit choice.

A group key can be generated on device or imported as NIP-19 `nsec` or a raw
64-character hex secret. Device keys are AES-GCM encrypted by a non-exportable Android
Keystore key. Private keys must never be logged or published. Generated keys use
`SecureRandom`, not a general-purpose PRNG.

NIP-44 v2 encrypts JSON from the group key to its own public key. Names, amounts,
descriptions, settings, and settlements are ciphertext. Event times, kinds, public key,
and padded size remain public. NIP-09 deletion IDs are also public. There is no forward
secrecy, per-member authentication, or per-member revocation.

## Event envelope

All events follow NIP-01 and have `pubkey = group public key`. Verify `id` and `sig`
before decrypting. Use `created_at` for display and replaceable-event selection, never
as a ledger ordering guarantee.

| Record | Kind | `d` tag | Other tags |
| --- | ---: | --- | --- |
| Group settings | 30078 | `nexpense:v1:group` | `t=nexpense-v1` |
| Member | 30078 | `nexpense:v1:person:<uuid>` | `t=nexpense-v1` |
| Expense/settlement | 78 | `nexpense:v1:ledger:<uuid>` | `t=nexpense-v1` |
| Deletion | 5 | none | `e=<id>`, `k=78`, `t=nexpense-v1` |

Kinds 78 and 30078 are NIP-78 application data. Relays may require NIP-42 AUTH; the
client signs kind 22242 with `relay` and `challenge` tags.

### Group settings and members

```json
{"version":1,"type":"group_settings","name":"Lisbon trip","currency":"EUR"}
```

```json
{"version":1,"type":"person","person_id":"uuid","name":"Alice","active":true}
```

The latest valid addressable event wins, with event ID as a deterministic tie-breaker.
One addressable event per member prevents concurrent roster additions overwriting each
other. Renaming publishes a replacement with the same `d` tag.

### Expense

```json
{
  "version": 1,
  "type": "expense",
  "entry_id": "uuid",
  "payer_id": "alice-uuid",
  "description": "Dinner",
  "currency": "EUR",
  "total_minor": 3000,
  "incurred_at": 1760000000,
  "shares": [
    {"person_id":"alice-uuid","amount_minor":1000},
    {"person_id":"bob-uuid","amount_minor":1000},
    {"person_id":"carol-uuid","amount_minor":1000}
  ]
}
```

`total_minor` is integer euro cents and equals the sum of shares. The payer's own share
is explicitly stored but creates no obligation. Equal-split remainder cents are given
to members in lexicographic UUID order. Custom splits are stored as final shares, so
future roster changes cannot reinterpret history.

### Settlement and deletion

```json
{"version":1,"type":"settlement","entry_id":"uuid","from_person_id":"bob-uuid","to_person_id":"alice-uuid","amount_minor":1000,"currency":"EUR","note":"Cash","settled_at":1760000200}
```

A settlement records a cash payment and subtracts that amount from the directed
position. NIP-09 kind 5 references the original Nostr event ID with `e` and `k`.

## Deterministic ledger

For each expense share where `person_id != payer_id`, add `amount_minor` to the directed
position `debtor -> payer`. For each settlement from A to B, subtract its amount from
`A -> B`. Pair balance is `position[A][B] - position[B][A]`: positive means A owes B;
negative means B owes A. Duplicate IDs apply once; deletion removes target deltas.
Overpayments become reverse positions rather than disappearing.

For line details, obligations are reconciled per directed pair in `(created_at, event id)` order.
Opposite expenses remain visible as independent outstanding lines. Explicit settlements consume
oldest open lines in their payment direction; overpayments create reverse lines. Line allocation is
derived UI state; signed pairwise balances remain authoritative and net both directions.

## Synchronization and application

Nexpense uses Nostr-native eventual consistency. Temporary differences are unavoidable
with offline phones or incomplete relays. Given the same valid events and tombstones,
all devices converge to identical balances. The UI exposes offline, syncing, and
pending-change states; the syncing animation sits in a fixed navbar slot, so nothing
shifts when it appears or disappears. Writes update Room immediately
and schedule a network-constrained outbox worker; publishing does not require a foreground catch-up fetch.

Room stores signed ciphertext events, decrypted entries, tombstones, relay cursors,
and an outbox. Live subscriptions and overlapping catch-up requests filter by author,
kinds, and `t=nexpense-v1`, deduplicating IDs. No shared balance snapshot is authoritative.

Native Kotlin, Compose Material 3, Room, DataStore, WorkManager, OkHttp, Kotlin
serialization, and Bouncy Castle. The persistent header shows the group and a large
centered acting-member name with a deterministic avatar. Request supports equal splits
and everyone-including-requester. Pay supports partial and full settlement. Every
write confirmation repeats the acting member.

GitHub Releases contain a signed, versioned `nexpense-release.apk` for Obtainium.
Upgrades require the same signing certificate and an increasing `versionCode`.

Its content is NIP-44 encrypted metadata. Clients treat valid deletions as persistent
tombstones even when relays keep serving the original. Nostr cannot guarantee physical
deletion on all relays. Every deletion requires explicit UI confirmation.
