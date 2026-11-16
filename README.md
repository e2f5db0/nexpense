# Nexpense

Nexpense is a mobile-first Nostr ledger for shared expenses among trusted friends. It
tracks EUR debts and cash settlements; it does not move money. A shared Nostr keypair
identifies and encrypts each independent group.

## Features

- Generate a group key on Android or import one as `nsec` or 64-character hex.
- Confirm who you are acting as at every launch and switch members without logging out.
- Add all members at creation and name or remove groups; deterministic avatars distinguish them at a glance.
- Equal or custom requests from any subset, including an optional requester share.
- Partial/full cash settlements and directed line-by-line outstanding debts per person.
- NIP-01 signatures, NIP-44 v2 encryption, NIP-42 relay AUTH, and NIP-09 deletion.
- Room cache, immediate local writes, background outbox delivery, overlapping catch-up, and periodic sync.
- Deterministic integer-cent balances with duplicate/out-of-order event handling.
- Pixel-art or monospace interface with orange, blue, purple, red, or green accents, N€ branding, and animated loading indicators.

**Trust and consistency:** every key holder can read, write, impersonate, and delete as
any member. Event contents are private, but Nostr metadata is not. Devices can show
temporarily different balances while offline or relay sets differ. They converge when
they have the same valid events and deletion requests. Nostr deletion does not erase
copies held by other relays or clients. There is no forward secrecy.

## Build

Requires JDK 17 and Android SDK 35. The Gradle wrapper supplies Gradle 8.9. Set
`sdk.dir` in `local.properties`, and if `java` is not on `PATH`, prefix Gradle with
`JAVA_HOME` (on this machine `JAVA_HOME=$PWD/.tooling/jdk ./gradlew …`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Device keys are encrypted
with Android Keystore. Android backup and screenshots are disabled to reduce accidental
key disclosure. `local.properties`, `.tooling/`, and signing secrets are not committed.

See [the protocol and architecture](docs/architecture.md) for event schemas and ledger
semantics. Tests include official NIP-19, NIP-44, and BIP-340 vectors, self-encryption, tamper
rejection, splitting, self-shares, netting, partial/full payment, overpayment,
duplicates, deletion, and arrival-order convergence.

## Signed releases and Obtainium

Create a long-lived release keystore and keep a secure backup. Copy
`keystore.properties.example` to `keystore.properties`, fill in its absolute path and
passwords. Configure the repository secrets that `.github/workflows/release.yml`
signs with on tag pushes:

- `NEXPENSE_KEYSTORE_BASE64`: Base64-encoded release JKS
- `NEXPENSE_STORE_PASSWORD`
- `NEXPENSE_KEY_ALIAS`
- `NEXPENSE_KEY_PASSWORD`

No `nsec` belongs in GitHub, release assets, logs, issues, or CI secrets. Those secrets
are only for the Android application signing key.

### Release checklist

One release cycle is one feature commit plus one version bump. Pushing a `v*` tag runs
the "Test and release" workflow, which re-tests, re-signs with the repository secrets,
and attaches `nexpense-release.apk` to a GitHub Release with generated notes — the
published asset is always CI's build, never a local copy.

1. Validate locally: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
2. Commit the cycle's changes as one feature commit with a short imperative subject,
   for example `Improve ledger workflows and pixel-art interface`.
3. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts` and commit it
   as `Bump version to X.Y.Z`.
4. Tag the bump commit with a lightweight `vX.Y.Z` tag named after `versionName`.
5. Rebuild `assembleRelease` and copy `app-release.apk` to `nexpense-release.apk`
   after the bump if you want a local APK for device testing; it is gitignored, and
   a copy built before the bump still carries the old `versionCode`.
6. `git push origin main vX.Y.Z` publishes the release.

Keep the same signing certificate and increment `versionCode`/`versionName` for every
update; otherwise Android will reject upgrades. In Obtainium choose **Add app**, enter
this repository's GitHub URL, and select the release APK.

## License

Nexpense is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

Nexpense is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the [GNU AGPL](LICENSE) for more details.

SPDX-License-Identifier: AGPL-3.0-or-later

Copyright (C) 2026 e2f5db0

Corresponding source for every published APK is this repository at the matching
`v*` tag; each release attaches the signed APK and GitHub exposes the source
archive for that tag.

## Validation limits

JVM protocol tests and Android builds run without an emulator. Relay-specific
policies and Obtainium installation must also be tested on a physical Android
device before a production release.
