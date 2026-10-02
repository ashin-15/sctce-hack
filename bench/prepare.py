import io
import json
import time
from pathlib import Path

import numpy as np
import requests

from .data import SEED, digest, dump, jsonl

LIMIT = 5_000_000_000
CACHE = Path('.cache')


def download(url, target, expected_size=None):
    target = Path(target)
    CACHE.mkdir(exist_ok=True)
    ledger_path = CACHE / 'downloads.json'
    ledger = json.loads(ledger_path.read_text()) if ledger_path.exists() else []
    if target.exists() and any(x['path'] == target.as_posix() and x['sha256'] == digest(target) for x in ledger):
        return target
    used = sum(x['bytes'] for x in ledger)
    target.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(url, stream=True, timeout=(30, 120)) as response:
        response.raise_for_status()
        size = int(response.headers.get('Content-Length', expected_size or 0))
        if used + size > LIMIT:
            raise RuntimeError('Download exceeds approved 5 GB budget')
        written = 0
        with target.with_suffix(target.suffix + '.part').open('wb') as output:
            for chunk in response.iter_content(1024 * 1024):
                written += len(chunk)
                if used + written > LIMIT:
                    raise RuntimeError('Download exceeds approved 5 GB budget')
                output.write(chunk)
    target.with_suffix(target.suffix + '.part').replace(target)
    ledger.append({'url': url, 'path': target.as_posix(), 'bytes': written, 'sha256': digest(target)})
    dump(ledger_path, ledger)
    return target


def public_audio():
    import pyarrow.parquet as pq
    import soundfile as sf
    response = requests.get('https://datasets-server.huggingface.co/parquet', params={'dataset': 'google/fleurs'}, timeout=120)
    response.raise_for_status()
    files = response.json()['parquet_files']
    rng = np.random.default_rng(SEED)
    clips = []
    provenance = []
    provenance_path = Path('data/audio_provenance.json')
    expected = {r['config']: r['sha256'] for r in json.loads(provenance_path.read_text(encoding='utf-8'))['sources']} if provenance_path.exists() else {}
    for config, language in [('en_us', 'en'), ('hi_in', 'hi'), ('ml_in', 'ml')]:
        entry = next(x for x in files if x['config'] == config and x['split'] == 'test')
        print(f'Downloading licensed FLEURS test subset {config}: {entry["size"]} bytes', flush=True)
        path = download(entry['url'], CACHE / f'fleurs-{config}-test.parquet', entry['size'])
        if config in expected and digest(path) != expected[config]:
            raise RuntimeError('Public parquet ref changed: hash differs from committed provenance; refusing to overwrite ground truth')
        records = pq.read_table(path).to_pylist()
        provenance.append({'config': config, 'url': entry['url'], 'sha256': digest(path),
                           'licence': 'CC-BY-4.0', 'attribution': 'Google FLEURS; Conneau et al., FLEURS: Few-shot Learning Evaluation of Universal Representations of Speech (2022)',
                           'card': 'https://huggingface.co/datasets/google/fleurs', 'split': 'test'})
        cursor = 0
        for n in range(10):
            chunks, transcripts, source_ids = [], [], []
            seconds = 0
            minimum = [10, 20, 40, 60, 90][n % 5]
            while seconds < minimum:
                row = records[cursor]
                cursor += 1
                samples, sr = sf.read(io.BytesIO(row['audio']['bytes']), dtype='float32')
                if samples.ndim > 1:
                    samples = samples.mean(axis=1)
                duration = len(samples) / sr
                if seconds + duration > 120:
                    continue
                chunks.append(samples)
                transcripts.append(row.get('raw_transcription') or row['transcription'])
                source_ids.append(str(row['id']))
                seconds += duration
            samples = np.concatenate(chunks)
            for noisy in [False, True]:
                output = samples.copy()
                if noisy:
                    signal_rms = float(np.sqrt(np.mean(output ** 2)))
                    noise = rng.normal(size=len(output)).astype('float32')
                    noise *= signal_rms / (10 ** (10 / 20)) / max(float(np.sqrt(np.mean(noise ** 2))), 1e-10)
                    output = np.clip(output + noise, -.999, .999)
                dest = Path('data/audio') / f'{config}-{n:02d}-{"noise" if noisy else "clean"}.wav'
                dest.parent.mkdir(parents=True, exist_ok=True)
                sf.write(dest, output, sr, subtype='PCM_16')
                clips.append({'id': dest.stem, 'path': dest.as_posix(), 'sha256': digest(dest),
                              'language': language, 'transcript': ' '.join(transcripts), 'duration_s': len(output) / sr,
                              'sample_rate': sr, 'noise': 'white_noise_10dB' if noisy else 'original_recording',
                              'source_ids': source_ids, 'pair_id': f'{config}-{n:02d}',
                              'source': 'google/fleurs', 'split': 'test', 'licence': 'CC-BY-4.0',
                              'augmentation': 'concatenated complete public utterances, no transcript truncation'})
    jsonl('data/audio.jsonl', clips)
    dump('data/audio_provenance.json', {'sources': provenance, 'seed': SEED,
          'coverage_gap': 'Hinglish/Manglish and harassment-domain speech unavailable in FLEURS; not fabricated. Noise is synthetic white noise, not realistic environmental audio.',
          'validation': {'clips': len(clips), 'all_10_to_120_s': all(10 <= x['duration_s'] <= 120 for x in clips)}})
    print(f'Prepared {len(clips)} audio clips; natural code-mixed coverage remains pending.')


def whisper_model():
    repo = 'Systran/faster-whisper-base'
    revision = 'ebe41f70d5b6dfa9166e2c581c45c9c0cfc57b66'
    dest = Path('models/faster-whisper-base')
    required = ['config.json', 'model.bin', 'tokenizer.json', 'vocabulary.txt']
    for name in required:
        download(f'https://huggingface.co/{repo}/resolve/{revision}/{name}', dest / name)
    dump(dest / 'provenance.json', {'repo': repo, 'revision': revision, 'licence': 'MIT',
                                  'files': {p.name: digest(p) for p in sorted(dest.iterdir()) if p.is_file() and p.name != 'provenance.json'}})
    print(f'Prepared {repo} at {revision}; inference uses local_files_only=True.')
