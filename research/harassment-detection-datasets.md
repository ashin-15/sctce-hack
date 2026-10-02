# Sakshi harassment-detection datasets: research and supplementary-data specification

Research date: 2 October 2026. Deliverables A-G. Scope: a privacy-first Android evidence organizer using compact local classifiers, user review, and a separate temporal engine. Laya integration remains deferred.

## Executive decision

**Use a portfolio, not one universal harassment dataset.** Public resources are useful for recognizing abusive text, identity-targeted hate, threat language, and gendered abuse. A smaller subset supplies reply context or session timestamps. None of the resources verified here establishes a ready-to-use benchmark for multilingual Indian private-chat harassment with long-term boundary violations, coercive control, escalation, and original-to-derived multimodal evidence links.

Recommended research portfolio:

1. **Native-script Indic abuse:** MACD, subject to noncommercial-research restrictions. Its 152,422 real ShareChat comments cover Hindi, Tamil, Telugu, Malayalam, and Kannada; its masked user/post metadata and chronological splits are valuable, but not a labeled longitudinal harassment corpus. [S06]
2. **Romanized/code-mixed text:** DravidianCodeMix for Malayalam/Tamil/Kannada, HASOC ICHCL for contextual Hinglish, HOLD-Telugu for Telugu-English, and TB-OLID for Romanized Bengali. Licenses/access are not equally permissive. [S05, S11, S14, S16]
3. **Gendered abuse:** Uli for Hindi/Tamil/Indian English and the 2025 women-targeted Tamil/Malayalam shared task. Uli is particularly relevant to participatory annotation, but preserves disagreement rather than one universally agreed label. [S12-S13]
4. **Threats and context:** CAD for contextual person-directed abuse and highlighted evidence; Jigsaw for a small threat-positive slice; THREAT and InViS as permission-cleared threat benchmarks. Their definitions differ materially. [S02, S18, S27-S28]
5. **Conversation dynamics:** CGA-WIKI, CGA-CMV-Large, ICHCL, and the BullyBlocker temporal releases. Distinguish within-thread deterioration from relationship-level harassment over weeks. [S11, S19-S23]
6. **Diagnostic evaluation:** HateCheck, Multilingual HateCheck's Romanized Hindi suite, and HateCheckHIn. These are deliberately authored/template-expanded tests, not naturally occurring victim evidence. [S31-S33]
7. **Supplementary Sakshi data:** consented, de-identified real sequences; independently authored fictional contrast sets; separately tracked LLM synthetic augmentation; staged OCR/STT/device fixtures. Keep all four provenance classes separate.

**Product licensing warning:** research suitability does not imply permission to ship a commercial model or redistribute examples. MACD, TRAC, AMiCA, THREAT, and SafeCity require special attention. Unclear terms are a blocker, not permission.

## Research method, confidence, and boundaries

Five search angles were used: general abuse/threats; India and Indic script/code-mixing; gendered/sexual harassment; conversations/temporal behavior/coercive control; diagnostic/synthetic/multimodal resources. Papers, organizer pages, original repositories, release licenses, and author-maintained dataset cards were preferred over third-party mirrors. Claims were checked against release terms and arithmetic; conflicts are retained below rather than silently repaired.

This is a source-verified research inventory, **not a downloaded-and-audited census of every release**. Only the supplied local CSV was statistically inspected. Most sizes/balances are published source statistics. No private chat was collected, no author was contacted, no account was authenticated, no model was trained, and no evidence was uploaded. Some PDF fetches returned binary content; searchable primary-source extraction was used instead. Harvard Dataverse pages returned no readable terms, so their data licenses remain unverified here. Access viability can change.

Terminology:

- **R:** naturally occurring public text or reported real experiences, human annotated. A reported experience is not independently authenticated proof of an incident.
- **R+AI:** real user turns with chatbot context. Bot replies do not make the user abuse a fully LLM-generated corpus.
- **D:** historical decoy interaction: real adult communication with a volunteer posing as a minor, not a real minor's experience and not LLM synthesis.
- **H:** human-authored fictional examples or templates and deterministic expansions. Public artificial diagnostic data, not observed incidents.
- **L:** LLM-generated synthetic text, even if subsequently human annotated.
- **U:** provenance insufficiently documented; quarantine until established. Do not assume either real or synthetic.
- **NR:** not reported or not established from the consulted release. This is not a zero count or an absent feature unless stated.
- Country/context describes collection setting, **not an inferred nationality of every author**. Annotation venue is not the geographic origin of posts.
- Ordered replies, timestamps, chronological train/test splits, and longitudinal relationship histories are four different capabilities.

## Supplied `data/labeled_data.csv`: inspection and interpretation

The schema, first examples, sample size, and counts strongly identify the Davidson et al. ICWSM 2017 dataset, *Automated Hate Speech Detection and the Problem of Offensive Language*. The upstream README describes the same columns. An exact upstream byte-for-byte comparison was not performed, so provenance is a strong identification, not a cryptographic upstream attestation. [S01]

| Check | Local result |
|---|---|
| Rows | 24,783 |
| Columns | unnamed row index, `count`, `hate_speech`, `offensive_language`, `neither`, `class`, `tweet` |
| `class=0` | Hate speech: 1,430 (5.770%) |
| `class=1` | Offensive language: 19,190 (77.432%) |
| `class=2` | Neither: 4,163 (16.798%) |
| Empty/whitespace-only text | 0 |
| Exact duplicate text excess | 0; normalized/near-duplicate and quotation leakage not measured |
| Annotation votes per row | 3-9 |
| Vote sum mismatch | 0: three vote columns sum to `count` for every row |
| Conversation / participant / event-time fields | None |
| SHA-256 of supplied file | `fcb8bc7c68120ae4af04a5b9acd58585513ede11e1548ebf36a5c2040b6f6281` |

The hate/offensive/neither columns are **annotation counts**, not model features or three independent behavior labels. Preserve votes for disagreement analysis; never feed them, `count`, `class`, or the row index into the text classifier. Label codes differ from HateXplain and MACD. MACD uses `0=abusive`, `1=non-abusive`; HateXplain's card uses `0=hate`, `1=normal`, `2=offensive`. Explicit adapters are mandatory.

Use this CSV for an English auxiliary baseline and data-pipeline smoke tests. Do not relabel every offensive tweet as interpersonal harassment or every `neither` item as safe private-chat behavior. Profanity, reclaimed language, song lyrics, quotations, and dialect differences need contextual review. A 77.43% majority-class predictor illustrates why accuracy alone is inadequate. The upstream repository explicitly links a racial-bias follow-up. [S01]

Keep the supplied file unchanged. Decode HTML entities, normalize Unicode, mask direct identifiers, and deduplicate only in versioned derivatives with source-row links. Preserve negation, emojis, punctuation, slang, and code-switching. Use near-duplicate/quoted-source clusters for splitting; the file does not support a defensible participant-disjoint or chronological split by itself.

# A. Dataset comparison tables

The following three joined tables collectively report every requested field. Stable D-IDs connect identity/paper/size to structure/balance and rights/access/limitations. Language subsets are separate where their imbalance or practical relevance differs. Variant rows do not imply that variants are independent datasets suitable for pooling.

## A1. Identity, paper, year, language, context, size, modality, and labels

