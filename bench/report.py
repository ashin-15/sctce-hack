import csv
import json
from collections import Counter
from pathlib import Path

import numpy as np

from .data import LANGUAGES, dump
from .registry import SLOTS
from .scoring import gate, pareto, score_slot


QUALITY = {'classifier': 'macro_f1', 'language': 'accuracy', 'ocr': 'cer', 'stt': 'wer', 'extraction': 'field_f1', 'linking': 'grouping_recall', 'integrity': 'tamper_detected'}
LATENCY = {'classifier': 'ms_per_message', 'language': 'ms_per_message', 'ocr': 'seconds_per_screenshot', 'stt': 'real_time_factor', 'extraction': 'ms_per_thread', 'linking': 'ms_per_1000_messages', 'integrity': 'verify_10000_seconds', 'storage': 'insert_10000_seconds', 'key': 'unlock_seconds', 'pdf': 'render_50_incidents_seconds', 'ingest': 'ms_per_export_fixture'}


def csv_file(path, rows, fields):
    with Path(path).open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fields, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(rows)


def latest_runs():
    latest = {}
    for path in sorted(Path('results/metadata').glob('*.json')):
        metadata = json.loads(path.read_text(encoding='utf-8'))
        key = (metadata['component'], metadata['candidate'], metadata['vad'], metadata['subset_limit'])
        previous = latest.get(key)
        if previous is None or path.name.split('-')[-1] > previous[0].name.split('-')[-1]:
            latest[key] = (path, metadata)
    return list(latest.values())


