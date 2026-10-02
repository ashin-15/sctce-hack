import subprocess
import sys
import unittest

from bench.runner import unit


class RunnerTests(unittest.TestCase):
    def test_offline_guard_blocks_socket(self):
        code = "from bench.runner import prohibit_network; import socket; prohibit_network(); socket.create_connection(('127.0.0.1', 1))"
        result = subprocess.run([sys.executable, '-c', code], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Network disabled during benchmark inference', result.stderr)

    def test_units(self):
        self.assertEqual(unit('peak_rss_bytes'), 'bytes')
        self.assertEqual(unit('ms_per_message'), 'ms')
        self.assertEqual(unit('verify_10000_seconds'), 's')
