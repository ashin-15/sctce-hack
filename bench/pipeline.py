import gc
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import numpy as np

from .data import LABELS, digest, dump, load


class Queue:
    def __init__(self, path):
        self.conn = sqlite3.connect(path)
        self.conn.execute('PRAGMA journal_mode=WAL')
        self.conn.execute('PRAGMA synchronous=FULL')
        self.conn.execute('CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, status TEXT NOT NULL, source_hash TEXT NOT NULL)')
        self.conn.execute('CREATE TABLE IF NOT EXISTS receipts (id TEXT PRIMARY KEY, output_hash TEXT NOT NULL)')
        self.conn.commit()

    def enqueue(self, job_id, source_hash):
        with self.conn:
            self.conn.execute('INSERT OR IGNORE INTO jobs VALUES (?, ?, ?)', (job_id, 'pending', source_hash))
        existing = self.conn.execute('SELECT source_hash FROM jobs WHERE id=?', (job_id,)).fetchone()[0]
        if existing != source_hash:
            raise ValueError('Job identity reused with altered source')

    def recover(self):
        with self.conn:
            self.conn.execute("UPDATE jobs SET status='pending' WHERE status='running'")

    def claim(self):
        self.conn.execute('BEGIN IMMEDIATE')
        row = self.conn.execute("SELECT id,source_hash FROM jobs WHERE status='pending' ORDER BY id LIMIT 1").fetchone()
        if row:
            self.conn.execute("UPDATE jobs SET status='running' WHERE id=?", (row[0],))
        self.conn.commit()
        return row

    def finish(self, job_id, output_hash):
        with self.conn:
            self.conn.execute('INSERT OR IGNORE INTO receipts VALUES (?, ?)', (job_id, output_hash))
            self.conn.execute("UPDATE jobs SET status='complete' WHERE id=?", (job_id,))

    def close(self):
        self.conn.close()


def export_allowed(records):
    return bool(records) and all(r.get('user_confirmed') is True for r in records)


def recovery_test():
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / 'queue.sqlite'
        queue = Queue(path)
        for i in range(5):
            queue.enqueue(f'fake-{i}', hashlib.sha256(f'fake-{i}'.encode()).hexdigest())
        queue.close()
        result = subprocess.run([sys.executable, '-m', 'bench.pipeline', 'crash', str(path)], capture_output=True)
        if result.returncode != 73:
            raise AssertionError('Crash injection did not occur at expected running-job checkpoint')
        queue = Queue(path)
        running = queue.conn.execute("SELECT COUNT(*) FROM jobs WHERE status='running'").fetchone()[0]
        queue.recover()
        while row := queue.claim():
            queue.finish(row[0], row[1])
        completed = queue.conn.execute("SELECT COUNT(*) FROM jobs WHERE status='complete'").fetchone()[0]
        receipts = queue.conn.execute('SELECT COUNT(*) FROM receipts').fetchone()[0]
        queue.close()
        assert running == 1 and completed == 5 and receipts == 5
        return {'interrupted_running_jobs': running, 'resumed_completed_jobs': completed, 'unique_receipts': receipts,
                'scope': 'actual laptop child-process exit mid-job, synthetic hash queue only; not Android app lifecycle recovery'}


