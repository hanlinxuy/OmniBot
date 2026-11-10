import json
import pathlib
import tempfile
import unittest
from aggregate_results import collect


class AcceptanceTests(unittest.TestCase):
    def test_predicate_requires_terminal_review_and_both_cleanups(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            run = root / 'ios-android-mw-example-qwen-123'
            run.mkdir()
            fixture = {
                'task': {'sourceTask': 'mw-example'},
                'result': {'promptResult': {'stopReason': 'end_turn'}},
                'cleanup': {'workspaceAbsent': True, 'reverseRemoved': True},
                'backend-report': {'sourceJudge': 1.0, 'backendRestored': True,
                                   'baselineHash': 'baseline', 'restoredHash': 'baseline'},
                'independent-review': {'passed': True},
            }
            for name, value in fixture.items():
                (run / (name + '.json')).write_text(json.dumps(value))
            self.assertEqual(collect(root)['tasksWithAnyPass'], 1)
            for name, broken in [
                ('result', {'promptResult': {'stopReason': 'cancelled'}}),
                ('independent-review', {}),
                ('cleanup', {'workspaceAbsent': True, 'reverseRemoved': False}),
                ('backend-report', dict(fixture['backend-report'], restoredHash='changed')),
                ('backend-report', dict(fixture['backend-report'], sourceJudge=[0.0, 'missing effect'])),
            ]:
                with self.subTest(name=name, broken=broken):
                    path = run / (name + '.json')
                    path.write_text(json.dumps(broken))
                    self.assertEqual(collect(root)['tasksWithAnyPass'], 0)
                    path.write_text(json.dumps(fixture[name]))
