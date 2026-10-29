import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('summarize', Path(__file__).with_name('summarize.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.manifest = {'cases': [{'id': 'child', 'source_id': 'BA02'}, {'id': 'other', 'source_id': 'BA02'}]}
        self.attempt = {'case_id': 'child', 'source_id': 'BA02', 'attempt_id': 'a1', 'status': 'failed',
                        'execution_layer': 'normal_chat', 'cleanup_actor': 'host_automation',
                        'execution': {'passed': True, 'action_count': 2, 'duration_ms': 120, 'tools': ['PRIVATE']},
                        'acceptance': {'passed': False}, 'cleanup': {'passed': True},
                        'error': 'PRIVATE', 'evidence': ['/private/secret']}

    def test_history_and_missing_are_not_source_success(self):
        passed = dict(self.attempt, attempt_id='a2', status='passed', acceptance={'passed': True})
        result = m.summarize(self.manifest, [{'attempts': [self.attempt]}, {'attempts': [passed]}])
        self.assertEqual(['failed', 'passed'], [x['status'] for x in result['cases'][0]['attempts']])
        self.assertEqual('not_run', result['cases'][1]['status'])
        self.assertNotIn('PRIVATE', json.dumps(result))
        self.assertNotIn('/private/', json.dumps(result))
        self.assertNotIn('sources', result)

    def test_rejects_invalid_success_and_mismatched_source(self):
        with self.assertRaises(ValueError):
            m.summarize(self.manifest, [{'attempts': [dict(self.attempt, status='passed')]}])
        with self.assertRaises(ValueError):
            m.summarize(self.manifest, [{'attempts': [dict(self.attempt, source_id='OTHER')]}])

    def test_rejects_duplicate_and_untrusted_identifiers(self):
        with self.assertRaises(ValueError):
            m.summarize(self.manifest, [{'attempts': [self.attempt, self.attempt]}])
        with self.assertRaises(ValueError):
            m.summarize(self.manifest, [{'attempts': [dict(self.attempt, attempt_id='/secret')]}])


if __name__ == '__main__':
    unittest.main()
