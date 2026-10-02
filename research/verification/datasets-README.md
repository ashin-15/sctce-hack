# Dataset research completion

Completed 2 October 2026. This verifies the research inventory and reconciles its
proposed research envelope with the proposed temporal event contract. It does not
create a training corpus, implement an adapter, train a model, establish product
rights or validate an Android app. No private evidence was collected or uploaded.

## Reproduce local checks

Run from the repository root; the validator uses only Python's standard library:

```sh
python research/verification/datasets-validate.py
```

`datasets-validation.json` records the unchanged supplied CSV's SHA-256, row/class
counts, empty/exact-duplicate text checks, vote totals and range. No source text is
printed or copied into the receipt. Normalized/near-duplicate leakage remains
unmeasured, and upstream byte-for-byte provenance is not attested.

The validator checks sections A-G, all 41 source keys including ranges, exactly
D01-D39 in each comparison table, and E4 coverage of all 20 required temporal
event fields. It parses the fictional research example and checks its code-point
anchors, derivative references and prefix scope. This is not schema validation
of a production research format or execution of a research-to-app adapter.

## Primary-source spot checks

The following were checked live on 2 October 2026. They remain publisher-reported
counts, not independent counts of downloaded corpora.

| Check | Result and source |
|---|---|
| MACD total and classes | Table 2: 152,422 = 74,550 abusive + 77,872 non-abusive; 92,881 posts and 70,453 users. [Original paper](https://proceedings.neurips.cc/paper_files/paper/2022/file/a7c4163b33286261b24c72fd3d1707c9-Paper-Datasets_and_Benchmarks.pdf) |
| Dravidian totals | Table 1/2: Malayalam 20,010; Tamil 43,919; Kannada 7,772. Malayalam nonoffensive 17,697/20,010 = 88.44%, conflicting with the 85% prose. [Organizer paper](https://aclanthology.org/2021.dravidianlangtech-1.17.pdf) |
| Dravidian splits/classes | Malayalam 16,010/1,999/2,001; Tamil 35,139/4,388/4,392; Kannada 6,217/777/778. Table 2 training classes sum to the train totals; Malayalam offense-positive 239 + 140 + 191 = 570 (3.56% of training). [Participant paper, tables 1-2](https://aclanthology.org/2021.dravidianlangtech-1.44.pdf) |
| ICHCL node counts | Table 1 level sums: 2021 train 5,740 (2,841 HOF / 2,899 NONE); 2022 train 4,914 (1,636 SHOF / 888 CHOF / 2,390 NONE); 2023 test 998 (254/147/597); unlabeled 26 + 3,928 + 4,571 = 8,525. The combined reply CHOF cell says 736, while component years imply 717 + 79 = 796. [2023 organizer overview](https://ceur-ws.org/Vol-3681/T6-2.pdf) |
| SafeCity stories/splits | Paper reports 9,892 stories; README gives 7,201 + 990 + 1,701 = 9,892 while describing 10% test selection. Listed test share is 17.20%. Its eight multi-label combinations also sum to 9,892. Keep the conflict and require permission before data use. [Paper](https://aclanthology.org/D18-1303.pdf), [author README](https://raw.githubusercontent.com/swkarlekar/safecity/master/README.md) |
| InViS positives | 243/30,000 = 0.81%; platform positives 73 + 158 + 12 = 243 and sexual-violence positives 11 + 1 + 1 = 13. Section 4 states a balanced 484-row subset, inconsistent with all 243 positives plus equal negatives (486). Section 3 platform counts 9,936 + 10,093 + 9,973 = 30,002 also need file reconciliation. [Original paper](https://arxiv.org/pdf/2506.03312) |
| Quarantined D39 | Listing claims 75,000 rows, seven categories, automatic toxic spans and CC BY 4.0; no collection/consent/annotation audit or raw import was performed. Quarantine remains. [Mendeley listing](https://data.mendeley.com/datasets/x5rpmydktp/1) |
| Legal reference scope | Official PIB notice confirms notified 2025 DPDP Rules and phased compliance. India Code's alternate official BSA PDF identifies section 63; it is compiled as of 6 October 2025. These checks do not establish full current-law compliance. [PIB](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014), [India Code compiled text](https://www.indiacode.nic.in/indiacode/bitstream/123456789/20063/1/aa202347.pdf) |

PDF text extraction supported the checks. Browser screenshot fetches failed for
MACD, Dravidian and ICHCL, so no successful visual PDF-page audit is claimed.
Unverified release licenses, access conditions and original source conflicts stay
open. The remaining ledger entries retain the original investigation's scope.

## Consistency changes

- Current pilot covers English, Malayalam and Hindi, including Manglish, Hinglish
  and mixed scripts. Other language rows remain future-scope comparisons.
- The proposed pilot retains 240 windows across six diagnostic strata; the
  authored benchmark proposal becomes 1,200 fictional sequences. No such data
  was generated, so lower-model dataset-generation delegation was unnecessary.
- E4 maps all required event fields and preserves research-only provenance,
  rights, splits, gold labels and governance outside the strict event object.
- Annotation availability, source/capture clocks, user review, private versus
  communicated boundaries, confidence semantics, duplicates and case-scoped
  identity have explicit conversion limits and pending implementation tests.
- The optional Lavish comparison page was not built; the complete comparison
  remains in the report's three joined tables. This does not block the handoff's
  required verification or schema reconciliation.

## Regression checks

```sh
uv run --no-project --offline --python 3.12 \
  --with scikit-learn==1.7.2 --with numpy==2.2.6 \
  --with pycryptodome==3.23.0 --with psutil==7.0.0 \
  python -m unittest discover -s bench/tests -v
```

Result: 20 tests run, 19 passed, one optional faster-whisper decoder test skipped
because its runtime is absent. No dependency files, supplied data bytes, model
packs or measured benchmark results changed. The completed handoff was removed
after its verification results and remaining implementation limits were recorded.