| ID | Dataset; paper and year | Languages and country/context | Published sample size and unit | Modality; labels; provenance |
|---|---|---|---|---|
| D01 | Davidson; *Automated Hate Speech Detection and the Problem of Offensive Language*, Davidson et al., 2017 [S01] | English; Twitter, US-oriented English social-media discourse; country not per-row verified | 24,783 tweets; locally verified | Text; hate/offensive/neither plus votes; R |
| D02 | Jigsaw Toxic Comment Classification Challenge, 2017-2018 release; no canonical paper supplied by card [S02] | English; global English Wikipedia talk-page comments | 159,571 training comments; 153,164 test texts, only a subset scored | Text; toxic, severe_toxic, obscene, threat, insult, identity_hate; multi-label; R |
| D03 | OLID; *Predicting the Type and Target of Offensive Posts in Social Media*, Zampieri et al., 2019 [S03] | English; Twitter, international/English social-media context | 14,100 tweets = 13,240 train + 860 test | Text; OFF/NOT, targeted TIN/untargeted UNT, IND/GRP/OTH targets; R |
| D04 | HateXplain; *HateXplain: A Benchmark Dataset for Explainable Hate Speech Detection*, Mathew et al., 2021 [S04] | English; Twitter and Gab; India-led research, not an India-only corpus | Card describes 20,148 posts; HF supervised splits total 19,229 = 15,383/1,922/1,924 | Text; hate/offensive/normal, target communities, token rationales, individual votes; R |
| D05 | DravidianCodeMix / OffensEval Dravidian, Malayalam subset; Chakravarthi et al., shared-task paper 2021; journal DOI publication 2022 [S05] | Malayalam-English; Kerala-linked YouTube/movie-trailer discourse; native, Latin and mixed scripts | 20,010 comments = 16,010/1,999/2,001 train/dev/test | Text; nonoffensive, untargeted offense, individual/group-targeted insults, other-target slot, not-Malayalam; R |
| D06 | Same family, Tamil subset [S05] | Tamil-English; Tamil-speaking India and diaspora YouTube discourse; native/Latin/mixed scripts | 43,919 = 35,139/4,388/4,392 comments | Text; same six-way schema with not-Tamil; R |
| D07 | Same family, Kannada subset [S05] | Kannada-English; Karnataka-linked YouTube discourse; native/Latin/mixed scripts | 7,772 = 6,217/777/778 comments | Text; same six-way schema with not-Kannada; R |
| D08 | MACD; *Multilingual Abusive Comment Detection at Scale for Indic Languages*, Gupta et al., NeurIPS 2022 [S06] | Hindi, Tamil, Telugu, Malayalam, Kannada; Indian ShareChat comments, deliberately low Roman-character/code-mixing proportion | 152,422 comments; 92,881 posts; 70,453 users. Rounded language sizes: Hi 33k, Ta 30k, Te 30k, Kn 33k, Ml 25k, not exact additive counts | Text; abusive/non-abusive. Sexual/profanity/violent-intention categories inform rubric, not verified full fine-grained labels; R |
| D09 | HASOC 2019 Hindi; *Overview of the HASOC Track at FIRE 2019* and task specification, Mandl et al., 2019 [S07] | Hindi, including code-mixing; Indian Twitter/Facebook task scope | 4,665 training posts; test commonly 1,318, one system paper says 1,319 | Text; HOF/NOT; hate/offensive/profane subclasses; targeted/untargeted task where released; R |
| D10 | Bohra Hindi-English HS; *A Dataset of Hindi-English Code-Mixed Social Media Text for Hate Speech Detection*, Bohra et al., 2018 [S08] | Romanized Hindi-English; Indian political/protest/riot Twitter topics | 4,575 tweets retained from 112,718 collected tweets | Text; hate/normal plus token language hin/eng/other in paper; R |
| D11 | HEOT and HOT, related but nonidentical releases; *Detecting Offensive Tweets in Hindi-English Code-Switched Language*, Mathur et al., SocialNLP 2018; *Did you offend me?*, Mathur et al., ALW 2018 [S09] | Romanized Hinglish; Twitter, Indian code-switched/profanity-selected discourse | HEOT: 3,679 tweets; HOT: 3,189 tweets. Do not merge/count them as disjoint | Text; non-offensive, abusive, hate-inducing; R |
| D12 | TRAC-1; *Aggression-annotated Corpus of Hindi-English Code-mixed Data* and 2018 shared-task report, Kumar et al. [S10] | Hindi in Devanagari and Latin scripts, English; Indian/social-media Facebook discourse; additional platform test sets | 15,000 train+dev comments per language = 12,000+3,000; additional tests vary by platform | Text; overt/covert/non-aggressive; R |
| D13 | HASOC ICHCL 2021/2022/2023; *Identification of Conversational Hate-Speech* overview series [S11] | Hinglish; Indian controversial Twitter threads. 2022 also has a German task, not counted in Hinglish counts here | 2023 paper Table 1: 5,740 labeled 2021 train nodes; 4,914 labeled 2022 train nodes; 998 labeled 2023 test nodes; 8,525 unlabeled 2023 train nodes, calculated from level counts | Text; HOF/NOT in 2021; SHOF/CHOF/NONE and binary equivalents in later releases; R, with separate pseudo-labels in baseline |
| D14 | Uli; *The Uli Dataset: An Exercise in Experience Led Annotation of oGBV*, Arora et al., WOAH 2024 [S12] | Hindi, Tamil, Indian English; Indian/South Asian gendered Twitter discourse, 2018-2021 source pool | Paper: English 7,638, Hindi 7,714, Tamil 7,914 posts with at least one annotation; total 23,266. Website's 24,000/6,000-each statement is inconsistent | Text; three independent questions about gendered abuse under targeting conditions and explicit/aggressive language; annotator-level responses/skips; R |
| D15 | Abusive Tamil Text Targeting Women, 2025; Rajiakodi et al. task findings [S13] | Tamil including noisy/code-switching social text; Tamil-speaking YouTube communities | 3,986 comments = 2,790 train + 598 dev + 598 test | Text; abusive/non-abusive targeting women; R |
| D16 | Abusive Malayalam Text Targeting Women, same task [S13] | Malayalam; Kerala/Malayalam-speaking YouTube communities | 4,191 = 2,933 train + 629 dev + 629 test | Text; abusive/non-abusive targeting women; R |
| D17 | HOLD-Telugu; *Findings ... Hate and Offensive Language Detection in Telugu Codemixed Text*, Premjith B et al., 2024 [S14] | Telugu-English; Andhra Pradesh/Telangana-linked YouTube discourse; native and Romanized Telugu | 4,500 comments = 4,000 train + 500 test | Text; hate/non-hate, broad hate/offense task rubric; R |
| D18 | BD-SHS; *A Benchmark Dataset for Learning to Detect Online Bangla Hate Speech in Different Social Contexts*, Romim et al., 2022 [S15] | Bengali/Bangla script; Bangladesh Facebook/YouTube and controversial events, not a West Bengal-specific sample | 50,281 comments | Text; HS/NH; individual/male/female/group targets; slander/religion/gender/callToViolence types; multi-label downstream; R |
| D19 | TB-OLID; *Offensive Language Identification in Transliterated and Code-Mixed Bangla*, Raihan et al., 2023 [S16] | Latin-script Bangla and Bangla-English, some Hindi mixing; top Bangladesh Facebook pages | 5,000 comments = 4,000 train + 1,000 test | Text; transliterated/code-mixed, offensive/non-offensive, individual/group/untargeted; R |
| D20 | MOLD 1.0/2.0 and SeMOLD; *Cross-lingual Offensive Language Identification ... Marathi*, Gaikwad et al., 2021; *Predicting the Type and Target ... Marathi*, Zampieri et al., 2022 [S17] | Marathi; Indian Twitter, primarily native-script; v1 rejects excessive non-Marathi words | README: v1 2,500 (1,875/625); v2 3,600 (3,100/500); papers say 2,499 and 3,611 respectively. SeMOLD: 8,000 pseudo-labeled real tweets | Text; v1 OFF/NOT; v2 OLID type/target hierarchy; SeMOLD weak labels, not synthetic text; R |
| D21 | CAD; *Introducing CAD: the Contextual Abuse Dataset*, Vidgen et al., 2021 [S18] | English; Reddit discussion threads; global/English public discourse | 26,550 entries: 1,394 titles + 1,394 bodies + 23,762 comments; ConvoKit reports 1,395 conversations | Text; identity/affiliation/person-directed abuse, counter speech, non-hateful slurs, neutral; threatening and other secondary labels; spans/context-needed; R |
| D22 | CGA-WIKI; *Conversations Gone Awry*, Zhang et al., 2018; *Trouble on the Horizon*, Chang and Danescu-Niculescu-Mizil, 2019 [S19] | English; Wikipedia editor talk pages | 4,188 conversations; 30,021 comments; 8,069 speakers | Text; comment/conversation personal attack; paired controls, verified flag; R |
| D23 | CGA-CMV / CGA-CMV-Large; *Trouble on the Horizon*, 2019; expanded release and *How Did We Get Here? Summarizing Conversation Dynamics*, Hua et al., 2024 [S20] | English; Reddit ChangeMyView debates | Original: 6,842 conversations/42,964 comments; large: 19,578/116,793, 24,555 speakers, conversations through 2022 | Text; final-comment Rule-2 removal proxy; optional human-written and machine-generated dynamics summaries, separately tagged; R with H/L-derived summaries |
| D24 | AMiCA ASKfm; *Automatic Detection of Cyberbullying in Social Media Text*, Van Hee et al., 2018 [S21] | English and Dutch; adolescent-oriented public ASKfm, Netherlands/Flanders-linked research, not all users geolocated | 113,698 English and 78,387 Dutch posts | Text; bullying-related content, threat/blackmail, insult, curse, exclusion, defamation, sexual harassment, defense/encouragement and roles per guide; R |
| D25 | Instagram 2015; *Analyzing Labeled Cyberbullying Incidents on the Instagram Social Network*, Hosseinmardi et al., 2015 [S22] | Predominantly English; public Instagram media sessions, US adolescent-motivated research | 998 annotated media sessions in the original study; later variants have different sizes | Image + comments + profile/social metadata; session cyberbullying/cyberaggression; R |
| D26 | BullyBlocker temporal Instagram release; *Temporal Properties of Cyberbullying on Instagram*, Gupta et al., 2020; related content-pattern release, Hamlett et al., 2022 [S23] | Instagram; US research setting, verify actual release language distribution | NR on access page; request session/comment counts and version manifest | Session/comment text and metadata; temporal cyberbullying labels; 2022 adds detailed content-pattern annotations; R |
| D27 | Insta-CTSR; *SpectrumNet: Detecting LGBTQ+ Cyberbullying with Dynamic Context-Aware Attention*, Arslan et al., ICWSM 2026, organizer-listed [S23] | Instagram, LGBTQ+-focused; US research group; language distribution NR | NR in readable release sources consulted | Session-oriented cyberbullying; topics/severity/roles indicated by dataset title, exact enums NR; R claimed, release annotation provenance to audit |
| D28 | ConvAbuse; *ConvAbuse: Data, Analysis, and Benchmarks for Nuanced Abuse Detection in Conversational AI*, Cercas Curry et al., 2021 [S24] | English; real user interactions with CarbonBot, ELIZA, Alana; bot-directed abuse, not human-to-human chat | Paper: 6,837 examples/20,710 annotations; publicly releasable systems: 1,515 CarbonBot + 2,670 ELIZA = 4,185 examples | Text; non-abuse/ambiguous/three abuse severity levels; sexism, sexual harassment, racist/ableist/etc., target and directness; R+AI |
| D29 | SafeCity; *Understanding Diverse Forms of Sexual Harassment Personal Stories*, Karlekar and Bansal, 2018 [S25] | English reports; India-centered reporting platform, includes wider locations | 9,892 incident descriptions; same stories reused across binary and multi-label tasks | Text narratives; commenting, ogling/staring, groping/touching; original platform has 13 tags, study selects three; R reported experiences |
| D30 | THREAT / YouTube Threat Corpus; *THREAT: A Large Annotated Corpus for Detection of Violent Threats*, Hammer et al., 2019; earlier Wester et al., 2016 [S27] | English; 19 religious/political YouTube videos, collected summer 2013; not India-specific | 9,845 comments, 28,643 sentences, 5,484 users in 2019 Table I | Text; violent threat or sympathy with violence vs not; R |
| D31 | InViS; *Cross-Platform Violence Detection on Social Media: A Dataset and Analysis*, Chen et al., WebSci 2025 [S28] | English; Reddit political discussion, Parler, incels.is; US-oriented/extreme-misogyny/political context | 30,000 posts reported; platform subtotals vary by 1-2 rows within paper | Text; violence, sexual violence, political violence; human-coded; R |
| D32 | MDMD; *From Laughter to Inequality: Annotated Dataset for Misogyny Detection in Tamil and Malayalam Memes*, Ponnusamy et al., 2024 [S29] | Tamil/Malayalam with English mixing; Instagram/Facebook/Pinterest, India and diaspora | 1,776 Tamil + 1,000 Malayalam memes | Images + extracted text; misogyny/non-misogyny; R public memes, not verified real incident depictions |
| D33 | PAN12 Sexual Predator Identification; *Overview of the International ... Competition at PAN-2012*, Inches and Crestani, 2012 [S30] | English; historical Perverted Justice decoy logs + Omegle + IRC negatives; not India | 66,927 training conversations; 155,128 test conversations from Table 1 sums | Text chat; author-level predator IDs and suspicious lines; source-based labels; R+D |
| D34 | HateCheck; *Functional Tests for Hate Speech Detection Models*, Röttger et al., 2021 [S31] | English; controlled tests, seven protected target groups; no real collection country | 3,728 final cases/29 functionalities; 3,901 initial cases | Text; hateful/non-hateful, functionality/target/contrast links; H |
| D35 | Multilingual HateCheck; *Functional Tests for Multilingual Hate Speech Detection Models*, Röttger et al., 2022 [S32] | Arabic, Dutch, French, German, Hindi, Italian, Mandarin, Polish, Portuguese, Spanish; Hindi explicitly Latin-script, culturally selected targets | 36,582 total cases; Hindi 3,565; union of 34 functionalities, not all present in every language | Text; hateful/non-hateful, templates, contrasting cases, gold and reviewer disagreement; H |
| D36 | HateCheckHIn; *Evaluating Hindi Hate Speech Detection Models*, Das et al., 2022 [S33] | Hindi plus Romanized/multilingual forms; India-focused authored cases | 5,884 cases, 34 functionalities; 4,754 monolingual + 1,130 multilingual cases | Text; hate/non-hate, functionality/template variants; H |
| D37 | ToxiGen; *A Large-Scale Machine-Generated Dataset for Adversarial and Implicit Hate Speech Detection*, Hartvigsen et al., 2022 [S34] | English; 13 minority-group target sets; GPT-3 output, not geographically observed discourse | Paper 274,186 generated statements; HF train config about 251k; annotated config 8,960 train + 940 test; 27,450 raw human responses released 2024 | Text; toxicity intent/scores, groups, generation method, human ratings; L, including human-annotated L |
| D38 | NSW DFV narrative research corpus; *Text Mining Domestic Violence Police Narratives to Identify Behaviours Linked to Coercive Control*, Karystianis et al., 2024 [S35] | English; New South Wales police domestic/family violence reports, Australia, 2009-2020 | 406,196 single-person-of-interest/single-victim reports analyzed | Text narratives; 48 rule-extracted coercive-control-related behaviors, including intimidation/contact/financial/social abuse; R, rule-derived labels |
| D39 | Multi-Platform Cyberbullying and Toxic Span Detection, Mendeley v1 2026; no linked peer-reviewed paper established [S36] | Language/country NR; card claims Facebook/Instagram/Reddit/news comments | 75,000 claimed records; not independently counted | Text; seven classes including Harassment_Stalking plus automatic character spans; U |

## A2. Conversation structure, temporal structure, and class balance

