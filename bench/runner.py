import csv
import importlib.metadata
import json
import os
import platform
import socket
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import psutil

from .data import SEED, digest, dump, load
from .registry import ANDROID, IMPLEMENTED, SLOTS

DEFAULTS = {'classifier': ['rules', 'tfidf-logreg', 'rules-tfidf-ensemble'], 'language': ['script-heuristic'],
            'ocr': ['RapidOCR-ONNX'], 'stt': ['faster-whisper-base'], 'extraction': ['template-extractive'],
            'linking': ['sender-time-rules'], 'storage': ['SQLite-AES-GCM', 'Argon2id-laptop'],
            'integrity': ['SHA256-chain', 'Merkle-tree', 'HMAC', 'Ed25519'], 'pdf': ['ReportLab'], 'ingest': ['whatsapp-export']}
FIELDS = ['run_id', 'slot', 'candidate', 'language', 'metric', 'statistic', 'value', 'unit', 'samples', 'status', 'reason', 'measurement_kind', 'subset_limit', 'vad', 'quantization', 'runtime_versions', 'git_commit', 'device_model', 'soc_cpu', 'ram_bytes', 'os_version', 'thermal_state', 'offline_verified', 'licence_commercial', 'model_sha256', 'metadata_path']


def prohibit_network():
    def denied(*args, **kwargs):
        raise RuntimeError('Network disabled during benchmark inference; prepare assets first')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    os.environ.update({'HF_HUB_OFFLINE': '1', 'TRANSFORMERS_OFFLINE': '1', 'HF_DATASETS_OFFLINE': '1'})


def host_metadata():
    hardware = {'device_model': platform.node(), 'soc_cpu': platform.processor(), 'os_version': platform.platform(), 'ram_bytes': psutil.virtual_memory().total}
    if platform.system() == 'Windows':
        command = "$c=Get-CimInstance Win32_ComputerSystem; $p=Get-CimInstance Win32_Processor; $o=Get-CimInstance Win32_OperatingSystem; @{device_model=($c.Manufacturer+' '+$c.Model);soc_cpu=$p.Name;ram_bytes=$c.TotalPhysicalMemory;os_version=($o.Caption+' '+$o.Version)} | ConvertTo-Json -Compress"
        try:
            result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', command], capture_output=True, text=True, timeout=30, check=True)
            hardware.update(json.loads(result.stdout))
        except (OSError, subprocess.SubprocessError, json.JSONDecodeError):
            hardware['hardware_probe'] = 'CIM unavailable; platform fallback'
    versions = {d.metadata['Name']: d.version for d in importlib.metadata.distributions() if d.metadata.get('Name')}
    try:
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip())
    except subprocess.CalledProcessError:
        commit, dirty = 'unknown', True
    temperatures = getattr(psutil, 'sensors_temperatures', lambda: {})()
    return {**hardware, 'python': sys.version, 'versions': versions, 'git_commit': commit, 'git_dirty': dirty,
            'source_sha256': {p.as_posix(): digest(p) for p in sorted(Path('bench').glob('*.py'))},
            'data_sha256': {p.as_posix(): digest(p) for p in sorted(Path('data').glob('*.jsonl'))},
            'thermal_state': temperatures or 'unknown: no accessible thermal sensor',
            'energy': 'not measured: battery/energy attribution unavailable', 'android': 'not measured',
            'measurement_kind': 'laptop_proxy', 'target': {'ram_gb': 6, 'single_stage_limit_gb': 2.5, 'soc': 'unspecified', 'npu': False},
            'seed': SEED, 'temperature': 0, 'clock': 'time.perf_counter',
            'rss_method': 'Windows peak_wset + sampled RSS (5 ms)' if platform.system() == 'Windows' else 'sampled process RSS (5 ms); child native allocations included, short spikes may be missed',
            'p95_method': 'numpy percentile linear interpolation of five batch runs; low sample confidence',
            'thermal_throttling_events': 'not measured', 'cpu_threads': 'STT/OCR 4; others adapter/runtime defaults'}


