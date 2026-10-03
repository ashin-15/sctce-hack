# Full implementation sequence

Status: DRAFT planning deliverable. No production implementation authorized by this document. PRD decisions and acceptance approval remain pending.

## M0 - Resolve scope and freeze contracts

Resolve D-001 to D-005. Freeze disclosure, supported capture modes, draft key/loss policy, stop behavior, limits, provenance schema, and acceptance conditions. A real-device spike using synthetic ordinary and protected screens should validate consent/service ordering, first-frame behavior, app selection, rotation, and OS stop. Do not copy incompatible AgentHita code. Outputs: approved PRD/design/AC, spike receipts, and exact module interfaces.

## M1 - One screenshot from consent to encrypted case evidence

Files: `android/settings.gradle.kts`, app build/manifest-policy checks, `android/acquisition/projection/**`, narrow app ActivityResult/container/coordinator wiring and collection-screen resources.

Include module, fix misleading secure-content claims, register the non-exported typed service, implement one-use consent and idempotent resource cleanup. Add bounded frame-readiness and correct row-stride handling. Implement encrypted ephemeral draft storage with agreed stop/key policy. Add screenshot disclosure/countdown, stop notification, return/unlock, review/select/save/discard. Stream import into the existing vault; make retries idempotent. Keep the vault background lock. No OCR is necessary to prove this slice.

Gate: AC-001 to AC-008 and AC-014 to AC-016 for screenshots. Demonstrate Start -> actual Android consent -> synthetic screen -> Stop -> unlock -> review -> vault save -> decrypt/hash match. Repeat denial, lock, revocation, low space, process death, and save retry.

## M2 - Bounded burst and geometry/lifecycle robustness

Depends on M1. Add event-driven sampling, hard deadlines/frame/byte limits, queue backpressure, geometry generations and surface resizing, exact duplicate suppression, explicit gaps, and a selectable draft gallery. Never auto-scroll. Bound thumbnail decoding. Protect key lifetime across app activity recreation, service shutdown, expiry, and stale callbacks.

Gate: AC-004/006/009/010/015. Real-device portrait/landscape, selected-app switching, system UI, bright/dark/theme/edit fixtures, repeated actions, and capture-limit receipts. Agree budgets before measuring; report actual peak memory, elapsed capture/save time, dropped counts, disk use, thermal/battery conditions. No invented benchmark target passes.

## M3 - Source-linked OCR, local suggestions, and reports

Depends on M1; M2 sampling identities must be stable before burst pattern analysis. Reuse existing image processing after save. Preserve OCR anchors and unknown attribution; prevent duplicate screenshot-derived events from inflating temporal patterns. Correct/limit layout parser claims. Add capture provenance and limitations to evidence details and explicit reports. Keep local provisioning and multilingual OCR deferral visible.

Gate: AC-011 to AC-013. OCR succeeds/fails/has no text; images survive each case. Offline execution without network. Accepted/rejected/edited findings trace to correct image areas. No capture-time/message-time conflation or fabricated sender/direction.

## M4 - Optional screen video

Depends on D-001, M1 lifecycle/security, and a frozen encrypted video container/export design. Add MediaCodec pipeline, encrypted complete segments, bounded duration/storage, crash-finalization receipts, local playback, frame extraction, review selection, provenance, and explicit export. Decide codec compatibility and rotation behavior before coding. Separate consent sessions for separate modes initially.

Gate: AC-017 and common consent/storage/lifecycle/compatibility gates. Verify no plaintext media files; validate playback, timestamp/gap mapping, interrupt/crash recovery, codec failure, and resource limits on actual phones.

## M5 - Optional supported internal audio

Depends on approved audio scope, M4 tracks/container, RECORD_AUDIO disclosure and source-policy tests. Add AudioPlaybackCapture/AudioRecord, explicit UID/usage restrictions, synchronized encrypted track segments, unavailable-audio outcomes, and optional local STT after retention. No microphone fallback or call-recording assumption.

Gate: AC-018 plus audio-specific bounds, permission denial/revoke, eligible and disallowed producers, profile mismatch, silence/unavailability, AV sync, local STT/source mapping, and native-speaker evaluation where relevant.

## M6 - Independent acceptance and release

Run feature instrumentation plus existing app/notification/Accessibility/import/vault/OCR/analysis/policy JVM and lint gates. Run repository Python verification where processing changes apply. Classify unrelated environment failures separately and record them. Check final merged release manifest, no debug fixture grants/packages, no new network/export/overlay access, backup exclusions, release-mode UI, upgrade/rollback, and real-device failure matrix.

Record exact APK digest, build/device/API/OEM versions, synthetic fixture provenance, commands, screenshots/video of workflow, timing/memory/space results, and AC result/evidence matrix under `research/verification/`. Update benchmark.md and architecture threat model when implementation evidence exists. Complete only when every applicable blocking AC passes; do not equate source/tests with acceptance.

## Ownership and coordination

After approval, create AGENT_PLAN.md with exact non-overlapping ownership. Shared controller/state/metadata/storage interfaces freeze first. A UI/review slice and a platform capture slice can then run in parallel; storage/security changes and integration stay sequential where files/contracts overlap. No agents are spawned for this planning request.
