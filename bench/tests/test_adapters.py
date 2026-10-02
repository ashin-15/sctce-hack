import json
import unittest
from unittest.mock import patch

from bench.adapters import Classifier, Integrity, Language, chain, merkle, parse_export, rule_scores


class AdapterTests(unittest.TestCase):
    def test_classifier_artifact_json_serializable(self):
        with patch('bench.adapters.dump', side_effect=lambda path, payload: json.dumps(payload)):
            adapter = Classifier('tfidf-logreg')
            self.assertEqual(adapter.scores(['synthetic fixture']).shape, (1, 6))

    def test_merkle_root_binds_entry_count(self):
        entries = [b'first', b'second', b'third']
        self.assertNotEqual(merkle(entries), merkle(entries + [entries[-1]]))
        self.assertNotEqual(merkle([]), merkle([b'']))

    def test_integrity_flip_reorder_and_truncate(self):
        for function in [chain, merkle]:
            original = [b'first', b'second', b'third']
            self.assertNotEqual(function(original), function([b'first', b'Second', b'third']))
            self.assertNotEqual(function(original), function(list(reversed(original))))
            self.assertNotEqual(function(original), function(original[:-1]))
        for name in ['SHA256-chain', 'Merkle-tree', 'HMAC', 'Ed25519']:
            adapter = Integrity(name)
            self.assertTrue(adapter.verify(adapter.entries))
            self.assertFalse(adapter.verify(adapter.entries[:-1]))
            changed = adapter.entries.copy()
            changed[0], changed[1] = changed[1], changed[0]
            self.assertFalse(adapter.verify(changed))

    def test_distress_not_harassment(self):
        self.assertTrue((rule_scores(['I feel scared and cannot sleep after what happened.']) < .5).all())

    def test_language_native_scripts(self):
        self.assertEqual(Language.identify('हिन्दी'), 'hi')
        self.assertEqual(Language.identify('മലയാളം'), 'ml')

    def test_export_multiline(self):
        rows = parse_export('1/2/2025, 10:15 - Fake: hello\ncontinued\n1/2/2025, 10:16 - Other: reply')
        self.assertEqual(rows[0]['text'], 'hello\ncontinued')
        self.assertEqual(len(rows), 2)
