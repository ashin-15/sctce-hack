# MediaProjection agent plan

Status: Frozen for approved scope.
Specification approval reference: PRD/TECH_DESIGN/ACCEPTANCE frozen after user selected recommended decisions on 2026-10-03.
Shared contracts: ProjectionCaptureState, ProjectionCaptureLimits, ProjectionDraftStore, and ProjectionCaptureController in acquisition projection module.
Plan owner: Main agent.

## Tasks

### MP-T1: Platform capture and encrypted staging
- Objective: make the included projection module support robust one-use capture sessions and bounded encrypted drafts.
- Related FRs: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-009.
- Related ACs: AC-001 through AC-006, AC-009, AC-010, AC-015.
- Dependencies: frozen contracts above.
- Allowed paths: `android/acquisition/projection/**`.
- Forbidden paths: app UI/integration, vault database migrations, notification/accessibility collectors.
- Required output: lifecycle-safe projection foreground service, draft store, limits/state, source corrections, focused JVM/instrumentation tests.
- Checks: projection module JVM tests, policy/build checks, device receipt where available.

### MP-T2: App integration and permanent vault import
- Objective: connect consent, foreground service, review UI and explicit vault save.
- Related FRs: FR-001, FR-002, FR-004, FR-006, FR-007, FR-008, FR-010.
- Related ACs: AC-001, AC-004 through AC-008, AC-011 through AC-016.
- Dependencies: MP-T1 interfaces frozen; execute serially in same workspace.
- Allowed paths: `android/settings.gradle.kts`, `android/app/**`, projection app-facing service bridge only after contract review, `android/acquisition/projection/src/main/AndroidManifest.xml`.
- Forbidden paths: changes to notification/accessibility behavior except regression fixes directly required by integration; database schema/migration changes absent demonstrated need.
- Required output: consent result -> visible FGS, app-window/display picker, settings/UI, encrypted temporary review gallery, selected-case import receipts, error/retry/discard flows.
- Checks: app build/lint, focused integration tests, manifest verifier, actual device flow if attached.

### MP-T3: Main-agent system judgment and delivery
- Objective: inspect complete diff, run permitted project checks and acceptance evidence, update loop and verification record.
- Related FRs/ACs: all applicable approved scope.
- Dependencies: MP-T1 and MP-T2.
- Allowed paths: read all; write verification docs/receipts, benchmark milestone, threat-model implementation status.
- Forbidden paths: unrelated user changes, commits, publishing.
- Required output: per-AC Pass/Fail/Blocked matrix, precise device/build limits, local debug install only when existing authorization/device state makes it safe.
- Checks: frozen acceptance and relevant existing regressions.

No subagent is assigned because service, session locks, shared contracts and app wiring overlap heavily and serial ownership reduces integration risk.
