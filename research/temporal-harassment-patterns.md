# Sakshi: detecting evidence-backed patterns across messages and events

**Research date:** 2 October 2026

**Status:** Source-backed research, event-schema proposal and implementation design. No Android app, harassment model, sequence detector or battery workload was benchmarked in this investigation. All example timelines are synthetic and are not real evidence.

**Deliverables:** this report; `data/sakshi-event-schema.json` (JSON Schema Draft 2020-12); `.lavish/sakshi-temporal-patterns.html` (reviewable visual overview).

## 1. Recommendation and scope

Build a **deterministic, provenance-aware temporal engine** over user-selected evidence and permitted notification observations. Combine it with a small local context-aware classifier and, optionally, a bounded foreground LLM explanation. Laya is deferred under the current project decision. No cloud inference, background scraping, automatic reporting, legal conclusions or individual violence predictions are required.

```text
supported observations / selected originals / user boundary notes
  -> immutable evidence references + extraction uncertainty
  -> distinct contact units + conservative actor/conversation links
  -> interval-aware timeline + acquisition-coverage ledger
  -> window counts + episodes + boundary-relative recurrence
  -> category/behaviour transitions + optional statistical change checks
  -> typed pattern record with supporting and limiting evidence
  -> deterministic explanation, optional local paraphrase
  -> user review + explicitly selected report export
```

**Hackathon:** reviewed event tags, actor associations and boundary notes; counts for bounded windows; recurrence after a reviewed boundary; source-linked category transitions; one transparent frequency comparison; template explanations. Models, graphs and anomaly detection can enhance this later but are not necessary to demonstrate sequence-level detection.

### Three different tasks

1. **Observed sequence description:** counts, timing intervals, distinct retained contacts, repeated requests and category changes in actual available evidence.
2. **Interpretation:** possible repeated pressure, intimidation or controlling behaviour, subject to context and user review.
3. **Prediction:** probability of future abuse, physical violence or a person's intent. This is a different, higher-stakes task with different data and validation requirements. It is not an MVP output.

A report can state “six retained contact observations after the boundary note” without stating “this person is stalking you.” A threat can merit review on its own; a repetition threshold must not prevent preservation/review of a single serious event.

## 2. Research method and evidence limits

Primary papers, author repositories, official algorithm references and Android APIs were prioritized. The review includes relevant 2024-2026 developments as well as foundational temporal algorithms. It is a targeted state-of-the-art review, not an exhaustive systematic review or proof that a technique is universally best.

Evidence labels:

- **Verified research result:** the cited authors report a result under their dataset/protocol; it is not Sakshi performance.
- **Verified method/API:** a described algorithm or public Android contract, not guaranteed app behaviour on every phone.
- **Design inference:** a proposed adaptation to partial, local evidence.
- **Open question:** requires data, user-safety review or Android benchmarking.

The existing acquisition, notification and local-AI reports are retained as context, not edited here. Their central constraint matters: ordinary notification content is transient by default, candidates are separately consented and expiring, and the user chooses confirmed evidence. This means Sakshi cannot promise complete long-term pattern detection from notifications. No personal messages, platform archives or clinical/police datasets were acquired. Public-paper access is not permission to train on every underlying dataset.

## 3. State-of-the-art review

### 3.1 Conversation-level and contextual classification

**CAD, NAACL 2021:** primarily English Reddit entries annotated with conversation context, primary/secondary abuse categories, rationales and expert adjudication. This supports context-sensitive annotation and evidence-linked explanations, but its labels are not a direct taxonomy of private repeated unwanted contact or coercive control. The author repository records corrections to 95 entries in v1.1; pin the dataset version rather than mixing labels across releases. Zenodo advertises CC BY 4.0 [S1].

**Graphically Speaking, ACL 2025:** models prior comments and reply topology with a graph attention network. The inspected April 2025 arXiv v1 table reports mean F1 over ten runs: no-context BERT 0.7453 ± 0.0076; Text-Concat 0.7417 ± 0.0081; Embed-Concat 0.7488 ± 0.0025; three-layer GAT 0.7624 ± 0.0058 (95% confidence intervals) [S2]. The final ACL abstract confirms the graph-based approach, but these exact figures are from the inspected preprint, not assumed identical final-paper numbers.

Important conflicting detail: the preprint narrative says flattened-context models underperform no-context BERT, while its table puts Embed-Concat slightly above it. Use the table accurately: **context representation matters; naive concatenation can worsen performance, and context is not guaranteed to help**. More than three hops did not provide clearly significant additional gains in the reported setup. Training used desktop/server GPUs; no Android inference evidence was established. Its full-thread reconstruction procedure is not something Sakshi should implement against private apps.

**Implication:** classify the current message using selected preceding context, speaker roles, quoted content and available boundary information. Keep a separate sequence/pattern head. Do not collapse an entire chat into one harassment bit or mark every participant/message as abusive because a conversation received a positive label.

### 3.2 Sequence classification and temporal cyberbullying

**Temporal Properties of Cyberbullying on Instagram, 2020:** descriptive and burst analysis studies differences in number, timing and frequency within social-media sessions [S3]. It supports inspecting time structure, not a universal “more than N messages = harassment” threshold. Public comment pile-ons differ from private one-to-one repeated contact.

**TGBully, WWW 2021:** combines comment semantics, user-language history, topic similarity, time gaps and temporal graph interactions for session-level cyberbullying [S4]. Its full-training Instagram table reports recall 82.57 ± 3.10, F1 80.97 ± 2.03 and AUC 92.91 ± 1.30, versus HAN F1 76.99 ± 1.99. On Vine, TGBully F1 is 69.35 ± 2.04. These are different dataset results, not a combined accuracy guarantee.

Its random 80/10/10 **session split assumes cross-session independence**; it does not establish generalization to unseen people/relationships. Its inputs include historical platform comments that a local Android evidence app does not possess. The released environment is Python 3.6/Keras 2.2/TensorFlow-GPU 1.12, not a drop-in mobile runtime. Preserve the useful ideas (time gaps, actor history scoped to consented evidence, sequence structure), not the data-acquisition assumptions or old deployment stack.

**Suitable modern small sequence variant:** encode each permitted message once, concatenate compact role/category/time/boundary/missingness features, and run a small GRU or temporal convolution over a bounded sequence. A hierarchical transformer is an experimental alternative. Sequence length, real-time gaps and data leakage must be evaluated explicitly. A “time” embedding based only on message position cannot distinguish six messages in five minutes from six in a year.

### 3.3 Conversational forecasting and recent evaluation advances

**Conversations Gone Awry, But Then? (2025):** standardized evaluation of thirteen models and introduced forecast recovery [S5]. CGA-CMV-large contains 19,578 conversations; moderator deletion for a rude/hostile comment is a proxy label, not verified private harassment. The model must not see the final outcome-revealing comment. The inspected table, averaged across five seeds, reports:

| Model | Conversation accuracy | F1 | False-positive rate | Mean forecast horizon |
|---|---|---|---|---|
| CRAFT | 62.8% | 68.5% | 55.5% | 4.7 turns |
| BERT-base | 65.3% | 66.9% | 39.5% | 4.4 turns |
| Gemma2 9B | 71.0% | 72.3% | 34.2% | 3.9 turns |
| Mistral 7B | 70.7% | 72.1% | 34.6% | 4.0 turns |

These figures show that better generic forecasting is not a safety guarantee: false positives remain substantial in that protocol. They are not recommendations to load a 7B/9B model on every Sakshi notification. The same research warns about inconsistent evaluation, including models assuming eventual conversation length, and emphasizes tuning triggering thresholds on development data.

**Forecasting Conversation Derailments Through Generation, INLG 2025:** samples possible future trajectories using a fine-tuned LLM and votes over outcomes [S6]. Its English benchmark improvement does not make sampled futures observed evidence. Multiple generated trajectories multiply mobile generation cost; this is not recommended for the MVP.

**Wait! There's a Way Out, ACL 2026:** separates the decision to trigger an alert from estimated derailment likelihood and uses simulated paths to recovery to reduce false positives [S7]. The inspected official abstract establishes the method/result direction; full numerical replication and Android feasibility were not established here. Sakshi can adopt the principle of **separate interpretation and notification policy** without generating hypothetical futures or delaying review of already explicit threats.

