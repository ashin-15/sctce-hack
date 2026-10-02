# Handoff: Sakshi harassment-detection dataset research

**Date:** 2 October 2026
**Scope:** Research task "datasets suitable for training and evaluating Sakshi's harassment-detection system" + assessment of `data/labeled_data.csv`. This handoff lets a new session resume without the prior conversation. A parallel session owns `HANDOFF.md` (sentiment/emotion design); do not overwrite that file.

## Enduring objective

Sakshi is a privacy-first Android app for harassment-evidence preservation, local pattern detection, human review, and user-controlled reporting. Sensitive evidence stays on-device; AI findings are evidence-linked suggestions, never legal verdicts. Laya is **deferred** (see `AGENTS.md`); do not reopen that decision.

## Current state

### Done

- Deliverable written: `research/harassment-detection-datasets.md` (576 lines). All required sections A-G are present:
  - A: three joined comparison tables (identity/paper/size; structure/temporal/balance; license/access/limitations) covering 39 dataset rows (D01-D39) plus a conflicts/exclusions section A4.
  - B: training recommendations with rights-aware research vs product portfolios, label-mapping table, concrete pipeline.
  - C: evaluation layers, metrics, and test-hygiene rules.
  - D: missing-data gap analysis + four supplementary-data tracks (Real / Authored / Synthetic / Fixtures) with pilot targets.
  - E: proposed Sakshi schema (entity/field table, behavior + pattern ontology, illustrative fictional JSON record).
  - F: collection/annotation protocol incl. governance, annotator safety, splitting, withdrawal.
  - G: ethical/legal considerations (DPDP 2023/Rules 2025, BSA 2023 s.63, licensing, user safety).
- Source ledger S01-S41 at the bottom of the report; every major dataset claim cites a primary or authoritative source.
- Supplied CSV assessed: identified as Davidson et al. 2017 hate/offensive dataset. 24,783 rows; 1,430 hate (5.77%) / 19,190 offensive (77.43%) / 4,163 neither (16.80%); 0 empty texts; vote columns sum to `count`; no conversation/temporal fields; SHA-256 `fcb8bc7c68120ae4af04a5b9acd58585513ede11e1548ebf36a5c2040b6f6281`.
- Verified: no em dashes in the report (`grep -c '—'` = 0).

### Not done (in priority order)

1. **Verification pass (todo item 4).** The tables contain many source-reported statistics and several deliberately-flagged conflicts. A fresh session should spot-check a sample of numbers against the linked sources (MACD 152,422 / 74,550 / 77,872; DravidianCodeMix per-language splits; ICHCL node counts; SafeCity 9,892 vs split arithmetic; InViS positive counts) and confirm every `[Sxx]` key in the body resolves to the ledger.
2. **Lavish artifact (optional, considered but not built).** The user asked for a dense comparison deliverable and the lavish skill was invoked earlier. A `.lavish/` HTML dataset-comparison artifact (e.g. `.lavish/sakshi-dataset-landscape.html`) would improve reviewability. Follow `.lavish/sakshi-temporal-patterns.html` for style; use `lavish-axi` via the `lavish` skill.
3. **Commit.** Nothing from this session is committed. The report is untracked. Match existing commit style (`dd5abcc`, `0836200`); do NOT add a co-author trailer; commit only if the user asks.
4. **Sibling-file consistency check.** `data/sakshi-event-schema.json` and `research/temporal-harassment-patterns.md` exist from another session; the proposed schema in section E was written independently and should be reconciled with `data/sakshi-event-schema.json` before implementation begins.

## Decisions that must survive editing

- No single dataset suffices. Central conclusion: public datasets are modular components; Sakshi needs a supplementary consent-based dataset. Do not soften this into "MACD covers Indic" - MACD has <4% code-mixing and no private-chat longitudinal structure.
- Provenance classes are strict: R (real observed), D (decoy), H (human-authored fictional/diagnostic), L (LLM-generated), U (unverified - quarantined). Synthetic never counts as real evidence; human-annotated synthetic stays L.
- `labeled_data.csv` stays unchanged; it is an English auxiliary baseline only, never harassment ground truth.
- MACD is research-only (CC BY-NC-SA per authors' OpenReview statement); TRAC CC BY-NC-SA; THREAT/SafeCity/AMiCA require permission. Unclear terms are a blocker, not permission. Do not relicense pooled data.
- Conflicting statistics are preserved with qualifiers (Uli 23,266 vs 24,000; MACD 150K vs 152,422; AMiCA percentage conflict; MOLD size drift). Do not "fix" them silently.
- The 2026 Mendeley cyberbullying dataset (D39) is quarantined: 75,000 claimed rows, automatic spans, unverified provenance.
- Missing label != negative; silence != consent; matched/paired subsets != prevalence. These rules are embedded throughout the report.
- Report intentionally distinguishes: ordered replies vs timestamps vs chronological splits vs true longitudinal dyads - four different capabilities.

## Quick verification commands

```bash
# CSV stats used in the report
cd /home/ashin/Hackathon/sctce-hack
python3 -c "
import pandas as pd, hashlib
df = pd.read_csv('data/labeled_data.csv')
print(len(df), df['class'].value_counts().to_dict())
print((df['hate_speech']+df['offensive_language']+df['neither'] != df['count']).sum())
"
sha256sum data/labeled_data.csv

# Structure + style checks
grep -c '—' research/harassment-detection-datasets.md   # must be 0
grep -n '^#' research/harassment-detection-datasets.md  # sections A-G
grep -o 'S[0-9][0-9]' research/harassment-detection-datasets.md | sort -u | wc -l  # ~41 ledger keys
```

## File map

- `research/harassment-detection-datasets.md` - this session's deliverable.
- `data/labeled_data.csv` - assessed input (Davidson 2017); do not modify.
- `HANDOFF.md` - belongs to the parallel sentiment/emotion session; references this report as sibling work.
- `data/sakshi-event-schema.json`, `research/temporal-harassment-patterns.md` - sibling session artifacts to reconcile with report section E.
- `.lavish/*.html` - existing artifacts; style reference if building the dataset-comparison page.
- `AGENTS.md` - operating contract: local-first, no private-app scraping, Observed/Inferred/Pattern/Unknown output classes.
- Full conversation summary: `/home/ashin/.local/share/devin/cli/summaries/history_c6947982a24f45dc.md` - contains the complete dataset research trail and per-source findings.

## Style constraints

- No em dashes (project rule; verified clean).
- Sources cited as `[Sxx]` keys into the ledger, not inline URLs in table cells.
- Mark unknown/conflicting data as NR/conflicting rather than filling gaps.
- Do not present publisher numbers as independently re-counted where only papers were consulted; only the CSV was locally inspected.
