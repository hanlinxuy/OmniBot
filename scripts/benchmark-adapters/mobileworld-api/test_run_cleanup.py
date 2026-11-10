import json
import pathlib
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


class CleanupCheckpointTest(unittest.TestCase):
    def test_cleanup_transport_failure_retains_prior_judgment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            source = root / 'source'
            tasks = source / 'src/mobile_world/tasks/definitions/mastodon'
            tasks.mkdir(parents=True)
            (tasks / 'mastodon_new_post.py').write_text('class Task:\n    goal = "Create an owned post"\n')
            output = root / 'output'
            reset_count = 0

            def transport(command, **kwargs):
                nonlocal reset_count
                if command[0] == sys.executable:
                    artifact = output / 'ios-android-owned-test'
                    artifact.mkdir()
                    (artifact / 'result.json').write_text('{"terminal":true}')
                    return subprocess.CompletedProcess(command, 0, '', '')
                if ' stop web ' in command[-1]:
                    reset_count += 1
                    if reset_count == 2:
                        raise subprocess.TimeoutExpired(command, 90)
                return subprocess.CompletedProcess(command, 0, b'owned data', b'')

            values = {'source': source, 'ios-source': root, 'runtime-root': root,
                      'output': output, 'compose': '/owned/omnibot-mw-test/compose.json',
                      'ssh-target': 'fixture', 'ssh-control': '/unused', 'adb': 'adb',
                      'serial': 'fixture', 'task': 'mastodon_new_post.py'}
            argv = ['run.py'] + [part for key, value in values.items() for part in ('--' + key, str(value))]
            with patch.object(sys, 'argv', argv), patch('subprocess.run', side_effect=transport), patch('judge.judge', side_effect=[0.0, 1.0]):
                with self.assertRaises(subprocess.TimeoutExpired):
                    runpy.run_path(str(pathlib.Path(__file__).with_name('run.py')), run_name='__main__')
            report = json.loads((output / 'ios-android-owned-test/backend-report.json').read_text())
            self.assertEqual(report['sourceJudge'], 1.0)
            self.assertEqual(report['runnerExitCode'], 0)
            self.assertFalse(report['backendRestored'])
            self.assertNotIn('restoredHash', report)


if __name__ == '__main__':
    unittest.main()
