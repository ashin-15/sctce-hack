import importlib.util
import subprocess
import sys
import tempfile
import unittest
import wave
from pathlib import Path

from bench.runner import result_rows, unit


class RunnerTests(unittest.TestCase):
    @unittest.skipUnless(importlib.util.find_spec('faster_whisper'), 'optional faster-whisper not installed')
    def test_audio_decoder_dependency_compatibility(self):
        from faster_whisper.audio import decode_audio
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'fixture.wav'
            with wave.open(str(path), 'wb') as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(16000)
                output.writeframes(b'\0' * 3200)
            self.assertEqual(len(decode_audio(str(path))), 1600)

    def test_offline_guard_blocks_socket(self):
        code = "from bench.runner import prohibit_network; import socket; prohibit_network(); socket.create_connection(('127.0.0.1', 1))"
        result = subprocess.run([sys.executable, '-c', code], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Network disabled during benchmark inference', result.stderr)

    def test_metadata_link_is_committed_not_ignored_raw(self):
        metadata = {k: 'synthetic-fixture' for k in ['git_commit', 'device_model', 'soc_cpu', 'ram_bytes', 'os_version', 'thermal_state']}
        metadata['versions'] = {}
        payload = {'metadata': metadata, 'component': 'classifier', 'candidate': 'fake', 'subset_limit': 0,
                   'vad': False, 'quantization': 'none', 'licence_commercial': 'yes', 'artifacts': [],
                   'trials': [{'metrics': {'all': {'accuracy': 1.}}, 'seconds': 1.} for _ in range(5)],
                   'peak_rss_bytes': 1, 'model_bytes': 1, 'load_seconds_single_observation': 1.}
        rows = result_rows(payload, Path('results/raw/fake.json'))
        self.assertEqual({r['metadata_path'] for r in rows}, {Path('results/metadata/fake.json').as_posix()})

    def test_units(self):
        self.assertEqual(unit('peak_rss_bytes'), 'bytes')
        self.assertEqual(unit('ms_per_message'), 'ms')
        self.assertEqual(unit('verify_10000_seconds'), 's')
