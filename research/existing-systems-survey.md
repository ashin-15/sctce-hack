# Sakshi: existing systems, overlap, and differentiation opportunities

Research snapshot: 2 October 2026. This is a state-of-the-art/prior-art landscape survey, not a product ranking, legal opinion, security certification, systematic-review completeness claim, or patent freedom-to-operate assessment.

## Executive findings

1. Abuse documentation, multimedia incident journals, chronological exports, user-controlled sharing, and private storage all have substantial prior art.
2. AI-assisted communication plus preserved records is already present in co-parenting platforms. AI-assisted abuse interpretation, event generation, timelines, document organization, and user approval are described by Aimee Says. AI + evidence + reporting is therefore not a defensible broad novelty claim.
3. Android-local message classification exists in public projects such as Vigil. Agent Hita describes local conversation analysis plus a temporal escalation engine. These are relevant prior art even without independent evidence of production effectiveness.
4. Offline encrypted evidence acquisition exists in Tella. Cryptographic media provenance exists in Proofmode and eyeWitness. Enterprise AI-assisted evidence management exists in Axon Evidence.
5. Temporal cyberbullying modeling predates current LLM products: Soni and Singh (2018), CONcISE (2019), and UCD (2020) explicitly move beyond isolated toxic-message classification.
6. The plausible gap is not an individual feature. It is an evaluated Android workflow combining supported acquisition, offline multimodal analysis, user-correctable temporal patterns, traceable originals and derivatives, and independently verifiable selective exports, particularly for Indic/code-mixed language use.
7. This survey did not establish an independently evaluated, production-ready system satisfying that entire combination. That is an evidence gap, not proof that none exists.

## Scope, method, and interpretation

Search angles covered survivor documentation and safety apps; parental/keyboard moderation; conversation-analysis products; forensic vaults and provenance; Android notification/SMS/accessibility projects; local inference applications; and temporal academic systems. Official documentation, developer repositories, developer interviews, papers, and available patent information were preferred. Search-result extracts were used when official pages were blocked or JavaScript-only; those limitations are recorded below.

The 30 entries include complete products, public prototypes, research methods, and reusable components. A component is not credited with an end-to-end survivor workflow. All claims describe the cited source's documented behavior, not independently verified execution. No app was installed, packet-captured, security-audited, benchmarked on Android, or tested with survivor evidence during this survey. Public-source prototypes and beta products are included as technical claims, not assumed mature deployments.

**Notation:**
- **U**: unknown/not established in reviewed sources. Not equivalent to absent.
- **N/A**: outside the system's documented purpose or not supplied by the component itself.
- **D**: documented feature or explicit developer claim; not a certification.
- **P**: partial, optional, restricted, or indirectly supported.
- **H**: a human can inspect chronology/patterns; automated temporal inference is not established.
- **L**: local storage/inference, as applicable. **C**: remote/cloud service. **L+C**: local plus optional/required remote stages, specified in the row.
- **ND**: launch/publication year not verified. “Documented 2026” is the observation year, not an asserted launch year.
- UI translation is distinguished from evaluated AI-language performance.
- A timestamped list is not an escalation detector. A severity score on one message is not longitudinal analysis. A checksum is not proof of authenticity or admissibility. Encryption in transit is not end-to-end encryption.

# A. Comparison tables

The following three tables use the same IDs. Together they record the requested identity, acquisition, AI, language, timeline, escalation, integrity, privacy, human review, and export fields. Table A4 records limitations and implementation implications for every system. Citations resolve in the source register.

## A1. Identity, target users, inputs, and acquisition

| ID | System; year/status | Organization/authors; platform; source status | Target user and main problem | Inputs and acquisition mechanism |
|---|---|---|---|---|
| 01 | DocuSAFE; 2020; download availability ended 1 Oct 2023 | NNEDV Safety Net + 3Advance; Android/iOS; proprietary app, public help content | Survivors of domestic violence, stalking, sexual assault, harassment; consolidate documentation | Manual incident forms; user-selected screenshots/photos/audio/video/screencasts; in-app photo/video capture. User-mediated, not automatic chat extraction. [S01] |
| 02 | Bright Sky UK; V1 2016, expanded V2 2018; current regional variants | Hestia, Vodafone Foundation, Thames Valley Partnership/TecSOS, Aspirant; Android/iOS; proprietary | People experiencing abuse, friends/family, practitioners; recognition, support, journaling | Manual questionnaires and text/photo/audio/video journals; user initiates capture and emails entries to a chosen address. Not notification/accessibility analysis. [S02] |
| 03 | VictimsVoice; documented 2019; current PWA | VictimsVoice, founder Sheri Kurdakul; browser/mobile PWA; proprietary | Survivors and service providers; structured records relevant to investigations | Manual prompted incident entries and image uploads with metadata; white-label multimedia customization is separately advertised. Current product says nothing stored locally. [S03] |
| 04 | ONRECORD; documented by 2021; launch ND | ONRECORD; Android/iOS/web; proprietary | Individuals and legal/advice professionals; organize repeated incidents and disputes | Manual text/voice records, photos/screenshots, documents, audio/video attachments; automatic record-time/location tagging. Cloud upload, not automatic third-party message collection. [S04] |
| 05 | Arc; launch ND; official material documented 2021 | Domestic Violence Resource Centre Victoria, now Safe and Equal; Transpire/CI&T development; Android/iOS/web documented; proprietary | People experiencing family violence; record events and sense-check denied experiences | Manual diary entries, photos, audio/video uploads; user-mediated cloud record creation. [S05] |
| 06 | Smashboard; 2019 launch reporting; current operation not tested | Smashboard, Noopur Tiwari and team; mobile/network service; proprietary, reusable source not established | Survivors and feminist support networks; private journal and access to experts | Manual notes and user-provided photos/screenshots/documents/audio/video; encrypted timestamped journal. Not automatic messaging-app access. [S06] |
| 07 | myPlan; 2015 research protocol; current app redesigned over time | Johns Hopkins School of Nursing, Nancy Glass and collaborators; Android/iOS/browser; proprietary app | Survivors and supporters; personalized safety decision-making | User answers relationship, danger, and priority questionnaires. Manual self-report, not an evidence collector or passive message detector. [S07] |
| 08 | bSafe; launch ND; current documentation 2026 | bSafe Technology Inc.; Android/iOS + monitoring platform; proprietary | General personal-safety users and organizations; emergency alerts and incident recording | User-triggered SOS, voice or supported button activation; then automatic camera/audio/location recording and live streaming. Not always-on harassment analysis. [S08] |
| 09 | ReThink; concept/patent lineage 2014; app launch ND | ReThink, Trisha Prabhu; Android/iOS keyboard and Chrome extension; proprietary/patented | Young people composing messages; interrupt hurtful posting before sending | Keyboard observes outgoing typed text; Chrome integration handles composition. Automatic analysis after choosing the keyboard, not collection of received conversations. [S09] |
| 10 | Bark; launch ND; current documentation 2026 | Bark Technologies; Android/iOS-related tools, dedicated devices and parent portal; proprietary | Parents/guardians; flag online dangers including bullying and grooming | Automatic monitoring of supported device data and linked accounts: texts, emails, app content, saved media, searches. Exact acquisition path varies by app/device; do not classify all of Bark as notification-only or supported third-party APIs. [S10] |
| 11 | SafeToNet HarmBlock; current product/policy 2025-26; launch ND | SafeToNet; embedded/OEM/device and app integrations, including AI PCs; proprietary | Children, OEMs and platform integrators; prevent harmful sexual visual content exposure/capture/sharing | Automatic transient visual analysis integrated into device/screen/camera/filesystem pathways. Not a generic Play Store app guaranteed to access every Android screen. Historical SafeToNet keyboard is a different product generation. [S11] |
| 12 | OurFamilyWizard + ToneMeter AI/Writing Assistant; platform launch ND; current features documented 2026 | OurFamilyWizard; Android/iOS/web; proprietary | Co-parents and family-law professionals; reduce conflict and retain communications | Messages/files/calendar/journal within its own platform; automatic native message records/read timestamps; AI scans user drafts. No claim of third-party chat scraping. [S12] |
| 13 | TalkingParents; documented since 2012; current features 2026 | Monitored Communications LLC; Android/iOS/web; proprietary | Co-parents/professionals; accountable communication and verified records | Own-platform messages, attachments, calls/video calls, journal/calendar/payment records; automatic call recording/transcription; user-triggered sentiment scans of own messages. [S13] |
| 14 | Aimee Says; publicly reported 2023; current features documented 2026 | Aimee Says Inc., Anne Wintemute and Steven Nichols; browser/web service; proprietary | IPV/coercive-control survivors; interpret communications and organize history | User-written chats/context and uploaded documents/evidence; AI-generated draft events from supplied conversations; no verified automatic Android notification ingestion. [S14] |
| 15 | Tella; 2019 launch; current Android/iOS/desktop ecosystem | Horizontal; Android, iOS, Desktop and Tella Web; open source, Android FOSS flavor | Human-rights defenders, journalists and other at-risk documenters; protected offline evidence collection | In-app camera/audio recording, user file imports and forms; automatic encryption; optional verification metadata at capture; user-controlled server/nearby sharing. [S15] |
| 16 | Proofmode Capture/Android; 2017 public launch, earlier project lineage; 2026 provenance features | Guardian Project, WITNESS, Okthanks and Proofmode Reality Systems; Android/iOS and verification ecosystem; open source | Journalists, activists, ordinary documenters; verifiable media provenance | User camera capture; older Android architecture automatically signs newly observed media; Share Proof/export. Acquisition behavior must be checked against the selected app version. [S16] |
| 17 | eyeWitness to Atrocities; 2015 launch | International Bar Association/eyeWitness + LexisNexis; Android; proprietary, reusable app source not established | Human-rights documenters/investigators; preserve verifiable atrocity footage for legal review | User in-app photo/video/audio capture, notes; automatic capture metadata and custody data; user submits to eyeWitness repository. [S17] |
| 18 | Axon Evidence; launch ND; current AI-enabled product 2026 | Axon; cloud web platform with connected cameras/mobile collection ecosystem; proprietary | Police/investigators and justice agencies; evidence management and disclosure | Automatic ingestion from supported Axon devices, public submissions, manual uploads and third-party integrations; video/audio/photos/files/metadata. Not a consumer local-first vault. [S18] |
| 19 | Notify History (kemalatli/NotifyHistory); ND, repository reviewed 2026 | kemalatli; Android; public source, open-source license not established in inspected page | Android users/developers; preserve received notification history | Automatic NotificationListenerService after notification-access grant; local Room history/filtering. Receives exposed notifications, not private messaging databases. [S19] |
| 20 | Vigil; public Android prototype documented 2026 | kevintheliao and contributors; Android Kotlin/Compose; source available, all rights reserved | SMS recipients; scam/phishing/harassment alerts | Automatic incoming SMS broadcasts via RECEIVE_SMS; onboarding also requests READ_SMS; usage-access/overlay for alerts; local rolling detection log. Not WhatsApp/Instagram acquisition. [S20] |
| 21 | Agent Hita; public Android code documented 2026 | Agent Hita LLC; Android Kotlin; explicitly source available, not open source; commercial license required | People vulnerable to grooming, sextortion, scams and manipulation; local contextual alerts | AccessibilityService reads visible/permitted 1:1 messaging UI; WhatsApp/Instagram/SMS packages listed; group chats excluded; raw text transient. Not notification-based or historical-database extraction. [S21] |
| 22 | Perspective API; 2017 launch; ends 31 Dec 2026, new-access/quota request window ended Feb 2026 | Jigsaw/Google; cloud API; hosted service with public docs/model cards, not complete deployable open-source service | Publishers/platform moderators; score perceived comment toxicity | Caller supplies text to API; automatic scoring, integration acquires content. No Android acquisition or survivor evidence storage. [S22] |
| 23 | Detoxify; repository created 2020, documented model updates 2021; models tied to 2018-20 Jigsaw tasks | Unitary/Laura Hanu and contributors; Python/PyTorch; Apache-2.0 open source with public checkpoints | Developers/researchers; reusable toxicity classifiers | Caller supplies text strings/batches; no native message acquisition. Host application chooses manual/API/import mechanisms. [S23] |
| 24 | Google AI Edge Gallery; public Android app 2025; current repo 2026 | Google AI Edge; Android/iOS; Apache-2.0 app, model licenses separate | Users/developers evaluating mobile GenAI; private local inference | User prompts/chats, chosen images/audio depending on supported model/use case; downloadable or user-loaded models; no passive third-party evidence acquisition. [S24] |
| 25 | CONcISE, Cyberbullying Ends Here; WWW 2019 | Mengfan Yao, Charalampos Chelmis, Daphney-Stavroula Zois, University at Albany; research algorithm; paper public metadata, reusable implementation/license U | Researchers/platform operators; timely repeated-bullying detection | Research Instagram sessions with sequential incoming comments, content/features/timing. Dataset/research streams, not an Android connector. [S25] |
| 26 | Time Reveals All Wounds; ICWSM 2018 | Devin Soni, Vivek Singh, Rutgers; academic analysis; paper, reusable source/license U | Researchers; model temporal differences between bullying and ordinary sessions | Crowd-labeled Instagram media sessions and comment timestamps; retrospective dataset analysis, not live device acquisition. [S26] |
| 27 | UCD, Time-Informed Gaussian Mixture Model; CIKM 2020 | Lu Cheng, Kai Shu, Siqi Wu, Yasin Silva, Deborah Hall, Huan Liu; ASU/IIT/ANU; Python research code, MIT | Researchers; unsupervised session-level cyberbullying detection | Dataset sessions: text, social/network features, comment inter-arrival timing. Offline preprocessing/research evaluation, not Android data collection. [S27] |
| 28 | SafeVoice (ashutosh887/SafeVoice); launch ND; public prototype documented 2026 | ashutosh887 and contributors; Expo/React Native repository; public source, license U | Abuse survivors; voice-led incident documentation and follow-up | User voice narration, guided questions and structured incidents; AI extraction; no verified notification/message connector. [S28] |
| 29 | Laya; release 0.3.23 described in repo reviewed 2026; initial year ND | NandhaKishorM and contributors; Python, ONNX, TypeScript/browser tools; Apache-2.0 repository, checkpoint terms require review | Developers; schema/typed decisions rather than generated prose | Caller supplies state text/JSON and questions; no acquisition layer, evidence vault or survivor workflow. [S29] |
| 30 | PAL / The Abuse Log; iOS V1 beta advertised at research date; launch ND | PAL Strategies Inc.; iOS beta; proprietary | People documenting coercive behavior, stalking, family/workplace/financial disputes | Manual logs and one-tap photo/video/audio/scan capture; connect profiles/files/cases/locations; no documented passive ingestion or AI. [S30] |