**Conversation-dynamics summaries, ACL 2026:** pragmatic annotation and trajectory summaries for forecasting [S8]. The abstract reports gains, but without the detailed metric/baseline/phone protocol established in this investigation, those percentages are not transferred to Sakshi. Useful implication: summarize turn-level changes and rebuttals rather than just appending more raw text. Keep summary claims tied to underlying events so compression does not erase context.

**Antisocial Behavior Prediction survey, WASSA 2026:** reviews 49 ML studies across temporal, structural and behavioural dimensions and reports fragmented benchmarks and interpretability tradeoffs [S9]. It reinforces that detection, forecast, recidivism and coordinated abuse are separate tasks, not one generalized harassment score.

### 3.4 Controlling behaviour, intimidation and longitudinal profiling

**MentalManip, ACL 2024:** 4,000 multi-turn fictional movie dialogues with manipulation/technique/vulnerability annotations [S10]. Results show that toxicity/mental-health training does not reliably solve subtle manipulation detection. Use as a research challenge source, not real intimate-partner evidence. Do not label a user as psychologically vulnerable from dialogue.

Licensing conflict is material: the inspected Hub tag says CC BY-SA 4.0, while the Hub body and actual author repository LICENSE say **CC BY-NC 4.0**. Treat commercial-training permission as unresolved; do not assume the less restrictive tag is authoritative. Movie script rights and derivative permissions also need review. Paper CC BY is not the dataset license.

**Police-narrative text mining, Crime Science 2024:** rule-based extraction of 48 behaviours from 406,196 NSW domestic/family violence reports [S11]. This demonstrates scalable extraction of documented behavioural markers, including threats and contact-related behaviour. Police narrative style, selection/recording bias and institutional access differ sharply from mobile chat excerpts. The study does not validate causal intent or a general smartphone coercive-control classifier.

**DCAP, Digital Investigation 2026:** hybrid rules+BERT with cumulative behavioural profiling and human review. The author-institution abstract reports 0.85 macro F1 and 92.8% reduction in target review volume on a **simulated 8,451-message case**, initialized on synthetic data and stress-tested by injecting 200 real-world toxic samples [S12]. This is highly relevant architectural prior art, but synthetic/injected-toxic evaluation does not establish real-world longitudinal coercive-control accuracy. Full independent replication, model artifacts and Android benchmarks were not verified here.

**Lifetime toxicity patterns, Scientific Reports 2025:** nearly 500 million Reddit/Wikipedia comments over fourteen years, with model-derived and community-vote proxies; trends vary by era and platform [S13]. It supports monitoring concept/base-rate drift rather than assuming a stable universal behavioural trajectory. It does not justify downloading a sender's outside history or applying population-level trends to an individual case.

**Longitudinal risk analysis:** survival analysis, discrete-time hazards and recurrent-event models can estimate defined future outcomes only with appropriately collected outcomes, follow-up, censoring and external validation. Instruments such as Danger Assessment were validated in specific intimate-partner-violence populations using factors not recoverable from notifications [S14]. Their scores/coefficients must not be reverse-engineered into an automatic Sakshi chat-risk number. For this product, “longitudinal analysis” should initially mean observed trajectories plus coverage/uncertainty, not individualized lethality prediction.

### 3.5 Event extraction, linking and temporal graphs

**Temporal NLP:** distinguish when a message was observed from the time of an event mentioned inside it. “Yesterday you called” is a report; “tomorrow I will come” is future/conditional language, not a completed physical encounter. Extract action, actor/target, negation, modality, quotation, temporal expression and evidence spans before proposing event links.

MATRES deliberately separates temporal axes and evaluates verb-event start-point relations; the 2023 unified ETRE framework expresses relations through event endpoints [S15]. These tasks mainly involve narrated events, not Android notification deduplication. Use their insights to maintain `before`, overlap and unknown relations, not to invent precise times for ambiguous text.

**Event linking is not identity resolution.** Links such as reply-to, quotation, repeated topic or after-boundary must have an explicit basis and uncertainty. A matching display name, similar language or cross-app timing is not proof of one human actor. User-confirmed actor aliases remain user assertions, not authenticated identity.

**Temporal/behavioural graph:** nodes can be events, case-scoped actor aliases, source artifacts and boundary records; edges represent source-explicit reply/quote relations, reviewed alias association, temporal ordering and proposed recurrence. This is useful without a GNN. TGN is a general framework for dynamic event graphs with memory/message/embedding modules [S16], not a prevalidated harassment detector. Neural graph memory also creates deletion and replay costs. For one-to-one evidence, indexed adjacency and a state machine are usually enough.

## 4. Compare the five approaches

| Approach | Detects well | Weakness / false-positive risk | Data need | Compute / Android fit | Decision |
|---|---|---|---|---|---|
| Rule-based temporal engine | Exact counts, episode recurrence, reviewed boundary ordering, configured category transitions | Rigid thresholds; clock/link errors; misses indirect meaning | Definitions and fixtures; no trained parameters | Small Kotlin/SQL reducer; incremental and explainable | **MVP backbone** |
| Statistical model | Changes in counts/gaps/category rates, recurrent bursts, uncertainty bands | Cold start; nonstationary baseline; overdispersion; selection bias; unusual does not mean abusive | Comparable observations and calibrated baseline | O(1) EWMA/CUSUM; bounded offline PELT or BOCPD later | Simple descriptive comparison first; statistical flags conditional |
| Sequence model | Order-sensitive context, repeated semantic pressure, irregular time gaps with features | Conversation labels can smear blame; learned shortcuts; poor missing-context transfer | Labeled sequences with actor/source-safe splits | Small feature GRU/TCN feasible; language encoder often dominates memory | Next research phase after baseline |
| LLM reasoning | Ambiguous context, readable sequence explanation, extraction suggestions | Hallucinated intent/time/links; arbitrary counting; prompt injection; refusal and context loss | Prompt fixtures and grounded evaluation; possibly domain tuning | Optional foreground 1B-3B model, seconds/GB class; never count through generation | Bounded explanation aid, not temporal ground truth |
| Hybrid | Combines exact facts, semantic interpretation, changes and review | More components to validate; errors can cascade | Layer-specific tests and end-to-end calibration | Rules always; compact classifier selectively; LLM on demand | **Recommended final composition** |

Rules/statistics are not automatically accurate because they are explainable. A correct count over the wrong actor or duplicated observations is still misleading. LLM reasoning is not automatically superior because it sounds persuasive. Assess end-to-end pattern accuracy, evidence support, missed context and alert burden.

## 5. Suitable algorithms and selection rules

### 5.1 Streaming windows and episodes: implement first

For an eligible actor/case scope and half-open time window `[a,b)`:

`C[a,b) = number of distinct, eligible contact units with a supported timestamp inside [a,b)`.

Keep separate counts for all retained contact observations, user-marked unwanted contacts, reviewed categories, unreviewed candidates and uncertain duplicates. One event with three labels is still one contact. One conversation export plus its screenshot may be two artifacts of one contact, not two messages.

Use deques for in-order windows or an indexed store for historical/late imports. Proposed UI windows of ten minutes, one hour, one day and seven days are conveniences, **not harassment criteria**. Timezone, day boundaries and source precision must be explicit. Episode grouping can use a configurable inactivity gap (e.g. thirty minutes for a synthetic demo); absence of observations is not proof that no contact occurred.

### 5.2 Descriptive frequency and robust anomaly checks

Report raw counts, observed span, timestamps used and denominator. Compare rates only when two windows have comparable source selection, collection/filter/retention policy and capture conditions. Listener connection uptime is not known message-detection probability.

`observed_rate = distinct retained contacts / eligible comparison duration` is an **observed-retention rate**, not total incoming message rate. If only incident candidates are retained, a category/retention-threshold change can increase the rate without sender behaviour changing. Prefer “12 retained observations versus 3 in the preceding comparable hour” to a universal anomaly score.

Use medians/MAD or empirical quantiles on comparable bins for a lightweight anomaly baseline. For `MAD=0`, do not divide by zero or claim infinite risk; fall back to counts or insufficient baseline. Poisson counts may be an initial experiment, but bursty messaging is often overdispersed; compare negative-binomial baselines before significance claims. No statistical rarity is a harassment probability.

Isolation Forest, Local Outlier Factor (LOF), one-class SVM and reconstruction-error autoencoders are additional anomaly comparators, not harassment classifiers. They need a defined reference distribution and sufficient representative data. LOF's novelty mode must score new unseen samples, not re-score its training data as a prospective test [A7]. A small cohort-trained Isolation Forest over count/gap features is more plausible than a per-user autoencoder trained on a few selected incidents, but still needs an Android export and bias/drift evaluation. Dense recurring abuse may become an inlier, so unsupervised anomaly detection cannot replace explicit boundary and content rules. No rarity score should be presented as probability of harm.