| ID | Conversation structure | Temporal structure | Class balance, with denominator |
|---|---|---|---|
| D01 | Isolated tweets; quoted/reposted text is not a reliable thread | No timestamps or sequences in supplied CSV | Local: 1,430 hate / 19,190 offensive / 4,163 neither, out of 24,783 |
| D02 | Isolated comment texts in challenge; not full threads | No usable event timeline in standard challenge schema | Highly sparse multi-label positives; train threat commonly reported 478/159,571 (0.30%), toxic 15,294. Recount downloaded revision before use |
| D03 | Isolated tweets; no complete parent context | No released longitudinal relationship history | Full set: NOT 9,460 / OFF 4,640; targeted 4,089 / untargeted 551 within OFF. TIN merges insults and threats |
| D04 | Individual posts and token rationales; no reply trees | No longitudinal participant timelines | Roughly 7.8k normal/5.9k hate/5.5k offensive + 919 all-different votes; exact raw totals vary. Count chosen supervised revision, not mixed card statistics |
| D05 | Individual YouTube comments, not reconstructed sessions | No released harassment histories | Training: nonoffensive 14,153; not-language 1,287; individual 239; untargeted 191; group 140; other 0. Only 570/16,010 offense-positive (3.56%) |
| D06 | Individual comments | No usable labeled temporal progression | Training: nonoffensive 25,425; untargeted 2,906; group 2,557; individual 2,343; not-language 1,454; other 454 |
| D07 | Individual comments | No usable labeled temporal progression | Training: nonoffensive 3,544; not-language 1,522; individual 487; group 329; untargeted 212; other 123 |
| D08 | Masked comment/post/user relationships support grouping; sampled comments are not complete conversations; surrounding multimodal posts are not verified supplied | Collected Sep 2021-Feb 2022; creation-time-ordered 60:20:20 splits. Per-row timestamp availability not established here; no escalation gold labels | Exact total: abusive 74,550 / non-abusive 77,872. Rounded abuse rates Hi 52%, Ta 46%, Te 52%, Kn 49%, Ml 45%; intentional balancing, not deployment prevalence |
| D09 | Standalone posts, not complete private threads | Source dates not a verified longitudinal benchmark | Train HOF 2,469 / NOT 2,196; 52.92% HOF |
| D10 | Individual tweets; token language tags | Five-year source retrieval is not a released five-year sequence | Hate 1,661 / normal 2,914; 36.31% hate |
| D11 | Individual tweets; related variants may overlap | HEOT collected Nov-Dec 2017; not conversation timelines | HEOT nonoffensive 1,414 / abusive 1,942 / hate-inducing 323; HOT 1,121 / 1,765 / 303 |
| D12 | Individual posts/comments; surrounding thread not established | Additional cross-platform tests, not longitudinal labeling | Three classes, distribution differs by language/split; exact chosen-release counts NR here |
| D13 | Nested tweet -> comments -> replies; IDs join labels, preserve parent trees | Reply hierarchy/order but inspected organizer schema lists no timestamp field; no multi-week dyad labels | Calculated 2021 train HOF 2,841 / NONE 2,899; 2022 train SHOF 1,636 / CHOF 888 / NONE 2,390; 2023 test 254/147/597. 2023 unlabeled nodes have no gold training balance |
| D14 | Individual tweets; actual target handles masked; hypothetical targeting questions compensate partially | 2018-2021 pool, not released longitudinal harassment paths | Per-label, per-annotator, with missing answers; no single defensible class percentage. Must calculate coverage and distributions separately |
| D15 | Individual comments, not private-chat threads | No escalation/time-series labels | Train abusive 1,366 / non-abusive 1,424; 48.96% abusive |
| D16 | Individual comments | No escalation/time-series labels | Train abusive 1,531 / non-abusive 1,402; 52.20% abusive |
| D17 | Individual comments; context-aware annotators do not imply released dialogue context | No temporal progression gold | Train hate 1,939 / non-hate 2,061; test 250/250. Paper's total-test cell 250 contradicts its two 250 class counts |
| D18 | Independent comments, with target/type context labels, not complete relationship histories | No verified event sequences/timestamps in standard text release | HS 24,156 / NH 26,125; 48.04% HS; downstream type/target labels conditional on HS |
| D19 | Isolated Facebook comments, not thread trees | No verified event timestamps/timelines | Offensive 2,381 / nonoffensive 2,619; targets I 1,192 / G 954 / U 235. Romanized 2,959 / mixed 2,041; mixed 40.82%, not erroneous published 41.82% |
| D20 | Standalone tweets; variants overlap; SeMOLD not independent human gold | No longitudinal abuse labels | v1 approximately 35% OFF in secondary experiment descriptions; exact official file counts NR; v2 and SeMOLD class counts NR |
| D21 | Reddit threads and parents; context-needed labels and highlighted spans | Per-entry Unix timestamps/date/order; short threads, not dyadic long-term histories | ConvoKit primary counts: neutral 21,935; identity 2,216; affiliation 1,111; person 951; counter 210; slur 127. Counts sum to 26,550; multi-label original handling must be checked |
| D22 | Reply-linked conversations, speakers, paired conversations, verification metadata | Turn timestamps; collection late 2017-early 2018; attack onset derivable; not risk escalation across weeks | Matched attack/nonattack pairs; do not treat matched balance as population prevalence. Verify selected subset pair completeness |
| D23 | Reply-linked CMV paths; moderator outcome, optional dynamics summaries | Turn timestamps; expanded through 2022; onset/forecasting from prefixes | Matched removed/nonremoved outcomes; verify pair balance in chosen version. Moderator intervention is not expert severity gold |
| D24 | ASKfm Q/A/profile context; anonymous questions complicate identity linking | April/Oct 2013 collection; no established complete relationship timeline or escalation gold | English 5,375/113,698 bullying-related (4.73%); Dutch published 5,106/78,387. Those counts imply 6.51%, conflicting with 6.97% reported in arXiv table |
| D25 | Image with associated comment session; participant/social metadata | Comment timing examined; original session-level labels, not long-term dyad history | Selected high-negativity sessions; exact chosen-release balance NR here; not representative general Instagram prevalence |
| D26 | Session and comment-level labels according to access page | Specifically temporal/burst analyses within sessions; inspect requested files for timestamps, onset and gaps | NR until permission-cleared files/manifest supplied |
| D27 | Session-oriented according to project description; exact graph schema NR | Timestamp and multi-session continuity availability NR | NR; do not infer from paper/model title |
| D28 | `conv_id`, current user/agent and previous user/agent turns; limited context window | No verified wall-clock longitudinal timeline | Paper annotation-level abuse: CarbonBot 6.7%, ELIZA 21.2%, Alana 27.2%; not majority-labeled example prevalence |
| D29 | One retrospective incident narrative; mentions of repetition are not independently time-linked observations | Dates/locations may exist on platform, not established in released text task files; no verified dyad timeline | Commenting 39.3%, ogling 21.4%, groping 30.1% positives, overlapping. No selected label does not mean no harassment |
| D30 | Sentences grouped into comments, video and user identifiers | Source relative ages/comment order illustrated; precise longitudinal timestamps not established | 1,387 threat-positive sentences/28,643 (4.84%); 1,287 positive comments/9,845. Earlier 2016 has 1,384/1,285; version distinction matters |
| D31 | Individual posts selected across platforms; complete reply trees not established | Different source collection periods, not a longitudinal harassment benchmark | 243/30,000 violent (0.81%); 13 sexual-violence positives in platform breakdown. Balanced experimental subset is not full corpus prevalence |
| D32 | Individual memes, no interaction history | No timestamp/escalation labels | Tamil misogyny 448 / non-misogyny 1,328 (25.23% positive); Malayalam 400/600 (40%) |
| D33 | Multi-turn chat, anonymized author IDs, lines; sessions capped at 150 messages and split after >25-minute breaks | Message time fields; full dates/real gaps and cross-session continuity need audit | PJ-source train 2,723/66,927 and test 5,321/155,128 conversations; not equivalent to line-level abuse positives. Predator-author positives extremely sparse |
| D34 | Independent test cases with reference case/template links | No real event time; contrast pairs are not conversations | 2,563 hateful / 1,165 non-hateful; purpose-built, not population balance |
| D35 | Authored test cases with template/contrast links | None | Total suite 25,511 hateful / 11,071 non-hateful; Hindi-specific balance NR here |
| D36 | Authored examples with multilingual transformations, not real threads | None | Monolingual 3,338 H / 1,416 NH; multilingual 1,130 H; total 4,468 H / 1,416 NH |
| D37 | Generated statements and prompting seeds, not observed interactions | None | Generation intent/classes and human toxicity ratings differ; no one verified corpus-wide gold binary balance; choose documented threshold on human-rated subset |
| D38 | Police narratives with person/victim pairs, not raw chat conversations | 2009-2020 reports; repeat-person linkage not a released public longitudinal benchmark | Article reports 223,778 behavior-positive events and 54.6%, but 223,778/406,196 = 55.09%; denominators/percentages conflict. Not a manually labeled positive-negative chat set |
| D39 | Six-column isolated text schema, no conversation/actor links | No timestamps in stated schema | Approximately 10,714-10,715 per class by construction, not observational prevalence |

## A3. License, access, limitations, and Sakshi suitability

**License interpretation rule:** paper copyright, repository software license, dataset license, and underlying platform content rights are separate layers. MIT/GPL/AGPL labels on a code repository are not automatically a grant over third-party social posts. A permissive dataset release also does not eliminate privacy duties.