## A2. AI, processing locality, language, chronology, and escalation

| ID | AI/model and cloud/local processing | Multilingual support | Timeline and escalation detection |
|---|---|---|---|
| 01 | AI not documented. App incident log; backup uses chosen iCloud/Google/email; sharing requires remote interaction. Complete storage architecture U. | U | Date/content-sorted incident log D; users recognize repetition/escalation H, not a documented detector. |
| 02 | No documented AI; questionnaires/resources. Journal sent through email, not retained as a local vault; official notice says not saved on app/device/Hestia server. | UK UI: English, Welsh, Polish, Urdu, Punjabi; no AI-language claim. | Journaling D; integrated chronological analysis U; questionnaires are not longitudinal escalation detection. |
| 03 | No documented AI. C-hosted PWA; provider explicitly says nothing stored locally. | White-label language/dialect customization D; installed standard-language list U. | Repeated incident records P; exact timeline UI U; automated escalation U. |
| 04 | AI not documented. C database plus mobile/web entry. | U | Calendar/map/timeline D; manual impact ratings and human frequency/severity review H. |
| 05 | AI not documented. C encrypted off-device record storage per official site. | English app listing; other languages U. | Event history/pattern recognition H; automated escalation U. Current availability not tested. |
| 06 | AI not documented. Networked journal; encrypted space advertised, exact client/server plaintext boundaries U. | U; website machine translation is not app/AI language validation. | Timestamped journal D; integrated timeline P; automated escalation U. |
| 07 | Personalized decision aid/questionnaire, not established as an ML classifier/LLM. Native/browser processing and storage boundaries U. | English/Spanish current app; culturally adapted research versions exist. | No evidence-event timeline documented; risk assessment D, but not longitudinal message escalation. Research follow-ups are not an app timeline. |
| 08 | SOS and voice activation; specific AI/model U. L capture plus remote live streaming/distribution; not offline-only. | U | Emergency recording history P; longitudinal harassment/escalation analysis U. |
| 09 | Proprietary offensive-content detector; architecture/weights U. “No data collection” vendor claim; exact inference implementation not independently established. | Current store lists English, Spanish, Hindi, French, Italian, Greek. Per-language detection evaluation U. | Pre-send nudge D; no documented victim timeline or temporal escalation engine. |
| 10 | Proprietary contextual AI/NLP and media analysis; models U. C processing/storage documented; device scanning does not mean all inference stays local. | Exact language coverage and per-language metrics U. | Alert/conversation context D; validated longitudinal escalation method U. |
| 11 | Proprietary on-device visual AI; model U. L transient analysis, no cloud analysis claimed for HarmBlock. | Visual safeguard; language-specific performance N/A/U, not evidence of multilingual text analysis. | Explicitly does not monitor conversations, profile behavior or infer intent; no evidence timeline. |
| 12 | AI sentiment/tone analysis and generative rewrites; models U. C messaging records; precise ToneMeter model execution location U, no verified offline path. | Localized service sites exist; AI-language coverage/metrics U. | Timestamped persistent message history/calendar D; anticipated conflict in draft text is not temporal escalation detection. |
| 13 | Automatic STT and sentiment classification; model/provider U. C hosted communications and records; offline inference U. | STT/sentiment language support U. | Chronological records D; own-message Positive/Neutral/Negative scans D; not documented cross-person escalation analysis. |
| 14 | Domain-instructed/trained LLM; current base/version U. 2026 academic study describes the evaluated Aimee as GPT-based; not necessarily current deployment. C-hosted AI/service, no verified Android-local core. | Language model may accept languages; evaluated product-language coverage U. | AI draft events/timeline, tags and document-pattern analysis D by product docs + founder interview; automated escalation forecasting/validation U. |
| 15 | No harassment AI documented. L encrypted files/forms, optional C submission or fully offline nearby vault transfer. | 25 UI languages on current official site; no classifier-language claim. | File/forms/reports organization P; abuse incident timeline/automated escalation U. |
| 16 | Capture integrity pipeline is cryptographic, not harassment AI. L signing/provenance; optional online notaries/attestation/storage stages. Separate Verify AI-detection tooling must not be conflated with Capture. | App localization U; cryptographic verification language-independent. | Capture/provenance chronology P; harassment escalation N/A. |
| 17 | Human legal review; no documented harassment classifier. L encrypted capture followed by C submission/repository review. | Exact current UI language list U. | Metadata/custody chronology P; longitudinal interpersonal escalation N/A. |
| 18 | AI transcription, auto-tagging/redaction; underlying models U. C evidence and AI platform. | Transcription language coverage depends on service/configuration; not verified here. | Case/evidence/audit chronology D; survivor-oriented behavioral escalation U. |
| 19 | No AI documented. L Room notification cache. | UI/detection U; can store notification strings, which is not multilingual analysis. | Timestamped history D; no documented escalation model. |
| 20 | DistilBERT int8 ONNX, approximately 67 MB, SAFE/SCAM/HARASSMENT; earlier rule scorer retained. L, repo says no INTERNET permission. | Training sources predominantly English; validated multilingual support U. | Rolling 100-entry classification history P; no demonstrated longitudinal escalation engine. |
| 21 | Rules + MediaPipe/Gemma local LLM, model/version U. L analysis; remote config, feedback/telemetry/alert endpoints exist, so do not call whole app network-free. | Validated language coverage/metrics U. | Temporal engine tracks urgency/secrecy/dependency/extraction D (developer description); independent effectiveness U. Risk-event summaries/history P, not preserved original conversation evidence. |
| 22 | Proprietary hosted attribute classifiers; historical English model card describes CNN/GloVe, not a reliable statement of current architecture. C inference even with doNotStore=true. | Toxicity docs list 18 languages including Hindi/Hinglish; other attributes support smaller subsets. No Malayalam/Tamil/etc coverage established. | Individual comments scored; supplied context/session fields do not establish native longitudinal escalation. |
| 23 | BERT-base, RoBERTa-base, XLM-R-base variants; smaller ALBERT models. L/self-hosted inference after fetching assets; Android conversion/benchmarking not supplied. | Multilingual model documented for English, French, Spanish, Italian, Portuguese, Turkish, Russian. Not validated for Indic languages. | No timeline or temporal escalation module in library. |
| 24 | Downloadable supported LLM/multimodal models with LiteRT-related runtimes; model-specific. L inference once assets installed. External skills/tools may create network flows. | Model-dependent; app is not harassment-language validation. | Chat history/context P; no incident/evidence timeline or escalation model. |
| 25 | Two-stage online sequential hypothesis testing/feature evaluation; research computation, not a cloud-dependent API or demonstrated Android deployment. | Evaluated dataset language/domain-specific; multilingual/Indic evaluation U. | Sequential repeated-bullying detection D; early detection, not proof of forecasting severity or physical violence. |
| 26 | Temporal point-process features and dataset comparison/modeling; research computation. | Language-independent timing features do not establish multilingual behavioral accuracy. | Temporal session modeling D; no user-facing timeline or validated interpersonal escalation predictor. |
| 27 | HAN + graph autoencoder representations; GMM energy estimation jointly trained with comment inter-arrival prediction. Local/server research code. | Evaluated Instagram corpus; multilingual support U. | Session temporal features D; native incremental alerting/escalation severity forecasting U. |
| 28 | Gemini reasoning, ElevenLabs voice, Google Cloud storage, Confluent event streaming per README. C-dependent; no offline core established. | Voice/AI-language support U. | Secure timeline and repetition/escalation claimed D; correctness/performance not independently established. |
| 29 | Non-autoregressive encoder decision engine trained with RLCD; choice/score/noul outputs. Repo describes ModernBERT-large 421M English/typed models and mmBERT-base 322M multilingual. L/self-hosted possible; Android deployment U. | 100+ languages claimed; task/language-specific harassment metrics U. General locale benchmarks are not harassment evaluation. | No timeline; score-based outputs do not by themselves implement escalation. |
| 30 | No AI documented. “Local-first” claimed; full sync/network architecture U. | U | Timelines/calendars/maps/connected-event chains D; automated escalation U. |

