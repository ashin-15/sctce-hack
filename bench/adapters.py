import hashlib
import hmac
import json
import os
import re
import sqlite3
import tempfile
import time
from datetime import datetime
from pathlib import Path

import numpy as np

from .data import LABELS, SEED, dump, load
from .metrics import classifier_metrics, error_rates, field_f1, grouping_metrics, quote_faithfulness, threshold_for_target

RULES = {
    'insult': ['worthless', 'idiot', 'बेकार', 'मूर्ख', 'bekaar', 'bewakoof', 'കൊള്ളില്ല', 'വിഡ്ഢി', 'kollilla', 'viddhi'],
    'threat': ['hurt you', 'not be safe', 'चोट', 'सुरक्षित नहीं', 'chot', 'safe nahi', 'ഉപദ്രവിക്കും', 'സുരക്ഷിതമാകില്ല', 'upadravikkum', 'safe aakilla'],
    'sexual_harassment': ['nude', 'sexual messages', 'नग्न', 'अश्लील', 'ashleel', 'നഗ്ന', 'അശ്ലീല', 'ashleela'],
    'caste_religious_slur': ['caste are dirty', 'religion makes you inferior', 'जाति', 'धर्म', 'jaati', 'dharm', 'ജാതി', 'മതം', 'jaathi', 'matham'],
    'doxxing': ['publish', 'प्रकाशित', 'പരസ്യമാക്കും'],
    'coercive_control': ['password', 'पासवर्ड', 'പാസ്‌വേഡ്'],
}


def rule_scores(texts):
    return np.array([[.98 if any(k.casefold() in text.casefold() for k in RULES[label]) else .02 for label in LABELS] for text in texts])


class Classifier:
    licence = 'BSD-3-Clause + project rules'
    quantization = 'fp64 sparse baseline; no neural quantization'

    def __init__(self, candidate):
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.multiclass import OneVsRestClassifier
        self.candidate = candidate
        rows = load('data/text.jsonl')
        train = [r for r in rows if r['split'] == 'train']
        val = [r for r in rows if r['split'] == 'val']
        self.vectorizer = None
        if candidate != 'rules':
            self.vectorizer = TfidfVectorizer(analyzer='char', ngram_range=(2, 5), min_df=2, max_features=40000)
            x = self.vectorizer.fit_transform([r['text'] for r in train])
            self.model = OneVsRestClassifier(LogisticRegression(C=4, class_weight='balanced', random_state=SEED, max_iter=500))
            self.model.fit(x, self.targets(train))
        vp, vy = self.scores([r['text'] for r in val]), self.targets(val)
        self.thresholds = []
        self.operating = []
        for j in range(len(LABELS)):
            choices = []
            for threshold in np.linspace(.1, .9, 17):
                pred = vp[:, j] >= threshold
                tp = int((pred & (vy[:, j] == 1)).sum())
                f1 = 2 * tp / max(1, int(pred.sum() + vy[:, j].sum()))
                choices.append((f1, float(threshold)))
            self.thresholds.append(max(choices)[1])
            self.operating.append({mode: threshold_for_target(vy[:, j], vp[:, j], target, mode) for mode, target in [('precision', .9), ('recall', .95)]})
        artifact = Path('models') / (candidate + '.json')
        payload = {'rules': RULES, 'thresholds': self.thresholds, 'operating': self.operating, 'labels': LABELS, 'seed': SEED}
        if self.vectorizer:
            payload.update({'vocabulary': {k: int(v) for k, v in self.vectorizer.vocabulary_.items()}, 'idf': self.vectorizer.idf_.tolist(),
                            'coef': [m.coef_.tolist() for m in self.model.estimators_],
                            'intercept': [m.intercept_.tolist() for m in self.model.estimators_]})
        dump(artifact, payload)
        self.artifacts = [artifact]

    @staticmethod
    def targets(rows):
        return np.array([[int(label in row['labels']) for label in LABELS] for row in rows])

    def scores(self, texts):
        rules = rule_scores(texts)
        if self.candidate == 'rules':
            return rules
        p = self.model.predict_proba(self.vectorizer.transform(texts))
        return np.maximum(p, rules) if self.candidate == 'rules-tfidf-ensemble' else p

    def execute(self, limit=0):
        rows = [r for r in load('data/text.jsonl') if r['split'] == 'test']
        rows = rows[:limit] if limit else rows
        started = time.perf_counter()
        p = self.scores([r['text'] for r in rows])
        elapsed = time.perf_counter() - started
        metrics = {}
        for language in sorted({r['language'] for r in rows} | {'all'}):
            indexes = [i for i, r in enumerate(rows) if language == 'all' or r['language'] == language]
            subset = [rows[i] for i in indexes]
            metrics[language] = classifier_metrics(self.targets(subset), p[indexes], LABELS, self.thresholds, self.operating, subset)
            metrics[language]['ms_per_message'] = elapsed * 1000 / len(rows)
            metrics[language]['samples'] = len(indexes)
        predictions = [{'id': r['id'], 'language': r['language'], 'challenge': r['challenge'], 'truth': r['labels'],
                        'scores': p[i].tolist(), 'predicted': [l for j, l in enumerate(LABELS) if p[i, j] >= self.thresholds[j]]} for i, r in enumerate(rows)]
        return metrics, predictions


