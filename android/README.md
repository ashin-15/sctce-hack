# Sakshi Android project

Implementation follows `../MEGAPLAN.md`. This directory contains the complete foundation implementation: pure-Kotlin core, encrypted storage layer, evidence acquisition, text processing, analysis and pattern evaluation, Keystore-signed export bundles and reports, and the full Jetpack Compose application screen suite. Storage, importer, analysis, and report modules pass instrumented tests on an attached phone (see `../benchmark.md`).

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin/JVM | Typed event contract for `../data/sakshi-event-schema.json`, code-point spans, schema adapter, application invariants |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, count-bound Merkle v2, RFC 8785 canonical JSON |
| `:core:temporal` | Kotlin/JVM | Deterministic pattern engine: contact canonicalisation with count bounds, partial-order time, coverage gaps, four demo pattern rules, template explanations. Synthetic timelines A-F ship as test fixtures |
| `:core:crypto` | Kotlin/JVM | Chunked AES-256-GCM blob envelope with authenticated random access, key wrapping interface |
| `:core:database` | Android library | Room schema (26 tables), insert-only triggers, DAOs, SQLCipher open path |
| `:core:vault` | Android library | Keystore key wrapper, blob store, audit hash chain, case/evidence repositories, event store, actor registry, review coordinator, derivative store |
| `:acquisition:importer` | Android library | Share intent, picker and paste readers, streaming limits, manual note codec |
| `:processing:text` | Kotlin/JVM | Script and language hints, rules cue engine, label mapping, WhatsApp text-export parser |
| `:processing:analysis` | Android library | Text analysis pipeline, derivative storage, single-pass code-point body and quote extraction (`EventText`), on-demand pattern engine with supporting events |
| `:export:bundle` | Kotlin/JVM | Bundle writer, offline verifier, command-line tool (`./gradlew :export:bundle:run --args="verify <dir>"`) |
| `:export:report` | Android library | Report model, PDF renderer, Keystore signer (ECDSA P-256), export service, export audit records |
| `:app` | Android | Full screen suite: Onboarding, Biometric/Device Lock, Case Management, Import (ShareTargetActivity, pickers, paste, notes), Analysis Prompts, Timeline, Event Review, Who Is Who, Patterns, and Report Export. Permissions: `USE_BIOMETRIC`, legacy `USE_FINGERPRINT`, and dynamic receiver permission (no `INTERNET` permission) |

## Requirements

- JDK 21 for Gradle (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on the development host; the default JDK 27 is not used).
- Android SDK platform 36 (`ANDROID_HOME` or `local.properties`).
- No NDK or model is needed yet. A device or emulator is needed to verify the parts listed under "Verified on a device, and what is not".

## Commands

Run from this directory:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew test :app:assembleDebug
./gradlew :app:lintDebug :app:verifyManifestPermissions
./gradlew :core:vault:connectedDebugAndroidTest :acquisition:importer:connectedDebugAndroidTest :processing:analysis:connectedDebugAndroidTest :export:report:connectedDebugAndroidTest   # needs a connected device
```

The Python reference suite is unchanged and still runs from the repository root:

```sh
python -m unittest discover -s bench/tests -v
```

## Shared test vectors

`testfixtures/integrity-vectors.json` is generated from the reference functions `chain` and `merkle` in `../bench/adapters.py`, so Kotlin and Python must agree byte for byte. Regenerate from the repository root:

```sh
uv run --no-project --with numpy --with scikit-learn python android/tools/gen_integrity_vectors.py
```

`processing/text/src/test/resources/bench-rule-labels.json` holds the Python reference results for the cue engine; regenerate with `python3 android/tools/gen_rule_expectations.py . android/processing/text/src/test/resources/bench-rule-labels.json`.

`testfixtures/event-*.json` are synthetic events. The valid one is the example from `../research/temporal-harassment-patterns.md` section 6.3; its all-zero hash is a placeholder, not integrity evidence.

## Lint

`:app:lintDebug` passes with no errors. It reports version-availability warnings (compileSdk and targetSdk 37, a newer Compose BOM, a newer JSON schema validator). They are left open on purpose: the megaplan pins API 36, Android SDK platform 37 is not installed on the development host, and Compose BOM 2026.06.01 is the newest that compiles against API 36.

## Verified on a device, and what is not

Instrumented tests on one Samsung SM-S928B (Android 16) cover the SQLCipher database, the Keystore key wrapper, on-disk confidentiality, tamper detection, event storage, import through a real content provider, text analysis and derivative generation, on-device Keystore report signing, and PDF report creation. Not yet verified: the unlock flow and every screen with a person using the app, a file share from another app, the pickers, lock on background, backup and device-transfer exclusion, Android versions below 16, and 16 KB page devices.

## Permissions check

`./gradlew :app:verifyManifestPermissions` (part of `check`) fails the build if the merged manifest gains a permission outside the allowlist in `app/build.gradle.kts`.
