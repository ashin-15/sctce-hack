# Sakshi Android project

Implementation follows `../MEGAPLAN.md`. This directory currently contains slice 1 only (megaplan section 39): the Gradle skeleton, an empty app shell, and two pure-Kotlin modules. No evidence feature is implemented yet and nothing here has been measured on an Android device.

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin/JVM | Typed event contract for `../data/sakshi-event-schema.json`, code-point spans, schema adapter, application invariants |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, count-bound Merkle v2, RFC 8785 canonical JSON |
| `:app` | Android | Launcher activity with a placeholder screen; declares no permissions |

## Requirements

- JDK 21 for Gradle (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on the development host; the default JDK 27 is not used).
- Android SDK platform 36 (`ANDROID_HOME` or `local.properties`).
- No NDK, device or model is needed for this slice.

## Commands

Run from this directory:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew :core:model:test :core:integrity:test :app:assembleDebug
./gradlew :app:lintDebug
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

## Manifest note

AndroidX core contributes an app-private signature permission for dynamic broadcast receivers. It is removed in `app/src/main/AndroidManifest.xml` so the merged manifest declares zero permissions. Revisit this when a library that registers such receivers is added.