## A3. Integrity, encryption/privacy, review, and reporting

| ID | Evidence integrity mechanism | Encryption/privacy mechanism | Human review and export/reporting |
|---|---|---|---|
| 01 | Context/type/date fields; cryptographic hashes/signatures U. Activity/login log is not file integrity proof. | PIN/biometrics, failed-login delay, access/activity history; backup/share leakage warned about; at-rest cryptography U. | User authors/contextualizes incidents; chooses some/all content and approves recipient access; print/share D. |
| 02 | Journal material; signatures/hashes U. | No journal retention on app/device/provider server claimed; email is the destination, not E2EE evidence vault. Safety/covert modes; anonymous aggregate usage events collected. | User writes/reviews and selects safe destination email; journal emailing D; verified structured export U. |
| 03 | Full image metadata, structured questions, admissibility-oriented claims; hash/signature implementation U. | Multi-layer authentication/security standards advertised; cloud storage; algorithms/E2EE U. “Nothing stored locally” does not eliminate browser/account traces. | User-controlled reporting/disclosure D; recipient/designated-user workflows; export formats U. |
| 04 | Time/location tagging and preserved attachments; public cryptographic verification mechanism U. | Hosted access controls and confidential messaging; encryption details/provider-readability U. | User labels and impact ratings; selected chronological download/print; professional access with permission D. |
| 05 | Incident/media history; cryptographic seal/custody U. | Official site says encrypted cloud/off-device; delete/reinstall app without losing records; key ownership/E2EE U. | User records and tells story; legal/support sharing intent D; export format/mechanics U. |
| 06 | Timestamped journal D; blockchain-based ledger reported historically, but public reproducible proof protocol U. | Encrypted journal/messaging, pseudo-anonymity, no location tracking/data harvesting claimed; encryption/key design U. | User chooses expert contact/disclosure; journal/evidence organization D; export formats U. |
| 07 | N/A: safety decision aid, not integrity-preserving evidence system. | Anonymous use/no account, PIN, dummy code, changeable icon; exact at-rest encryption U. | User weighs priorities/strategies; personalized plan D; forensic report/export N/A. |
| 08 | Emergency recordings/location; hashes/custody/signatures U. | Remote streaming to guardians/monitoring after activation; encryption/retention details U. | User selects contacts/initiates SOS; live stream/record distribution D; structured chronology report U. |
| 09 | N/A: pre-send prevention, no original evidence vault documented. | Vendor says no data collection; full implementation U. | User sees warning and decides whether to change/send; evidence export N/A. |
| 10 | Alert snippets/context, not documented forensic signing/custody. | Vendor security guide describes encrypted databases/servers/web sessions, TLS/AWS; not E2EE or local-only. Current retention must follow current policy, not old blog alone. | Parent/guardian reviews alerts/context; survivor consent/label-correction workflow not equivalent; forensic export U. |
| 11 | N/A: deliberately transient safeguarding without logging/profiles. | Policy explicitly says local transient processing without personal-data storage/transmission for HarmBlock. | Content prevention may be automatic; evidence label approval/report/export N/A. |
| 12 | Sent messages cannot be edited/deleted; send/first-read timestamps; permanent hosted records. “Tamper-proof” vendor wording is not independently verified cryptographic immutability. | Password protection, hidden notification previews, permissioned professional access; encryption/provider-readability U in sources inspected. | Tone suggestions optional/private; filtered PDF message reports D. Explicitly no report of ToneMeter analysis/use. |
| 13 | Digitally signed PDFs, authentication codes, archived reference records; physical-record watermarks/affidavit options. Verified signing boundary is exported record, not truth of every statement. | Secure hosted communications; exact at-rest/E2EE scheme U in inspected documentation. | User reviews transcript/sentiment and can rate accuracy; scans cannot inspect co-parent's messages; download/email audio/transcripts/PDFs D. |
| 14 | User-supplied documents/events; original-byte hashes, immutable provenance chain and independently verifiable manifests U. AI classifications are not independent factual verification. | No selling/sharing/training-on-user-data claims; provider says employee access restricted; MFA on upgraded accounts. Encryption algorithms, AI-provider boundaries/current retention U; detailed policy page failed to render. | Founder describes review/approve/edit generated events and attach evidence. Document/binder/report/chat PDF workflows described; exact current formats/tier boundaries only partly verified. |
| 15 | Android Verification Mode records file hash, time/device/sensor/location metadata in separate CSV; edits saved as copies; import metadata preservation configurable. | Local at-rest file/database encryption, app locks, TLS required for server connections, screen protection/camouflage; nearby transfer uses mutual TLS. Android Play flavor lists Firebase/Crashlytics; FOSS removes trackers; optional privacy-preserving analytics. | User capture/import/review/forms and chosen sharing; raw export, verification CSV, server reports and nearby transfer. Exported plaintext/third-party app visibility warned about. |
| 16 | Historical SHA-256 + OpenPGP signatures and separate proof metadata; current Capture supports C2PA, official site reports Android conformity May 2026. Online notarization/attestation options are distinct. | Classic design explicitly signing, not encryption. Metadata/device/location can be sensitive; selected version/service privacy must be reviewed. | User controls capture/share; proof bundles and verification tools; no AI-harassment approval workflow. |
| 17 | Capture metadata, chain-of-custody information and preservation of original footage in repository, per official launch partner. Does not make arbitrary imported screenshots authenticated. | Encrypted in-app capture/storage + secure remote repository; anonymity option; legal team access. Current algorithm/key/audit details U. | User adds notes/submits; eyeWitness legal team reviews. Submission to designated organization, not general user-editable report generator. Store warns lost footage cannot be returned to user. |
| 18 | Digital file fingerprints, immutable audit events/UUIDs, evidence-action history, preservation workflows. | Cloud encryption at rest/in transit; roles/permissions, compliance claims. No local-default processing. | Professional review required for AI output; evidence sharing/disclosure and PDF audit exports D; consumer label-review flow N/A. |
| 19 | No documented hashing/signatures; notification history is a derivative observation, not authenticated chat transcript. | Local Room storage D; app-specific encryption/no-network policy U. | User filters/views notifications; export U. |
| 20 | No documented forensic manifest/signature; last-100 detection records in SharedPreferences. | No INTERNET permission claimed; at-rest encrypted log U. No INTERNET limits direct app networking, not every possible IPC/backup/export route. | User sees alerts/history/confidence; correction/approval and evidence export U. |
| 21 | Raw messages deliberately not stored; event scores are not original evidence; sender SHA-256 hashing is not content-integrity proof. | Room + SQLCipher; preferences protection; transient raw text; guardian alert category/severity only with explicit consent. Hashing low-entropy identifiers is not guaranteed anonymity. | Local nudges/check-ins/consented guardian alerts D; evidence-backed report/export U. |
| 22 | N/A: scores do not preserve source evidence. | HTTPS/API service; doNotStore optional and defaults false according to methods docs. Setting true does not prevent cloud inference. | Model card intended for human-assisted moderation, warns against character judgments/fully automated use; score JSON returned, not incident reports. |
| 23 | N/A: caller must preserve originals/results/versioning. | Local inference possible; downloads need network initially; evidence encryption/telemetry behavior depends on host app. | Scores for downstream human triage; no built-in survivor review or report generation. |
| 24 | N/A: model/chat application, not evidence-integrity stack. | Local inference D; model download and external skills require separate network controls; encrypted evidence vault U. | User prompts/compares/retries outputs; no confirmed structured forensic export. |
| 25 | N/A: detection algorithm, not custody system. | Dataset/research compute; Android encryption/consumer privacy mechanisms N/A. | Research labels and performance evaluation; no survivor-review/export UI. |
| 26 | N/A: temporal research, not preserved evidence application. | Research dataset; timing-only features can reduce text requirements, but timestamps/social metadata remain sensitive. | Crowd labeling/statistical analysis; research results, not user-reviewed evidence reports. |
| 27 | N/A: research model, not evidence vault. | Self-run research code; no mobile vault/consent/export security layer. | Dataset labels/evaluation; outputs precision/recall/F1/AUC over replications; no survivor-report workflow. |
| 28 | Original hashing/custody/signatures U despite preservation wording. | README claims encrypted Google Cloud storage; decoy meditation UI, PIN, quick exit. Algorithms/key ownership/audits U. | User voice/follow-up/review intent; chosen timeline export claimed; exact format and implementation completeness U. |
| 29 | Model revision/SHA pinning described; this protects model assets, not evidence. No evidence-integrity stack. | Local inference possible; server/MCP modes also available; host must provide encrypted storage and prevent uploads. | Confidence/calibration/abstention controls, downstream review; typed outputs/ONNX export, not incident report export. |
| 30 | “Capture original first” claim; byte preservation/hashes/signatures/custody U. | Local-first claim, device authentication, Quick Lock, blurred previews/private labels/redaction/location controls; encryption algorithm/E2EE U. | User connects/reviews incidents, selects/excludes/redacts for focused report/export D; formats U, beta implementation not tested. |