### 5.3 EWMA and CUSUM: compact optional statistics

EWMA `z_t = alpha*x_t + (1-alpha)*z_(t-1)` summarizes drift; CUSUM can accumulate departures from an estimated baseline [A1][A2]. For count signals, use exposure-aware count residuals or a specified likelihood rather than copying normal-process “three sigma” thresholds. Baselines can include time-of-day/week only when data supports them.

A Poisson upward-shift CUSUM candidate with predeclared `lambda1 > lambda0 > 0` and eligible bin exposure `E_t` is:

`G_t = max(0, G_(t-1) + x_t*log(lambda1/lambda0) - (lambda1-lambda0)*E_t)`.

The alarm boundary is tuned to false alarms/run length on held-out streams, not guessed. Gaps, changed acquisition policy and inadequate baseline disable the statistic. Do not use zero counts for missing bins, or decay a history to “safe” during a gap. A high chronic baseline must not normalize repeated unwanted contact: independent boundary/category rules remain active.

### 5.4 PELT and BOCPD: useful but defer from MVP

**PELT** finds an optimal penalized retrospective segmentation for a chosen cost. Linear expected cost holds under assumptions, not universally; worst-case candidate work can remain quadratic [A3]. Use Poisson/negative-binomial or suitable feature costs, minimum segment duration and penalties. A Gaussian L2 default over sparse counts is not automatically appropriate. Retrospective segmentation can look at future bins and must not be scored as an online early-warning system.

**BOCPD** maintains a run-length posterior and can model count data with a conjugate likelihood [A4]. Exact work/memory per step grows with elapsed observations; truncating to R retained run lengths bounds work but changes inference. For one canonical constant-hazard update, zero-run posterior mass can equal the hazard, so thresholding `P(run_length=0)` is not automatically a data-responsive alarm. Define and validate a variant/decision statistic such as posterior recent-run mass and predictive shift. Every “change probability” describes a chosen statistical model, not an abuse probability.

Both need timestamp/exposure quality, stable capture conditions and enough baseline. Display the affected interval and before/after evidence, not an exact invented escalation onset.

### 5.5 Kleinberg bursts and Hawkes processes: research comparators

Kleinberg's burst automaton uses penalized transitions between arrival-intensity states and can produce nested burst intervals [A5]. Truncate to K states for bounded implementation. Bursts in news/email do not equal harassment in private chats. Raw arrival counts plus explanation are easier for users than hidden state indices.

Hawkes processes model self-exciting event arrivals through intensity `lambda(t)=mu+sum g(t-t_i)` [A6]. Exponential kernels admit compact streaming recurrences, while fitting parameters and heterogeneous/missing exposure are harder. Self-excitation is statistical dependence, **not proof one event caused another, coordinated intent or escalation toward violence**. Sparse selected evidence is a poor foundation for fitting a personalized Hawkes model; defer.

### 5.6 Sequence and behavioural models

Feature logistic regression or a small boosted-tree model over reviewed count/gap/category/boundary features is a strong learned baseline before RNNs. Include missingness, scope changes, review state and timestamp uncertainty. Do not train on “sender identity” strings as shortcuts.

A causal unidirectional GRU/TCN can process feature vectors or cached permitted embeddings. Use time gaps, direction, target, topic, boundary state and extraction confidence. A bidirectional sequence model over the completed conversation is acceptable for retrospective analysis but not a prefix forecast; no future turns may enter online evaluation.

GAT/TGN can be compared when real reply trees or multiple participant patterns exist. The MVP's behavioural graph should be a typed relational index, not a knowledge graph of inferred perpetrators or victims. Attention weights are not causal explanations or evidence; reconstruct supporting event paths explicitly.

### 5.7 Escalation model: dimensions and transitions, not a single rank

Track separate axes: contact density; persistence after a boundary; repeated requests for location/proof; threats becoming more explicit; new harm targets; more concrete actions/time references; sexual pressure plus threatened consequences; references to private information. A reduction in profanity can coexist with a more specific threat.

Use reviewed category transitions plus stable source spans: “earlier insults, later explicit harm statement.” Call it a **possible escalation in wording** when semantic interpretation is involved. Do not assume every category has one universal severity order. Do not require frequency to increase before detecting a new explicit threat.

Track counterevidence, apologies, resumption/limited-contact context and corrected attribution, but an apology does not delete the earlier evidence or automatically remove a communicated boundary. A resumption only changes the applicable scope chosen by the user.

## 6. Proposed event schema

The machine-readable contract is `data/sakshi-event-schema.json`, version 1. It is deliberately more explicit than `timestamp, sender, severity, confidence`: each can otherwise encode unjustified certainty. It is a design schema, not an installed database or legal-evidence standard.

### 6.1 Field contract

| Field | Meaning / type | Invariant |
|---|---|---|
| `schema_version`, `event_id`, `case_id`, `revision` | Version and opaque local identifiers | Event revisions are immutable; analysis references exact revisions |
| `event_kind` | Message/contact observation, boundary, note, reported external event or notification lifecycle | Mentioned actions are not counted as extra contacts; lifecycle records never increment contact counts |
| `observed_at` | Device/Sakshi observation or capture wall time | Not automatically message-send time or when a narrated event happened |
| `available_at` | When this revision/evidence first became available to analysis | Online tests cannot use evidence or review decisions added later |
| `timestamp` | Earliest/latest bounds, time basis, precision, timezone, collector session and optional monotonic clock | No guessed precision; monotonic time is comparable only inside its collector/boot session |
| `sender` | Case-scoped actor alias, display claim, identity basis and association review | Display-name equality is not authenticated identity; null means unknown, not an adversary |
| `source` / `source_app` | Acquisition kind, package/app claim, profile, conversation hint, record id and parser version | Group/notification key is not universal chat/message identity; imports can have unknown source app |
| `direction` | Incoming/outgoing/system/unknown | Absence of outgoing observations never means the user did not reply |
| `categories[]` | Multi-label suggestions/user tags, score semantics, producer version, cited spans and review status | Preserve multiple labels; user tags have no invented model confidence |
| `confidence` per label/link | Calibrated probability, uncalibrated bounded score, not applicable or unknown | Include calibration version only when actually fitted; never multiply unrelated confidences into a pattern probability |
| `severity` | Review priority, basis and evidence refs | Triage scheduling, not legal seriousness, danger score or violence probability |
| `evidence_references[]` | Artifact/hash, original/excerpt/manual/OCR/transcript representation, locator | At least one actual source; a manual statement supports a reported fact, not authenticated external occurrence |
| `user_confirmation` | Pending/confirmed/rejected/expired, review time and confirmation scope | Preserving evidence is not acceptance of every AI tag, time or identity assertion |
| `deduplication` | Distinct observation, same representation, possible duplicate or lifecycle-only; canonical id | Hash/text equality alone never proves one real-world occurrence |
| `coverage` | Context completeness for selected range, text status, outgoing coverage and gap refs | “Complete selected range” is not complete account history; capture probability remains unknown |
| `boundary` | Reviewed stop/disengagement/limited-contact/resumption marker, actor/scope, communication status and wantedness | Internal disengagement is not necessarily a stop request communicated to the other party |
| `relationship_to_previous_events[]` | Reply/quote/possible same occurrence/after-boundary/topic/recurrence/transition/user link | Every link retains method, uncertainty and separate review; cross-case references rejected |
| `retention` | Session-only, encrypted expiring candidate or confirmed vault; consent generation | JSON metadata does not enforce encryption/consent; storage layer must do so |

### 6.2 Evidence anchors and representation layers

Use **half-open Unicode code-point offsets** `[start,end)` against a specific preserved text derivative/revision. Kotlin/Java String indices are UTF-16; implement/test conversion rather than treating surrogate pairs as one code unit. OCR anchors resolve through an image/page region id and transform map; transcripts through source-audio millisecond ranges and derivative alignment. Video can reference its frame artifact plus mapped source time through separate metadata. Empty/inverted/out-of-range spans fail application validation.

A SHA-256 hash validates matching bytes, not sender identity, original-app authenticity or court admissibility. Notifications preserve only their excerpt representation, not the underlying messenger original. Quotes, OCR and user corrections are derivatives with parent links; do not overwrite received artifacts.