class RSS:
    def __init__(self):
        self.process = psutil.Process()
        self.peak = 0
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self.poll, daemon=True)

    def sample(self):
        info = self.process.memory_info()
        self.peak = max(self.peak, info.rss, getattr(info, 'peak_wset', 0))

    def poll(self):
        while not self.stop_event.wait(.005):
            self.sample()

    def __enter__(self):
        self.sample()
        self.thread.start()
        return self

    def __exit__(self, *args):
        self.sample()
        self.stop_event.set()
        self.thread.join()


def worker(candidate, component, limit, vad, output, ram_cap):
    metadata = host_metadata()
    prohibit_network()
    from .adapters import create
    start = time.perf_counter()
    with RSS() as memory:
        adapter = create(candidate, vad)
        load_seconds = time.perf_counter() - start
        adapter.execute(limit)
        print(f'{candidate}: warmup complete', flush=True)
        trials = []
        final_predictions = []
        for i in range(5):
            started = time.perf_counter()
            metrics, final_predictions = adapter.execute(limit)
            elapsed = time.perf_counter() - started
            memory.sample()
            if ram_cap and memory.peak > ram_cap * 1_000_000_000:
                raise MemoryError('Observed process RSS exceeds requested laptop guard; not an OS memory cap')
            trials.append({'run': i + 1, 'utc': datetime.now(timezone.utc).isoformat(), 'seconds': elapsed,
                           'metrics': metrics, 'peak_rss_bytes': memory.peak,
                           'device': {k: metadata[k] for k in ['device_model', 'soc_cpu', 'ram_bytes', 'os_version', 'thermal_state']}})
            print(f'{candidate}: timed run {i + 1}/5 complete ({elapsed:.3f}s)', flush=True)
    artifacts = [{'path': p.as_posix(), 'sha256': digest(p), 'bytes': p.stat().st_size} for p in adapter.artifacts]
    payload = {'candidate': candidate, 'component': component, 'subset_limit': limit, 'vad': vad, 'warmups': 1,
               'timed_runs': 5, 'load_seconds_single_observation': load_seconds, 'peak_rss_bytes': memory.peak,
               'model_bytes': sum(p['bytes'] for p in artifacts), 'artifacts': artifacts,
               'quantization': adapter.quantization, 'licence': adapter.licence,
               'offline_verified': True, 'offline_guard': 'Python socket connections blocked; no external inference subprocess. This is not an OS firewall.',
               'licence_commercial': 'unknown' if candidate == 'RapidOCR-ONNX' else 'yes',
               'ram_guard_gb': ram_cap, 'rss_scope': 'process-lifetime peak includes load, warmup and allocator-retained memory',
               'metadata': metadata, 'trials': trials, 'predictions': final_predictions}
    dump(output, payload)


def unit(metric):
    if metric.endswith('bytes'):
        return 'bytes'
    if 'seconds' in metric:
        return 's'
    if metric.startswith('ms_'):
        return 'ms'
    if metric in ['samples'] or metric.endswith('support'):
        return 'count'
    return 'ratio'


def append_csv(path, rows):
    exists = Path(path).exists()
    with Path(path).open('a', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS)
        if not exists:
            writer.writeheader()
        writer.writerows(rows)


