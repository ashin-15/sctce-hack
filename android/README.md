# Sakshi Android project

Implementation follows `../MEGAPLAN.md`. This directory currently contains megaplan phases 1 to 3: the pure-Kotlin core, the encrypted storage layer, and an app with onboarding, app lock and case management. Evidence import is not implemented yet. Nothing here has been installed, run or measured on an Android device or emulator; everything is verified by JVM tests only.

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin/JVM | Typed event contract for `../data/sakshi-event-schema.json`, code-point spans, schema adapter, application invariants |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, count-bound Merkle v2, RFC 8785 canonical JSON |
| `:core:temporal` | Kotlin/JVM | Deterministic pattern engine: contact canonicalisation with count bounds, partial-order time, coverage gaps, four demo pattern rules, template explanations. Synthetic timelines A-F ship as test fixtures |
| `:core:crypto` | Kotlin/JVM | Chunked AES-256-GCM blob envelope with authenticated random access, key wrapping interface |
| `:core:database` | Android library | Room schema (26 tables), insert-only triggers, DAOs, SQLCipher open path |
| `:core:vault` | Android library | Keystore key wrapper, blob store, audit hash chain, case and evidence repositories, job queue |
| `:app` | Android | Onboarding, biometric or device-credential lock, case list. Permissions: `USE_BIOMETRIC` only (plus the library's legacy `USE_FINGERPRINT` and an app-private AndroidX receiver permission) |

## Requirements

- JDK 21 for Gradle (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on the development host; the default JDK 27 is not used).
- Android SDK platform 36 (`ANDROID_HOME` or `local.properties`).
- No NDK or model is needed yet. A device or emulator is needed to verify the parts listed under "Unverified without a device".

## Commands

Run from this directory:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew test :app:assembleDebug
./gradlew :app:lintDebug :app:verifyManifestPermissions
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

`testfixtures/event-*.json` are synthetic events. The valid one is the example from `../research/temporal-harassment-patterns.md` section 6.3; its all-zero hash is a placeholder, not integrity evidence.

## Lint

`:app:lintDebug` passes with no errors. It reports version-availability warnings (compileSdk and targetSdk 37, a newer Compose BOM, a newer JSON schema validator). They are left open on purpose: the megaplan pins API 36, Android SDK platform 37 is not installed on the development host, and Compose BOM 2026.06.01 is the newest that compiles against API 36.

## Unverified without a device

These compile and are covered by design review only. They need an instrumented run on a phone or emulator before any claim is made:

- SQLCipher: `SakshiDatabaseFactory.openEncrypted` (native library, 16 KB page alignment, triggers and foreign keys under SQLCipher).
- Android Keystore: `KeystoreKeyWrapper` (key creation, StrongBox fallback, authentication window, invalidation). On API 26 to 29 the authentication window is set through reflection because the only API for it is deprecated.
- The biometric prompt, `FLAG_SECURE`, lock on background, backup and device-transfer exclusion, and the rendering of every screen.

JVM database tests use plain in-memory SQLite through Robolectric, and vault tests use a software key wrapper.

## Permissions check

`./gradlew :app:verifyManifestPermissions` (part of `check`) fails the build if the merged manifest gains a permission outside the allowlist in `app/build.gradle.kts`.
