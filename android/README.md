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
| `:processing:ocr` | Android library | Bundled ML Kit Latin text recognition (no download), line regions with engine scores, EXIF rotation, decode bounds. Its manifest removes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions that ML Kit's telemetry library adds |
| `:processing:stt` | Android library (arm64 native) | whisper.cpp base q5_1 speech to text: `SttProcessor`, `ModelSessionManager` (hash-pinned model, one heavy model at a time, unloads), `ModelProvisioner`, in-memory `MediaCodec` decoding over the vault's random-access reader, transcript with time ranges, silence as an explicit result. Library only: no screen yet. See "Speech to text (phase 11)" |
| `:processing:analysis` | Android library | Text analysis pipeline, screenshot text (OCR derivative with regions, one event per image, cues anchored to text span and image region), derivative storage, single-pass code-point body and quote extraction (`EventText`), on-demand pattern engine with supporting events |
| `:export:bundle` | Kotlin/JVM | Bundle writer, offline verifier, command-line tool (`./gradlew :export:bundle:run --args="verify <dir>"`) |
| `:export:report` | Android library | Report model, PDF renderer, Keystore signer (ECDSA P-256), export service, export audit records |
| `:app` | Android | Full screen suite: Onboarding, Biometric/Device Lock, Case Management, Import (ShareTargetActivity, pickers, paste, notes), Analysis Prompts, Timeline, Event Review, Who Is Who, Patterns, and Report Export. Permissions: `USE_BIOMETRIC`, legacy `USE_FINGERPRINT`, and dynamic receiver permission (no `INTERNET` permission) |

## Requirements

- JDK 21 for Gradle (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on the development host; the default JDK 27 is not used).
- Android SDK platform 36 (`ANDROID_HOME` or `local.properties`).
- The NDK is needed only for `:processing:stt` (NDK `29.0.14206865`, SDK CMake `3.31.6`, both from the SDK manager; see "Speech to text (phase 11)"). The bundled model of the other modules is ML Kit's Latin OCR model, which comes from Maven with the library. A device or emulator is needed to verify the parts listed under "Verified on a device, and what is not".

## Commands

Run from this directory:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew test :app:assembleDebug
./gradlew :app:lintDebug :app:verifyManifestPermissions
./gradlew :core:vault:connectedDebugAndroidTest :acquisition:importer:connectedDebugAndroidTest :processing:ocr:connectedDebugAndroidTest :processing:analysis:connectedDebugAndroidTest :export:report:connectedDebugAndroidTest   # needs a connected device
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

Instrumented tests on one Samsung SM-S928B (Android 16) cover the SQLCipher database, the Keystore key wrapper, on-disk confidentiality, tamper detection, event storage, import through a real content provider, text analysis and derivative generation, on-device Keystore report signing, and PDF report creation. The OCR and image analysis tests also ran on a CPH2695 (Android 16): ML Kit reads synthetic Latin screenshots in a process without network permission and returns no text for Malayalam and Devanagari renders. Not yet verified: OCR in the app with a person using it, release-build OCR latency and memory, the unlock flow and every screen with a person using the app, a file share from another app, the pickers, lock on background, backup and device-transfer exclusion, Android versions below 16, and 16 KB page devices.

## Speech to text (phase 11)

`:processing:stt` is a library and device-test deliverable only; it has no screen and the app does not call it yet. It runs whisper.cpp (multilingual base, 5-bit `q5_1`) on the CPU, offline. There is no network code in the module and the model is not in the APK.

Preparation steps (the only network steps; the Gradle build never downloads and fails with a pointer to the script if the source is missing):

```sh
# one pinned whisper.cpp release (tag and SHA-256 are in the script), extracted to android/third_party/ (git-ignored)
./tools/prepare-whisper.sh
# model: the repository's models/whisper.cpp/ggml-base-q5_1.bin (git-ignored), SHA-256 422f1ae4...a8898.
# For device tests, push it where the tests expect it:
adb push ../models/whisper.cpp/ggml-base-q5_1.bin /data/local/tmp/ggml-base-q5_1.bin
```

