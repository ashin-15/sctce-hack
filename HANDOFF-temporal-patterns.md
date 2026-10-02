# Handoff: Sakshi temporal harassment-pattern research

**Date:** 2 October 2026
**Scope:** Research and design task "detecting harassment patterns across multiple messages and events rather than classifying each message independently". This handoff lets a new session resume without the prior conversation. Full history: `/home/ashin/.local/share/devin/cli/summaries/history_be3ad67431cd4733.md`.

## Enduring objective

Sakshi is a privacy-first Android app for harassment-evidence preservation, local pattern detection, human review, and user-controlled reporting. Sensitive evidence stays on-device; AI findings are evidence-linked suggestions, never legal verdicts. **Laya is deferred** per `AGENTS.md`; do not reopen that decision. The temporal engine is deterministic and separate from the classifier.

## Deliverables produced this session

- `research/temporal-harassment-patterns.md` (703 lines) - the main report. 15 sections: recommendation, method, state-of-the-art review, five-approach comparison, algorithm selection, event schema design, linking/timeline/counting, analysis pipeline, explanation contract, six synthetic timelines, false-positive/negative analysis, hybrid architecture + budgets, evaluation methodology, hackathon plan, source register.
- `data/sakshi-event-schema.json` (207 lines) - JSON Schema draft 2020-12 design contract. 20 required top-level fields. Not an implemented storage format.
- `.lavish/sakshi-temporal-patterns.html` - interactive visual overview. Synthetic timeline replay (prefix slider + boundary-mode selector), algorithm filter table, method/evidence comparisons. No remote assets.

## Validation already performed

- Schema parses and passes `jsonschema.Draft202012Validator.check_schema`.
- The synthetic event example in report section 6.3 validates against the schema with **0 errors, format checks enabled** (reproduce: extract the single fenced `json` block from the report and validate; done via `uv run --no-project --with "jsonschema[format]"`).
- No em dashes or en dashes in any of the three files; no TODO/FIXME markers; no `Saxshi` typos.
- HTML: no remote assets (all `http` refs are explicit source links), all JS-referenced element IDs exist, `node --check` passes, headless Chromium renders (16 timeline items, replay output populated, theme button present).
- No git commit or push was made.

## Not yet done

1. **Full browser UX check** of the artifact in light theme and at 390px width (the earlier local-AI artifact got this treatment; this one only got a headless DOM check). Interactive states (slider mid-values, `private`/`unknown` boundary modes, algorithm filter) are worth one manual pass.
2. **Cross-check figures** in the report against sources on re-read (e.g., GAT F1 0.7624, CGA Gemma2 71.0%/34.2% FPR, TGBully 80.97/69.35) - all were transcribed from primary sources but a fresh-eyes pass is cheap insurance.
3. **Commit.** All files listed under `git status` below are uncommitted. Do not commit this session's files together with the parallel sentiment-session files unless the user asks for one combined commit.
4. **Implementation** - nothing is built. Next engineering step per `AGENTS.md` priority order is Android evidence acquisition, but the temporal reducers and unit tests listed below are the relevant follow-up for this design.

## Decisions that must survive editing

- Three-tier output model: **observed facts** (counts, intervals, ordering), **reviewable interpretations** (repeated pressure, category transition, possible escalation), and **excluded predictions** (no danger scores, violence prediction, guilt, or legal conclusions in MVP).
- `observed_at` vs `available_at` vs `timestamp` interval are three distinct concepts. A later user review cannot be used by an earlier analysis run (anti-leakage rule; the report's 09:10/09:20 example encodes this).
- Notification callbacks != messages; lifecycle events != contacts; quoted/reposted text != new contacts; equal text/hash != one real-world occurrence; sender display labels != authenticated identity.
- `severity` is a review priority, not a danger or seriousness score. `confidence.semantics` distinguishes calibrated probability / uncalibrated bounded score / not applicable / unknown.
- Rules are the backbone; sequence models, PELT/BOCPD, Hawkes, neural graphs are deferred comparators, not MVP components. Anomaly detection (Isolation Forest/LOF/autoencoder) is comparator-only and explicitly called out in section 5.2/5.3 area with the [A7] scikit-learn reference added this session.
- Boundary markers (`do_not_contact`, `user_disengagement`, `limited_contact`, `user_resumption`) distinguish *communicated* boundaries from private/internal markers - a private disengagement note cannot support a "sender ignored the stop request" claim.
- Text anchors use **Unicode code points, not UTF-16** (Kotlin/Java conversion must be implemented and tested).
- The all-zero SHA-256 in the schema example is an explicit fixture placeholder, never real integrity evidence.
- Retention tiers: transient non-candidates, encrypted expiring candidate inbox (proposed 24h / 100 records / 2 MiB - unvalidated starting values), confirmed vault. RAM counters cannot reconstruct discarded text.

## Pending engineering tests (from report section 13.4)

Duplicate notifications, quoted/reposted content, missing timestamps, uncertain sender, partial coverage, boundary markers, event revisions, `available_at` leakage, cross-case links, Unicode code-point offsets, late-import replay equivalence, benign high-density contrast.

## File map and parallel-work warning

- `HANDOFF.md` (repo root) belongs to a **parallel session** on the sentiment/emotion design (`research/sentiment-emotion-local-ai-android-design.md`, `research/probes/`). Do not overwrite it; its open items are that session's responsibility.
- `research/harassment-detection-datasets.md` is also from a parallel session (dataset inventory).
- This session's files: `research/temporal-harassment-patterns.md`, `data/sakshi-event-schema.json`, `.lavish/sakshi-temporal-patterns.html`.
- Prior-session local-AI deliverables to build on: `research/local-ai-architecture.md`, `research/laya-source-analysis-and-sakshi-local-ai-architecture.md`, `.lavish/sakshi-local-ai-architecture.html`, `.lavish/sakshi-laya-local-ai.html`.

## Style constraints

- No em dashes or en dashes (project rule - verified clean this session).
- British "behaviour" spelling throughout the report; stay consistent.
- Match the existing `.lavish/` plum/cream/gold design system; system fonts; no remote assets.
- Never present synthetic fixtures as real-world evidence; keep measured/estimated/unvalidated claims distinct.
