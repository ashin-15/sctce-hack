# Sakshi - Design Specification

## 1. Product

**Sakshi: Evidence That Only You Can See** is a privacy-first Android application for organizing and understanding harassment-related evidence on the user's device.

Core flow:

```text
Evidence -> Local Processing -> AI Analysis -> User Review -> Incident Timeline -> Secure Report
```

Primary principles:
- Local-first: sensitive evidence stays on-device by default.
- Human-controlled: AI flags and explains; the user confirms, edits, or rejects.
- Evidence-preserving: originals are never modified.
- Minimal collection: process only evidence the user provides or Android legitimately exposes.
- No automatic legal conclusions.

---

## 2. Design Goals

1. Make evidence collection simple during stressful situations.
2. Surface repeated behaviour and escalation that is hard to see manually.
3. Keep AI outputs traceable to source evidence.
4. Protect evidence against accidental loss and detectable tampering.
5. Work offline for the core workflow.
6. Keep compute, storage, and battery usage practical for Android phones.

---

## 3. UI / Visual Design

### Style
- Calm, private, trustworthy, non-alarming.
- Avoid red-heavy "danger" styling except for confirmed high-priority warnings.
- Use clear status labels instead of dramatic scores.
- Sensitive content should be hidden by default until the user opens it.

### Suggested palette
- Background: `#F8FAFC`
- Primary: `#0F766E`
- Primary dark: `#115E59`
- Text: `#0F172A`
- Secondary text: `#475569`
- Warning: `#D97706`
- Critical: `#B91C1C`
- Success: `#15803D`

### Typography
Use a readable Android system font. Prefer clear hierarchy over decorative typography.

---

## 4. Navigation

Bottom navigation:

```text
Home | Evidence | Incidents | Vault | Reports
```

### Home
Shows:
- recent incident candidates
- pending AI reviews
- recent evidence
- quick action to add evidence
- privacy/offline status

### Evidence
Unified inbox for:
- screenshots/images
- text/messages
- audio/voice notes
- documents

Actions:
`Add` -> `Share to Sakshi` / `Import` / supported Android capture flow.

### Incidents
Groups related evidence into incidents and shows:
- date range
- source apps
- categories
- review status
- escalation indicators

### Vault
Encrypted local evidence store.
Users can inspect original evidence and its integrity metadata.

### Reports
Creates a user-reviewed timeline/report containing:
- incident summaries
- linked evidence references
- timestamps
- evidence index
- integrity/hash manifest
- disclaimer that the report is not legal advice

---

## 5. Evidence Acquisition

Sakshi must distinguish between **automatic signals** and **user-provided evidence**.

### Automatic / passive
Use supported Android mechanisms such as notification access where appropriate.

Possible data:
- notification text
- sender/title information exposed by the notification
- timestamps
- notification/media indicators

Do not assume access to another app's private database or files.

### User-mediated
Use Android-supported flows for:
- screenshots
- images
- audio
- video
- documents
- exported/shared content

### View Once / disappearing media
Do not bypass messaging-app protections. Research and implement only supported, user-consented acquisition paths. If the actual media cannot be obtained, Sakshi may preserve the available metadata/signal and ask the user to provide evidence through a supported flow.

---

## 6. Local AI Pipeline

```text
Input
  |
  +--> Image -> OCR ----+
  |
  +--> Audio -> STT -----+--> Normalized Event
  |
  +--> Text -------------+
                            |
                            v
                     Local AI Router
                       /          \
              Fast classifier    Laya
                       \          /
                        v        v
                       Decision / Score
                            |
                            v
                     Temporal Engine
                            |
                            v
                      User Review
```

### AI rules
- Prefer lightweight models for routine classification.
- Use a local LLM only when context or explanation needs justify the extra cost.
- AI output must reference source evidence.
- AI must support an `uncertain/abstain` outcome.
- Never present model output as fact when the source evidence does not support it.

### Laya
Laya is a candidate decision/classification engine. Integrate it behind a stable internal interface so it can be benchmarked against other local models without changing the app architecture.

Example decisions:
- harassment category
- threat: yes/no
- intimidation score
- controlling-language score
- uncertainty/abstention

Fine-tuned models must be evaluated separately from base models, with conversation/person-level data splitting to avoid leakage.

---

## 7. Incident & Pattern Engine

Each analyzed item becomes an event:

```text
Event
- id
- timestamp
- source
- sender/actor if available
- category
- confidence
- evidence_id
- user_review_status
```

The pattern engine analyzes:
- recurrence
- frequency
- category changes
- temporal clustering
- escalation signals
- relationships between events

The UI should say **"Potential pattern"** or **"Escalation signal"**, not make a legal determination.

---

## 8. Evidence Security

```text
Original Evidence
      |
      +--> SHA-256 hash
      +--> encrypted storage
      +--> metadata record
      +--> integrity manifest
```

Recommended controls:
- Android Keystore for key protection.
- AES-GCM or an equivalent authenticated-encryption scheme.
- App-private storage.
- Backup/export controls designed to avoid accidental leakage.
- Optional biometric/PIN gate.

Hashing demonstrates detectable integrity changes; it does not by itself establish authenticity or court admissibility.

---

## 9. Privacy & Safety

### Must not build
- silent third-party app scraping
- cloud AI by default
- always-on high-compute monitoring
- automatic legal conclusions
- huge models that are impractical on phones
- a complex backend for the core workflow
- unsupported claims of court admissibility

### Main risks
- Android access restrictions
- false positives
- false negatives
- hallucination
- key loss
- evidence leakage
- device compromise
- multilingual weakness
- user-safety failure

Sensitive actions should be explicit and reversible where possible.

---

## 10. MVP Scope

### Build first
1. Android shell + navigation.
2. User-imported screenshots/images/text.
3. Local OCR + text normalization.
4. Local message classifier with Laya integration behind an interface.
5. Evidence records + encrypted storage.
6. User review screen.
7. Incident timeline.
8. SHA-256 integrity manifest.
9. Timeline/report export.

### Next
- notification ingestion
- local audio/STT
- multilingual/code-mixed support
- local retrieval over confirmed incidents
- personalized local feedback

### Later / research-only
- advanced multimodal local models
- on-device adaptation/fine-tuning
- federated learning
- broader platform coverage

---

## 11. Core UX Principle

Sakshi should feel like a **private evidence organizer with AI assistance**, not a surveillance application.

The product should always make three things clear:

```text
What was observed?
What did the AI infer?
What did the user confirm?
```