def audit_results(root=Path('results')):
    runs = []
    for path in sorted((root / 'metadata').glob('*.json')):
        metadata = json.loads(path.read_text(encoding='utf-8'))
        trials = json.loads((root / 'trials' / path.name).read_text(encoding='utf-8'))
        if metadata['warmups'] != 1 or metadata['timed_runs'] != 5 or len(trials) != 5:
            raise ValueError(f'{path.name}: incomplete measurement protocol')
        if not (root / 'predictions' / path.name).exists():
            raise ValueError(f'{path.name}: missing predictions')
        for trial in trials:
            if not np.isfinite(trial['seconds']) or trial['seconds'] < 0 or trial['peak_rss_bytes'] <= 0:
                raise ValueError(f'{path.name}: invalid timing/RSS')
            for metrics in trial['metrics'].values():
                if not all(np.isfinite(value) for value in metrics.values()):
                    raise ValueError(f'{path.name}: non-finite metric')
            if not all(k in trial['device'] for k in ['device_model', 'soc_cpu', 'ram_bytes', 'os_version', 'thermal_state']):
                raise ValueError(f'{path.name}: missing per-run device metadata')
        runs.append({'run_id': path.stem, 'candidate': metadata['candidate'], 'component': metadata['component'],
                     'subset_limit': metadata['subset_limit'], 'protocol_valid': True})
    repaired = 0
    for path in root.glob('*.csv'):
        with path.open(newline='', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            fields = reader.fieldnames
            records = list(reader)
        if not fields or 'metadata_path' not in fields:
            continue
        changed = False
        for record in records:
            if record.get('status') != 'measured':
                continue
            target = root / 'metadata' / (record['run_id'] + '.json')
            if not target.exists():
                raise ValueError(f'{path.name}: measured CSV row lacks committed metadata')
            if record['metadata_path'] != target.as_posix():
                record['metadata_path'] = target.as_posix()
                repaired += 1
                changed = True
        if changed:
            csv_file(path, records, fields)
    dump(root / 'audit.json', {'runs': runs, 'validated_runs': len(runs),
         'metadata_links_repaired': repaired, 'note': 'Only metadata links repaired; measured numeric CSV values unchanged. Audit validates protocol/completeness, not model quality.'})
    return runs


def report():
    audit_results()
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    Path('results/plots').mkdir(parents=True, exist_ok=True)
    rows = []
    weaknesses = []
    snapshots = []
    for path, metadata in latest_runs():
        if metadata['subset_limit']:
            continue
        trials = json.loads((Path('results/trials') / path.name).read_text(encoding='utf-8'))
        predictions = json.loads((Path('results/predictions') / path.name).read_text(encoding='utf-8'))
        snapshots.append(metadata)
        metric = {k: float(np.median([t['metrics']['all'][k] for t in trials])) for k in trials[0]['metrics']['all']}
        slot = 'key' if metadata['candidate'] == 'Argon2id-laptop' else metadata['component']
        quality_name = QUALITY.get(slot)
        quality = metric.get(quality_name) if quality_name else None
        accuracy = max(0., 1. - quality) if quality_name in ['cer', 'wer'] and quality is not None else quality
        latency_name = LATENCY.get(slot)
        row = {'slot': slot, 'candidate': metadata['candidate'] + (' + Silero VAD' if metadata['vad'] else ''),
               'quality_metric': quality_name or 'not measured', 'quality_value': quality,
               'accuracy': accuracy, 'latency_metric': latency_name, 'latency': metric.get(latency_name),
               'peak_ram': metadata['peak_rss_bytes'], 'size': metadata['model_bytes'],
               'integration_effort': None, 'battery_thermal': None, 'licence_openness': None,
               'offline': metadata['offline_verified'], 'licence_commercial': True if metadata['licence_commercial'] == 'yes' else None,
               'run_id': path.stem, 'measurement_kind': 'laptop_proxy'}
        rows.append(row)
        if slot == 'classifier':
            for language in LANGUAGES:
                subset = [p for p in predictions if p.get('language') == language]
                false_positives = sum(not p['truth'] and bool(p['predicted']) for p in subset)
                false_negatives = sum(bool(p['truth']) and not p['predicted'] for p in subset)
                for challenge in ['all', 'veiled_threat', 'victim_distress', 'friendly_joking', 'misspelling']:
                    cases = subset if challenge == 'all' else [p for p in subset if p['challenge'] == challenge]
                    if not cases:
                        continue
                    weaknesses.append({'language': language, 'candidate': row['candidate'], 'challenge': challenge,
                                       'samples': len(cases), 'flag_false_positives': sum(not p['truth'] and bool(p['predicted']) for p in cases),
                                       'flag_false_negatives': sum(bool(p['truth']) and not p['predicted'] for p in cases),
                                       'label_set_errors': sum(set(p['truth']) != set(p['predicted']) for p in cases),
                                       'note': 'synthetic held-out fixture only; tiny per-language/challenge support'})
    ranked = []
    for slot in sorted(set(SLOTS) | {r['slot'] for r in rows}):
        ranked += score_slot([r for r in rows if r['slot'] == slot])
    fields = ['slot', 'candidate', 'quality_metric', 'quality_value', 'latency_metric', 'latency', 'peak_ram', 'size', 'status', 'score', 'measurement_kind', 'run_id']
    csv_file('results/summary.csv', ranked, fields)
    csv_file('results/weaknesses.csv', weaknesses, ['language', 'candidate', 'challenge', 'samples', 'flag_false_positives', 'flag_false_negatives', 'label_set_errors', 'note'])
    text = ['# Slot summaries — laptop proxies only', '', 'Latest full applicable-data run for each candidate/VAD setting. Five batches after one warmup; committed metadata/trials preserve versions and hashes. See component CSVs for all languages and p95.', '',
            'Weighted ranking is withheld when mandatory dimensions or hard gates are unknown. No battery, thermal-throttling or native Android measurements exist. Model bytes exclude runtimes/app dependencies. Not all candidate families have adapters.', '']
    for slot in sorted(set(SLOTS) | {r['slot'] for r in rows}):
        subset = [r for r in ranked if r['slot'] == slot]
        text += [f'## {slot}', '', '| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |', '|---|---|---|---:|---:|---|']
        for row in subset:
            text.append(f"| {row['candidate']} | {row['quality_metric']}: {row['quality_value'] if row['quality_value'] is not None else 'unknown'} | {row['latency_metric']}: {row['latency'] if row['latency'] is not None else 'unknown'} | {row['peak_ram']/1e6:.2f} | {row['size']/1e6:.3f} | {row['score'] if row['score'] is not None else row['status']} |")
        if not subset:
            text.append('| No measurements | unknown | unknown | unknown | unknown | pending |')
        text += ['', 'Top 3: withheld unless complete score inputs and gates exist. Planned options: ' + ', '.join(SLOTS.get(slot, [])), '']
        points = [r for r in subset if r['accuracy'] is not None and r['latency'] is not None]
        fig, ax = plt.subplots(figsize=(9, 6))
        if points:
            for row in points:
                ax.scatter(row['latency'], row['accuracy'], s=max(30, row['peak_ram']/1e6), alpha=.55)
                ax.annotate(row['candidate'], (row['latency'], row['accuracy']), xytext=(5, 5), textcoords='offset points', fontsize=8)
            frontier = sorted(pareto(points), key=lambda r: r['latency'])
            ax.plot([r['latency'] for r in frontier], [r['accuracy'] for r in frontier], '--', color='gray')
            ax.set_xlabel(LATENCY.get(slot, 'latency'))
            ax.set_ylabel('1 - error rate (clipped at 0)' if QUALITY.get(slot) in ['cer', 'wer'] else QUALITY.get(slot, 'quality'))
        else:
            ax.text(.5, .5, 'No comparable accuracy/latency measurements\nNot a ranked shortlist', ha='center', va='center', transform=ax.transAxes)
        ax.set_title(f'{slot}: descriptive laptop proxy; bubble area proportional to process RAM')
        fig.tight_layout()
        fig.savefig(Path('results/plots') / f'{slot}-pareto.png')
        plt.close(fig)
    Path('results/SUMMARY.md').write_text('\n'.join(text).rstrip() + '\n', encoding='utf-8')
    csv_file('results/stack_ranking.csv', [{'stack': s, 'rank': '', 'status': 'pending', 'reason': 'Actual requested native/model components unavailable; not replaced by proxy pipeline'} for s in ['A', 'B', 'C', 'D', 'E']], ['stack', 'rank', 'status', 'reason'])
    manual = []
    for path, metadata in latest_runs():
        if metadata['component'] == 'extraction':
            predictions = json.loads((Path('results/predictions') / path.name).read_text(encoding='utf-8'))
            manual += [{'candidate': metadata['candidate'], 'source_id': p['id'], 'source_citation_valid': '', 'all_claims_supported': '', 'indic_language_correct': '', 'reviewer': '', 'notes': ''} for p in predictions[:30]]
    csv_file('results/summary_review.csv', manual, ['candidate', 'source_id', 'source_citation_valid', 'all_claims_supported', 'indic_language_correct', 'reviewer', 'notes'])
    classifiers = [r for r in rows if r['slot'] == 'classifier']
    best = max(classifiers, key=lambda r: (r['accuracy'], -r['latency'])) if classifiers else None
    winner = f"Best fixture macro-F1 among executed baselines: {best['candidate']} ({best['quality_value']:.4f}). This is not a validated real-world or Android winner." if best else 'No classifier measurements yet.'
    recommendation = f'''# Sakshi recommendation — provisional, not a final stack ranking

## Evidence boundary

{winner}

The requested full candidate matrix and stacks A–E are not complete. All measurements are laptop proxies on authored fixtures/public read speech. Weighted scores/top-three shortlists are deliberately withheld because integration effort, battery/thermal and some model licences remain unverified. Do not infer Android seconds or battery use from these results. See results/SUMMARY.md and results/weaknesses.csv.

## MVP engineering hypothesis (requires validation)

Prefer explicit share/picker/text-export ingestion, serial OCR/STT stages, an independently validated small classifier applied to every message, source-bound template extraction/summaries, encrypted local storage, an externally anchored integrity root, and a mandatory user-confirmation gate before PDF. Start without an LLM; add one only after native memory and exact-quote faithfulness tests. Native ML Kit Latin/Devanagari plus a separately validated Malayalam fallback is a hypothesis, not a measured OCR winner. A phone STT engine is not selected by the extra laptop faster-whisper base test.

The executed TF-IDF/rules baselines are useful regression fixtures but insufficient for harassment decisions. The ensemble may improve recall while increasing false positives; inspect validation-selected thresholds and per-language errors rather than choosing by a single macro-F1.

## Fallback hypothesis

Manual transcript/text import, conservative rules-assisted highlighting, no generative summaries, user-selected verbatim quotes and user-confirmed dates/senders. Never silently drop unflagged evidence or equate negative victim sentiment with harassment. Evidence hashing remains enabled even when ML is disabled. PDF Indic shaping needs visual verification before export is offered to users.

## Riskiest assumptions / data to collect

- English: broader jokes, sarcasm, indirect threats and distress negatives; current rules know the generated templates.
- Hindi: misspelling, dialects, Hindi/English switches, negated/reported threats and native OCR; labels need native review.
- Hinglish: natural speech and spelling/transliteration diversity are absent from the audio set; avoid assuming romanized text is English.
- Malayalam: native-script OCR is not covered by bundled RapidOCR weights; font rendering and STT domain adaptation need review.
- Manglish: natural phonetic spellings and bilingual conversations, including benign uses of trigger vocabulary.
- Mixed: language boundaries, source attribution and quoted speech; semantic parallel-template leakage inflates baseline confidence.
- Current extraction fixtures give date/sender/platform to the extractor; they do not establish recovery of missing/ambiguous metadata.
- Current linking case labels are synthetic arbitrary groups; collect coherent multi-platform sender aliases, time-window boundaries and escalation trajectories.
- Read-speech FLEURS with white noise is not a voice-note harassment benchmark. Natural background noise, code-mixed speakers, and consented in-domain clips remain priorities.
- Public dataset attribution/consent documentation is not a substitute for app-user consent and legal review of intended usage.

## Highest priority real-device re-checks

On a named 6 GB Android phone record SoC, OS, battery state and thermal state for every run. Run one warmup + five repeats; use dumpsys meminfo/Android profiler and batterystats/Battery Historian. Verify serial unloading, 2.5 GB stage hard gate, load/TTFT/decode rates, 30 screenshots + 5 notes, four-GB degraded mode, battery saver, and OS-killed queue recovery. A laptop observed-RSS guard is NOT a four-GB Android cap. Verify offline model availability after cold launch in airplane mode, Keystore key wrapping, review-gated export, Indic font rendering, and native ingestion policy compliance.

## Open work

Implement and test all pending OCR/STT/LID/normalization/neural/embedding/LLM/linking/Android adapters; check exact model identifiers, commercial licences and supported runtime/quant combinations; export family winners to ONNX/TFLite int8 and measure accuracy deltas; manually review 30 LLM summaries; collect missing natural code-mixed audio; run the five actual shortlisted stacks. Pending implementation is not an allowable unsupported-model skip.
'''
    Path('RECOMMENDATION.md').write_text(recommendation, encoding='utf-8')
    print(f'Published {len(rows)} measured candidate/VAD summaries; full weighted rankings remain withheld.')