Time bounds express resolution/ambiguity, not automatically calibrated confidence intervals. Store the original timestamp string/locale in the artifact/parser metadata. Distinguish a source-claimed time from collector time and the time of a narrated/conditional event. Multiple described incidents can be separate **reported-event annotations**, linked to one contact observation; they must not multiply frequency counts.

### 6.3 Complete synthetic event example

This synthetic reviewed event has a user-accepted ordinary tag and no model-generated probability. Its significance can come from the sequence rather than this text alone. The fake all-zero hash is an explicit fixture placeholder and must never be accepted as integrity evidence for a real artifact.

```json
{
  "schema_version": 1,
  "event_id": "demo-schema-1",
  "case_id": "demo-case-a",
  "revision": 1,
  "event_kind": "message_observation",
  "observed_at": "2026-10-01T09:05:00+05:30",
  "available_at": "2026-10-01T09:05:00+05:30",
  "timestamp": {
    "earliest": "2026-10-01T09:05:00+05:30",
    "latest": "2026-10-01T09:05:59.999+05:30",
    "basis": "source_claim",
    "precision": "minute",
    "source_timezone": "+05:30",
    "collector_session_id": null,
    "monotonic_ms": null
  },
  "sender": {
    "actor_id": "demo-actor-a",
    "display_label": "Person A",
    "identity_basis": "user_asserted",
    "association_review": "confirmed"
  },
  "source": {
    "kind": "selected_export",
    "source_app": "user-selected-chat-export",
    "profile_scope_id": null,
    "conversation_scope_id": "demo-chat-a",
    "source_record_id": "demo-row-2",
    "parser_version": "demo-parser-v1"
  },
  "direction": "incoming",
  "categories": [{
    "label": "ordinary",
    "basis": "user_tag",
    "confidence": {"value": null, "semantics": "not_applicable", "calibration_version": null},
    "producer_version": "manual-review-v1",
    "evidence_reference_ids": ["demo-ref-schema-1"],
    "review_status": "accepted"
  }],
  "severity": {"review_priority": "review", "basis": "user_set", "evidence_reference_ids": ["demo-ref-schema-1"]},
  "evidence_references": [{
    "reference_id": "demo-ref-schema-1",
    "artifact_id": "demo-export-a",
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "representation": "preserved_import",
    "locator": {"kind": "text", "start": 0, "end": 3, "unit": "unicode_code_points"}
  }],
  "user_confirmation": {
    "status": "confirmed",
    "reviewed_at": "2026-10-01T09:20:00+05:30",
    "scope": "preservation_and_selected_annotations"
  },
  "deduplication": {"status": "distinct_observation", "canonical_event_id": null, "method_version": "demo-dedup-v1"},
  "coverage": {"context": "selection_partial", "text_status": "available", "outgoing_coverage": "unknown", "gap_reference_ids": []},
  "boundary": {"marker": "none", "actor_id": null, "review_status": "not_applicable", "communication_status": "not_applicable", "unwanted_contact": "user_marked_unwanted"},
  "relationship_to_previous_events": [],
  "retention": {"mode": "confirmed_vault", "expires_at": null, "consent_generation": 1}
}
```

The user confirmed this record at 09:20, so a confirmed-only analysis at 09:10 cannot use that later decision. `available_at` here records capture availability; review availability is separate. Source offsets above describe the fixture text “Hi?”; a real export needs the actual record's offset inside its owned artifact/derivative. Root and annotation revisions should be separately tracked in production even though this proposed interchange example stores them together.

### 6.4 Schema validation versus application invariants

The schema enforces types/enums/bounds, known/unknown time shape, explicit evidence refs, score semantics, candidate expiry presence, confirmed-vault confirmation and duplicate canonical-id presence. **Use a validator with date-time format checking enabled.** JSON Schema is not sufficient for cross-record/domain correctness.

Application validation must additionally enforce:

1. Unique event/anchor ids and valid version references; referenced artifacts exist and have actual matching hashes.
2. `earliest <= latest`; compatible clock basis/session; valid date/timezone interpretation. Equal bounds can represent a point, not independent precision validation.
3. Offsets/time ranges are nonempty and inside the correct artifact; category/severity ref ids resolve within this event or explicit selected context.
4. Actor aliases belong to the case/profile scope; distinct identities are never merged solely by name. Reject links across cases and self-links.
5. Same-representation links target a valid canonical contact and do not form cycles; same text at different source ids/times can remain two contacts.
6. Contact units count only incoming eligible contact events; lifecycle, notes, boundary markers and quoted/hypothetical actions do not increment contact counts.
7. Rejected/expired data is immediately ineligible; consent-generation changes invalidate queued analysis; confirmed preservation does not accept unreviewed category/identity suggestions.
8. Review decisions and links are available at the analysis cutoff; no future annotations leak into prospective evaluation.
9. Boundary scope and communication status are retained; no silent upgrade from user disengagement to sender knowledge of a stop request.
10. No record persists outside its consent/retention policy. Expiry/deletion removes contributing indexes, summaries and model/temporal state; encryption is enforced independently.

The schema contains no inferred perpetrator/victim role, legal offence, guilt label or numeric danger score. Changes to enums/semantics need a schema version and migration, not an unnoticed classifier update.

## 7. Event linking, timeline and counting design

### 7.1 Conservative linking hierarchy

1. Source-explicit stable record/reply references within a supported export, with provenance and parsing caveats.
2. Profile/app/conversation-scoped source hints for notifications, validated against actual test fixtures.
3. User-confirmed case/actor association, preserved as user assertion.
4. Similar text/topic/timing as a **candidate link only**, never definitive identity or duplicate proof.

Only (1)-(3), at a chosen reviewed confidence/state, enter an actor-specific confirmed pattern. Unknown association can still produce “several retained observations with this source label,” but not “the same person contacted you across apps.” Do not crawl external profiles or compare voice/face identity to resolve actors.

### 7.2 Notification dedup and multi-source provenance

A notification callback is an observation update, not a message. Parse supported message arrays, distinguish historic entries/group summaries/reactions/system items, and compare source fields conservatively. If one callback contains three independently identifiable message entries, create up to three contact observations; if five callbacks repeat those entries, count them once each. Record ambiguity where no stable source id/distinction exists [P1][P2].

An exported message, screenshot and manual note describing it can all reference the same underlying occurrence. Retain all artifacts, but count one contact when that equivalence is source-supported or user-reviewed. A text fingerprint is sensitive, not anonymized; bound RAM dedup state and retain durable matching metadata only for permitted evidence. Distinct repeated “Hi” messages must not all collapse to one contact.

### 7.3 Time is a partial order

If event i has interval `[l_i,u_i]` and j `[l_j,u_j]`, claim definitely-before only when `u_i < l_j`. Overlapping bounds mean ordering may be unknown, even if a deterministic UI tiebreaker displays one first. Within the same source, explicit sequence order can provide a separate ordering relation when timestamp precision is coarse; record the basis, not a fabricated exact time.

Sort for display with deterministic tie-breaking, but propagate uncertain order into pattern eligibility. Avoid comparing source-clock times directly with collector-clock times without an established relation. Use monotonic differences only within the same collector/boot session. Clock shifts, timezone imports, DST and copied screenshot dates need fixtures.

Use both event time and knowledge time. Importing a month-old message changes retrospective analysis, not the claim that Sakshi detected it last month. A watermark/finality policy can bound online updates, but never discard late evidence merely for convenience. Recompute affected scopes/windows on late import or review correction.

### 7.4 Count bounds, not false precision

For possible duplicate groups, a lower bound can collapse each unresolved group to one observed contact and an upper bound count all plausible distinct contacts. This only bounds **retained observations**, not unseen true contact. Time-uncertain events contribute to definite-window versus possible-window counts. Missing identity reduces actor-attributed counts, not certainty about who sent them.

State source selection, retention policy and evidence-view type with every result. “At least five confirmed retained contacts” is defensible only if canonicalization and timestamps support that count; if duplicate uncertainty allows four, show “four to five retained contacts.” Never extrapolate unseen messages from notification unread totals.

## 8. The analysis pipeline and explicit pattern rules

Every rule returns `candidate`, `supported_description`, `insufficient_context` or `not_observed`; “not observed” is not “safe.” Defaults below are **synthetic demo settings**, not validated harassment thresholds.

### 8.1 Frequency analysis

