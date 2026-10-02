import unittest

from bench.scoring import gate, pareto, score_slot


class ScoreTests(unittest.TestCase):
    def test_hard_gates_and_missing_metrics(self):
        self.assertIn('disqualified', gate({'peak_ram': 2_500_000_001}))
        self.assertIn('disqualified', gate({'offline': False}))
        self.assertIn('disqualified', gate({'licence_commercial': False}))
        self.assertIn('pending', gate({'offline': True, 'licence_commercial': True, 'peak_ram': 1}))

    def test_weights_not_renormalized_to_hide_missing(self):
        a = {'candidate': 'a', 'offline': True, 'licence_commercial': True, 'accuracy': .9,
             'latency': 1, 'peak_ram': 1, 'size': 1, 'integration_effort': 1, 'battery_thermal': 1, 'licence_openness': 1}
        b = {**a, 'candidate': 'b', 'accuracy': .1, 'latency': 2, 'peak_ram': 2, 'size': 2,
             'integration_effort': 2, 'battery_thermal': 2, 'licence_openness': 0}
        c = {**a, 'candidate': 'c', 'battery_thermal': None}
        results = score_slot([a, b, c])
        self.assertAlmostEqual(results[0]['score'], 1.)
        self.assertIsNone(results[-1]['score'])
        self.assertEqual([x['candidate'] for x in pareto([a, b])], ['a'])