## A4. Limitations, lessons, and what Sakshi could do differently

| ID | Major limitations/open questions | What Sakshi can learn | Potential difference, not an asserted novel invention |
|---|---|---|---|
| 01 | Discontinued download; manual capture; cryptographic/local architecture not fully established. | Survivor-controlled documentation and recipient approval matter more than automatic accusations. | Maintain offline incident organization and portable exit/export paths even if service disappears. |
| 02 | Evidence destination is email; safe-account access is crucial; no local AI/vault. Regional features vary. | A local-storage design is not always the safest design for someone with a searched phone. | Offer clearly explained local-vault and explicit secure-transfer options, not automatic email. |
| 03 | Hosted US legal framing; security/admissibility marketing lacks publicly reproducible implementation. | Structured incident questions can improve completeness. | Local structured capture with jurisdiction-neutral factual reports and transparent verification. |
| 04 | Cloud dependence; manual categorization/impact scoring; cryptographic verifier U. | Date-ordered attachments, labels and impact ratings support a course-of-conduct narrative. | Add calibrated local analysis and trace every suggested pattern back to selected source events. |
| 05 | Cloud key/provider boundaries, export/current availability U; not verified AI. | Records support sense-checking without forcing a legal decision. | Offline review and explicit uncertainty; preserve the user's own interpretation separately. |
| 06 | Ledger/key protocol not reproducible; blockchain descriptions are not security proof; current availability U. | Evidence storage can be paired with optional support contacts and pseudo-anonymous access. | Minimize persistent identifiers and implement auditable signatures without unnecessary blockchain/backend. |
| 07 | Manual self-report, not evidence acquisition/classification; decision support is not emergency response. | User priorities, coercion-aware UX and research evaluation are essential. | Connect factual documentation to optional resources without letting AI dictate safety decisions. |
| 08 | Network/guardian dependence, permissions and emergency behavior; no validated long-term pattern detector. | User-triggered recording can reduce missing emergency documentation. | Keep evidence organization useful without streaming or remote contacts; never imply guaranteed emergency response. |
| 09 | Outgoing text only; weights/evaluation details U; not victim documentation. | Nudges preserve agency and can reduce reflexive automated enforcement. | Analyze voluntarily supplied received evidence as well as context, rather than requiring a replacement keyboard. |
| 10 | Broad monitoring/remote exposure, heterogeneous acquisition, parental rather than survivor control; forensic properties U. | Context-rich alerts can be preferable to flooding users with isolated keyword hits. | Survivor owns scope, corrections and export; local-default core without account linking/blanket monitoring. |
| 11 | OEM/embedded access is not normal Android app access; focused on sexual visual content, no behavior timeline. | On-device transient safeguarding is existing prior art, not a Sakshi novelty claim. | Intentionally preserve user-selected originals for documentation instead of discarding all analyzed content. |
| 12 | Own-platform scope; paid/platform dependence; tone outputs not reportable, no proven harassment escalation. | Preserve original sent records and keep draft interventions private. | Support cross-source user imports with source-specific provenance and independent reports. |
| 13 | Own-platform scope; plan restrictions; cannot scan other parent's messages; signatures certify records, not statements. | Digital PDF signatures/authentication codes and transcript-original linkage are mature patterns. | Open offline verifier and local analysis of imported sources, not dependence on both parties using one service. |
| 14 | Cloud dependence; current model/privacy boundaries U; hallucination acknowledged; forensic provenance U. | Automated draft events plus approval/editing, attachments and document-pattern analysis already overlap strongly. | Differentiate through verifiable offline operation, non-destructive derivatives and audited evidence-linked reports, not “AI-generated timeline.” |
| 15 | Not an abuse classifier; export leakage and original-gallery copies; 2023 audit observed temporary plaintext media during processing; current fix status requires version audit. | Offline encrypted capture, configurable metadata, copies for edits and published security limitations. | Add local analytical layers while preserving the vault's security properties and avoiding plaintext OCR/STT/temp leaks. |
| 16 | Signing is not encryption; metadata can identify user/location; older audit disclaimer versus newer C2PA documentation are different scopes. | Sidecar manifests, stable hashes and independently inspectable provenance are established. | Link originals to OCR/STT/labels/report transformations, using existing provenance formats where suitable. |
| 17 | Specialized legal-team submission workflow; remote destination; original recovery restrictions; not general survivor report builder. | Capture-origin metadata and custody need explicit workflow design. | User-controlled evidence packages and offline recovery choices; distinguish imported media from in-app camera capture. |
| 18 | Agency/cloud scale and cost; professional permissions; not survivor-local or private consumer reporting. | Record access, transformation, sharing and AI actions in audit trails. | A minimal local audit graph rather than copying enterprise backend complexity. |
| 19 | Partial notification payloads, app/OS filters, missing historical content; encryption/export U. | Notification listeners plus Room already solve basic local history acquisition. | Capture completeness indicators, consent scopes, deduplication, encrypted evidence and explicit observation provenance. |
| 20 | SMS only; rolling logs; restricted public-source license; held-out scores are not evidence of Indic/contextual real-world accuracy; Play SMS permissions need review. | Small quantized Android classifier is concrete prior art; training/export/evaluation scripts are inspectable. | Support manual multimedia evidence, temporal review and durable integrity-aware archives beyond a per-message alert. |
| 21 | Accessibility coverage/UI fragility and policy constraints; groups excluded; no preserved raw evidence; restrictive license/patent claims; independent validation U. | Local contextual scoring and temporal detection are already described. Data minimization has trade-offs for explainability. | Explicitly retained, user-approved evidence with traceable patterns; favor supported shares/imports/notifications over broad accessibility capture. |
| 22 | Cloud transfer; domain/identity biases; attribute-language variation; imminent sunset. | Human assistance, bias cautions and scoped language support are prior art. | Local, maintained classifier with explicit Indic evaluation and no cloud service dependency. |
| 23 | Comment toxicity is not harassment/coercive control; documented profanity/identity bias; conversion to Android and domain recalibration required. | Reusable baselines and per-language metrics; multilingual architecture does not imply coverage of all languages. | Conversation/context labels and group-safe split evaluation on Sakshi's actual acquisition data. |
| 24 | Hardware/model variability; download sizes; no domain safety validation/vault; external tools can disclose prompts. | Real-device local inference and TTFT/latency benchmarking are demonstrable today. | Strip external tools from sensitive core and test under network denial across mid-range phones. |
| 25 | Research-platform dataset transfer; feature-selection/early-detection performance is not safety outcome validation. | Sequential decisions can trade false alerts against detection delay; large LLM per message unnecessary. | Calibrated mobile streaming event engine that abstains when capture coverage is insufficient. |
| 26 | Session timing studies do not prove causation, escalation severity or transfer to private IPV conversations. | Repetition/bursts/time spacing belong in pattern detection. | Correct for missed captures and preserve evidence-backed event sets rather than overinterpreting frequency. |
| 27 | Old Python 2.7/TensorFlow 1.12 stack; research assumptions/domain; mobile latency/memory U. | Joint time/content/social modeling and session-level evaluation are prior art. | Modern compact temporal representations with explanations and user corrections, benchmarked on Android. |
| 28 | Cloud services contradict offline core; broad README claims not independent evaluation; license/custody U. | Voice-first capture and gentle follow-up can reduce cognitive burden; AI + timeline + escalation already proposed. | Offline STT and template-based completeness checks, preserving narration as source and edits as derivatives. |
| 29 | General decision benchmark not harassment evaluation; 322-421M encoder sizes still require mobile testing; T4/Apple timings not phone timings. Non-generation does not eliminate incorrect decisions. | Typed outputs, calibration, abstention, routing and fine-tuning/export are existing tools. | Validate checkpoint/export parity and Indic task quality before selecting it; separate decision score from evidentiary assertion. |
| 30 | iOS beta, architecture/encryption/integrity details U, no independently verified AI. | Local-first connected case/event records and selective redacted export already exist as product claims. | Deliver measured Android behavior, cryptographic lineage and carefully evaluated local AI, not generic “local evidence log.” |

