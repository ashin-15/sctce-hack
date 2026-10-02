import csv
import itertools
from pathlib import Path

QUANTS = ['Q3_K_M', 'Q4_K_M', 'Q5_K_M', 'Q8_0', 'IQ4_XS']
RUNTIMES = ['llama.cpp', 'MediaPipe', 'MLC', 'ExecuTorch', 'ORT-GenAI']
LLMS = ['Qwen2.5-0.5B', 'Qwen2.5-1.5B', 'Qwen2.5-3B', 'Qwen3-0.6B', 'Qwen3-1.7B', 'Qwen3-4B', 'Llama3.2-1B', 'Llama3.2-3B', 'Gemma3-1B', 'Gemma3-4B', 'Gemma3n-E2B', 'Gemma3n-E4B', 'Phi3.5-mini', 'Phi4-mini', 'SmolLM2-1.7B', 'SmolLM3-3B', 'Sarvam1-2B', 'Airavata']
SLOTS = {
    'ocr': ['MLKit-Latin', 'MLKit-Devanagari', 'Tesseract-eng', 'Tesseract-hin', 'Tesseract-mal', 'PaddleOCR', 'RapidOCR-ONNX', 'EasyOCR', 'docTR', 'Surya', 'SmolVLM', 'Qwen2.5-VL-small', 'Florence2', 'MLKit-Tesseract-hybrid', 'MLKit-Paddle-hybrid'],
    'stt': [f'whisper.cpp-{m}-{q}' for m in ['tiny', 'base', 'small'] for q in ['q5_1', 'q8_0']] + ['faster-whisper-small', 'faster-whisper-large-v3-turbo', 'IndicWhisper', 'IndicConformer', 'Vosk-hindi', 'Vosk-english', 'sherpa-onnx', 'Meta-MMS', 'Android-offline-SpeechRecognizer', 'faster-whisper-base'],
    'language': ['fastText-lid', 'lingua', 'CLD3', 'script-heuristic'],
    'normalization': ['IndicXlit', 'rule-transliteration', 'none'],
    'classifier': ['tfidf-logreg', 'fastText', 'CNN', 'BiLSTM', 'DistilmBERT', 'multilingual-MiniLM', 'mDeBERTa-v3-small', 'MuRIL', 'IndicBERT-v2', 'XLM-R-base', 'LaBSE-logreg', 'multilingual-e5-small-logreg', 'EmbeddingGemma-logreg', 'rules', 'rules-tfidf-ensemble'] + ['zero-shot-' + m for m in LLMS],
    'extraction': LLMS + ['template-extractive'],
    'linking': ['sender-time-rules', 'multilingual-e5-small-clustering', 'MiniLM-clustering', 'LaBSE-clustering', 'LLM-grouping'],
    'storage': ['Room-SQLCipher', 'SQLite-AES-GCM', 'Realm-encrypted'],
    'key': ['Keystore-Argon2id', 'Argon2id-laptop'],
    'integrity': ['SHA256-chain', 'Merkle-tree', 'HMAC', 'Ed25519'],
    'pdf': ['Android-PdfDocument', 'OpenPDF', 'PDFBox-Android', 'WebView-PDF', 'ReportLab', 'WeasyPrint'],
    'ingest': ['share-sheet', 'media-picker', 'whatsapp-export', 'MediaProjection', 'notification-listener'],
}
IMPLEMENTED = {'tfidf-logreg', 'rules', 'rules-tfidf-ensemble', 'script-heuristic', 'RapidOCR-ONNX', 'faster-whisper-base', 'template-extractive', 'sender-time-rules', 'SQLite-AES-GCM', 'Argon2id-laptop', 'SHA256-chain', 'Merkle-tree', 'HMAC', 'Ed25519', 'ReportLab', 'whatsapp-export'}
ANDROID = {'MLKit-Latin', 'MLKit-Devanagari', 'MLKit-Tesseract-hybrid', 'MLKit-Paddle-hybrid', 'Android-offline-SpeechRecognizer', 'Room-SQLCipher', 'Realm-encrypted', 'Keystore-Argon2id', 'Android-PdfDocument', 'PDFBox-Android', 'WebView-PDF', 'share-sheet', 'media-picker', 'MediaProjection', 'notification-listener'}


def candidates():
    rows = []
    for slot, names in SLOTS.items():
        for name in names:
            variants = [{'vad': v} for v in ['off', 'Silero']] if slot == 'stt' else [{}]
            for variant in variants:
                rows.append({'slot': slot, 'candidate': name, 'quantization': '', 'runtime': '',
                             'output_mode': '', 'vad': variant.get('vad', ''),
                             'status': 'ready' if name in IMPLEMENTED else 'blocked_device' if name in ANDROID else 'pending_adapter',
                             'reason': '' if name in IMPLEMENTED else 'No Android hardware/native test app available' if name in ANDROID else 'Adapter not implemented; NOT evidence model is unavailable',
                             'licence_commercial': 'unknown', 'offline_verified': 'unknown'})
    for model, quant, runtime, mode in itertools.product(LLMS, QUANTS, RUNTIMES, ['free-text', 'json-prompt', 'grammar']):
        rows.append({'slot': 'extraction-matrix', 'candidate': model, 'quantization': quant,
                     'runtime': runtime, 'output_mode': mode, 'vad': '', 'status': 'pending_support_check',
                     'reason': 'Planned matrix only. Quant/runtime support and licence must be checked before execution; not all combinations exist.',
                     'licence_commercial': 'unknown', 'offline_verified': 'unknown'})
    for name in ['Qwen3-0.5B', 'Qwen3-1.5B', 'Qwen3-3B', 'Qwen2.5-1.7B', 'Qwen2.5-4B', 'SmolLM2-3B', 'SmolLM3-1.7B']:
        rows.append({'slot': 'requested-name-check', 'candidate': name, 'quantization': '', 'runtime': '', 'output_mode': '', 'vad': '',
                     'status': 'pending_identifier_check', 'reason': 'Requested family/size pairing requires exact published model identifier; no substitute claimed.',
                     'licence_commercial': 'unknown', 'offline_verified': 'unknown'})
    for name in SLOTS['classifier']:
        if not name.startswith('zero-shot') and name not in ['rules', 'rules-tfidf-ensemble']:
            for runtime in ['ONNX-int8', 'TFLite-int8']:
                rows.append({'slot': 'classifier-export', 'candidate': name, 'quantization': 'int8', 'runtime': runtime, 'output_mode': '', 'vad': '',
                             'status': 'pending_export', 'reason': 'Export the winning family candidate, then measure conversion delta on identical test predictions.',
                             'licence_commercial': 'unknown', 'offline_verified': 'unknown'})
    return rows


def write_registry():
    rows = candidates()
    Path('results').mkdir(exist_ok=True)
    with Path('results/candidate_inventory.csv').open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f'{len(rows)} planned/ready entries; inventory is not an executed-results table.')