| ID | License / verified terms | Access requirements | Known limitations and recommended role |
|---|---|---|---|
| D01 | Original repository MIT; retain notices and review underlying tweet rights [S01] | Local CSV already present; upstream public | Keyword/user selection, English/dialect and annotator bias, strong offense skew, no context. Auxiliary training or external test, not full harassment gold |
| D02 | Annotation release CC0; underlying Wikipedia text CC BY-SA 3.0 per Google card [S02] | Official Kaggle download typically account/rules; file mirrors require provenance check | Threat scarcity, identity/profanity correlations, no history; possible Wikipedia overlap with CGA. Auxiliary multi-label text learning |
| D03 | Original data license not established here; HF mirror says more information needed [S03] | Public release/mirror exists; check original task terms first | TIN conflates insults and threats, keyword sampling, no context. Auxiliary only after rights clearance |
| D04 | Original repo MIT; HF metadata CC BY 4.0 but body MIT: document conflict [S04] | Public repo JSON/splits; HF loader script should not be blindly trusted/executed | Moderate agreement, lexicon selection, no external context, raw/card count drift. Useful rationale/target task, not authenticity evidence |
| D05 | Zenodo dataset CC BY 4.0 [S05] | Public ZIP/GitHub; historical task test-label availability verify | Severe positive scarcity in Malayalam, movie/fan discourse, no thread histories; not-Malayalam is not a benign-behavior label. Core code-mixed training |
| D06 | Same CC BY 4.0 | Same public release | Movie/celebrity domain, language-script mix does not guarantee private-chat transfer. Core Tamil code-mixed training |
| D07 | Same CC BY 4.0 | Same public release | Small sample, large out-of-language class, minority subtype scarcity. Core Kannada code-mixed training with explicit uncertainty |
| D08 | Author OpenReview statement: CC BY-NC-SA, research only, commercial use prohibited; exact version/full grant must be archived [S06] | Public CSVs, metadata and alternate splits | Deliberately <4% code-mixing, balanced sampling, broad profanity/sexual rubric, sampled user histories. Strong noncommercial research training/evaluation; commercial permission gate |
| D09 | Dataset-specific grant NR; paper CC terms do not resolve it [S07] | Organizer ZIP password via registration; mirrors not a substitute for authorization | Broad HOF, year/test-size drift, political/public domain bias. Hindi research training/evaluation after terms review |
| D10 | Repo GPL-3.0; text-specific rights NR [S08] | IDs/labels public; author invites email request for text; lawful X API recovery only if permitted | Missing/deleted tweets, broad hate definition includes nongroup-directed harm, no guaranteed word-level release. Secondary Hinglish resource |
| D11 | Release dataset license NR [S09] | Author HOT code/data repo; HEOT availability/version confirmation needed | HEOT/HOT differing sizes/taxonomy, overlap, profanity-based sampling; verify exact file. Secondary training, not presumed independent holdout |
| D12 | Organizer dataset CC BY-NC-SA 4.0; repo Apache-2.0 does not override organizer data terms [S10] | Public original repo after shared task; noncommercial restrictions | Covert aggression not coercive-control gold; individual comments, platform shift. Research auxiliary training; permissions before product use |
| D13 | Dataset-specific license NR [S11] | Password-protected organizer downloads/registration for 2023; archived-year conditions verify | Selected controversies, context endorsement rather than repeated unwanted contact; 2023 train is unlabeled. Strong Hinglish context benchmark; never treat pseudo-labels as gold |
| D14 | Original dataset LICENSE explicitly CC BY 4.0; website instead says open database license [S12] | Public GitHub training/testing CSVs; archive actual release LICENSE and seek clarification if using website variant | Missing labels, disagreement, hypothetical target, inconsistent website counts. High-priority Indian gendered auxiliary data; do not merge questions into one certainty score |
| D15 | Data-specific license NR; ACL paper license not enough [S13] | CodaLab task 20701; obtain current data terms and test gold availability | Targeted-topic sampling, narrow gender target, Tamil alpha 0.6474; ~balanced not natural prevalence. Recommended conditional supplementary training/evaluation |
| D16 | Same unresolved data license | Same task access | Small binary corpus, no consent/history labels; published counts vary by header handling. Recommended conditional Malayalam gendered slice |
| D17 | Data-specific license NR [S14] | CodaLab competition 16095/organizers; terms/test gold confirmation | Small, binary broad offense rubric, arithmetic typo, YouTube shift. High-priority Romanized Telugu evaluation; training conditional on rights |
| D18 | Author Kaggle release advertises CC BY 4.0; repo MIT; paper itself CC BY-NC 4.0 is not dataset grant [S15] | Public author Kaggle listing; account may be needed for files; original repo links it | Bangladesh != all Indian Bengali; broad HS includes personal slander, targets/types conditional. Core Bengali training after recording chosen release terms |
| D19 | Original repo AGPL-3.0; dataset-specific interpretation needs clarification; arXiv paper CC BY-NC-ND is not data license [S16] | Public JSON train/test files | Bangladesh pages, keyword filtering, three computing-student annotators, no threads. Valuable Romanized Bengali research benchmark |
| D20 | Original repo CC BY 4.0 [S17] | Public versioned CSVs | v1/v2 counts conflict across papers/README; overlaps; native-heavy filtering, pseudo-labels in SeMOLD. Optional Marathi extension; use human gold for tests |
| D21 | Dataset CC BY 4.0 per corpus documentation [S18] | Public original release/ConvoKit | Reddit topics, low rare-type counts, masked/deleted authors, 1,394 threads vs 1,395 converted conversations. High-priority contextual auxiliary training |
| D22 | Corpus-specific release license not established by consulted docs; Wikipedia source attribution/share-alike rights apply [S19] | Public ConvoKit download | Paired selection, editor domain, no relationship escalation labels. Conversation-prefix training/evaluation after corpus rights review |
| D23 | Corpus-specific dataset grant NR; ConvoKit software license does not license Reddit text/summaries [S20] | Public ConvoKit large corpus | Moderator Rule-2 proxy and public debate; generated summaries are not human gold; variants overlap. Conversation forecast research |
| D24 | Raw text academic-purpose request only; feature replication public OSF; article CC BY does not free raw posts [S21] | Contact authors for raw text; signed conditions may apply | Youth privacy, anonymous actors, sparse positives, defense is bullying-related but not perpetration. Taxonomy reference/permission-cleared research evaluation, not default product training |
| D25 | Data-specific license NR [S22] | CU Boulder team request described in original downstream release instructions [S22] | Youth-identifying images, selected sessions, media URLs can disappear; original 998 != later 2,218 variant. Optional authorized multimodal/session research |
| D26 | No open commercial grant on access page [S23] | Email BullyBlocker team for 2020/2022 releases | Unknown current size/media rights, old platform language, within-session not long-term coercion. Important temporal lead, not yet approved training data |
| D27 | Dataverse license/access terms unreadable here [S23] | Organizer links DOI 10.7910/DVN/MUVBRH; inspect terms and files, no permission bypass | Fresh 2026 lead; exact labels, counts, languages, annotation origin and time fields unresolved. Prioritize follow-up, not an established benchmark in this report |
| D28 | Original repo CC BY 4.0 [S24] | Public CSVs; Alana withheld for privacy | Bot-directed sexual aggression != human consent context; annotation rows not examples; ELIZA consent limitations noted by authors. Controlled auxiliary context/ambiguity task |
| D29 | **Research purposes only; contact SafeCity moderators for permission before use** [S25] | Public repo does not waive the permission requirement | Reports vs incoming messages, no benign everyday chat population, repeated task views of same stories; split-count conflict. Narrative/report testing only after permission |
| D30 | **Academic use only; agree to delete on request** [S27] | Download link accepts terms; separate earlier corpus available by author request | Quotes of violent scripture can be positive, undecidable sentences forced negative, no credibility gold. Research threat benchmark, not default shipping corpus |
| D31 | Data license unverified; paper CC BY 4.0 does not establish Dataverse terms [S28] | DOI 10.7910/DVN/ANGOX0; inspect license and files | 243 positives, only 13 sexual-violence cases, platform bias and count inconsistencies. Cross-platform evaluation after access/definition review |
| D32 | Data-specific release license NR; paper CC BY-NC 4.0 [S29] | Original paper/task organizers; current direct author release and images' rights need confirmation | Meme-template leakage, annotation ambiguity, extracted-text quality, visual-only meaning. Indic OCR+classification benchmark candidate, not chat-evidence integrity data |
| D33 | No data license displayed on consulted Zenodo record [S30] | Public 91.2 MB archive; review archive-specific terms before processing | Decoy source confounding, historical grooming rather than adult harassment, source labels, minors/sensitive content concerns. Defer MVP training; carefully approved research-only stress test |
| D34 | Dataset CC BY 4.0 [S31] | Public final suite CSV | Template artifacts; general insults can be non-hate, but not non-harassment. Holdout diagnostic only, never prevalence calibration |
| D35 | Dataset CC BY 4.0 [S32] | Public per-language final CSVs; author HF Hindi card | Authored Latin-script Hindi != representative Hinglish private chats; seven selected targets, gold/reviewer disagreement. Holdout per-function diagnosis |
| D36 | Original repo MIT; paper CC BY-NC 4.0 separately [S33] | Public two CSVs | Multilingual partition all H, artificial balance, template-family leakage. Functional evaluation only |
| D37 | Current LICENSE.txt includes MIT software and **CDLA-Permissive 2.0 data agreement**; README describes research-purpose intent. Archive exact terms and reconcile intended use [S34] | Public HF data; older code uses auth token but current card is readable without authentication | GPT-3/model/prompt artifacts, classifier-in-loop bias, subjective human ratings, no Indic/temporal validity. Optional low-weight synthetic augmentation, separate synthetic test scores |
| D38 | No public raw-data grant; sensitive police corpus under institutional access controls [S35] | Institutional/NSW Police approvals; no drop-in public download verified | Rule labels not adjudicated private-chat gold, reporting bias, denominator inconsistencies. Taxonomy/method reference, not available MVP training data |
| D39 | Mendeley card CC BY 4.0 [S36] | Public listing; raw file not audited | No verifiable collection/consent/annotator details established, automatic spans, exactly balanced classes and no sequences. **Quarantine; label name alone does not validate stalking detection** |

## A4. Important conflicts, exclusions, and complementary resources

1. **Dravidian imbalance:** organizer full Malayalam counts show 17,697/20,010 nonoffensive, about 88.44%, whereas surrounding prose says 85%. Training counts are clearer for the selected split. Other-target offense has zero Malayalam training examples despite a schema slot. Do not claim six well-trained Malayalam behaviors. [S05]
2. **HateXplain:** 20,148 in card prose, commonly 20,153 elsewhere, 19,229 supervised split rows, and 919 unresolved votes must not be mixed. HF header CC BY vs body/original MIT conflict is explicit. Downloaded-release counting is still needed. [S04]
3. **SafeCity:** paper says 9,892 stories. README's 7,201+990+1,701 split counts also total 9,892, but contradict its stated 10% test split. Use actual story IDs/files, not a percentage-implied allocation. Binary and multi-label views are the same underlying stories. [S25]
4. **ICHCL:** 2023 training is unlabeled. Table-level counts support the row calculations above, but its combined reply-CHOF total is inconsistent with component years. Keep per-year partitions and original IDs; do not pool all years then reuse any old test. [S11]
5. **InViS:** paper overall positive count 243, balanced experiment size 484 rather than 486, and conflicting platform totals need file-level reconciliation. Accuracy on balanced experimental subsets does not validate 0.81%-prevalence deployment. [S28]
6. **Uli:** original repository LICENSE and paper agree CC BY 4.0; website says open database license. Website total 24,000 and 6,000 in each of three languages cannot both be true. Base planning on the paper's per-language annotated-post counts and license in the chosen artifact. The Uli slur list is a **lexicon** including Malayalam/Bengali metadata, not a labeled conversation dataset or complete Malayalam classifier-training corpus. [S12]
7. **IndicAbusive:** Das et al. 2022 aggregates/reuses 14 sources across eight languages/ten forms. It is useful for label adapters and baselines, not 14 new independent corpora. In particular, it includes Davidson, Dravidian and related Hindi/Bengali material, creating cross-dataset leakage risk. Its MIT repo does not override upstream dataset terms. [S37]
8. **SHR/#YouToo:** Sawhney et al. 2019 offers research on personal recollections rather than abusive sender messages. The author repository listing says anonymization is pending; no ready current data release was established. Do not count it as an available corpus. [S26]
9. **ACTSA, Dakshina, Indic speech/OCR corpora:** sentiment/transliteration/transcription resources can improve extraction and language handling, but negative sentiment is not harassment and speech text is not abuse gold. ACTSA's 5,410 sentiment sentences are not a Telugu harassment dataset. [S38]
10. **Unverified Hub coercive-control collections:** names such as `claude-data` and `AbusivePatterns`, or a domestic-violence CSV with multilingual metadata, do not establish real participant provenance, language fidelity, consent, or independent labels. A model-looking instruction is not proof of generation provenance. Keep U until documented; do not promote them into evaluation gold. [S39]
11. **Translated/augmented releases:** machine translation, transliteration, back-translation, paraphrases, and SMOTE-derived representations are derivatives of source examples, not new independently observed incidents. Split the source family first. TTS renditions are artificial audio, not abusive calls.
12. This search is not an assertion that no other stalking/longitudinal dataset exists. The defensible finding is that **a rights-cleared, suitably labeled Indian private-chat benchmark was not established in the consulted primary releases**.

# B. Suitable training datasets and training strategy

## B1. Two rights-aware portfolios

| Training capability | Noncommercial research portfolio | Product-intended portfolio before legal approval |
|---|---|---|
| Native-script Indic abuse | MACD human-labeled train partitions; language-balanced minibatches | Do not automatically use MACD/AbuseXLMR. Obtain commercial permission or collect cleared equivalent data; use individually cleared CC BY resources and evaluate remaining gaps |
| Malayalam/Tamil/Kannada Romanized and code-mixed offense | DravidianCodeMix train partitions | CC BY 4.0 candidate; archive notices, underlying rights review, add consented private-chat fine-tuning |
| Hindi/Hinglish | HASOC Hindi, ICHCL 2021/2022 train, Bohra/HEOT/HOT after access clearance; TRAC optional | Uli CC BY candidate; other releases conditional. Never treat accessible ZIP or code GPL as automatic product rights |
| Telugu | MACD native-heavy; HOLD-Telugu for mixed/Romanized | HOLD permissions or newly collected Telugu chat data; no Hindi-to-Telugu transfer claim without tests |
| Bengali/Bangla | BD-SHS + TB-OLID after terms review | BD-SHS chosen CC BY artifact candidate; TB-OLID AGPL/data-scope review; add West Bengal/Indian Bengali independently |
| Gendered abuse | Uli and permission-cleared 2025 women-targeted Tamil/Malayalam | Uli candidate; Tamil/Malayalam task release terms must be obtained |
| English insult/hate/rationale | Limited Davidson, HateXplain, CAD, Jigsaw | Archive rights for exact releases, preserve attribution, avoid drowning Indic/private-chat data in English bulk |
| Threat language | Jigsaw positives; CAD secondary threatening; THREAT/InViS if approved | No automatic THREAT reuse. Independently licensed/consented threat contrast data with source-based rubric |
| Conversation/context | ICHCL and CAD; CGA training prefixes if rights approved | Consent-cleared Sakshi sequences; publicly licensed context only where corpus grant verified |
| Stalking/coercive behavior/intimidation patterns | Human-annotated supplementary Sakshi histories; restricted corpora inform rubric only | Same, with documented consent/legal basis. No open source in this inventory suffices by itself |
| Multimodal | Permission-cleared MDMD/Instagram; staged OCR/STT fixtures | Cleared media/actors and non-sensitive staged app screenshots/audio; preserve artificial provenance |

A released open model trained on restricted data is not a workaround for restricted corpus rights. Models already trained on Davidson, MACD, or Dravidian test material also require contamination review before calling those splits independent.

## B2. Label mapping: partial supervision, not one collapsed boolean

Use a shared small encoder with separate behavior heads and dataset-specific auxiliary heads, or stage training if simpler. The final number of labels should be constrained by validated annotation coverage. Do not assume every dataset labels every head.