Inputs: eligible distinct contacts, definite/possible time bounds, scope and comparison-policy version. Compute bounded window counts, unique contact days, minimum/median interarrival intervals and comparable before/after bins. A ratio needs a meaningful denominator; when prior count is zero, report the counts rather than infinite escalation.

Example demo flag: current comparable one-hour count >=6 and >=3 times the preceding hour's nonzero count. It is a **density-change review cue**, not a harassment determination. A six-message urgent logistics conversation can trigger the same arithmetic and should not be semantically labeled unwanted without user/context evidence.

### 8.2 Recurrence and repeated unwanted contact

Group contacts into episodes using a configured inactivity gap on supported event time. Report episode count and observed gap, then assess user-marked wantedness/boundary scope. Recurrence of a topic/category can be a reviewed pattern even when exact text changes.

**Communicated boundary:** a reviewed “do not contact” event tied to an actor/scope, supported by an outgoing selected record or explicit user report; subsequent distinct incoming events ordered after it; applicable boundary remains in scope. Demo review threshold: at least three such contacts in one day. Explain `communication_status` accurately: selected outgoing text does not prove delivery/read receipt, and a manual report is not an original outgoing message.

**User disengagement only:** a user-entered pause/decision to disengage can anchor “contact observed after your disengagement marker.” Do not state the sender ignored a communicated request if no communication evidence exists. **No response observed** is only “no outgoing reply in the selected records”; it cannot automatically define nonresponse for notification-only history.

**Resumption:** user-marked limited or resumed contact adjusts the rule scope but does not retroactively invalidate unwantedness or imply consent to threats/sexual requests. An apology alone does not reset the boundary. Group-chat messages not directed at the user require target review.

### 8.3 Controlling-behaviour patterns

Extract/review repeated demands for whereabouts, proof/photos, access/passwords, restrictions on social contact and conditional consequences. Link consistent targets/topics and reviewed refusal/boundary markers. Pattern requires multiple actual supporting events and contextual qualifiers, not just a word list or category-count threshold.

Example: three requests for live location, a reviewed refusal, then a statement imposing a consequence for refusing. Report “repeated location requests and a stated consequence after refusal” as observed text; “possible controlling pressure” is an interpretation. Do not infer what the sender actually monitors, whether location was shared, relationship power or legal coercive control from absence of contrary data.

### 8.4 Escalation analysis

Track transitions across reviewed tags and extracted features, retaining source spans. Demo transition rule: earlier accepted verbal-abuse event plus a later accepted explicit-threat event in the same reviewed case/actor scope, within a chosen seven-day window. Wording: “the selected sequence moves from insults to a statement of harm.” No numeric scalar escalation level is needed.

Also distinguish:

- Increased specificity: vague consequence language becomes named action/target/time.
- Increased persistence: more retained contact after a reviewed boundary.
- New harm domain: a threat to disclose private information appears after repeated pressure.
- Frequency change without semantic escalation: a burst of ordinary logistics.
- Semantic change without frequency growth: one more specific threat after a quiet period.

Threat mention time is not predicted execution time. A phrase “tonight” is an observed temporal expression with parser uncertainty; it is not a verified future attack. Unreviewed model tags generate candidates, not confirmed transitions.

### 8.5 Pattern record contract

Persist a pattern projection separately from events:

| Field | Meaning |
|---|---|
| `pattern_id`, `pattern_type`, `rule_or_model_version` | Opaque id; repeated_contact / recurrence / density_change / wording_transition / repeated_control_requests |
| `case_id`, `actor_scope`, `evidence_view` | Case; accepted alias or unresolved source scope; confirmed-only versus candidate preview |
| `knowledge_cutoff`, `window`, `clock_basis` | What evidence was available and how times were interpreted |
| `supporting_events` | Event ids **and revisions**, evidence anchor ids and role in the pattern |
| `context_events` | Boundary, refusal, apology/resumption and relevant alternative-explanation records |
| `measurements` | Counts/bounds, spans, comparable rate denominators and transition features; never LLM arithmetic |
| `interpretation` | Observed description plus optional inferred/pattern wording |
| `limitations` | Missing outgoing, gaps, uncertain time/actor/dedup, selection bias, unreviewed tags |
| `assessment_status`, `user_review_status` | Supported description / candidate / insufficient context / stale; user review separate |
| `generated_at`, `dependencies`, `retention` | Recompute/invalidate on event/link/tag/policy deletion, correction or expiry |

For an event-derived pattern there is no single meaningful probability without a trained/calibrated pattern model. Report label confidence, link/time quality and count bounds separately. A pair of 0.9 classifier scores does not imply an 81% true pattern probability.

## 9. Pattern explanation: facts first, inference bounded

Use three sections:

1. **Observed:** “Six distinct retained incoming observations are linked to Person A between 09:05 and 09:40 on 1 October. The reviewed boundary record is at 09:00.”
2. **Pattern interpretation:** “This may indicate repeated unwanted contact after that boundary.”
3. **Limitations:** “The boundary was reported by you; delivery/read status and complete outgoing history are unknown. Sender association is user-confirmed, not authenticated.”

Each factual sentence links to event ids/spans and exact structured calculations. A context panel shows counterevidence/uncertainty without asserting that apologies prove safety. Template generation is enough for the MVP.

An optional local LLM receives only a bounded evidence packet: computed counts/time bounds, source-anchored event excerpts, boundary scope, review status and limitations. It may paraphrase, but cannot add identities, dates, counts, offences, intent or future facts. Validate every citation and numeral, enforce the supported schema, treat evidence text as untrusted instructions and fall back to templates on refusal/truncation. Never show generated future conversation as a source event.

Avoid wording such as “stalker,” “guilty,” “proves coercive control,” “danger score 87,” or “will become violent.” Avoid recommending confrontation or automatic intervention based on a model's interpretation. Analysis can be useful without becoming legal adjudication or a protection guarantee.

## 10. Synthetic example timelines

These examples are designed fixtures, not research prevalence, real testimony or model-generated ground truth. Times use local +05:30 where specified. Categories and wantedness are user-reviewed **fixture annotations**, not measured classifier predictions.

### A. Harmless-looking texts after a boundary

| Id | 1 October 2026 | Actor / item | Review |
|---|---|---|---|
| A0 | 09:00 | User: “Please do not contact me again.” | Selected outgoing artifact; scope Person A, all direct contact; delivery unknown |
| A1 | 09:05 | Person A: “Hi?” | Distinct incoming; user-marked unwanted |
| A2 | 09:07 | Person A: “Are you there?” | Same |
| A3 | 09:10 | Person A: “Just checking.” | Same |
| A4 | 09:15 | Person A: “Can we talk?” | Same |
| A5 | 09:20 | Person A: “Hello?” | Same |
| A6 | 09:40 | Person A: “Please reply.” | Same |

**Output:** six distinct retained contacts in the 09:00-10:00 window, spanning 35 minutes from first to last, after a selected outgoing stop-contact record. “Possible repeated unwanted contact” is supported for review although no individual message needs an abuse label. Delivery/reading, full history and human identity remain unknown. Under notification-only selective retention this sequence may not be recoverable; do not promise it is.

### B. Verbal abuse to more specific harm wording

| Id | Local time | Item | Reviewed tag |
|---|---|---|---|
| B1 | 1 Oct, 18:00 | “You're useless.” | Verbal abuse |
| B2 | 1 Oct, 18:20 | “You'll regret refusing.” | Intimidation / implied consequence, uncertain harm |
| B3 | 2 Oct, 08:00 | “I will hurt you tonight.” | Explicit harm statement; time expression |

**Output:** an earlier insult and later explicit harm statement, with a 14-hour gap between B1 and B3. Possible escalation **in wording**, linked to B1/B3. Do not require a message-count increase or wait for repetition before showing B3. Do not state that harm will occur tonight or that B2 definitively established violent intent.

### C. Frequency increase that should not become harassment

Comparable retained export windows: 08:00-09:00 has three incoming messages; 09:00-10:00 has twelve. Reviewed context: consensual group project deadline, both sides actively exchanging logistics, no stop request/user unwantedness tag.

**Output:** observed message density is 4x in those selected comparable hours. No repeated-unwanted-contact conclusion. Statistical anomaly detection can flag the rate change, but semantics/user context should prevent a harassment interpretation. Selection must truly cover both ranges; selected highlights alone cannot support a total-rate claim.

### D. Recurrence after disengagement, with a capture gap