def result_rows(payload, path):
    metadata = payload['metadata']
    base = {k: '' for k in FIELDS}
    base.update({'run_id': Path(path).stem, 'slot': payload['component'], 'candidate': payload['candidate'],
                 'status': 'measured', 'measurement_kind': 'laptop_proxy', 'subset_limit': payload['subset_limit'],
                 'vad': 'Silero' if payload['vad'] else 'off', 'quantization': payload['quantization'],
                 'runtime_versions': json.dumps(metadata['versions'], sort_keys=True), 'git_commit': metadata['git_commit'],
                 'device_model': metadata['device_model'], 'soc_cpu': metadata['soc_cpu'], 'ram_bytes': metadata['ram_bytes'],
                 'os_version': metadata['os_version'], 'thermal_state': str(metadata['thermal_state']),
                 'offline_verified': 'yes', 'licence_commercial': payload['licence_commercial'],
                 'model_sha256': json.dumps(payload['artifacts']), 'metadata_path': (Path('results/metadata') / Path(path).name).as_posix()})
    rows = []
    languages = payload['trials'][0]['metrics']
    for language, metrics in languages.items():
        for metric in metrics:
            values = [t['metrics'][language][metric] for t in payload['trials']]
            for statistic in ['median', 'p95']:
                rows.append({**base, 'language': language, 'metric': metric, 'statistic': statistic,
                             'value': float(np.median(values) if statistic == 'median' else np.percentile(values, 95)),
                             'unit': unit(metric), 'samples': len(values)})
    for metric, value in [('peak_rss_bytes', payload['peak_rss_bytes']), ('model_bytes', payload['model_bytes']), ('load_seconds_single_observation', payload['load_seconds_single_observation'])]:
        rows.append({**base, 'language': 'all', 'metric': metric, 'statistic': 'single_observation', 'value': value, 'unit': unit(metric), 'samples': 1})
    times = [t['seconds'] for t in payload['trials']]
    for statistic, value in [('median', np.median(times)), ('p95', np.percentile(times, 95))]:
        rows.append({**base, 'language': 'all', 'metric': 'batch_seconds', 'statistic': statistic, 'value': float(value), 'unit': 's', 'samples': 5})
    return rows


def record_blocked(component, candidate, reason, status='pending_adapter', language='all'):
    row = {k: '' for k in FIELDS}
    row.update({'slot': component, 'candidate': candidate, 'language': language, 'status': status, 'reason': reason,
                'measurement_kind': 'unmeasured'})
    append_csv('results/skipped.csv', [row])


def run(args):
    Path('results/raw').mkdir(parents=True, exist_ok=True)
    names = [args.candidate] if args.candidate else DEFAULTS[args.component]
    failures = []
    for candidate in names:
        if candidate not in IMPLEMENTED:
            reason = 'No native Android test app/device' if candidate in ANDROID else 'Adapter not implemented; not an unavailable-model claim'
            record_blocked(args.component, candidate, reason, 'blocked_device' if candidate in ANDROID else 'pending_adapter')
            print(candidate + ': ' + reason)
            failures.append(candidate)
            continue
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')
        dest = Path('results/raw') / f'{candidate}-{"vad" if args.vad else "novad"}-{stamp}.json'
        command = [sys.executable, '-m', 'bench.runner', candidate, args.component, str(args.limit), str(int(args.vad)), str(dest), str(args.ram_cap_gb or 0)]
        print('Benchmarking ' + candidate + ' (1 warmup + 5 runs, offline CPU)', flush=True)
        result = subprocess.run(command, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='replace')
        if result.returncode:
            record_blocked(args.component, candidate, result.stderr[-4000:], 'failed')
            print(result.stderr, file=sys.stderr)
            failures.append(candidate)
            continue
        payload = json.loads(dest.read_text(encoding='utf-8'))
        append_csv(f'results/{args.component}.csv', result_rows(payload, dest))
        if candidate == 'RapidOCR-ONNX':
            for language in ['hi', 'ml']:
                record_blocked('ocr', candidate, 'Bundled Chinese/Latin PP-OCRv4 recognition weights do not support this script; other multilingual weights are not evaluated.', 'unsupported_language', language)
        dump(Path('results/metadata') / dest.name, {k: v for k, v in payload.items() if k not in ['predictions', 'trials']})
        dump(Path('results/trials') / dest.name, payload['trials'])
        dump(Path('results/predictions') / dest.name, payload['predictions'])
        print(f'{candidate}: median batch {np.median([t["seconds"] for t in payload["trials"]]):.6f}s; peak {payload["peak_rss_bytes"] / 1e6:.1f} MB', flush=True)
    if failures:
        raise SystemExit('Incomplete candidates: ' + ', '.join(failures))


if __name__ == '__main__':
    worker(sys.argv[1], sys.argv[2], int(sys.argv[3]), bool(int(sys.argv[4])), sys.argv[5], float(sys.argv[6]))