| Source label | Can supervise | Must not be inferred automatically |
|---|---|---|
| Offensive/profane, OFF/HOF | Source-specific broad offensiveness auxiliary head | Repetition, non-consent, personal targeting, credible threat, stalking |
| Group-targeted hate | Identity/group abuse signal under that source's definition | Individual interpersonal harassment in every case |
| Targeted insult/TIN | Targeted offense indicator | Threat subtype: TIN mixes insult and threat |
| Jigsaw threat / CAD threatening | Threat-language candidate using the documented rubric | Capability, imminence, authenticity, legal threat credibility |
| THREAT positive | Threat/support-for-violence under THREAT rubric | Quoted/reporting text is speaker-originated threat under Sakshi |
| Uli hypothetical targeting questions | Gendered-abuse tasks separately, with response masks | Actual recipient identity, sexual non-consent, one agreed binary gendered-abuse gold |
| SafeCity commenting/ogling/groping | Narrative-reported behavior category | Sender message-level behavior or benign absence of all harassment |
| ICHCL CHOF | Context-dependent endorsement/support | Longitudinal escalation or stalking |
| AMiCA defense/encouragement | Conversation-related role/response | Harasser action; victim statements must not be labeled perpetration |
| CGA outcome | Observed future attack/removal proxy for research forecasting | Certainty of future harm, coercive control, violence severity |
| Non-hate in HateCheck | Not identity-targeted hate under test definition | Not abusive, not harassment, or safe conversation |

Store a supervision mask per head: positive, negative, unknown/unannotated/not-applicable. Missing labels are **not negative labels**. Preserve source ontology, vote distributions, and mapping version. A single message can contain multiple behaviors; avoid a mutually exclusive dominant-category model as the only classifier.

## B3. Concrete training pipeline

1. **Acquisition registry:** record owner, paper, exact source URL, immutable revision/DOI version, retrieval time, file hash, terms, restrictions, consent/PII notes, split membership, source ontology and artificial provenance. No download until access requirements are satisfied.
2. **Cleaning:** retain raw originals separately; derive Unicode-normalized text, optional transliteration and OCR/STT with mapping back to original spans. Preserve emojis, negation, dialect, intensifiers and code-switching. Deduplicate by source IDs, exact/normalized text, quoted/repost clusters and semantic-neighbor review; avoid stripping the actual evidence signal.
3. **Rights and privacy filtering:** exclude unresolved grants from product-intended training, remove identifiers from training derivatives, manually review rare/high-risk examples. Annotation vote counts and platform names remain audit metadata, not default prediction inputs.
4. **Leakage-safe split before augmentation:** group entire conversations, dyads/cases, participant-connected components, related source posts/videos, meme templates, translation families, synthetic scenario/prompt families and duplicate clusters. When a giant component makes strict participant splitting infeasible, report it and choose an explicit weaker benchmark rather than pretending independence.
5. **Baseline:** character/word TF-IDF + logistic regression or linear SVM per source/language, then a compact multilingual/Indic encoder. Fine-tune with partial-label masks; use modest loss weights/sampling by language/source. Train text-only, text+parent, and text+bounded-history ablations. Keep the temporal engine separate from lexical abuse scores.
6. **Domain adaptation:** verified consented/staged private-chat language and survivor-authored reviewed examples; cap synthetic sampling and test 0%, 10%, 25% synthetic **as experimental batch proportions**, not a claim of optimality. Do not allow a huge English or synthetic corpus to dominate.
7. **Calibration:** reserved real, deployment-like calibration conversations, per-label and per-language/script where support is sufficient. Thresholds are selected for measured precision/recall and review load, not arbitrary confidence constants. Class balancing changes priors; evaluation/calibration should not inherit balanced artificial prevalence.
8. **Error analysis:** false accusations from quotes, banter, minority identifiers and survivor narration; missed ordinary-word coercion, boundary-crossing repetition, indirect threats, slang and code-switching. Record missing context separately from model mistakes.
9. **Export and Android validation:** validate final quantized model/tokenizer/normalization together on identical test fixtures. Report task quality, uncertainty, latency, RAM, battery and offline behavior per language. Training can run on approved de-identified research infrastructure; runtime remains local and raw evidence is never silently uploaded.

# C. Suitable evaluation datasets and evaluation design

## C1. Evaluation layers

| Layer | Suitable corpus/split | What it tests | What the score cannot establish |
|---|---|---|---|
| Public short-text baseline | Held-out DravidianCodeMix, MACD if allowed, Uli, HOLD, BD-SHS, TB-OLID, HASOC Hindi, optional MOLD | Per-language/script abuse classification under source ontology | Private-chat performance, actual prevalence or stalking |
| Cross-source transfer | Train MACD, hold out Dravidian/women-targeted/HOLD source; reverse direction where rights permit; Bengali native vs TB-OLID Latin | Platform/script/topic shift and linguistic transfer | Same-script performance automatically transferring to Romanized chat |
| Context sensitivity | ICHCL labeled holdout and CAD threads | Parent-dependent labels, endorsement, quotes, target resolution | Weeks-long relational coercion |
| Conversation-prefix forecast | CGA-WIKI/CMV and approved BullyBlocker | Detecting onset/derailment using past-only input; compare context ablations | Ground-truth future injury or legal culpability |
| Threat transfer | InViS held-out platform, THREAT held-out video/user, Jigsaw/CAD threat slices after permissions | Explicit/indirect threat language and definition shift | Threat credibility or large-sample sexual-threat sensitivity from 13 InViS positives |
| Functional diagnostics | HateCheck final suite, MHC Hindi, HateCheckHIn | Negation, quotes/counter-speech, homonyms, identifiers, Romanization, obfuscation | Naturally occurring prevalence, general private-chat accuracy |
| Multimodal extraction | Cleared MDMD plus staged Indic screenshots/voice notes/video snippets | OCR/STT degradation and combined text/visual behavior | Real incident authenticity or long-term multi-evidence integrity |
| Primary Sakshi gold | New consented real conversations, participant/case-held-out | Repeated contact, boundary violations, coercive demands, evidence links, abstention | Universal danger prediction; selection of volunteers remains biased |
| Supplemental artificial challenge | Independently authored fictional cases and LLM synthetic heldouts | Controlled boundary contrasts and device/temporal edge cases | Real-world evidence, deployment validation or external authenticity |

Do not use a benchmark for both model selection and untouched final evaluation. Once inspected/tuned against, it becomes a development benchmark. Maintain a fresh evaluator-owned heldout set. Original dataset train/test membership is preserved for comparability, but a separate stricter grouped split may be needed for generalization claims; report both clearly.

## C2. Metrics and test hygiene

- **Message behavior:** per-label precision/recall/F1, macro-F1, PR-AUC; confusion matrices and support counts. ROC-AUC is secondary for rare threats. Separate strong explicit cues, implicit cues and unknown context.
- **Language:** English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi where enabled; native vs Romanized vs intra-turn mixed; cross-turn switches, dialect/slang and transliteration variants. Never pool a weak language behind an English aggregate.
- **Context:** target-only vs parent vs bounded prior turns vs full authorized history. Compare full-context annotations with partial-context annotations; expected unknown output is not an error when consent/history is genuinely absent.
- **Temporal:** event/dyad-level precision/recall, pattern evidence-link precision/recall, alerts per 100 conversations or observed user-day, deduplicated contact frequency, onset delay in turns/time, earliest-supported transition, false alerts on benign repeated contact. Measure at fixed precision/false-alert budgets selected before final testing.
- **Causal prefixes:** each input ends at `as_of_event`; no future turns, eventual moderator label, generated summary, outcome description, retrospective user declaration or severity annotation is visible unless it truly existed at that time. Define the prediction horizon and censor incomplete histories.
- **Rationale:** span precision/recall/F1 or IoU with valid alignment; source citation precision, hallucinated-source rate, minimum necessary context coverage. Human attention overlap is not proof of causal explanation or legal authenticity.
- **Calibration/abstention:** Brier/NLL, classwise reliability/ECE, coverage-risk curves, false negatives among high-confidence predictions, per-language abstention and user review load. Keep epistemic uncertainty distinct from missing evidence.
- **Multimodal:** OCR character/word error and STT word error, script-aware normalization policy, speaker attribution error, timestamp/region alignment, classification delta from clean transcript to actual extracted text, missing-modality abstention.
- **Confidence intervals:** bootstrap by independent case/conversation/participant cluster, not by individual messages from the same history. Show denominators and rare-event uncertainty; 13 sexual-violence examples cannot justify broad safety claims.
- **Contamination:** benchmark membership, seed examples, public fine-tuning of candidate checkpoints, translated copies and model-generated training that regurgitates tests. Public old tests may have entered pretraining. Prefer fresh participant-heldout primary evidence for release validation.

Illustrative precision planning for rare events, **not a Sakshi estimate**: at 1% prevalence, sensitivity 90% and specificity 95%, PPV = 0.009 / (0.009 + 0.0495) = 15.38%. High balanced-test F1 can coexist with many false alerts. Reweighting public samples can test scenarios, but true calibration requires a legally collected representative sample and known sampling probabilities.

# D. Missing-data analysis

## D1. Capability gaps

| Sakshi need | What verified datasets provide | Missing component and implementation implication |
|---|---|---|
| Repeated unwanted contact | ICHCL/CAD reply context; CGA/session datasets; MACD actor/post grouping | Boundary evidence, consistent dyad identity, multiple sessions/platforms, genuine gaps and duration. Build consented case sequences; do not use offensive-message counts as a stalking proxy |
| Escalation | CGA derailment outcome; temporal session bursts | Evidence-linked transitions from ordinary contact to persistence/pressure/threats, plus stable/de-escalating controls. Annotate change points and avoid predetermined escalation ladders |
| Coercive behavior | Restricted NSW narratives, AMiCA threat/blackmail rubric | Direct/private-chat demands, conditional consequences, autonomy restrictions, financial/social control, missing-context abstention. Collect contextual signals rather than diagnose a person |
| Intimidation | Threat/violent-intention broad labels | Nonviolent implied consequences, relationship/power context and ordinary-word intimidation. Annotate supplied facts and unknown credibility separately |
| Sexual harassment | SafeCity reports, ConvAbuse bot-targeted sexual labels, Uli/gendered comments, sparse sexual-violence InViS | Unwantedness, consent changes, refusals, persistence, unsolicited sexual material, blackmail. Sexual vocabulary alone must not define harassment |
| Private-chat context | PAN historical decoy conversations; limited ConvAbuse; public threads | Modern adult WhatsApp/Instagram/Telegram-style short turns, voice notes, address terms, omissions, sarcasm, multilingual private exchanges. Do not scrape private databases to fill this gap |
| Indic code-switching | DravidianCodeMix, Hinglish ICHCL, TB-OLID, HOLD | Multiple Indic languages in the same case, cross-turn switches, dialect/phonetic Romanization, Manglish spelling variation, OCR/STT noise. MACD deliberately limits mixing |
| Bengali in India | Bangladeshi Bengali resources, authored Hindi suite includes culturally selected identities | West Bengal/Indian Bengali language and social-context transfer tests; do not infer country equivalence from language |
| Evidence relationships | HateXplain rationales, CAD spans, IDs in conversation datasets | OCR region -> screenshot -> original, STT segment -> audio interval, quoted/reposted event duplicates, claim -> multi-event chain. Build a lineage graph with evidence anchors |
| Multimodal harm | MDMD images/text, Instagram sessions | Indic voice notes/video, multimodal contradictions, transcript uncertainty, sender attribution, non-text context, source integrity. Stage extraction fixtures and collect rights-cleared real cases separately |
| Observation missingness | Few explicit partial observation gold labels | Redacted/truncated notifications, missing messages/attachments, uncertain dates, unknown sender, disabled permissions, duplicate notification updates. Build availability masks, not fabricated content |
| Benign difficult histories | Generic nonoffensive controls and HateCheck contrast cases | Consensual repeated reminders, family logistics, banter, relationship arguments, reclaimed speech, consensual adult sexual chat, emergency contact and rejection respected. These are necessary to control false positives |
| Authenticity/legal outcomes | Public user content/narratives | Neither social labels nor hashes prove authorship, guilt, admissibility or future harm. Keep authenticity unverified unless independently documented; model outputs remain suggestions |

## D2. Supplementary-data tracks

