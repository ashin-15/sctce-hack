import tempfile
import unittest
from pathlib import Path

from bench.pipeline import Queue, export_allowed, recovery_test


class PipelineTests(unittest.TestCase):
    def test_review_gate(self):
        self.assertFalse(export_allowed([]))
        self.assertFalse(export_allowed([{'user_confirmed': False}]))
        self.assertFalse(export_allowed([{'user_confirmed': True}, {}]))
        self.assertTrue(export_allowed([{'user_confirmed': True}]))

    def test_process_crash_recovers(self):
        result = recovery_test()
        self.assertEqual(result['resumed_completed_jobs'], 5)
        self.assertEqual(result['unique_receipts'], 5)

    def test_job_identity_and_idempotent_enqueue(self):
        with tempfile.TemporaryDirectory() as tmp:
            queue = Queue(Path(tmp) / 'test.sqlite')
            queue.enqueue('fake', 'hash-one')
            queue.enqueue('fake', 'hash-one')
            with self.assertRaises(ValueError):
                queue.enqueue('fake', 'altered-hash')
            self.assertEqual(queue.claim(), ('fake', 'hash-one'))
            self.assertIsNone(queue.claim())
            queue.finish('fake', 'output')
            queue.finish('fake', 'output')
            self.assertEqual(queue.conn.execute('SELECT COUNT(*) FROM receipts').fetchone()[0], 1)
            queue.close()