# B. Feature-by-feature overlap matrix

Column definitions: **Android** = native Android app or documented Android product integration; **Local AI** = evidence/content AI runs on user device, not merely local storage; **Multi-input** = accepts multiple source modalities/files (not just multiple text attributes); **Timeline** = event/communication chronology, not model training sequence; **Temporal** = automated longitudinal/repetition/escalation analysis; **Integrity** = specific hash/signature/custody/unalterable-record mechanism; **Local vault** = protected local evidence repository; **Review** = explicit authoring, selection, correction/approval or human review of records/AI; **Report** = structured chronological/incident/verified report (raw sharing alone is partial).

D and P describe documentation, not independent validation. U never means the product lacks the feature. N/A means the component does not supply it. HarmBlock's automatic visual prevention is not harassment analysis. Academic temporal models do not supply user-facing timelines.

| ID / system | Android | Local AI | Multi-input | Timeline | Temporal | Integrity | Local vault | Review | Report |
|---|---|---|---|---|---|---|---|---|---|
| 01 DocuSAFE | D | N/A | D | D | H | U | P | D | D |
| 02 Bright Sky | D | N/A | D | U | U | U | N/A | D | P |
| 03 VictimsVoice | P (PWA) | N/A | P | P | U | P (metadata only) | N/A | D | D |
| 04 ONRECORD | D | N/A | D | D | H | P (metadata only) | U | D | D |
| 05 Arc | D | N/A | D | P | H | U | N/A | D | P |
| 06 Smashboard | P | N/A | D | P | U | P (timestamp/ledger claim) | U | D | U |
| 07 myPlan | D | N/A | N/A | N/A | N/A | N/A | N/A | D | P (safety plan only) |
| 08 bSafe | D | U | D | P | U | U | U | P | P |
| 09 ReThink | D | U | N/A | N/A | N/A | N/A | N/A | D | N/A |
| 10 Bark | D | U (cloud involved) | D | P | U | U | U | P | U |
| 11 HarmBlock | P (integration) | D (visual) | D (image/video) | N/A | N/A | N/A | N/A | P | N/A |
| 12 OurFamilyWizard | D | U | D | D | U | D (unalterable hosted records) | U | D | D |
| 13 TalkingParents | D | U | D | D | U | D (signed exports) | U | D | D |
| 14 Aimee Says | P (web) | U (cloud service) | D | D | P (patterns, forecasting U) | U | U | D | D |
| 15 Tella | D | N/A | D | P | U | D | D | D | D (forms/server reports) |
| 16 Proofmode | D | N/A (Capture) | D (photo/video) | P | N/A | D | U (signing not vault) | D | P (proof bundle) |
| 17 eyeWitness | D | N/A | D | P | N/A | D | D | D (legal team) | P (submission) |
| 18 Axon Evidence | P (ecosystem) | U (cloud AI) | D | D | U | D | N/A | D | D |
| 19 Notify History | D | N/A | N/A | D | N/A | U | U | P | U |
| 20 Vigil | D | D | N/A | P | U | U | U | P | U |
| 21 Agent Hita | D | D | N/A | P | D (developer claim) | N/A (no originals) | P (risk events only) | D | P (alerts) |
| 22 Perspective | N/A (API) | N/A (cloud API) | N/A | N/A | N/A | N/A | N/A | P (integration) | N/A |
| 23 Detoxify | U (port required) | P (self-hosted, mobile U) | N/A | N/A | N/A | N/A | N/A | P (integration) | N/A |
| 24 AI Edge Gallery | D | D | D (model-dependent) | P (chat only) | N/A | N/A | U | D | U |
| 25 CONcISE | U | U (Android U) | P (feature types) | N/A | D (repetition/early detection) | N/A | N/A | P (research labels) | N/A |
| 26 Time Reveals All Wounds | U | U (Android U) | N/A (timing analysis) | N/A | D (temporal modeling) | N/A | N/A | P (research labels) | N/A |
| 27 UCD | U | U (Android U) | P (text/time/graph) | N/A | D (temporal modeling) | N/A | N/A | P (research evaluation) | N/A |
| 28 SafeVoice | P (cross-platform prototype) | U (cloud dependent) | P (voice + structured text) | D (claim) | D (claim) | U | U | D (claim) | D (claim) |
| 29 Laya | U | P (self-hosted, mobile U) | N/A (text/JSON) | N/A | N/A | N/A (model hashes only) | N/A | P (abstention integration) | N/A |
| 30 PAL | U (only iOS beta advertised) | N/A | D (claim) | D (claim) | U | U | P (local-first claim) | D (claim) | D (claim) |

## Strong overlaps that change Sakshi's positioning

- **Aimee Says:** abuse-oriented AI + user-supplied communications/documents + generated events + timeline + user approval/editing + documentation/binders. Offline execution and forensic byte lineage remain unestablished here, but the functional overlap is substantial.
- **Agent Hita:** Android + local conversation reasoning + temporal risk engine + privacy-minimized event storage + consent-based disclosures. Its deliberate non-retention of raw content is a meaningful distinction from evidence preservation, not grounds to ignore its temporal prior art.
- **Tella:** offline-first acquisition + protected local multimedia vault + verification metadata/hash + selective sharing + safety UX. Local storage, encryption, camouflage and explicit sharing are already established.
- **OurFamilyWizard/TalkingParents:** AI tone/sentiment or transcription + durable own-platform records + chronology + professional reports; TalkingParents adds specific cryptographic PDF verification.
- **Axon Evidence:** multimodal evidence + AI assistance + integrity/custody/audit trails + disclosure. Enterprise/cloud focus differs, but the high-level combination is not new.
- **SafeVoice/PAL:** relevant smaller/beta projects respectively describe AI-led voice documentation/escalation/export and local-first connected evidence records/timelines/redacted exports. Their claims should be validated, but cannot be excluded solely for being early stage.

# C. Capabilities already common

“Common” here means recurring within the relevant surveyed category, not a market-share prevalence estimate across all Android apps.

1. Manual incident journals with date, narrative and attachments: DocuSAFE, VictimsVoice, ONRECORD, Arc, Smashboard, PAL.
2. Photos/screenshots/audio/video as user-provided evidence: DocuSAFE, Bright Sky, Arc, Tella and other documentation products.
3. Chronological record views or reports: ONRECORD, DocuSAFE, co-parenting platforms, Aimee, PAL.
4. User-initiated sharing/export to trusted helpers or professionals: survivor tools, Tella, co-parenting platforms.
5. Password/PIN protection, quick exit or discreet UI: myPlan, Tella and several survivor apps. These are established patterns, not universal guarantees of concealment.
6. Hosted encrypted evidence storage and role-based access: VictimsVoice/Arc claims and Axon; security properties differ substantially.
7. AI toxicity/tone scores, warnings and human moderation: Perspective, Detoxify, ReThink's proprietary detector, Bark, ToneMeter and TalkingParents.
8. Automatic in-platform preservation of messages/calls/read timestamps: co-parenting services.
9. Cryptographic hashes, signatures, audit histories and custody concepts in forensic/provenance systems: Proofmode, eyeWitness, Axon, TalkingParents.
10. Multilingual interfaces and selected multilingual classifiers: Bright Sky, Tella, ReThink, Perspective, Detoxify. Interface localization is not equivalent to broad detection accuracy.
11. Local notification history and local model inference as separate capabilities: Notify History, Vigil, AI Edge Gallery.
12. Human choice and review: optional nudges/rewrites, manually curated records, professional evidence review, AI-event editing.

# D. Capabilities uncommon or poorly evidenced

These are less frequently documented in the reviewed end-to-end survivor tools. Some have substantial research/component prior art.