class Language:
    licence = 'project rules'
    artifacts = []
    quantization = 'none'

    @staticmethod
    def identify(text):
        if any('\u0900' <= c <= '\u097f' for c in text):
            return 'hi'
        if any('\u0d00' <= c <= '\u0d7f' for c in text):
            return 'ml'
        tokens = set(re.findall(r'\w+', text.casefold()))
        hi = bool(tokens & {'tum', 'tumne', 'tumhara', 'main', 'nahi', 'karunga', 'darr', 'neend', 'bola', 'mera'})
        ml = bool(tokens & {'ninte', 'ninne', 'njan', 'enikku', 'ente', 'njangal', 'tharu', 'venam'})
        en = bool(tokens & {'i', 'you', 'your', 'will', 'the', 'and', 'are', 'please', 'stop', 'send'})
        return 'mixed' if (hi or ml) and en else 'hinglish' if hi else 'manglish' if ml else 'en'

    def execute(self, limit=0):
        rows = [r for r in load('data/text.jsonl') if r['split'] == 'test']
        rows = rows[:limit] if limit else rows
        start = time.perf_counter()
        predictions = [self.identify(r['text']) for r in rows]
        elapsed = time.perf_counter() - start
        return {lang: {'accuracy': np.mean([p == r['language'] for r, p in zip(rows, predictions) if lang == 'all' or r['language'] == lang]), 'ms_per_message': elapsed * 1000 / len(rows)} for lang in sorted({r['language'] for r in rows} | {'all'})}, [{'id': r['id'], 'predicted': p} for r, p in zip(rows, predictions)]


class OCR:
    licence = 'Apache-2.0; bundled PP-OCR weights require separate licence review'
    quantization = 'ONNX fp32'

    def __init__(self):
        import rapidocr
        from rapidocr import RapidOCR
        folder = Path(rapidocr.__file__).parent / 'models'
        self.artifacts = sorted(folder.glob('*.onnx'))
        if len(self.artifacts) < 3:
            raise RuntimeError('Prepare OCR assets first; inference must not download')
        self.engine = RapidOCR(params={'Global.log_level': 'critical', 'EngineConfig.onnxruntime.intra_op_num_threads': 4})

    def execute(self, limit=0):
        rows = [r for r in load('data/screenshots.jsonl') if r['split'] == 'test' and r['language'] not in ['hi', 'ml']]
        rows = rows[:limit] if limit else rows
        predictions = []
        for row in rows:
            start = time.perf_counter()
            result = self.engine(row['path'])
            text = '\n'.join(result.txts) if result.txts else ''
            predictions.append({'id': row['id'], 'text': text, 'seconds': time.perf_counter() - start})
        metrics = {}
        for language in sorted({r['language'] for r in rows} | {'all'}):
            pairs = [(r, p) for r, p in zip(rows, predictions) if language == 'all' or r['language'] == language]
            metrics[language] = error_rates([r['ocr_text'] for r, _ in pairs], [p['text'] for _, p in pairs])
            metrics[language].update({'seconds_per_screenshot': np.mean([p['seconds'] for _, p in pairs]),
                                     'sender_accuracy': np.mean([r['sender'] in p['text'] for r, p in pairs]),
                                     'timestamp_accuracy': np.mean([r['timestamp'] in p['text'] for r, p in pairs]), 'samples': len(pairs)})
        return metrics, predictions