In the app, the model is installed by an explicit "prepare speech model" action: `ModelProvisioner.forContext(context).importFrom(inputStream)` streams a user-chosen file into `noBackupFilesDir/models`, hashes it, and renames it into place only when it is the pinned file. `ModelSessionManager` verifies the SHA-256 again before every load and refuses on a mismatch.

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :processing:stt:testDebugUnitTest :processing:stt:connectedDebugAndroidTest
```

Limits, stated plainly:

- arm64-v8a only. The native library is not built for x86_64, so the module cannot be exercised on an x86_64 emulator; the JVM tests use a fake engine.
- Clips of at most 60 s (0.5 s container tolerance), one at a time, refused with `TooLong` before the whole file is decoded when the container states its duration. Silence is `NoSpeech`; unreadable audio is `Unsupported`; a phone at thermal status SEVERE or worse is refused (`Failed(THERMAL)`).
- Audio is decoded with `MediaExtractor` and `MediaCodec` reading through the vault's authenticated random-access reader (`BlobReader.asRandomAccessSource()`), so no decrypted byte is written to a file. Only the in-memory PCM (under 4 MB for 60 s at 16 kHz) exists.
- Segment confidence is the engine's mean token probability: uncalibrated, not a probability that the words are right. The model transcribes in the spoken language and never translates.
- The signal-level silence gate (about -50 dBFS), the model no-speech limit (0.6) and the low-confidence limit (0.5) are demonstration settings, not calibrated.
- Malayalam, Hindi and code-mixed speech accuracy is not measured. Device numbers from the instrumented tests are debug-build observations on one phone, not V-12 release measurements.

## Permissions check

`./gradlew :app:verifyManifestPermissions` (part of `check`) fails the build if the merged manifest gains a permission outside the allowlist in `app/build.gradle.kts`.

## Policy checks

`:tools:policy` holds plain JVM unit tests that scan the repository's source files as text and fail `./gradlew test` when the code drifts into forbidden territory (AGENTS.md "Do Not Build", megaplan security tests). Run them alone with:

```sh
./gradlew :tools:policy:test
```

Checks, each with its own test class and failure messages that name `file:line` and the rule:

- `ForbiddenApiCheck`: no AccessibilityService, MediaProjection, SMS, call log, contacts, broad storage or media permissions, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `java.net`, `javax.net`, OkHttp, Retrofit, Ktor, `android.net.http`, WebView, DownloadManager or NotificationListenerService in any `src/main` tree. `INTERNET` and `ACCESS_NETWORK_STATE` may only appear as `tools:node="remove"`. A per-module exception for the notification listener is a one-line change in `ForbiddenApiScanner.MODULE_EXCEPTIONS`.
- `ForbiddenDependencyCheck`: no networking, analytics, crash, ads, image-loader or cloud-AI dependency in `*.gradle.kts` or `gradle/libs.versions.toml`.
- `NoLoggingCheck`: no `Log`, `println`, `printStackTrace`, `System.out`, `System.err` or Timber in main sources. Only the verifier command-line tool may print.
- `NoSuppressionCheck`: no `@Suppress`, `@SuppressLint`, `@SuppressWarnings`, `tools:ignore`, `//noinspection` or weakened lint and warning settings.
- `TypographyCheck`: no em dash or en dash in any source, resource, build or markdown file; `strings.xml` values use the ellipsis character, not three dots.
- `ForbiddenPhraseCheck`: no legal, guilt, danger-score or admissibility wording in `strings.xml` values or in `ReportText.kt`. Negated honesty statements must be listed in `ReviewedExceptions.kt` with their exact text; changing the text of a listed string fails the test until it is reviewed again.

Comments are not exempt: a forbidden token in a comment is reported. The policy module is excluded from its own scans in `Repo.kt`. A real finding in another module may be recorded as a `KnownFinding` (one file, one pattern, no wildcards); the test fails when the entry becomes stale, so it is removed once the fix lands.

## Optional message observation (3 October 2026)

The app now exposes **Notification collection** and **Visible message capture** under Home's Collection controls. Each starts with its own disclosure and an explicit Android settings grant. Neither grant is automatic. Notification posting permission for neutral local cue reminders is separate from notification listener access.

Notification candidates remain in a bounded memory inbox until the person selects an active case and saves them. Optional background observation continues while switching apps with the vault closed; screen lock clears drafts and alerts. The default observes only while the vault session is open. Reminders use the unreviewed demo cue list and can miss or misinterpret content.

Visible capture starts only after choosing apps and pressing Start, lasts at most five minutes, and reads only exposed ordinary visible text. Stop/return/expiry retain snapshots for authenticated review. Screen lock, revocation and service loss clear them. Use ordinary chats only; do not open View Once/disappearing-content during capture. Protection detection is incomplete and no protected access is bypassed. Saved snapshots have unknown sender, direction, message time and boundaries, even when their text resembles a chat export.

See `../docs/spec-driven/message-observation/` for scope, design, acceptance status and source references. Isolated synthetic device test receipts are under `../research/verification/android-observation-2026-10-03/`; they do not establish third-party app reliability. Accessibility service policy exception is confined to `:acquisition:accessibility`, and both system-bound services are verified disabled-by-default and binder-permission protected.