- **Verified offline end-to-end abuse analysis:** acquisition, OCR/STT, classification, temporal interpretation, report creation and encrypted storage all working without remote AI.
- **Measured temporal escalation on user-mediated private evidence:** published precision/recall, false-alert rates, detection delay, calibration and capture-coverage sensitivity, rather than a generic “patterns” claim.
- **Evidence-linked AI provenance:** every assertion references an original hash plus exact text span/image region/audio interval, with derivative/version history and explicit user approval.
- **Validated Indic and code-mixed coverage:** separate English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish and Romanized/code-mixed metrics, including OCR/STT-induced errors. Laya's broad language claim does not fill this validation gap.
- **Completeness-aware notification analysis:** suppress unsupported temporal conclusions when sources are muted, truncated, grouped, filtered, duplicated, retroactively imported or disconnected.
- **Independently checkable, selective offline report bundles:** meaningful reports plus manifests, source/derivative links, redaction lineage and verifier that does not require a vendor account.
- **Survivor-centered inference safety evaluation:** technology-facilitated-abuse experts and affected users assessing inaccurate, unsafe or escalation-prone guidance, not only generic sentiment accuracy.
- **Local encrypted, versioned corrections that change analytical aggregates without rewriting originals.** Human correction exists; the full correction-to-pattern-to-verifiable-export chain is less evidenced.
- **Public Android battery/RAM/latency/thermal benchmarks on modest hardware** for the entire workflow, not GPU/server inference timings.
- **Recovery and service-exit designs tailored to both evidence retention and coercive phone access.** Secure local capture exists; independently tested recovery under these conflicting goals is less documented.

Not all these capabilities should be included in an MVP. Their value and safety depend on user research and measured feasibility.

# E. Potentially differentiated combinations

| Combination | Established ingredients/closest overlap | What could differentiate the implementation | What must be demonstrated |
|---|---|---|---|
| Supported Android intake + encrypted originals + local multimodal analysis + reviewed temporal reports | Notify History/Vigil + Tella/Proofmode + Aimee/temporal papers | A single offline survivor-owned workflow without third-party private-database access | Network-denied E2E test, capture limitations, cryptographic export verification, phone benchmarks |
| Local temporal reasoning + evidence retention + correction-aware patterns | Agent Hita + Aimee + DocuSAFE/ONRECORD | Preserve opt-in source evidence while keeping privacy/minimal disclosure and correcting false patterns | Traceable event sets, provenance, review UX, false-alert and detection-delay metrics |
| Indic/code-mixed pipeline + acquisition-realistic data + uncertainty-aware reporting | Perspective Hindi/Hinglish; Laya multilingual; local inference runtimes | Per-language, per-modality validation and abstention under OCR/STT uncertainty | Conversation/person/source-separated splits, calibration, code-switch tests, demographic/language error analysis |
| Verifiable original-to-OCR/STT-to-report lineage + selective redaction + offline verifier | Proofmode/Tella + TalkingParents/Axon | Verify the exact exported evidence/derivative relationships without a vendor service | Tamper tests, reproducible manifest verification, redaction leak checks, clear authenticity limits |
| Safety-aware local custody + explicit recovery/transfer + survivor control | myPlan safety UX + Tella nearby sharing + documentation products | Usable choice between local privacy, phone seizure resilience and voluntary backup | Threat-model review, recovery drills, neutral notifications, no surprise recipients or uploads |

These are candidate product combinations, not asserted inventions or patentable combinations. Search absence cannot establish novelty. A fuller novelty exercise would freeze feature definitions and search competing claims, archived versions, patents and academic implementations separately.

# F. Do not claim as novel

Avoid the following unqualified claims:

- “The first app to document domestic abuse, stalking or harassment.”
- “The first multimedia evidence journal/timeline.”
- “The first AI that detects cyberbullying/toxic messages.”
- “The first Android app to analyze messages locally.”
- “The first privacy-first/on-device safety AI.”
- “The first system to detect repeated harassment or use temporal patterns.”
- “The first AI-generated abuse timeline with user review.”
- “The first AI + evidence preservation + reporting system.”
- “Novel encrypted evidence vault/PIN lock/decoy/quick exit.”
- “Novel SHA-256 signatures, custody manifests, audit trails or signed PDF evidence.”
- “Novel OCR/STT/LLM pipeline, multilingual classification, model quantization, hybrid classifier routing, calibration or abstention.” These are established technical techniques even where not present in every surveyed consumer app.
- “Blockchain proves the report is true” or “hashes guarantee court admissibility.” Neither follows from the integrity mechanism.
- “No INTERNET permission guarantees nothing can ever leave the phone.” It blocks ordinary direct networking by that app UID, not other apps, IPC, user export, backup or compromised-device access.
- “Accessibility/notifications give complete access to private messages or View Once media.” They do not.
- “No text generation means no hallucination/no false findings.” Typed encoders can still produce incorrect classifications or scores.

A safer positioning statement is: **“Sakshi is being designed as an Android-first, local-default workflow for user-controlled evidence, uncertainty-aware pattern suggestions and traceable reports. Its performance and security claims will be tied to published tests.”** This states intent, not unverified market exclusivity.

## Patent observations

ReThink officially identifies US Patent 10,250,538 and related patents. Public patent-search extracts connect it to a 2014 priority lineage and later offensive-message detection continuations. Agent Hita publicly claims pending patent applications and links a PATENTS file. Patent ownership, enforceability, claim scope, legal status and freedom to operate were not assessed. The USPTO gazette result discovered during this session returned 404 when fetched; therefore no detailed claim interpretation is offered. Public technical prior art and licensing restrictions should still be considered before implementation.

# G. Ten technically grounded differentiation opportunities

Each opportunity lists existing prior art, a concrete mechanism, a verification target and limitations. These are implementation opportunities, not a winner recommendation or novelty certification.

## 1. Evidence-linked findings with enforceable source references

**Prior art:** Aimee generated events and document analysis; Proofmode/Tella provenance; Axon auditability.

**Mechanism:** Store source UUID, original SHA-256, acquisition type, OCR region or transcript/audio offsets with each proposed finding. Require quoted spans/evidence IDs in schema-validated outputs. Reject references outside stored evidence. Distinguish Observed, Inferred, Pattern and Unknown. Treat source text as untrusted data, not instructions to the model.

**Verification:** Every exported analytical claim resolves to existing approved evidence; invented quotes/IDs and prompt-injected instructions fail adversarial tests. Measure unsupported-claim rate with expert review.

**Limit:** Citation validity does not prove the interpretation is correct. User statements, observed text and legal conclusions must remain distinct.

## 2. Capture-completeness-aware temporal detection

**Prior art:** Notification history; CONcISE sequential detection; Soni timing features; Agent Hita temporal engine.

**Mechanism:** Record capture start/stop, listener connection and permission state, source app, timestamp origin and known gaps. Deduplicate updates to the same notification; do not count repeated imported screenshots as separate incidents. Derive frequency/category transitions only within known observed windows. Label inferred trends as incomplete when coverage changes.

**Verification:** Replay the same dataset with notification loss, grouping, edits, imports and outages; measure false escalation alerts and delay at each coverage level.

**Limit:** Absence of observed incidents is not proof of safety. Android APIs cannot recover content never exposed to the app.

## 3. Original-to-derivative provenance graph

**Prior art:** Tella copy-preserving edits and metadata; Proofmode signatures/C2PA; Axon transformation auditing.

**Mechanism:** Immutable source blobs; separate OCR, STT, normalized text, redacted copies, labels and reports. Record parent hashes, transform/version/settings, creation time and user corrections. Sign export manifests with a device-managed key; do not treat a local key as an independent trusted timestamp authority.

**Verification:** Mutate an original, transcript, relationship edge or report; verifier detects each inconsistency. Re-run documented deterministic transformations where practical.

**Limit:** A graph protects lineage after capture/import; it cannot authenticate a forged screenshot that was imported initially. Deletions and rollback require separate treatment.

## 4. Public Indic/code-mixed robustness evaluation

**Prior art:** Perspective Hindi/Hinglish, multilingual Detoxify, Laya multilingual decisions.

**Mechanism:** Consent/licensing-aware labeled data for the specified Indic languages and Romanization, including context, reclaimed language, jokes, threats without profanity and code-switching. Split by conversation/person/source; preserve original scripts and normalize only derivatives. Include screenshot OCR and audio transcription perturbations.

**Verification:** Report per-language/per-category precision, recall, F1, PR-AUC where appropriate, calibration and abstention coverage with confidence intervals; measure OCR/STT errors separately.

**Limit:** Synthetic samples may supplement tests but must be labeled synthetic and cannot substantiate real-world survivor accuracy.

## 5. Measured cheap-to-expensive local inference cascade

**Prior art:** Typed encoders/Detoxify/Vigil, Laya routing/abstention, mobile runtimes and sequential feature selection.

**Mechanism:** Cheap structural filtering, compact classifier, optional small local LLM for ambiguity/context, then deterministic temporal aggregation. Cache versioned outputs per source hash. Run heavy OCR/STT/summarization in user-initiated or constrained jobs rather than continuous high-compute monitoring.

**Verification:** Compare single-classifier, cascade and always-LLM baselines for error rates, detection delay, p95 latency, peak RAM, joules/battery and thermal behavior on mid-range phones.

**Limit:** Evaluate Laya ONNX/tokenizer/quantization parity on Android before adoption. Repo T4/Apple timings do not establish Android suitability.

## 6. Corrections that propagate into patterns without changing evidence

**Prior art:** Aimee event approval/editing; TalkingParents sentiment feedback; human-assisted moderation.

**Mechanism:** Versioned approve/reject/edit states; retain machine suggestion, user action and current reviewed label separately. Recompute aggregate patterns when labels/dates change. Keep user-authored contextual statements separate from observed source content. Avoid silent on-device fine-tuning that changes previous report meaning.

**Verification:** Relabel/remove/merge an event and confirm all counts, pattern explanations and subsequent exports update consistently while original hashes stay fixed.

**Limit:** Corrections can reflect uncertainty, coercion or changed interpretation; do not turn a user-approved label into legal proof.

## 7. Portable selective export with an independent verifier