Use four independent, explicitly named tracks. Public availability and artificiality are orthogonal: a public dataset can be H or L rather than R.

1. **Sakshi-Real, restricted access:** adults voluntarily donate selected de-identified excerpts via supported export/share workflows; partner NGOs/counselors recruit without pressure. Collect sufficient surrounding context only with separate permission. No automatic harvesting of all user histories. Treat reports about absent content as `reported`, not original observed messages.
2. **Sakshi-Authored, fictional:** trained native-language writers and survivor-informed practitioners author consent/relationship/boundary contrast scenarios without copying identifiable survivor cases. All actors/settings fictional. Record author role, scenario family, timing fabrication and edits. Expert authorship improves relevance but does not make a scenario real.
3. **Sakshi-Synthetic, LLM augmentation:** generate from abstract scenario specifications using approved local generation or nonsensitive public prompts. Never send survivor originals, identifying excerpts or linkable paraphrases to cloud models. Record generator/version, template, parameters, seed where supported, prompt hash, reviewer and transformation family. Human-reviewed generated text remains L. No auto-label from the generator as final gold.
4. **Sakshi-Fixtures, staged derivatives:** adult voice actors, consented fictional dialogues, mock screenshots and videos through test apps/devices. Record image/audio rendering/TTS/OCR/STT transformations. Test supported notification/import behavior, duplicate suppression, gaps, scripts and timing; not court evidence. Native human speech recordings are staged, TTS audio is machine-generated, neither is a real abusive call.

## D3. Proposed pilot targets and expansion logic

These are proposed annotation-workload targets, not existing sample counts or a power-certified safety dataset:

- First rubric pilot: 240 conversation windows, 30 per priority language/form: English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali and Hinglish. Explicitly include native/Romanized/mixed forms inside Indic strata. Include Marathi in a later separate 30-window pilot if enabled.
- First authored benchmark: 100 scenario families per priority stratum, each with one behavior-positive and one closely matched benign/ambiguous contrast: 1,600 fictional sequences total. Reserve at least 20% families for evaluator-owned tests; assign before writing/LLM variation, and keep family translations together.
- First real-data feasibility target: approximately 200 independently consented cases overall, with language/form strata recorded. This is a feasibility target, **not enough to certify rare-label performance in each language**. If safe recruitment fails, remain at authored/staged demonstration and explicitly do not claim validated real-world detection.
- First extraction fixtures: 25 screenshot layouts and 20 adult-recorded voice-note segments per language/form, with held-out devices/fonts/speakers. Reuse of the same scripted dialogue counts as a derivative family, not independent samples.
- Expansion: prioritize rare behavior/context combinations after annotation/error audits. Recruit more independent dyads rather than hundreds of extra messages from one person. Language/label coverage and confidence-interval width, not an attractive total row count, determine readiness.
- No fixed coercion/stalking success threshold is promised. Pre-register release criteria with practitioners using measured false-alert costs, supported positive counts and deployment observation coverage.

# E. Proposed Sakshi dataset schema

**Design proposal, not an implemented schema.** Use versioned JSONL records plus normalized tables or Parquet views for research. A compact Android event/label adapter can consume only approved fields. The research dataset and the user's evidence vault are different stores with different authorization and retention.

## E1. Entity and field specification

| Entity | Required fields and types | Purpose and safeguards |
|---|---|---|
| DatasetRelease | `dataset_id:string`, `schema_version:string`, `revision:string`, `ontology_version:string`, `source_manifest:list`, `rights_status:enum`, `terms_hash:string`, `artifact_hash:string`, `created_at:UTC` | Freeze data, mapping and terms; `rights_status` unresolved/research_only/product_cleared; do not mix rights classes silently |
| Case | `case_id:string`, `origin_kind:enum`, `source_dataset:string?`, `source_record_ids:list`, `split_group_id:string`, `language_tags:list`, `region_context:string?`, `setting:enum`, `observation_scope:enum`, `consent_ref:string?` | R/public_observed, R/consented_real, historical_decoy, human_authored_fiction, llm_synthetic, staged_fixture, mixed, unknown. Unknown cannot enter validated real gold |
| Participant | `participant_id:string`, `case_id:string`, `role_annotations:list`, `relationship_report:object?`, `identity_link_status:enum` | Opaque per-case IDs; roles are annotations with evidence and uncertainty, not immutable identities. Cross-case links only under separate consent; protected demographics optional/consented |
| Event | `event_id:string`, `case_id:string`, `session_id:string?`, `actor_id:string?`, `recipient_ids:list`, `reply_to_event_id:string?`, `sequence_index:int`, `source_time:object`, `observed_at:UTC?`, `available_from:UTC?`, `channel:enum`, `source_evidence_ids:list` | `source_time` stores value, precision, timezone, origin and uncertainty; separate event occurrence from device capture and availability. Do not invent exact timestamps |
| Observation | `observation_id:string`, `event_id:string?`, `acquisition_method:enum`, `visible_text:string?`, `truncation:enum`, `missing_modalities:list`, `redactions:list`, `dedup_group_id:string?` | Supported notification/share/SAF/manual-reported/staged. Multiple notifications can be one event; redacted text is unknown, not benign |
| EvidenceAsset | `evidence_id:string`, `media_type:string`, `encrypted_reference:string`, `sha256:string`, `rights_ref:string`, `privacy_tier:enum`, `source_status:enum`, `parent_ids:list` | Encrypted private original/reference; report/notification/screenshot distinctions. Research release excludes originals by default and does not expose reversible local paths |
| Derivative | `derivative_id:string`, `evidence_id:string`, `kind:enum`, `text:string?`, `tool_version:string`, `language_segments:list`, `alignment_map:list`, `quality:object`, `transform_family_id:string` | OCR/transcript/normalization/transliteration/translation. Native originals immutable. Map char offsets to OCR boxes, audio/video intervals; retain extraction uncertainty |
| BehaviorAnnotation | `annotation_id:string`, `event_ids:list`, `as_of_event_id:string`, `label:string`, `status:enum`, `epistemic_kind:enum`, `speaker_stance:enum`, `evidence_anchors:list`, `context_event_ids:list`, `missing_context:list`, `annotator_id:string`, `rubric_version:string` | status present/absent/insufficient_context/not_applicable/unannotated. epistemic observed_text/inferred_behavior/supported_pattern/unknown. speaker stance enacted/endorsed/quoted/reported/rejected/unclear |
| BoundaryAnnotation | `boundary_id:string`, `as_of_event_id:string`, `boundary_kind:string`, `status:enum`, `evidence_anchors:list`, `source_kind:enum` | Refusal/stop-contact request/consent withdrawal, direct evidence vs user report. Silence not consent and not proof of refusal either |
| PatternAnnotation | `pattern_id:string`, `case_id:string`, `as_of_event_id:string`, `window:object`, `label:string`, `event_ids:list`, `boundary_ids:list`, `observation_completeness:enum`, `change_points:list`, `unknown_factors:list` | Requires multiple distinct events where repetition is claimed; dedup groups excluded. Record partial/no-complete-history rather than asserting exhaustiveness |
| AnnotationReview | `review_id:string`, `annotation_id:string`, `independent_responses:list`, `adjudication:object?`, `skip_reason:string?`, `agreement_summary:object` | Preserve disagreement, annotator skip rights, distinction between adjudicated labels and a user's app confirmation |
| ArtificialProvenance | `author_or_generator:string?`, `generator_revision:string?`, `prompt_template_id:string?`, `prompt_hash:string?`, `parameters:object?`, `seed:string?`, `scenario_family_id:string`, `human_edits:list`, `is_time_fabricated:boolean` | Required for H/L/staged. A reviewed synthetic record never becomes R through editing |
| SplitAssignment | `split:enum`, `case_group:string`, `participant_group:string?`, `duplicate_cluster:string`, `source_family:string`, `scenario_family:string?`, `split_protocol_version:string` | Train/development/calibration/test/diagnostic/quarantine. Entire linked families remain one partition |
| ConsentGovernance | `consent_id:string`, `notice_version:string`, `purposes:list`, `allowed_modalities:list`, `retention_until:UTC`, `access_roles:list`, `withdrawal_status:enum`, `legal_basis_review_ref:string`, `third_party_review:object` | Stored separately from model inputs. Includes training/evaluation/public-release choices independently and withdrawal propagation status |
| ModelPrediction | `model_revision:string`, `backend_revision:string`, `input_event_ids:list`, `as_of_event_id:string`, `scores:object`, `abstentions:list`, `claimed_anchor_ids:list` | Never co-locate as human gold by default; prevent label/prediction leakage |

Offsets use **Unicode code-point indices, half-open `[start,end)`**, against a named derivative text version. Android/Kotlin UTF-16 offsets require an explicit adapter; Indic combining marks and grapheme clusters must be tested. OCR boxes store coordinate system/page/frame and rotation; audio/video intervals use milliseconds and explicit time origin. Keep raw hashes of private evidence inside the restricted vault; even hashes/IDs should not become public linkage handles without privacy review.

## E2. Behavior and pattern ontology

Initial message/window behavior labels, multi-label and evidence anchored:

- `person_directed_insult_or_degradation`
- `identity_directed_abuse`, with target mentioned in evidence, not inferred protected identity
- `speaker_originated_harm_threat_language`, with harm type and conditionality
- `intimidating_consequence_language`
- `autonomy_restricting_demand`
- `conditional_exposure_or_blackmail_demand`
- `sexualized_person_directed_language`
- `unwanted_sexual_conduct_indicator`, only when supplied consent/boundary context supports unwantedness
- `personal_information_exposure_or_exposure_threat`
- `contact_boundary_statement`
- `contextual_endorsement_of_abuse`

Separate interpretation flags: quoted/reported/counter-speech/consensual_or_banter_context/ambiguous_context. `benign` is a reviewed classification under the observed scope, not guaranteed safety. Legal offenses or psychiatric labels are not training categories.

Initial pattern labels:

- `repeated_contact_observed`: frequency, distinct-event count, observed coverage; not inherently harassment.
- `contact_after_explicit_boundary`: boundary evidence + subsequent event links; unknown linkage/identity yields uncertainty.
- `repeated_targeted_degradation`: consistent supplied target and linked events.
- `repeated_restrictive_or_conditional_demands`: supported demands/consequences across turns, not a diagnosis of coercive control.
- `possible_escalation_transition`: earlier/later observed behaviors and change point, with category/intensity/frequency dimensions separate.
- `de_escalation_or_boundary_respected`, `stable_pattern`, `insufficient_history`: necessary non-escalating controls.

An escalation label describes a retrospective change **supported by supplied observations up to that prefix**, not future danger. Do not define a universal ladder where insults automatically progress to violence. Missing contact, silence, refusal, channel changes and source completeness affect what can be concluded.

## E3. Illustrative valid record

This is a **human-authored fictional structural example**, not an incident or dataset row already collected. Non-sensitive text avoids reproducing real survivor evidence. Nulls mean not available, not invented.

```json
{
  "schema_version": "sakshi-research-0.1-proposed",
  "case_id": "fiction-case-001",
  "origin_kind": "human_authored_fiction",
  "scenario_family_id": "boundary-respected-001",
  "split": "diagnostic",
  "language_tags": ["en"],
  "is_time_fabricated": true,
  "events": [
    {
      "event_id": "event-001",
      "actor_id": "person-a",
      "recipient_ids": ["person-b"],
      "sequence_index": 0,
      "source_time": {"value": null, "precision": "unknown", "origin": "fiction"},
      "text": "Please stop contacting me."
    },
    {
      "event_id": "event-002",
      "actor_id": "person-b",
      "recipient_ids": ["person-a"],
      "sequence_index": 1,
      "source_time": {"value": null, "precision": "unknown", "origin": "fiction"},
      "text": "Understood. I will not contact you again."
    }
  ],
  "annotations": [
    {
      "label": "contact_boundary_statement",
      "status": "present",
      "epistemic_kind": "observed_text",
      "as_of_event_id": "event-001",
      "evidence_anchors": [{"event_id": "event-001", "start": 0, "end": 26}]
    },
    {
      "label": "contact_after_explicit_boundary",
      "status": "insufficient_context",
      "epistemic_kind": "unknown",
      "as_of_event_id": "event-002",
      "unknown_factors": ["Only an acknowledgement is observed; later contact is unavailable."]
    }
  ]
}
```