class STT:
    licence = 'MIT'
    quantization = 'CTranslate2 int8 CPU (not whisper.cpp q5)'

    def __init__(self, vad=False):
        from faster_whisper import WhisperModel
        folder = Path('models/faster-whisper-base')
        if not (folder / 'provenance.json').exists():
            raise RuntimeError('Run prepare-whisper before offline STT')
        self.artifacts = sorted(p for p in folder.iterdir() if p.is_file())
        if vad:
            import faster_whisper
            self.artifacts += sorted((Path(faster_whisper.__file__).parent / 'assets').glob('*.onnx'))
        self.engine = WhisperModel(str(folder), device='cpu', compute_type='int8', cpu_threads=4, num_workers=1, local_files_only=True)
        self.vad = vad

    def transcribe(self, path, language):
        segments, _ = self.engine.transcribe(path, language=language, beam_size=1, temperature=0, vad_filter=self.vad, condition_on_previous_text=False)
        return ' '.join(s.text.strip() for s in segments)

    def execute(self, limit=0):
        rows = load('data/audio.jsonl')
        rows = rows[:limit] if limit else rows
        predictions = []
        for row in rows:
            start = time.perf_counter()
            text = self.transcribe(row['path'], row['language'])
            predictions.append({'id': row['id'], 'text': text, 'seconds': time.perf_counter() - start})
        metrics = {}
        groups = sorted({r['language'] for r in rows} | {'all'})
        groups += [language + ':' + noise for language in sorted({r['language'] for r in rows}) for noise in ['original_recording', 'white_noise_10dB']]
        for group in groups:
            pairs = [(r, p) for r, p in zip(rows, predictions) if group == 'all' or group == r['language'] or group == r['language'] + ':' + r['noise']]
            if not pairs:
                continue
            metrics[group] = error_rates([r['transcript'] for r, _ in pairs], [p['text'] for _, p in pairs])
            metrics[group].update({'real_time_factor': sum(p['seconds'] for _, p in pairs) / sum(r['duration_s'] for r, _ in pairs), 'samples': len(pairs)})
        return metrics, predictions


class Extraction:
    licence = 'project rules'
    quantization = 'none; template is NOT an LLM'
    artifacts = []

    @staticmethod
    def extract(row):
        labels = [l for j, l in enumerate(LABELS) if rule_scores([row['text']])[0, j] >= .5]
        return {'date': row['timestamp'][:10], 'platform': row['platform'], 'sender': row['sender'],
                'threat_type': labels, 'quote': row['text']}

    def execute(self, limit=0):
        rows = [r for r in load('data/extraction.jsonl') if r['split'] == 'test']
        rows = rows[:limit] if limit else rows
        start = time.perf_counter()
        predicted = [self.extract(r) for r in rows]
        elapsed = time.perf_counter() - start
        metrics = {}
        for language in sorted({r['language'] for r in rows} | {'all'}):
            pairs = [(r, p) for r, p in zip(rows, predicted) if language == 'all' or language == r['language']]
            metrics[language] = {'json_validity': 1., 'field_f1': np.mean([field_f1(r['fields'], p) for r, p in pairs]),
                                 'hallucinated_quote_rate': np.mean([quote_faithfulness(r['text'], [p['quote']])['hallucinated_quote_rate'] for r, p in pairs]),
                                 'ms_per_thread': elapsed * 1000 / len(rows), 'samples': len(pairs)}
        return metrics, [{'id': r['id'], 'fields': p, 'summary': f"Source {r['id']} records a message from {r['sender']}. The recorded date is {r['timestamp'][:10]}. User review is required before any complaint is exported."} for r, p in zip(rows, predicted)]


def link_rows(rows, window_s=86400):
    groups = []
    for row in rows:
        timestamp = datetime.fromisoformat(row['timestamp'].replace('Z', '+00:00')).timestamp()
        group = next((g for g in groups if g['sender'] == row['sender'] and 0 <= timestamp - g['last'] <= window_s), None)
        if group is None:
            group = {'sender': row['sender'], 'last': timestamp, 'ids': []}
            groups.append(group)
        group['last'] = timestamp
        group['ids'].append(row['id'])
    return {id_: i for i, g in enumerate(groups) for id_ in g['ids']}


class Linking:
    licence = 'project rules'
    artifacts = []
    quantization = 'none'

    def execute(self, limit=0):
        rows = sorted(load('data/extraction.jsonl'), key=lambda r: r['timestamp'])
        start = time.perf_counter()
        mapping = link_rows(rows)
        elapsed = time.perf_counter() - start
        metric = grouping_metrics([r['case_id'] for r in rows], [mapping[r['id']] for r in rows])
        metric.update({'ms_per_1000_messages': elapsed * 1000000 / len(rows), 'samples': len(rows)})
        return {'all': metric}, [{'id': r['id'], 'group': mapping[r['id']]} for r in rows]


