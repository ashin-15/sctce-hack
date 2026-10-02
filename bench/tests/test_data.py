import tempfile
import unittest
from pathlib import Path

from bench.data import LABELS, generate_extraction, generate_text, load


class DatasetTests(unittest.TestCase):
    def test_screenshot_manifest_has_held_out_languages(self):
        rows = load('data/screenshots.jsonl')
        self.assertEqual({r['split'] for r in rows}, {'train', 'val', 'test'})
        self.assertEqual({r['language'] for r in rows if r['split'] == 'test'}, {'en', 'hi', 'hinglish', 'ml', 'manglish', 'mixed'})

    def test_reproducible_exact_split_and_group_isolation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rows = generate_text(root)
            self.assertEqual(rows, generate_text(root))
            self.assertEqual(len(rows), 600)
            self.assertEqual([sum(r['split'] == s for r in rows) for s in ['train', 'val', 'test']], [420, 90, 90])
            for group in {r['group_id'] for r in rows}:
                self.assertEqual(len({r['split'] for r in rows if r['group_id'] == group}), 1)
            for split in ['train', 'val', 'test']:
                self.assertEqual({l for r in rows if r['split'] == split for l in r['labels']}, set(LABELS))
            generate_extraction(root, rows)
            threads = load(root / 'extraction.jsonl')
            self.assertEqual(len(threads), 100)
            self.assertTrue(all(t['fields']['quote'] in t['text'] for t in threads))


if __name__ == '__main__':
    unittest.main()