| Id | Time | Item |
|---|---|---|
| D0 | 1 Oct, 10:00 | User enters “I stopped engaging with Person A.” No communicated stop request established |
| D1-D3 | 1 Oct, 10:10 / 10:14 / 10:20 | Three retained direct-contact observations |
| G1 | 1 Oct, 11:00 to 2 Oct, 09:00 | Notification collector unavailable; coverage unknown |
| D4-D5 | 2 Oct, 09:10 / 09:15 | Two retained direct-contact observations |

**Output:** five retained observations in two observed episodes after the disengagement marker; there is a 22-hour acquisition gap. Do not call the interval “22 hours without contact,” claim no replies occurred, or state the sender violated a communicated boundary. If the user later provides stop-request evidence, recompute with its provenance/time.

### E. Repeated location pressure with stated consequence

| Id | Local time | Item |
|---|---|---|
| E1 | 1 Oct, 20:00 | “Send your live location.” |
| E2 | 20:05 | User: “I don't want to share my location.” |
| E3 | 20:07 | “Send a photo to prove where you are.” |
| E4 | 20:10 | “Share it now or I'll tell your manager you're lying.” |

**Output:** repeated location/proof requests plus a stated consequence after refusal, supported by E1-E4. “Possible controlling pressure” is an interpretation. “Tell your manager” is not automatically a threat of violence or proof private data was disclosed. Consensual safety checks/cultural context, attribution and completeness need review.

### F. Five callbacks, three messages; two people with one label

A source-specific synthetic notification fixture provides callback payloads `[F1]`, `[F1,F2]`, `[F1,F2]`, `[F1,F2,F3]`, `[F1,F2,F3]`, with distinguishable source records. **Expected count: three contact observations, not five callbacks or eleven cumulative entries.** If F1 and F2 both say “Hi” but have distinct supported record ids, count both.

Two separate app/profile scopes display “Alex.” Without a user-reviewed association, they remain separate actor aliases. A matching name and a similar demand do not support cross-app recurrence by one person.

## 11. False positives, false negatives and uncertainty

| Failure mode | Example | Mitigation / correct output |
|---|---|---|
| Count inflation | Reposts, historic arrays, screenshot plus export | Contact-unit canonicalization; duplicate bounds; retain artifacts separately |
| Incorrect attribution | Group title treated as sender; two people named Alex | Scope hints, target/direction review and explicit alias association |
| Missing replies | Notifications expose incoming text but not ongoing replies | Outgoing coverage unknown; do not infer silence or unilateral pursuit |
| Boundary overreach | User privately disengages; source never received a stop request | Separate internal marker, user report and supported communicated request |
| Consensual density | Deadline, family emergency, planned frequent updates | Count change remains descriptive; wantedness/context review |
| Quote/forward confusion | User reports “he said I'll hurt you” | Quote speaker/mentioned actor distinction; reported event versus direct utterance |
| Cultural/script mismatch | Romanized Malayalam, code-mixed sarcasm/slang | Native/romanized language evaluation; abstention; user corrections |
| Classifier drift | New model/version flags more ordinary text | Pin model/taxonomy/calibration; invalidate baseline across policy change |
| Timestamp distortion | Import time mistaken for send time; minute resolution | Dual time, uncertainty intervals, original timestamp metadata |
| Selection bias | Only suspicious screenshots retained | No total-conversation frequency denominator; disclose subset |
| Chronic-pattern normalization | High recent baseline masks continued pressure | Independent boundary/semantic rules; do not equate typical with acceptable |
| Sparse history | One contact after a long gap | Insufficient baseline; single-event review remains available |
| Cold-start anomaly | First day has zero baseline count | No infinite ratio/significance; state counts only |
| Apparent recovery | Apology followed by resumed pressure | Preserve old evidence; boundary changes only through reviewed scope |
| Negative outcome prediction | Lower rate/no observed threats interpreted as safe | “Not observed in selected evidence,” never safety assurance |
| Analysis feedback loop | Prior model pattern becomes training/context “evidence” | Derived patterns are not source events; avoid double counting and self-reinforcement |
| Alert fatigue | Re-alert at every contact/overlapping window | Episode-level grouping and cooldown; new explicit threat bypasses suppression |

Multiple windows/categories/models create many opportunities for false alarms. Tune alert burden on whole streams, not independent window accuracy. Defer low-context interpretations, but do not defer saving or displaying observed explicit threats merely to wait for a possible recovery.

## 12. Hybrid architecture, retention and computational requirements

### 12.1 Android components

Proposed interfaces, not existing implementation classes:

- `EvidenceEventAdapter`: validated imports/notification excerpts/manual entries, bounded data and source references.
- `ContactCanonicalizer`: representation equivalence, conservative duplicate handling and distinct contact units.
- `CaseLinkResolver`: actor/conversation/target/link review, app/profile-scoped hints and identity unknowns.
- `TemporalProjection`: event-time partial order, knowledge cutoff, count windows and episode state.
- `PatternReducer`: explicit boundary/recurrence/transition rules, feature extraction and optional statistical flags.
- `PatternRepository`: versioned projections/dependencies, encrypted retention and invalidation/replay.
- `PatternExplanation`: typed measurements + template, optional source-limited LLM.
- `ReviewCoordinator`: user confirmations/corrections, annotation-level status, scope/consent changes and export selection.

The temporal engine consumes classifier **suggestions**, not a stream where every prior label is declared true. A small multi-label encoder can process reviewed bounded context; pattern triggers for frequency/boundaries must not depend on every message first being individually “harassing.” No Laya integration is required by this design.

Use Kotlin background work and a serialized case reducer. No NLP/database/model work in the NLS callback. Make reducer transactions idempotent and deterministic: repeated ingestion with the same id/revision should not increase counts. Late records, edits, rejections, deletions and consent revocation invalidate affected projections before they are shown/exported. Native model cancellation may finish computation, but stale results must not be committed.

A relational graph/index is sufficient; Room/SQLite storage is not encryption on its own. Encrypt event metadata, source labels, links, counters, indexes and prompt/summary caches under the vault/candidate key policy. Avoid plaintext indexed/FTS columns or journals; select a reviewed encrypted database or encrypted-record/index approach before implementation. No unneeded graph server, cloud vector DB or backend.

### 12.2 Retention versus longitudinal coverage

Three operating modes, explicitly disclosed:

1. **Selected-evidence analysis:** user selects exports/media/notes and chooses what to save. Most defensible hackathon mode for long patterns, including harmless-looking messages. Analysis covers selected evidence, not a whole account.
2. **Default notification mode:** ordinary content is transient; separately consented candidates expire; confirmed items persist as chosen. Cross-day patterns use only permitted retained items and therefore undercount innocuous buildup.
3. **Optional bounded metadata history:** separate explicit opt-in for encrypted per-scope counters/coverage metadata with a documented TTL. Metadata is sensitive, app-specific identity/link uncertainty remains, and no unreviewed ordinary text archive is created. This is a product/privacy expansion, not automatic implementation authorization.

RAM-only counters can detect an ongoing burst during the current process session but cannot support a persistent report with excerpts that were discarded. Do not export a cached template claiming deleted-source facts. Durable aggregate counts, if consented, must explicitly say they lack individual retained evidence anchors and cannot independently support a detailed sequence explanation.

This is an irreducible tradeoff: **discarding ordinary text protects privacy but limits retrospective detection of harmless-looking buildup**. Solve the MVP through user-selected conversation evidence and boundary notes, not by silently overriding retention.

### 12.3 Algorithmic budgets

Let N be retained eligible events, W events in a bounded window, B time bins, D feature dimension, H hidden size, K state/category count and E graph edges. Costs below are engineering analysis, not phone timings; model/tokenizer/SQLite allocations are additional.