def chain(entries):
    current = b'\0' * 32
    for entry in entries:
        current = hashlib.sha256(current + len(entry).to_bytes(8, 'big') + entry).digest()
    return current


def merkle(entries):
    nodes = [hashlib.sha256(b'\x00' + e).digest() for e in entries]
    if not nodes:
        return hashlib.sha256(b'\x00').digest()
    while len(nodes) > 1:
        if len(nodes) % 2:
            nodes.append(nodes[-1])
        nodes = [hashlib.sha256(b'\x01' + nodes[i] + nodes[i+1]).digest() for i in range(0, len(nodes), 2)]
    return nodes[0]


class Integrity:
    licence = 'Python stdlib / PyCryptodome BSD + public-domain components'
    artifacts = []
    quantization = 'none'

    def __init__(self, candidate):
        from Crypto.PublicKey import ECC
        from Crypto.Signature import eddsa
        self.entries = [json.dumps({'id': i, 'text': f'synthetic evidence {i}'}, sort_keys=True).encode() for i in range(10000)]
        self.candidate = candidate
        if candidate in ['SHA256-chain', 'Merkle-tree']:
            self.hash = chain if candidate == 'SHA256-chain' else merkle
            self.anchor = self.hash(self.entries)
        elif candidate == 'HMAC':
            self.key = os.urandom(32)
            self.tags = [hmac.digest(self.key, i.to_bytes(8, 'big') + e, 'sha256') for i, e in enumerate(self.entries)]
        else:
            self.signer = eddsa.new(ECC.generate(curve='Ed25519'), 'rfc8032')
            self.verifier = eddsa.new(self.signer._key.public_key(), 'rfc8032')
            self.tags = [self.signer.sign(i.to_bytes(8, 'big') + e) for i, e in enumerate(self.entries)]

    def verify(self, entries):
        if self.candidate in ['SHA256-chain', 'Merkle-tree']:
            return hmac.compare_digest(self.hash(entries), self.anchor)
        if len(entries) != len(self.tags):
            return False
        for i, (entry, tag) in enumerate(zip(entries, self.tags)):
            payload = i.to_bytes(8, 'big') + entry
            if self.candidate == 'HMAC':
                if not hmac.compare_digest(hmac.digest(self.key, payload, 'sha256'), tag):
                    return False
            else:
                try:
                    self.verifier.verify(payload, tag)
                except ValueError:
                    return False
        return True

    def execute(self, limit=0):
        start = time.perf_counter()
        valid = self.verify(self.entries)
        elapsed = time.perf_counter() - start
        altered = self.entries.copy()
        block = bytearray(altered[5000])
        block[5] ^= 1
        altered[5000] = bytes(block)
        detected = not self.verify(altered)
        if not valid or not detected:
            raise AssertionError('Integrity validation/tamper test failed')
        return {'all': {'verify_10000_seconds': elapsed, 'tamper_detected': int(detected), 'valid_verified': int(valid)}}, []


class Storage:
    licence = 'SQLite public-domain / PyCryptodome'
    artifacts = []
    quantization = 'AES-256-GCM; memory-only random test key'

    def execute(self, limit=0):
        from Crypto.Cipher import AES
        key = os.urandom(32)
        rows = [f'synthetic evidence {i}'.encode() for i in range(10000)]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'bench.sqlite'
            conn = sqlite3.connect(path)
            conn.execute('CREATE TABLE evidence (id INTEGER PRIMARY KEY, nonce BLOB, ciphertext BLOB, tag BLOB)')
            start = time.perf_counter()
            encrypted = []
            for i, row in enumerate(rows):
                cipher = AES.new(key, AES.MODE_GCM, nonce=os.urandom(12))
                cipher.update(i.to_bytes(8, 'big'))
                ciphertext, tag = cipher.encrypt_and_digest(row)
                encrypted.append((i, cipher.nonce, ciphertext, tag))
            conn.executemany('INSERT INTO evidence VALUES (?, ?, ?, ?)', encrypted)
            conn.commit()
            insert = time.perf_counter() - start
            start = time.perf_counter()
            decrypted = []
            for i, nonce, ciphertext, tag in conn.execute('SELECT * FROM evidence ORDER BY id'):
                cipher = AES.new(key, AES.MODE_GCM, nonce=nonce)
                cipher.update(i.to_bytes(8, 'big'))
                decrypted.append(cipher.decrypt_and_verify(ciphertext, tag))
            read = time.perf_counter() - start
            assert decrypted == rows
            size = path.stat().st_size
            conn.close()
        return {'all': {'insert_10000_seconds': insert, 'read_10000_seconds': read,
                        'file_bytes': size, 'file_over_payload_ratio': size / sum(map(len, rows))}}, []