The example does not turn a one-turn acknowledgement into continued harassment or assert that no future contact occurred. A fuller benign sequence can document respected boundaries within a specified observation window.

# F. Data collection and annotation protocol

## F1. Governance before collection

1. Appoint a data steward, privacy/security owner and an independent safety/ethics reviewer; involve regional language practitioners and people with lived experience in rubric decisions. Budget paid work and emotional support.
2. Complete a data-protection impact assessment and institutional ethics review where applicable. Identify legal basis, third-party privacy rights, retention, withdrawal and mandatory-reporting duties before recruitment. Public-data release licenses are not the whole review.
3. Use separate plain-language notices for app-local use, research donation, training, evaluation and public release. Consent to use Sakshi is **not** consent to train models. Offer notices in the participant's preferred language, and permit non-participation without loss of support.
4. Default pilot to adults. Do not obtain consent from an abusive partner or contact them to validate a report. Third-party message rights need legal review; lack of that review means no research ingestion/public release, not pressure on the survivor to contact the sender.
5. Prepare safe contact/withdrawal procedures chosen by each participant. Do not send revealing follow-up messages, coercive recruitment reminders, or notifications visible to someone monitoring the device. Avoid retaining a list mapping survivor identity to abuse themes unnecessarily.

## F2. Collection

- Invite **user-selected** supported chat exports, Sharesheet text/files, SAF/Photo Picker assets or separately enabled notification observations. Do not extract private app databases, bypass encryption/View Once, or remotely surveil third-party apps.
- Donations require an explicit preview and affirmative export action. App evidence stays on-device by default. Research upload is a separate opt-in workflow, not implied by local inference or report creation.
- Collect complete-enough local windows where safe, including benign context, refusals and acknowledgements; record omissions and reason codes. Never demand an entire relationship history or force distressing disclosure.
- Distinguish original message exports, notification snippets, screenshots, voice recordings and retrospective user narratives. Record source time/capture time/timezone/precision/availability. Unknown authorship/date stays unknown.
- Pseudonymize actors consistently within the donated case using random IDs. Redact phone numbers, handles, location identifiers, faces, contact lists, account photos and unique details. Review whether exact quotations remain searchable/reidentifiable; pseudonymization alone is not anonymization.
- Original legal evidence, if retained by the user, remains untouched. Research derivatives are separate. Use a consented secure intake/review environment; never commit real excerpts, identifying hashes, raw screenshots or voice prints to Git.
- Include participant-approved multimodal information only; no intimate imagery or actual child sexual material in an MVP research corpus. Use staged non-sensitive substitutes for pipeline tests.
- Freeze a case manifest, rights decisions, redaction log, artifact checksums and authorized observation range. Do not reinterpret missing events as zero contact.

## F3. Annotation workflow

1. **Rubric pilot:** native/fluent speakers annotate the initial 240 windows. Regional reviewers audit slang, Romanization, code-mixing, caste/religion/gender references, address terms and contextual meaning. Discuss disagreement; revise definitions before scaling.
2. **Independent labels:** at least three trained annotators per primary gold case/window, including regional language expertise and survivor-informed practice. Assign sensitive cases only to those who freely choose that scope. Never show model suggestions in the initial gold pass.
3. **Two context conditions:** one pass target-only or actual app-visible prefix, another full permitted context. Record both; an excerpt may legitimately be unknown even when the full context resolves it. Do not force partial observers to replicate full-history labels.
4. **Message behavior pass:** mark source spans and speaker stance, target, explicit vs implicit cues, direct vs reported evidence, missing context and multiple behaviors. Sexualized content and unwantedness are distinct labels.
5. **Boundary/event pass:** identify consent/refusal/stop-contact evidence; annotate acknowledgements, subsequent contact, identity/link uncertainty, repost/notification duplicates and real versus inferred timing.
6. **Pattern pass:** annotate bounded history and causal prefixes, linked event set, observed repetition, change points, stable/de-escalating alternatives and partial coverage. Repetition requires distinct interactions, not duplicate notifications. External threat credibility/capability generally remains unknown.
7. **Adjudication:** an expert reviews disagreements with original independent judgments retained. Allow irreducible `insufficient_context`/ambiguous labels; no majority vote converts missing facts into certainty. Preserve user self-reports as a separate evidence channel, not an annotator-invented fact.
8. **Reliability:** report per-language/per-label Krippendorff alpha or appropriate multi-rater agreement, prevalence-aware positive/negative agreement, span agreement and adjudication rates. Propose alpha >=0.67 as a pilot review trigger, not a universal proof of gold validity; rare labels require qualitative audit even with high agreement.
9. **Quality control:** blinded duplicate review of a small approved subset, contradiction/span-bound checks, gold provenance verification, group/split leakage scan, independent high-risk error review. Synthetic examples are independently annotated without generator-provided labels visible initially.
10. **Release review:** approve only de-identified, rights-cleared artifacts. Publish aggregate documentation and authored/staged examples first; real private excerpts should normally remain controlled-access with access logs and purpose restrictions.

## F4. Annotation team safety

Provide clear content warnings, paid training and fair compensation, voluntary skip/stop, short exposure batches, breaks, rotating tasks and confidential support. Do not penalize skipped material or optimize throughput at the expense of psychological safety. Annotators should not be forced to identify their own trauma; participation of lived-experience experts must be compensated and non-extractive.

## F5. Splits, retention, withdrawal, and quality gates

- Split entire cases and all related participants/source/scenario/translation families **before** creating windows or synthetic derivatives. Proposed real-data train/dev/calibration/test allocation 60/15/10/15 is provisional; enforce grouping and language/label support rather than exact percentages at all costs.
- Also build a later-time holdout and unseen-source/device holdout. Future-time and participant-disjoint constraints can conflict; document separate protocols instead of presenting them as one magical split.
- A simple control-plane registry tracks consent and rights outside model inputs. Access is least-privilege, encrypted, auditable; scheduled retention expiry applies to originals, derivatives, embeddings, caches and backups according to approved retention rules.
- Withdrawal removes identifiable participant material from active datasets and future training, plus derivatives/caches according to policy. Define backup expiry, checkpoint lineage and retraining/unlearning feasibility. Do not promise instantaneous removal from already trained weights or third-party public copies. Explain these limits before obtaining consent.
- Release gate: terms archived; no unknown origin in real gold; every claim anchored; no missing supervision treated as negative; independent test families; enough independent positives per supported label/language; documented false-alert/review burden; calibrated final Android model; manual fallback for unsupported contexts.

# G. Ethical and legal considerations

This is engineering guidance, not a legal opinion or an authorization to process private communications.

## G1. India-specific obligations and uncertainty

- Review the **Digital Personal Data Protection Act, 2023** and notified **DPDP Rules, 2025**, the actual commencement/enforcement schedule and applicable scope at deployment. Official government material describes phased implementation; do not assume all obligations became enforceable together or cite the earlier draft as current law. Research/statistical exemptions are conditional, not a blanket permission for survivor chats, commercial reuse or identifiable decisions. Obtain current Indian legal advice. [S40]
- Children's data requires particular protections and potentially verifiable parent/lawful-guardian consent under applicable provisions. An unsafe family situation can make consent complicated; do not resolve that by bypassing safeguards. Keep minors out of the first collection pilot and seek specialist child-protection/ethics advice for any future work.
- Counsel should review POCSO/other applicable mandatory-reporting and harmful-content obligations before collection; do not make automatic promises of confidentiality that cannot legally be honored. Minimize sensitive material and define a qualified escalation procedure, not automatic police reporting by an ML label.
- Do not call training labels legal determinations of harassment, stalking, sexual harassment or criminal intimidation. India-specific legal categories and workplace remedies have context/elements not available from short text. Label observed behavioral signals for user review.
- **Bharatiya Sakshya Adhiniyam, 2023, section 63** addresses electronic-record admissibility. Source hashes support later integrity comparison, not authorship, truth, chain-of-custody completeness or guaranteed admissibility. Preserve originals and derivation records; actual evidence/export practices need legal review. [S41]

## G2. Rights, consent and licensing

- Distinguish copyright/database licenses from platform terms, API access, privacy, confidentiality and research consent. A dataset publisher cannot necessarily grant every underlying right in screenshots, photos, voices or third-party posts.
- Noncommercial/research-only data can be suitable for an approved experiment yet unsuitable for a commercial Android app. Never erase notices or relicense a pooled corpus as MIT because the training script is MIT.
- Verify redistribution and derivative/model-result obligations for NC/SA, GPL/AGPL, ODbL and mixed-license corpora with counsel; do not assume every trained model is a derivative or assume none is. Keep corpora separable so a denied license does not contaminate the product training lineage.
- Historical decoy chats and survivor public disclosures carry ethical burdens despite public availability. Public access is not meaningful informed consent for a new harassment classifier. Avoid reverse-searching text, identifying participants or linking usernames across platforms.
- Bengali Bangladesh resources and foreign police/grooming data should not be presented as India-representative. Record language, script, dialect, collection setting, selection strategy and uncertainty.

## G3. User safety and model behavior

- The tool organizes **possible indicators for human review**; it does not diagnose perpetrators, rank guilt, predict certain violence or replace support professionals.
- Neither a benign output nor a low score should prevent a user from saving evidence, requesting help or exporting a reviewed report. Unsupported languages and missing context must have a classifier-free workflow.
- Do not discriminate against minority dialects, reclaimed language, sexual-health discussion or LGBTQ+ identity terms. Separately test survivorship narration, counter-speech and consensual adult communication to prevent victim statements becoming accusations.
- Minimize screenshots/voice retention, use encrypted restricted storage, forbid sensitive-content telemetry and public example galleries, and test for memorization/PII leakage from trained models. Hashes and pseudonyms are not anonymization guarantees.
- Explicit research export is required. Synthetic training data must never appear in an evidence vault/report as observed source evidence, and generated explanations must never fabricate quotes, events, identities or dates.

## Concrete implementation implications and next decisions

1. Preserve the supplied Davidson CSV, register its local hash and build explicit label-code adapters. It is an English auxiliary resource, not Sakshi's ground truth.
2. Maintain a **rights-aware dataset registry** and separate research-only from product-cleared artifacts/checkpoints. Ask MACD authors for the intended license version and commercial permission if product training is planned.
3. Use DravidianCodeMix and Uli as the first permissive-release candidates after underlying-rights review; prioritize CAD for evidence spans/context. Add Bengali/Telugu/Hinglish only after obtaining exact release terms and usable gold labels.
4. Request metadata/terms for BullyBlocker temporal releases and Insta-CTSR, and inspect InViS's Dataverse grant. These are promising follow-ups, not verified ready imports.
5. Freeze a small behavioral ontology and supplementary-data protocol before large collection. Build authored/staged diagnostic cases immediately without representing them as real incidents; real-data collection waits for ethics/legal/safety clearance.
6. Evaluate a compact multilingual classifier and simple temporal rules separately. No claim of stalking/coercion/escalation support until independently held-out, appropriately contextualized real cases are available.
7. Keep Laya deferred and avoid adding a backend merely to train on private evidence. All original evidence processing remains local by default; only separately authorized de-identified research artifacts leave the device.

## Sources and verification ledger

All sources below were consulted or located on 2 October 2026. Statistics are release/paper-reported unless explicitly marked locally computed. Links to papers are research sources, not implied download permission. Some release terms remain intentionally unresolved.