| Component | Work / state | Suggested initial bound |
|---|---|---|
| Ordered replay/timeline | O(N log N) sort; streaming in-order inserts O(1) plus index write, out-of-order inserts/replay more | Load/replay one selected case, paginate original evidence |
| Rolling-window counters | O(1) amortized per window on ordered deque, O(W) retained state; fixed number of windows | Four windows, bounded metadata only; use indexed lookup for late arrivals |
| Boundary/episode FSM | O(1) state update after ordering/link checks, history refs retained for explanation | One boundary scope per reviewed actor/context, explicit version |
| Label transitions | O(K) per event for category updates; pair matrix O(K²) if needed | About 10 categories, not arbitrary pairwise all-history comparison |
| EWMA/CUSUM | O(1) per eligible bin / O(1) state per metric | One or two signals; disable with missing/changed baseline |
| PELT | Conditional expected linear, worst-case O(B²) candidate evaluations for O(1) cost; O(B) scalar DP state | Offline experiment with bounded bins/minimum segment; defer default |
| BOCPD | Exact O(t) work/state per step; truncated O(R) approximate | R=128/256 research cap, not safety approval |
| GRU feature head | About O(DH+H²) per step, O(H) streaming hidden state | D≈32, H≈64; causal only for online mode |
| Transformer head | O(W²D) attention plus projections/FFN; bounded cached context | W≤32/64 retained turns for research; no full-history prompt |
| Relational graph | O(N+E) storage; local neighbor queries | Cap candidate neighbors, avoid O(N²) similar-event edges |
| GAT/TGN | Neighborhood/embedding/message costs depend on edge cap; language encoder dominates | Later only if graph structure improves measured tasks |
| Optional LLM | Prompt prefill + output tokens, weights/KV often GB-scale | One foreground model; 1K-2K prompt target and 128-256 output-token cap |

Arithmetic examples:

- 10,000 compact **256-byte** feature/identifier records total 2.56 MB. This excludes variable text, JSON object overhead, indexes, encryption, artifacts and database pages; it is not total app RAM.
- 32 features × 64 hidden units in one GRU layer: roughly `3*(D*H+H*H+H) = 18,624` parameters, about 74.5 KB FP32; frameworks with separate input/hidden biases differ slightly. The multilingual text encoder can still be hundreds of MB.
- A 10×10 float32 category-transition matrix is 400 bytes; probabilities require smoothing/validation and are not legal severity scores.
- 10,000 × 384-D float32 cached embeddings occupy 15.36 MB before index overhead. Cache only for consented retained evidence; an embedding is not anonymized text.
- 10,000 nodes with 128-D float32 TGN memory alone occupy 5.12 MB, before model, edges, language embeddings and replay checkpoints. Neural memory retention/deletion remains sensitive.

Frequency/episode/transition arithmetic should be cheap relative to language inference. Actual Android p95, peak native/Java memory, idle cost and energy must be measured. Use no always-on periodic polling solely to update these features: event-driven updates and foreground recalculation are enough for the MVP.

## 13. Evaluation methodology

### 13.1 Label the right units

Annotate source extraction, contact equivalence, actor/target/link correctness, timestamp relations, message categories, wantedness/boundary scope and **pattern intervals with supporting event sets** separately. Document annotator disagreement and uncertainty instead of forcing false consensus. Preservation confirmation and model-label acceptance are different annotations.

Pattern taxonomy: repeated unwanted contact; contact after communicated boundary; recurrence after private disengagement; observed density increase; repeated location/proof pressure; insult-to-explicit-harm wording transition; recurring category/topic; insufficient-context case. No legal-offence/intent/violence labels are needed.

Use adjudicated guidelines and, where feasible, input from appropriate domain experts and survivor-support practitioners. Do not ask annotators to infer psychological vulnerability or diagnose coercive-control victims from texts. Keep participant consent and safety review separate from publication-dataset licensing.

### 13.2 Dataset strategy and leakage prevention

| Source | Useful role | Limits / permission |
|---|---|---|
| CAD v1.1 | Context annotation, quote/counter-speech negatives, reply-tree modelling | English Reddit, not private repeated contact; Zenodo CC BY with notices |
| CGA-Wiki/CMV | Prefix forecasting, triggering/recovery evaluation methods | Different proxy outcomes; balanced/matched branches differ from real prevalence; verify exact release/license/platform terms |
| TGBully/temporal Instagram studies | Session timing/interaction baselines | Historical public-platform/user-history inputs, random session splits, data-access/license constraints |
| MentalManip | Multi-turn subtle manipulation challenge | Fictional English film dialogues; CC BY-NC source license despite conflicting Hub tag; not real-world evidence |
| DCAP | Relevant hybrid longitudinal architecture | Synthetic initialization/injected toxicity/simulated case, not unrestricted labeled chat corpus |
| Local synthetic fixtures | Deterministic clock/dedup/boundary/replay tests | Clearly synthetic; cannot establish real-world prevalence or safety accuracy |
| Consented real selected evidence | Intended-domain validation | Explicit consent, governance/security, license and user-safety review required; no automatic uploads |

Split by conversation/case/relationship/person/source and time as applicable. Keep overlapping windows, alias-related cases, screenshot/export duplicates and movie/dialogue source families together. Avoid train/test sharing of temporal graph nodes/edges unless transductive evaluation is explicitly reported. Keep train, development, calibration and final test distinct.

Use chronological **prefix replay with knowledge availability** for online claims; completed timelines for retrospective claims. User corrections/imports not available at a cutoff stay hidden from that prefix. Fit baselines/thresholds only on earlier eligible data. Outgoing missingness, notification clipping, capture gaps and repeated callbacks are part of the test distribution, not removed as inconvenient rows.

### 13.3 Metrics beyond message F1

- Extraction/contact linking: message/contact count error, duplicate false-merge/false-split, actor/target link precision, boundary communication/scope accuracy, span/quote accuracy, temporal-order correctness/abstention.
- Pattern detection: per-pattern precision/recall, episode/event-set support precision, interval overlap/boundary error and false positives on high-density benign streams. Match patterns one-to-one under a predeclared temporal/support rule; repeated alerts for one episode do not create multiple true positives.
- Timeliness: earliest **evidence-supported detection time**, delay from when the rule had sufficient available evidence, p50/p95 delay and missed patterns. Forecast lead time is only appropriate if separately evaluating future-outcome prediction.
- Alert burden: false alerts per active case-day and alerts per true episode; cooldown effects, new-high-risk-event bypass, user dismissal/correction rates. Repeated correlated windows require stream-level evaluation.
- Dynamic revision: ability to retract/correct a stale false-positive interpretation after reattribution/duplicate correction; retain observed historical facts. Adapt CGA recovery reasoning without treating “recovery” as a guarantee of safety.
- Calibration: Brier/ECE/reliability/selective risk for actual trained pattern/classifier outputs; no calibration claim for raw heuristics or LLM confidence. Count bounds are not probability intervals.
- Explanation: all citations resolve to selected available evidence; quote/numeral/date correctness; claim support and uncertainty/countercontext coverage; no legal/future-fact additions.
- Resources: Android reducer/classifier/LLM latency, peak memory, energy per processed event/session, app idle overhead and process-death/replay correctness.
- Privacy: expiration/deletion invalidates patterns, indexes and pending jobs; no retained ordinary-content leak, plaintext cache or unintended evidence network transfer.

Report all semantic metrics per English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish, Romanized Indic and code-mixed/slang, and per source modality/acquisition path. Native-script language results do not establish Romanized performance. All Sakshi-specific per-language results are currently unknown.

Cluster-bootstrap confidence intervals by independent case/relationship rather than overlapping event windows; report sample size/prevalence/selection and abstention. Accuracy alone is misleading when true patterns are rare. Validate threshold choice at expected review burden and worst-language false-negative cost, not only macro averages.

### 13.4 Functional and metamorphic tests

Inspired by HateCheck's targeted functional testing, adapted to sequences rather than copied as a harassment taxonomy [S17]:

1. Reposting the same payload does not add contact count.
2. Two genuinely distinct identical-text messages remain two contacts.
3. Adding an independently stored screenshot of an already reviewed message does not double count its occurrence.
4. Renaming a display label or changing unrelated profile metadata does not change actor identity automatically.
5. Shifting all known timestamps equally leaves relative interval patterns unchanged; timezone/DST conversion preserves actual instants.
6. Inserting a coverage gap prevents “no-contact” or comparable-rate assertions, not record preservation.
7. Moving a reviewed stop marker from before to after the contact sequence removes that after-boundary claim.
8. Removing communication evidence changes “after stop request” to a reported/private-marker interpretation or insufficient context.
9. Quoting a threat as a report does not attribute the quoted statement to the quoting sender.
10. An incoming-only view never proves absence of replies.
11. Correcting a tag/actor, rejecting a candidate or deleting an anchor invalidates dependent pattern facts and generated summaries.
12. Reordering arrival of historical imports produces the same final retrospective projection; online knowledge-time history is not rewritten as earlier detection.
13. Deadline/emergency high-density benign contrast does not become repeated-unwanted-contact solely due to count.
14. User resumption within a limited scope does not imply consent to different sexual/threatening content.
15. Unsupported language/model failure yields unknown/pending and manual review, not benign.
16. Every shown factual claim has existing source support; derived model summaries are never counted as new evidence.