class KeyDerivation:
    licence = 'MIT / Apache-2.0 / CC0 (Argon2 implementation)'
    artifacts = []
    quantization = 'Argon2id t=3, memory=65536 KiB, lanes=1'

    def execute(self, limit=0):
        from argon2.low_level import Type, hash_secret_raw
        start = time.perf_counter()
        result = hash_secret_raw(b'000000-synthetic-test-pin', b'fixed-fixture-salt', time_cost=3, memory_cost=65536, parallelism=1, hash_len=32, type=Type.ID)
        return {'all': {'unlock_seconds': time.perf_counter() - start, 'derived_bytes': len(result)}}, []


class PDF:
    licence = 'ReportLab BSD'
    artifacts = []
    quantization = 'none'

    def execute(self, limit=0):
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
        from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer
        from reportlab.lib.styles import ParagraphStyle
        from xml.sax.saxutils import escape
        font = Path('C:/Windows/Fonts/Nirmala.ttc')
        if not font.exists():
            raise RuntimeError('Supply an Indic-supporting TrueType font')
        pdfmetrics.registerFont(TTFont('Indic', str(font), subfontIndex=0, shapable=True))
        style = ParagraphStyle('body', fontName='Indic', fontSize=12, leading=20, shaping=True)
        rows = load('data/extraction.jsonl')[:50]
        start = time.perf_counter()
        story = []
        for row in rows:
            story += [Paragraph(escape(f"{row['id']} | {row['sender']} | {row['timestamp']}"), style), Paragraph(escape(row['text']), style), Spacer(1, 12)]
        dest = Path('results/sample-report.pdf')
        SimpleDocTemplate(str(dest)).build(story)
        return {'all': {'render_50_incidents_seconds': time.perf_counter() - start, 'file_bytes': dest.stat().st_size}}, [{'indic_font_review': 'pending native-speaker visual review; shaping enabled, correctness NOT claimed'}]


def parse_export(text):
    pattern = re.compile(r'^(\d{1,2}/\d{1,2}/\d{2,4}),\s+(\d{1,2}:\d{2}(?:\s*[AP]M)?)\s+-\s+([^:]+):\s?(.*)$', re.I)
    rows = []
    for line in text.splitlines():
        match = pattern.match(line)
        if match:
            date, timestamp, sender, body = match.groups()
            rows.append({'date_raw': date, 'time_raw': timestamp, 'sender': sender, 'text': body})
        elif rows:
            rows[-1]['text'] += '\n' + line
    return rows


class Ingest:
    licence = 'project rules'
    artifacts = []
    quantization = 'none'

    def execute(self, limit=0):
        text = '01/01/2025, 10:30 - Fake Sender: synthetic message\ncontinued line\n01/01/2025, 10:31 - Fake Other: synthetic reply'
        start = time.perf_counter()
        for _ in range(1000):
            rows = parse_export(text)
        elapsed = time.perf_counter() - start
        assert len(rows) == 2 and rows[0]['text'] == 'synthetic message\ncontinued line'
        return {'all': {'ms_per_export_fixture': elapsed, 'fixture_parse_pass': 1}}, [{'scope': 'one .txt fixture only; locale variants, ZIP import, system notices and attachments NOT validated'}]


def create(candidate, vad=False):
    if candidate in ['rules', 'tfidf-logreg', 'rules-tfidf-ensemble']:
        return Classifier(candidate)
    factories = {'script-heuristic': Language, 'RapidOCR-ONNX': OCR, 'faster-whisper-base': lambda: STT(vad), 'template-extractive': Extraction,
                 'sender-time-rules': Linking, 'SQLite-AES-GCM': Storage, 'Argon2id-laptop': KeyDerivation, 'ReportLab': PDF, 'whatsapp-export': Ingest}
    if candidate in ['SHA256-chain', 'Merkle-tree', 'HMAC', 'Ed25519']:
        return Integrity(candidate)
    if candidate not in factories:
        raise NotImplementedError(f'{candidate}: adapter not implemented; see inventory')
    return factories[candidate]()