**Prior art:** ONRECORD chronological reports, TalkingParents signed PDFs, Proofmode bundles, Axon disclosure.

**Mechanism:** User-selected PDF/HTML report plus machine-readable JSON manifest and chosen originals/derivatives in an encrypted package. Redact only copies, exclude unapproved findings by default, explain omitted evidence. Ship a small offline verifier and documented format without requiring a cloud account.

**Verification:** A fresh offline machine verifies hashes/signatures/lineage; selection and redaction tests check metadata, filenames, PDF layers, thumbnails and OCR text for leakage. Test export after uninstalling the originating app from a separate test device.

**Limit:** A package signature proves who signed specific bytes, not truth or admissibility. Encryption key delivery is an explicit user-controlled step.

## 8. Published threat model and evidence recovery drills

**Prior art:** myPlan safety warnings/dummy code; Tella audits/camouflage/nearby sharing; DocuSAFE PIN loss and backup warnings.

**Mechanism:** State protection boundaries for locked/unlocked phone, shared accounts, malicious keyboards/accessibility apps, root and coercive unlock. Protect originals, derivatives, indexes and caches; control OS backup behavior; allow explicit encrypted transfer to a trusted device. Offer neutral notifications and screen-preview protection without claiming invisibility.

**Verification:** Inspect logs/crash reports/temp files/backups; test permission revocation, interrupted imports, app reinstall and lost-key scenarios using non-sensitive fixtures. Commission external review.

**Limit:** Keystore protects key extraction but not all use of keys on a compromised unlocked device. Key-loss recovery and strong privacy conflict; user must understand chosen recovery policy. Deleting evidence is not a default safety solution.

## 9. Survivor-safe, bounded assistance instead of free-form safety verdicts

**Prior art:** myPlan researched decision aid; Aimee/Ruth; 2026 expert evaluation of TFA LLM responses.

**Mechanism:** Keep core reports factual; use curated/versioned local resources and templates for optional support. Do not predict guilt, advise confrontation or automatically notify contacts. Require explicit recipient/content preview for any disclosure. Use specialist-reviewed advice boundaries and abstain from unsupported recommendations.

**Verification:** Expert review and consented user studies assess accuracy, missing safety caveats, escalation-prone advice, cognitive load and unwanted disclosure. Separate this from classifier performance.

**Limit:** The cited 2026 study tests particular versions and single-turn scenarios, not all future assistants. Nevertheless it documents why empathic fluent output is not sufficient evidence of safety.

## 10. Reproducible longitudinal benchmarks, not a demo-only “risk score”

**Prior art:** CONcISE detection timeliness, UCD session-level modeling, Soni temporal features and existing dataset-labeling practices.

**Mechanism:** Freeze definitions for observed repetition, frequency changes and category transitions; use temporal splits and person/conversation/source-isolated evaluation. Compare per-message classifier, count-window rules and temporal models. Preserve annotation disagreement and event coverage. Publish model/export versions and a synthetic fixture replay harness separately from licensed real data.

**Verification:** Report event/pattern precision and recall, false alerts per observed week, detection delay, calibration, abstention and resource use. Test reproducibility after model updates and quantify confidence intervals.

**Limit:** Demonstrating a repeated-text pattern is not predicting physical harm, causation or legal harassment. Avoid a single scalar “danger probability” without calibration and a defined outcome.

# Android acquisition implications

1. **Notifications:** NotificationListenerService receives posted/removed notifications exposed by Android. It is not a messaging database API. Missing previews, grouped/updated notifications, app choices, work profiles and disconnected listeners limit coverage. Android 15 additionally redacts detected OTP content for untrusted notification listeners. [S31]
2. **Shares/imports:** ACTION_SEND/ACTION_SEND_MULTIPLE, SAF/Photo Picker and user-exported files are defensible acquisition pathways. Preserve original bytes and acquisition metadata separately from preprocessing.
3. **SMS:** Android SMS permissions and Play policy differ from ordinary file import. A sideloaded working SMS receiver is not proof of Play distribution eligibility.
4. **Accessibility:** Agent Hita is evidence of an implementation approach, not proof of completeness, policy eligibility or safety on every app/device. For Sakshi, prefer explicitly scoped supported/user-mediated workflows; do not replicate silent broad scraping or access-control circumvention.
5. **WhatsApp View Once:** None of these sources establishes a legitimate mechanism to extract protected View Once payloads. An exposed notification or user-described event is a different evidence type from the underlying media. No bypass is recommended or researched.
6. **Actual device validation:** Measure ingestion and offline pipeline behavior on real Android devices/app versions; list unavailable surfaces honestly. Do not infer Android readiness from Python/desktop compatibility.

# Uncertainty, conflicts, and follow-up gaps

- **DocuSAFE:** old feature documentation remains online, but official NNEDV update establishes download discontinuation. It is prior art, not a current deployment recommendation.
- **Perspective:** historical feature/model documentation persists, but the live official homepage establishes sunset and request cutoff. Use as prior art/baseline concept, not a new core dependency.
- **SafeToNet:** older keyboard privacy documents describe collected data/safety indicators; current HarmBlock policy describes transient visual AI without profiling. Do not transfer claims between generations.
- **Proofmode:** older Guardian page describes SHA/PGP sidecars and says no full audit/court testing at that time; current site advertises C2PA conformity in 2026. Conformity is not a full security/legal audit and does not retroactively prove old versions safe.
- **Tella:** current documentation distinguishes Android Play, FOSS and iOS analytics/tracker behavior. A 2023 audit noted temporary plaintext media; current-version remediation requires further source/version testing. Do not claim it never writes plaintext at any processing stage without testing.
- **VictimsVoice:** 2019 reporting described Android/web and image-limited inputs; current official product is PWA with nothing stored locally and separately customizable white-label multimedia. Historical and current capabilities differ.
- **Aimee:** official FAQ/documents establish LLM and document-pattern functions; timeline approval/editing/export details also come from founder interviews. Fine-grained current model architecture, retention, AI-subprocessor boundary and cryptographic evidence preservation remain unresolved. No user account was created to verify paid tiers.
- **Vigil/Agent Hita:** public repositories provide unusually specific mechanisms, but README claims are not security audits. Both are restrictively licensed, not open source. Agent Hita's endpoint configuration means local inference cannot be equated with a network-free app.
- **Smashboard/Arc:** credible historical/official descriptions exist; current operational availability and exact export/key details were not verified.
- **SafeVoice/PAL:** prototype/beta features are cited as developer claims; no inference of mature deployment, legal reliability or independent effectiveness.
- **Laya:** general multilingual/calibration/ONNX tooling is documented; no Android harassment benchmark established. Broad language support and no generated prose do not guarantee correct findings.
- **Research:** Instagram cyberbullying datasets/public-comment sessions differ from private IPV/coercive-control evidence. Legacy code or published accuracy does not demonstrate real-world Android efficacy. CONcISE full publisher fetch was blocked; no reproduction or complete code audit was performed.
- **Patents:** only specific relevant references were identified, not an exhaustive claim search. No conclusion on patentability/infringement is made.

# Primary-source and supporting-source register

Where an ID has several links, each supports a different field. “Extract” means the search index supplied content but fetching was blocked/failed. No source is treated as an independent security certification merely because it is official.

