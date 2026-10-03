# Message observation integration

Status: implementation authorised by the user on 3 October 2026: "Please do the app integration. I need the features to be working immediately."

Goal: preserve user-reviewed notification excerpts and visible chat-window text, with optional local review reminders, without cloud inference or unsupported access.

| ID | Required behaviour |
| --- | --- |
| FR-01 | Notification disclosure, explicit opt-in, app allowlist, Android settings grant, pause, disable, coverage and candidate review reachable in the app |
| FR-02 | Save selected notification excerpts into an existing active encrypted case with all original source and collector claims; save-and-analyse opens existing review pipeline |
| FR-03 | Separately opt into observing while switching apps: vault still locks; temporary notification drafts are memory-only and clear at screen lock, process exit, disable or revocation |
| FR-04 | Separate consent and POST_NOTIFICATIONS grant for neutral local cue reminders; matches are unreviewed demonstration phrase cues with false matches and misses |
| FR-05 | Explicit, selected-app, five-minute Accessibility session reads only exposed visible text; no interaction automation; reauthentication before reviewing/saving |
| FR-06 | Accessibility excerpts preserve collector provenance; sender, direction, message time and boundaries remain unknown; chat-shaped text never becomes a fabricated parsed conversation |
| FR-07 | Stitch Calm Sanctuary UI exposes collection controls and existing evidence/timeline/report workflows using real case data |

Out of scope: private databases, scraping without consent, View Once/disappearing-content circumvention, protected content, automatic replies/scrolling/sending, authenticated authorship, continuous reliable background monitoring, automatic evidence saving, external alerts, validated threat prediction. Other-app compatibility requires real app/version/device tests.

Temporary observation consent does not imply permanent saving, analysis, export or third-party sharing. No change to existing English/Malayalam/Hindi text scope or deferred Indic OCR.

AgentHita is a source-analysis reference only. Its local LICENSE prohibits building a competing product using its code. No source copied; independent Android API implementation.
