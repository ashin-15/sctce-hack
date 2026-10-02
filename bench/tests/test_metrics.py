import unittest

import numpy as np

from bench.metrics import ece, edit_distance, error_rates, field_f1, grouping_metrics, quote_faithfulness, threshold_for_target


class MetricTests(unittest.TestCase):
    def test_edit_distance_and_normalization(self):
        self.assertEqual(edit_distance('kitten', 'sitting'), 3)
        self.assertEqual(error_rates(['Hello  WORLD'], ['hello world']), {'cer': 0., 'wer': 0.})
        self.assertEqual(error_rates(['a b'], ['a']), {'cer': 2/3, 'wer': .5})

    def test_quotes_are_exact_not_case_normalized(self):
        self.assertEqual(quote_faithfulness('Hello source', ['Hello', 'hello', 'invented'])['hallucinated_quote_rate'], 2/3)
        self.assertEqual(quote_faithfulness('source', [])['quote_count'], 0)

    def test_operating_point_unattainable(self):
        self.assertIsNone(threshold_for_target(np.array([0, 0]), np.array([.1, .2]), .9, 'precision'))
        threshold = threshold_for_target(np.array([1, 0]), np.array([.9, .1]), .95, 'recall')
        self.assertIsNotNone(threshold)
        self.assertGreater(threshold, .1)

    def test_fields_and_groups(self):
        self.assertEqual(field_f1({'a': ['x', 'y']}, {'a': ['x']}), 2/3)
        self.assertEqual(grouping_metrics([0, 0, 1], [0, 0, 1]), {'grouping_precision': 1., 'grouping_recall': 1.})
        self.assertEqual(ece([1, 0], [1., 0.]), 0.)