- **S01 DocuSAFE:** [NNEDV launch and discontinuation](https://nnedv.org/latest_update/nnedv-launches-docusafe-new-evidence-collection-app-survivors/); [developer-hosted user documentation](https://3advance.github.io/NNEDV-Content/intro-to-docusafe.html).
- **S02 Bright Sky:** [current Hestia privacy notice](https://www.hestia.org/bright-sky-privacy-policy); [Hestia product](https://www.hestia.org/brightsky); [original developer/partner guide](https://www.hestia.org/Handlers/Download.ashx?IDMF=b4e6bf46-2685-4b65-a563-a4190f31473f) (extract); [Vodafone launch history](https://www.vodafone.co.uk/newscentre/press-release/bright-sky-launches/) (extract).
- **S03 VictimsVoice:** [official current product](https://victimsvoice.app/the-app/); [official 2019 announcement](https://victimsvoice.app/domestic-violence-survivor-gives-metoo-victims-a-legal-voice/) (extract); [2019 product reporting](https://technical.ly/startups/victimsvoice-sheri-kurdakul-app-digital-diary-domestic-abuse-victims-survivors/) (supporting extract).
- **S04 ONRECORD:** [official product](https://www.myonrecord.com/); [stalking documentation/impact ratings](https://www.myonrecord.com/how-to-guides/how-to-prove-stalking/).
- **S05 Arc:** [official app information](https://download.arc-app.org.au/) (extract); [original developer case study](https://ciandt.com/au/en-au/case-study/empowering-women-take-control-their-family-violence-story-dvrcv) (extract); [developer app listing](https://apps.apple.com/au/app/arc-app/id1445375459) (extract).
- **S06 Smashboard:** [official features](https://site.smashboard.org/our-app); [historical founder interviews/launch reporting](https://www.reuters.com/article/world/encrypted-app-aims-to-boost-sex-crime-reporting-in-india-idUSKBN1Z720R/); [2019 launch reporting](https://qz.com/india/1758197/after-metoo-an-app-now-uses-blockchain-against-sexual-abuse) (supporting extracts).
- **S07 myPlan:** [official app](https://myplanapp.org/); [Glass et al., 2015 protocol](https://pmc.ncbi.nlm.nih.gov/articles/PMC4563945/).
- **S08 bSafe:** [official product](https://www.getbsafe.com/product) (extract); [developer Android listing](https://play.google.com/store/apps/details?id=com.bipper.app.bsafe&hl=en) (extract).
- **S09 ReThink:** [official product/privacy FAQ](https://rethinkwords.com/getrethink) (extract); [official explanation](https://www.rethinkwords.com/whatisrethink); [developer Android listing and languages](https://play.google.com/store/apps/details?hl=en&id=com.rethink.app.rethinkkeyboard) (extract); [patent list](https://www.rethinkwords.com/rethinkListOfAppRelatedPatents); [patent attorney account](https://www.americanbar.org/groups/center-pro-bono/publications/pro-bono-exchange/2022/patentprobonoworkprotectstechnologyaimedatendingcyberbullying/) (extract).
- **S10 Bark:** [official monitoring flow](https://www.bark.us/how/); [Android coverage](https://www.bark.us/what-bark-monitors/android-devices/); [privacy policy](https://www.bark.us/privacy/) (extract); [security guide](https://www.bark.us/blog/privacy-security-guide-bark/); [subprocessors](https://www.bark.us/privacy-subprocessors/) (extracts).
- **S11 SafeToNet:** [current HarmBlock product](https://safetonet.com/); [current policy](https://safetonet.com/privacy-policy/); [Intel integration](https://safetonet.com/harmblock/intel/); [historical keyboard policy](https://safetonet.com/privacy-policy-simplified/).
- **S12 OurFamilyWizard:** [persistent messages and PDF reports](https://www.ourfamilywizard.com/product-features/messages); [ToneMeter AI](https://support.ourfamilywizard.com/hc/en-us/articles/36058984807053-What-is-ToneMeter-AI); [no tone-analysis reports](https://support.ourfamilywizard.com/hc/en-us/articles/34526745538061-ToneMeter) (help-page extracts).
- **S13 TalkingParents:** [unalterable records/signatures](https://talkingparents.com/features/unalterable-records); [automatic recorded/transcribed calls](https://talkingparents.com/features/accountable-calling); [Sentiment Scanner limitations and feedback](https://support.talkingparents.com/hc/en-us/articles/38689078511383-How-to-use-Sentiment-Scanner); [transcript/recording export](https://support.talkingparents.com/hc/en-us/articles/25952341971095-How-do-I-get-my-Calling-transcripts-and-recordings) (help-page extracts).
- **S14 Aimee Says:** [official FAQ, LLM/privacy/MFA/cautions](https://www.aimeesays.com/en/guide-to-aimee?topic=faq); [official document-pattern analysis](https://www.aimeesays.com/en/documents); [founder interview: automatic events, approval/editing, attachments, timeline and cross-document work](https://kateanthony.com/podcast/episode-353-aimee-says-updates-how-women-are-documenting-abuse-in-real-time-with-anne-wintemute/); [founder interview and chat PDF export](https://www.flyingfreenow.com/302-2/); [public 2023 reporting](https://www.kkco11news.com/2023/10/17/ai-powered-app-designed-help-domestic-violence-victims/).
- **S15 Tella:** [official app](https://tella.app/); [features and Verification Mode](https://tella.app/features); [security/privacy, flavor distinctions and limits](https://tella.app/security-and-privacy); [source repositories/FOSS](https://tella.app/open-source); [2019 launch history](https://blog.wearehorizontal.org/tella-hits-a-milestone-100-000-downloads-and-counting/); [2023 audit](https://tella-app.org/assets/files/2023.05%20-%20Tella%20security%20audit%20-%20Final%20report-bbed05fe1d6a3d0d6302db320fef3687.pdf) (indexed extract).
- **S16 Proofmode:** [current ecosystem/C2PA conformity announcement](https://www.proofmode.org/); [classic goals and signature-not-encryption distinction](https://guardianproject.info/apps/org.witness.proofmode/); [Android repository](https://github.com/guardianproject/proofmode-android); [2017 developer launch post](https://guardianproject.info/2017/02/24/combating-fake-news-with-a-smartphone-proof-mode/).
- **S17 eyeWitness:** [original IBA/LexisNexis launch and architecture](https://www.lexisnexis.com/community/pressroom/b/news/posts/international-bar-association-launches-mobile-app-that-captures-verifiable-images-to-aid-prosecution-of-human-rights-atrocities); [current developer listing and recovery caution](https://play.google.com/store/apps/details?id=com.camera.easy&hl=en) (extract).
- **S18 Axon Evidence:** [official current product](https://www.axon.com/products/axon-evidence); [evidence audit trail](https://www.axon.com/help/axon-evidence/software/axon-evidence/audit-trail/audit-trail.htm); [transcription and human oversight](https://www.axon.com/help/axon-auto-transcribe/software/auto-transcribe/transcript-requests.htm) (help-page extracts).
- **S19 Notify History:** [developer repository](https://github.com/kemalatli/NotifyHistory).
- **S20 Vigil:** [developer repository, model pipeline and permissions](https://github.com/kevintheliao/Vigil); [restrictive license](https://github.com/kevintheliao/Vigil/blob/main/LICENSE).
- **S21 Agent Hita:** [developer repository, acquisition/temporal/privacy/license descriptions](https://github.com/Agent-Hita/AgentHitaAndroid).
- **S22 Perspective:** [official sunset notice](https://www.perspectiveapi.com/); [attributes/languages](https://developers.perspectiveapi.com/s/about-the-api-attributes-and-languages); [doNotStore/context methods](https://developers.perspectiveapi.com/s/about-the-api-methods) (indexed extracts; fetched pages rendered errors); [English model card and uses to avoid](https://github.com/conversationai/perspectiveapi/blob/main/model-cards/English/toxicity.md) (extract); [2017 Google launch](https://blog.google/innovation-and-ai/products/when-computers-learn-swear-using-machine-learning-better-online-conversations/) (extract).
- **S23 Detoxify:** [official repository/models/languages/bias cautions](https://github.com/unitaryai/detoxify); [2021 multilingual update](https://github.com/unitaryai/detoxify/releases/tag/v0.4.0); [Apache-2.0 license](https://github.com/unitaryai/detoxify/blob/master/LICENSE).
- **S24 AI Edge Gallery:** [official repository and current model-dependent use cases](https://github.com/google-ai-edge/gallery); [historical local-first README snapshot](https://github.com/google-ai-edge/gallery/blob/ebb605131dc18145395526d2c0150c1656799a86/README.md) (extract); [official release 1.0.0, 20 May 2025](https://github.com/google-ai-edge/gallery/releases/tag/1.0.0).
- **S25 CONcISE:** Yao, Chelmis, Zois, *Cyberbullying Ends Here: Towards Robust Detection of Cyberbullying in Social Media*, WWW 2019, [publisher DOI](https://dl.acm.org/doi/10.1145/3308558.3313462) (fetch blocked); [authors' conference lightning-talk abstract](https://psycnet.apa.org/doi/10.1145/3308560.3316474) (indexed publisher/metadata extract).
- **S26 Soni/Singh:** *Time Reveals All Wounds: Modeling Temporal Characteristics of Cyberbullying*, ICWSM 2018, [author-hosted paper](https://sites.comminfo.rutgers.edu/vsingh/wp-content/uploads/sites/35/2020/02/Soni_ICWSM.pdf) (indexed readable abstract; fetch produced binary PDF).
- **S27 UCD:** Cheng, Shu, Wu, Silva, Hall, Liu, CIKM 2020, [author implementation, MIT and architecture](https://github.com/GitHubLuCheng/UCD); [author-hosted paper](https://ysilva.cs.luc.edu/publications/UCD-CIKM2020.pdf) (indexed readable abstract; fetch produced binary PDF); [publisher metadata](https://dl.acm.org/doi/10.1145/3340531.3411934) (extract).
- **S28 SafeVoice:** [developer repository and cloud technology list](https://github.com/ashutosh887/safevoice).
- **S29 Laya:** [actual repository, checkpoint architecture, typed outputs, calibration/abstention/fine-tuning/ONNX and licensing](https://github.com/NandhaKishorM/laya).
- **S30 PAL:** [official beta/product description](https://abuselogapp.com/).
- **S31 Android:** [NotificationListenerService API](https://developer.android.com/reference/android/service/notification/NotificationListenerService); [Android 15 all-app behavior changes, sensitive-notification restrictions](https://developer.android.com/about/versions/15/behavior-changes-all) (official indexed extracts).
- **S32 Temporal follow-on:** Gupta, Yang, Sivakumar, Silva, Hall, Nardini Barioni, *Temporal Properties of Cyberbullying on Instagram*, 2020, [NSF public-access record](https://par.nsf.gov/biblio/10196267-temporal-properties-cyberbullying-instagram); [author paper](https://ysilva.cs.luc.edu/BullyBlocker/documents/tempcb-cybersafety20.pdf) (extracts). This descriptive/burst analysis provides additional temporal prior art beyond the three comparison rows.
- **S33 TFA assistant evaluation:** Prakash, Almansoori, Hu, Chatterjee, Huang, 2026, [abstract](https://arxiv.org/abs/2602.17672); [full HTML](https://arxiv.org/html/2602.17672v1). Study evaluates GPT-4o, Claude 3.7, Aimee and Ruth under specified single-turn protocols; identifies inaccurate/incomplete advice and missing safety warnings. It does not establish comparative accuracy for Sakshi's classifiers or the current versions of these services.

## Concrete implementation implications

Build claims around observable behavior: supported intake on real Android devices; immutable user-selected originals; encrypted derivatives; schema-constrained local suggestions; reviewed temporal aggregates; explicit export and independent verification. Validate language/domain performance, survivor-safety failure modes and resource consumption separately. The meaningful opportunity is rigorously demonstrated integration and trustworthy boundaries, not a declaration that evidence journals, local AI or temporal harassment analysis are new.