### 13.5 Ablations and acceptance targets

Compare message-only classifier, rule temporal engine with manual tags, feature statistics, causal GRU/TCN, bounded LLM-only reasoning and hybrid under identical evidence/retention/cutoffs. Then remove timestamps, boundary notes, context, dedup, coverage checks and review state one at a time. Shuffle message order while preserving counts to test whether the sequence model actually uses order. Compare lexical/structured context to embeddings and graph methods only when useful reply/actor structure exists.

Predeclare measurable product targets, clearly **not achieved benchmarks**: e.g. <50 ms p95 reducer update on a 1,000-event selected case; bounded replay on a 10,000-event synthetic case without loading media; zero count inflation on deterministic repost fixtures; zero invalid citations in accepted exports through validation; explicit abstention on missing-scope/clock/actor cases. Semantic recall/alert thresholds must follow an annotated domain pilot; do not invent a clinical/harassment threshold for the demo.

## 14. Hackathon-feasible implementation plan

### Must implement

1. **Input and review:** user-selected text/one supported conversation export plus manual notes; selected OCR/STT-derived text can reuse existing pipelines. Preview records and uncertain timestamps/actors.
2. **Schema and anchors:** local event/annotation/link types reflecting the JSON contract; original preservation; explicit direction and boundary communication status.
3. **Canonicalization:** stable source ids where provided, explicit duplicates/reposts/possible duplicate review; count contact units rather than callbacks or labels.
4. **Four deterministic pattern cards:** retained contact count; after-boundary recurrence; insult-to-threat wording transition using reviewed tags; comparable-window frequency change with unknown-context handling.
5. **Boundary flow:** “stop request supported by evidence,” “I disengaged,” “limited contact,” and user-controlled resumption; never infer boundary communication from silence.
6. **Timeline/explanation:** event cards, supporting refs, gap shading and confidence/identity qualifiers; deterministic facts and templates; user confirms/edits/rejects findings.
7. **Replay/deletion:** scoped reducer, revision dependency invalidation, late imports and logical candidate expiry; explicit export after review.
8. **Fixtures/demo:** timelines A-F plus clock uncertainty, missing replies, actor collision and correction/delete tests. Show how a superficially ordinary sequence can yield a pattern without a legal label.

### Optional only if core works

One calibrated local context classifier for suggestion tags; EWMA/count residual visualization on comparable data; one foreground local LLM paraphrase constrained by source packet. Use existing approved model choices, not a new model zoo. Keep manual tags/template analysis fully functional.

### Defer

PELT/BOCPD production alarms, personalized Hawkes fits, GNN/TGN training, future-trajectory simulations, automatic actor resolution across apps, psychological profiling, external-history scraping and violence-risk scoring. Research methods can be valuable without becoming hackathon dependencies.

### Demo acceptance story

Import synthetic A-F fixtures, confirm source associations/tags/boundary, show six ordinary-looking unwanted contacts after a stop record, show B's wording transition, demonstrate C's benign density burst without harassment conclusion, demonstrate D's gap and internal-only boundary, and demonstrate F's three-message count from five callbacks. Change one tag/actor or delete one source and show deterministic recomputation. No model benchmark, personal-data capture or legal certainty is claimed.

## 15. Source register and open questions

All consulted 2 October 2026. Exact version/metric scopes are retained above; no source's result is a Sakshi result.

### Domain research and evaluation

- [S1] CAD official paper/repository/version correction and dataset license: https://aclanthology.org/2021.naacl-main.182/ ; https://github.com/dongpng/cad_naacl2021 ; https://zenodo.org/records/4881008
- [S2] Graphically Speaking final ACL abstract and inspected preprint v1 results: https://aclanthology.org/2025.acl-long.894/ ; https://arxiv.org/html/2504.01902 ; author code/data constraints: https://github.com/celia-nouri/ConversationALD/
- [S3] Temporal Properties of Cyberbullying on Instagram, research abstract and related hierarchical temporal work: https://par.nsf.gov/biblio/10196267-temporal-properties-cyberbullying-instagram
- [S4] TGBully original paper and released environment: https://arxiv.org/html/2011.00449v2 ; https://github.com/gesy17/TGBully
- [S5] Conversations Gone Awry, But Then? 2025 evaluation, tables, proxy labels and recovery metric: https://arxiv.org/html/2507.19470
- [S6] Generative future-trajectory forecasting: https://aclanthology.org/2025.inlg-main.40/
- [S7] Alert deferral/recovery decision mechanism: https://aclanthology.org/2026.acl-long.1943/
- [S8] Pragmatic conversation-dynamics summaries: https://aclanthology.org/2026.acl-long.243/
- [S9] Antisocial Behavior Prediction: A Survey and Practical Guide, WASSA 2026: https://aclanthology.org/2026.wassa-1.18/
- [S10] MentalManip original paper, fictional-data limits and license conflict: https://arxiv.org/html/2405.16584v1 ; https://github.com/audreycs/mentalmanip ; https://raw.githubusercontent.com/audreycs/MentalManip/main/LICENSE ; https://huggingface.co/datasets/audreyeleven/MentalManip
- [S11] Police-narrative behavioural extraction: https://link.springer.com/article/10.1186/s40163-024-00200-2
- [S12] DCAP 2026 institution-published author abstract, synthetic/injection/simulated evaluation: https://pure.hud.ac.uk/en/publications/a-hybrid-neural-symbolic-approach-for-the-longitudinal-profiling-/
- [S13] Lifetime toxicity patterns and platform/era dependence: https://www.nature.com/articles/s41598-025-07086-3
- [S14] Danger Assessment validation, author abstract through ERIC: https://eric.ed.gov/?id=EJ831090 ; PMC full-text fetch returned a browser challenge, so it was not treated as inspected full text: https://pmc.ncbi.nlm.nih.gov/articles/PMC7878014/
- [S15] MATRES main-axis/verb-event limits and event-endpoint reasoning: https://cogcomp.github.io/MATRES/ ; https://aclanthology.org/2023.acl-long.536/
- [S16] TGN original dynamic-event graph architecture: https://arxiv.org/html/2006.10637v3
- [S17] HateCheck functional testing, not a full harassment sequence dataset: https://aclanthology.org/2021.acl-long.4/
- [S18] 2026 LLM abuse-detection lifecycle survey (preprint, supplementary context, not a phone benchmark): https://arxiv.org/abs/2604.00323

### Algorithms and platform

- [A1] NIST EWMA definition/baseline requirements: https://www.itl.nist.gov/div898/handbook/pmc/section3/pmc314.htm
- [A2] NIST CUSUM and false-alarm design: https://www.itl.nist.gov/div898/handbook/pmc/section3/pmc313.htm
- [A3] PELT original paper and conditional computational guarantee: https://arxiv.org/html/1101.1438v3
- [A4] BOCPD original algorithm, hazard and per-step/truncation costs: https://arxiv.org/html/0710.3742v1
- [A5] Kleinberg original burst method, author-hosted paper abstract/text indexed in search and author publication listing: https://www.cs.cornell.edu/home/kleinber/bhs.pdf ; https://www.cs.cornell.edu/home/kleinber/ . Direct PDF fetch returned binary content, so no numerical results were taken from that fetch.
- [A6] Hawkes tutorial, continuous-time dependence, kernels and fitting: https://arxiv.org/html/1708.06401v2
- [A7] scikit-learn authoritative anomaly/outlier/novelty implementation guide and LOF usage limitations: https://scikit-learn.org/stable/modules/outlier_detection.html
- [P1] Android NLS observations/callbacks/active-notification contract: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- [P2] Android MessagingStyle message timestamps/sender/arrays: https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message

### Remaining empirical questions

- How much harmless-looking buildup is missed by default selective retention? Evaluate against a separately consented selected-history reference, without broadening production collection silently.
- Which context/link/clock features genuinely reduce pattern errors in each required language and source type?
- What recurrence/alert policies users find useful without fatigue or false reassurance?
- Can a small sequence model outperform rules+context classifier at matched review burden and phone energy?
- What are exact Android reducer replay/memory/energy costs and safe encrypted indexing/deletion behaviour?
- What additional model/dataset licenses are acceptable for the intended deployment?

**Implementation implication:** build the source-linked schema, canonicalized timeline, boundary-aware recurrence and transparent transitions first. Add learned temporal/graph/LLM components only when they improve evidence-grounded usefulness under the actual retention, missing-context and Android constraints.