def proxy_trial(ram_guard=None):
    from Crypto.Cipher import AES
    from .adapters import Classifier, Language, OCR, STT, chain, link_rows
    from .runner import RSS
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer
    from xml.sax.saxutils import escape
    screens = [r for r in load('data/screenshots.jsonl') if r['split'] == 'test' and r['language'] not in ['hi', 'ml']][:30]
    audio_manifest = load('data/audio.jsonl')
    audio = []
    for language, noise in [('en', 'original_recording'), ('hi', 'original_recording'), ('ml', 'original_recording'), ('hi', 'white_noise_10dB'), ('ml', 'white_noise_10dB')]:
        audio.append(next(r for r in audio_manifest if r['language'] == language and r['noise'] == noise))
    originals = {r['id']: r for r in load('data/text.jsonl')}
    stages = []
    records = []
    start_all = time.perf_counter()
    with RSS() as overall:
        def stage(name, operation):
            started = time.perf_counter()
            with RSS() as memory:
                result = operation()
            if ram_guard and memory.peak > ram_guard * 1_000_000_000:
                raise MemoryError('Proxy observed RSS guard exceeded')
            stages.append({'stage': name, 'seconds': time.perf_counter() - started, 'peak_rss_bytes': memory.peak,
                           'ram_scope': 'process cumulative high-water; allocations are not isolated by stage'})
            return result
        hashes = stage('ingest-hash', lambda: [digest(r['path']) for r in screens + audio])
        def ocr_stage():
            engine = OCR()
            for row in screens:
                result = engine.engine(row['path'])
                text = '\n'.join(result.txts) if result.txts else ''
                original = originals[row['message_id']]
                records.append({'id': row['id'], 'text': text, 'sender': 'unconfirmed-ocr-sender',
                                'timestamp': '2025-01-01T00:00:00Z', 'platform': row['platform'],
                                'truth': original['labels'], 'metadata_status': 'not extracted; placeholders require review'})
            del engine
            gc.collect()
        stage('OCR', ocr_stage)
        def stt_stage():
            engine = STT(False)
            for row in audio:
                text = engine.transcribe(row['path'], row['language'])
                records.append({'id': row['id'], 'text': text, 'sender': 'unknown-audio-speaker',
                                'timestamp': '2025-01-01T00:00:00Z', 'platform': 'voice-note', 'truth': [],
                                'metadata_status': 'timestamp/speaker unconfirmed'})
            del engine
            gc.collect()
        stage('STT', stt_stage)
        stage('language-none-normalization', lambda: [r.update(language=Language.identify(r['text'])) for r in records])
        def classification():
            classifier = Classifier('rules-tfidf-ensemble')
            probabilities = classifier.scores([r['text'] for r in records])
            for i, record in enumerate(records):
                record['labels'] = [l for j, l in enumerate(LABELS) if probabilities[i, j] >= classifier.thresholds[j]]
                record['uncertain'] = bool(np.any(np.abs(probabilities[i] - classifier.thresholds) < .1))
            del classifier
            gc.collect()
        stage('rules-classifier-all', classification)
        flagged = [r for r in records if r['labels'] or r['uncertain']]
        stage('LLM-disabled-template', lambda: [r.update(quote=r['text'], summary=f"Source {r['id']} contains an unconfirmed message. User review is required. No incident facts have been inferred.") for r in flagged])
        def store():
            key = os.urandom(32)
            ciphertexts = []
            for i, record in enumerate(records):
                cipher = AES.new(key, AES.MODE_GCM, nonce=os.urandom(12))
                cipher.update(record['id'].encode())
                ciphertext, tag = cipher.encrypt_and_digest(json.dumps(record, ensure_ascii=False).encode())
                ciphertexts.append((cipher.nonce, ciphertext, tag))
            return ciphertexts
        ciphertexts = stage('ephemeral-encrypted-store', store)
        stage('linking', lambda: link_rows(records))
        stage('user-review-blocks-export', lambda: export_allowed(records))
        assert not export_allowed(records)
        def synthetic_preview():
            font = Path('C:/Windows/Fonts/Nirmala.ttc')
            pdfmetrics.registerFont(TTFont('PipelineIndic', str(font), subfontIndex=0, shapable=True))
            style = ParagraphStyle('body', fontName='PipelineIndic', fontSize=11, leading=18, shaping=True)
            story = [Paragraph('SYNTHETIC BENCHMARK PREVIEW — not a user-confirmed complaint', style)]
            for record in flagged:
                story += [Paragraph(escape(record['id'] + ': ' + record['text']), style), Spacer(1, 8)]
            with tempfile.TemporaryDirectory() as tmp:
                SimpleDocTemplate(str(Path(tmp) / 'preview.pdf')).build(story)
        stage('synthetic-preview-PDF-not-export', synthetic_preview)
    y = np.array([bool(r['truth']) for r in records[:len(screens)]])
    predicted = np.array([bool(r['labels']) for r in records[:len(screens)]])
    tp = int((y & predicted).sum())
    quote_errors = sum(r['quote'] not in r['text'] for r in flagged)
    return {'seconds': time.perf_counter() - start_all, 'peak_rss_bytes': overall.peak, 'stages': stages,
            'screenshots': len(screens), 'voice_notes': len(audio), 'screen_flag_precision': tp / max(1, int(predicted.sum())),
            'screen_flag_recall': tp / max(1, int(y.sum())), 'hallucinated_quote_rate_vs_recognized_text': quote_errors / max(1, len(flagged)),
            'accuracy_scope': 'screens only; public read speech has no harassment annotations and is excluded from flag accuracy',
            'source_integrity_root': chain([bytes.fromhex(h) for h in hashes]).hex(),
            'scope': 'extra proxy-P0 only; OCR Latin-only, no LLM, no metadata extraction, ephemeral encrypted store, no escalation, no real user/PDF export; not stacks A–E',
            'source_quote_note': 'Quotes copy recognized text exactly, not necessarily original evidence: OCR/STT errors remain.',
            'encrypted_records': len(ciphertexts)}


def run_stacks(args):
    from .runner import host_metadata, prohibit_network
    if args.candidate and args.candidate != 'proxy-P0':
        raise SystemExit('Requested stack is pending native/model adapters; use --candidate proxy-P0 for the explicitly limited laptop integration check')
    recovery = recovery_test()
    metadata = host_metadata()
    prohibit_network()
    print('Extra proxy-P0: 1 warmup + 5 sequential trials, LLM disabled, laptop only', flush=True)
    proxy_trial(args.ram_cap_gb)
    trials = [proxy_trial(args.ram_cap_gb) for _ in range(5)]
    dump('results/proxy_pipeline.json', {'metadata': metadata, 'warmups': 1, 'timed_runs': 5, 'trials': trials,
         'median_seconds': float(np.median([r['seconds'] for r in trials])), 'p95_seconds': float(np.percentile([r['seconds'] for r in trials], 95)),
         'queue_recovery': recovery, 'ram_guard_gb': args.ram_cap_gb, 'android_battery_saver': 'not tested',
         'actual_stacks_A_to_E': 'pending', 'manual_summary_review': 'pending'})
    print('Proxy pipeline and interrupted hash-queue results saved; no native stack ranking claimed.')


if __name__ == '__main__':
    if sys.argv[1] == 'crash':
        queue = Queue(sys.argv[2])
        assert queue.claim() is not None
        os._exit(73)