- **S01:** Davidson original repository, data description, MIT license and bias follow-up: https://github.com/t-davidson/hate-speech-and-offensive-language/ ; data columns: https://github.com/t-davidson/hate-speech-and-offensive-language/blob/master/data/readme.md ; license: https://github.com/t-davidson/hate-speech-and-offensive-language/blob/master/LICENSE . Local CSV statistics computed separately.
- **S02:** Google/Jigsaw card, labels, source and dual rights: https://huggingface.co/datasets/google/jigsaw_toxicity_pred ; official challenge: https://www.kaggle.com/c/jigsaw-toxic-comment-classification-challenge/data ; size/count corroboration, not original grant: https://huggingface.co/datasets/Heliosoph/Jigsaw-Toxic-Comments . Full-label counts still require a chosen-file recount.
- **S03:** OLID paper: https://aclanthology.org/N19-1144.pdf ; data mirror and missing licensing field: https://huggingface.co/datasets/christophsonntag/OLID ; release files described at https://github.com/OceanSnape/OLID . Corpus license not established here.
- **S04:** HateXplain paper: https://arxiv.org/pdf/2012.10289 ; original repo: https://github.com/hate-alert/HateXplain ; card/license/statistics: https://huggingface.co/datasets/Hate-speech-CNERG/hatexplain ; split metadata: https://huggingface.co/datasets/Hate-speech-CNERG/hatexplain/blob/main/README.md . Card/raw count and licensing conflicts retained.
- **S05:** Dravidian shared-task overview: https://aclanthology.org/2021.dravidianlangtech-1.17.pdf ; train subtype counts: https://aclanthology.org/2021.dravidianlangtech-1.44.pdf ; primary CC BY release: https://zenodo.org/records/4750858 ; journal: https://link.springer.com/article/10.1007/s10579-022-09583-7 . Shared-task subsets may have different packaging from the combined sentiment/offense archive; verify chosen files.
- **S06:** MACD paper: https://proceedings.neurips.cc/paper_files/paper/2022/file/a7c4163b33286261b24c72fd3d1707c9-Paper-Datasets_and_Benchmarks.pdf ; datasheet: https://proceedings.neurips.cc/paper_files/paper/2022/file/a7c4163b33286261b24c72fd3d1707c9-Supplemental-Datasets_and_Benchmarks.pdf ; repository: https://github.com/ShareChatAI/MACD ; masked identifiers: https://github.com/ShareChatAI/MACD/blob/main/dataset_meta/README.md ; chronology: https://github.com/ShareChatAI/MACD/blob/main/dataset_chrono/README.md ; authors' indexed license statement: https://openreview.net/forum?id=HCnb1TByvx7 . OpenReview full page required browser verification; license statement available in search result and should be archived from definitive release before use.
- **S07:** HASOC 2019 original task/labels: https://hasocfire.github.io/hasoc/2019/call_for_participation.html ; access: https://hasocfire.github.io/hasoc/2019/dataset.html ; Hindi train counts and 1,319 test variant: https://ceur-ws.org/Vol-2517/T3-8.pdf ; later comparison reporting 1,318: https://aclanthology.org/2025.loreslm-1.34.pdf . Do not use pre-task approximate 8k target sizes as final counts.
- **S08:** Bohra original paper: https://aclanthology.org/W18-1105.pdf ; original ID/text-access repo: https://github.com/deepanshu1995/HateSpeech-Hindi-English-Code-Mixed-Social-Media-Text . Primary paper supports final size, votes, token language and class counts.
- **S09:** HEOT paper/counts: https://aclanthology.org/W18-3504.pdf ; HOT author repo: https://github.com/ramitsawhney27/OffensiveHinglishTweetClassification ; distinguish the ALW paper *Did you offend me?* from the SocialNLP HEOT paper. HOT count corroboration located through https://exa.ai/library/publication/587ym1m6kbq ; current file audit pending.
- **S10:** TRAC organizer data license and task: https://sites.google.com/view/trac1/shared-task ; original repo: https://github.com/kmi-linguistics/trac-1 ; proceedings: https://aclanthology.org/W18-44.pdf ; back-translation derivative example: https://github.com/julian-risch/TRAC-COLING2018 . Organizer data terms take precedence over an unrelated software license assumption.
- **S11:** ICHCL structure/labels: https://hasocfire.github.io/hasoc/2022/ichcl.html ; 2023 access: https://hasocfire.github.io/hasoc/2023/ichcl.html ; 2023 overview/counts: https://ceur-ws.org/Vol-3681/T6-2.pdf ; 2022 overview: https://ceur-ws.org/Vol-3395/T7-1.pdf . Train/test node counts are arithmetic from per-level table components.
- **S12:** Uli paper: https://aclanthology.org/2024.woah-1.16/ ; full paper version: https://arxiv.org/html/2311.09086v3 ; dataset: https://github.com/tattle-made/uli_dataset ; actual license: https://github.com/tattle-made/uli_dataset/blob/main/LICENSE ; current website/lexicon description: https://uli.tattle.co.in/dataset/ . Paper and actual dataset license favored over inconsistent website prose.
- **S13:** 2025 women-targeted Tamil/Malayalam overview: https://aclanthology.org/2025.dravidianlangtech-1.115.pdf ; participant count corroboration: https://aclanthology.org/2025.dravidianlangtech-1.25.pdf ; paper-linked task: https://codalab.lisn.upsaclay.fr/competitions/20701 . Direct data license/test gold not established.
- **S14:** HOLD-Telugu overview: https://aclanthology.org/2024.dravidianlangtech-1.8.pdf ; split corroboration: https://aclanthology.org/2024.dravidianlangtech-1.28.pdf ; task: https://codalab.lisn.upsaclay.fr/competitions/16095 . The 4,500 total and 250/250 test are supported by prose and participant table despite organizer table typo.
- **S15:** BD-SHS original paper: https://aclanthology.org/2022.lrec-1.552.pdf ; author repo: https://github.com/naurosromim/hate-speech-dataset-for-Bengali-social-media ; author data listing and indexed CC BY terms: https://www.kaggle.com/datasets/naurosromim/bdshs . Kaggle full fetch returned no readable body, so archive exact file terms at acquisition.
- **S16:** TB-OLID paper: https://aclanthology.org/2023.banglalp-1.1/ ; full text/counts: https://arxiv.org/html/2311.15023v1 ; original repo/AGPL: https://github.com/LanguageTechnologyLab/TB-OLID . Mixed percentage corrected arithmetically, not silently adopted from table.
- **S17:** MOLD original versions/license: https://github.com/TharinduDR/MOLD ; v1 paper: https://aclanthology.org/2021.ranlp-1.50.pdf ; v2 paper: https://publications.aston.ac.uk/id/eprint/44683/1/2211.12570.pdf . README/paper sizes and annotation-description mismatch retained.
- **S18:** CAD paper: https://aclanthology.org/2021.naacl-main.182/ ; converted corpus fields/counts/CC BY: https://convokit.cornell.edu/documentation/cad.html . Preserve original versus converted thread/label semantics.
- **S19:** CGA-WIKI fields, sizes and collection: https://convokit.cornell.edu/documentation/awry.html . Corpus-specific license review still needed.
- **S20:** CGA-CMV: https://convokit.cornell.edu/documentation/awry_cmv.html ; large corpus and human/machine summaries: https://convokit.cornell.edu/documentation/awry_cmv_large.html . Do not mix summary types or corpus variants without provenance.
- **S21:** AMiCA primary paper and academic-only raw-text conditions: https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0203794 ; searchable manuscript: https://ar5iv.labs.arxiv.org/html/1801.05617 ; replication repository: https://osf.io/rgqw8/ . English/Dutch count/percentage conflict flagged.
- **S22:** Original Instagram study: https://rick1han.github.io/Papers/socinfo2015_labeled.pdf ; access/schema notes: https://github.com/GitHubLuCheng/UCD/blob/master/data/README.md . Later-size variants are not the same 998-session sample.
- **S23:** BullyBlocker primary access page, 2020/2022 and 2026 releases: https://ysilva.cs.luc.edu/BullyBlocker/data ; project: https://ysilva.cs.luc.edu/BullyBlocker/index.html ; Insta-CTSR release: https://dataverse.harvard.edu/dataset.xhtml?persistentId=doi:10.7910/DVN/MUVBRH . Dataverse terms/counts not readable here.
- **S24:** ConvAbuse paper: https://aclanthology.org/2021.emnlp-main.587/ ; counts/consent limitations: https://aclanthology.org/2021.emnlp-main.587.pdf ; author dataset fields/CC BY: https://github.com/amandacurry/convabuse . Public example count excludes unreleased Alana.
- **S25:** SafeCity paper: https://aclanthology.org/D18-1303/ ; full paper: https://ar5iv.labs.arxiv.org/html/1809.04739 ; actual permission condition/splits: https://github.com/swkarlekar/safecity . No silent import authorized.
- **S26:** SHR paper: https://aclanthology.org/P19-1241.pdf ; author release-status listing: https://github.com/arijit1410/ACL2019-YouToo . Current full repo fetch failed; indexed author README says release pending anonymization.
- **S27:** THREAT primary paper: https://cms.simula.no/sites/default/files/publications/files/cbmi2019_youtube_threat_corpus.pdf ; explicit academic/delete-on-request terms: https://github.com/erikve/YouTube-Threat-Corpus ; earlier corpus: https://aclanthology.org/W16-0413.pdf . Updated versus earlier counts distinguished.
- **S28:** InViS paper: https://arxiv.org/pdf/2506.03312 ; DOI: https://doi.org/10.7910/DVN/ANGOX0 ; release: https://dataverse.harvard.edu/dataset.xhtml?persistentId=doi:10.7910/DVN/ANGOX0 . Paper body supplied data description, but release license not verified.
- **S29:** MDMD original paper/statistics: https://aclanthology.org/2024.lrec-main.660.pdf ; reuse in 2025 shared task: https://aclanthology.org/2025.dravidianlangtech-1.81.pdf . Direct source-specific data/media rights unresolved.
- **S30:** PAN12 original task: https://pan.webis.de/clef12/pan12-web/sexual-predator-identification.html ; overview with true decoy provenance and counts: https://ceur-ws.org/Vol-1178/CLEF2012wn-PAN-InchesEt2012.pdf ; archive: https://zenodo.org/records/3713280 . Do not repeat secondary claims that all chats came from police posing as children.
- **S31:** HateCheck original paper: https://aclanthology.org/2021.acl-long.4/ ; final suite/license: https://github.com/paul-rottger/hatecheck-data . Initial and final test counts differ.
- **S32:** MHC paper: https://aclanthology.org/2022.woah-1.15/ ; full paper: https://aclanthology.org/2022.woah-1.15.pdf ; original repo: https://github.com/rewire-online/multilingual-hatecheck ; Hindi author card: https://huggingface.co/datasets/Paul/hatecheck-hindi . Hindi is Latin-script and H, not naturally observed Hinglish.
- **S33:** HateCheckHIn paper: https://aclanthology.org/2022.lrec-1.575.pdf ; original repo/MIT: https://github.com/hate-alert/HateCheckHIn . Authored/template expansion, not LLM text.
- **S34:** ToxiGen paper: https://aclanthology.org/2022.acl-long.234/ ; author HF data: https://huggingface.co/datasets/toxigen/toxigen-data ; generation README: https://github.com/microsoft/ToxiGen ; actual dual software/data license: https://github.com/microsoft/TOXIGEN/blob/main/LICENSE.txt . Human annotations do not convert L origin into R.
- **S35:** NSW police-narrative study: https://link.springer.com/article/10.1186/s40163-024-00200-2 . Restricted data, rule-based behavioral extraction, Australia not India.
- **S36:** Mendeley stalking-labeled candidate, origin unverified: https://data.mendeley.com/datasets/x5rpmydktp/1 . Listing establishes a claimed dataset and CC BY terms, not authenticity/consent or valid stalking gold.
- **S37:** IndicAbusive aggregation: https://github.com/hate-alert/IndicAbusive ; paper: https://dl.acm.org/doi/fullHtml/10.1145/3511095.3531277 . Treat source reuse/bootstrapping separately from new observations.
- **S38:** ACTSA sentiment paper: https://aclanthology.org/W17-5408.pdf . Not abuse-labeled; included to prevent wrong task substitution.
- **S39:** Unverified coercion collections: https://huggingface.co/datasets/haseebakhlaq2000/claude-data ; https://huggingface.co/datasets/codingwithdidemm/AbusivePatterns ; questionable multilingual metadata example: https://github.com/tiya1012/safeguardAI/blob/main/domestic_violence_harmful_discourse_detailed_5000.csv . No positive real-data provenance conclusion drawn.
- **S40:** Government notification summary and phased compliance: https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014 ; MeitY current rules/timeline listing: https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa?pageTitle=Digital-Personal-Data-Protection-Rules-2025686cadad39.pdf . Counsel must consult Gazette/commencement details, not summary wording alone.
- **S41:** India Code, Bharatiya Sakshya Adhiniyam 2023, electronic-record section 63: https://www.indiacode.nic.in/handle/123456789/20063 . No guarantee of admissibility is made.
