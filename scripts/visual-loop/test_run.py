import contextlib
import io
import json
import pathlib
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import run


class BoundaryTests(unittest.TestCase):
    def invoke(self, action, policy='axes'):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        out = pathlib.Path(temp.name) / 'result'
        png = b'\x89PNG\r\n\x1a\n' + b'\x00'*8 + struct.pack('>II', 1080, 1920)
        def observe(cmd, **kwargs):
            if 'name' in cmd: return 'omnibot-lab\nOK\n'
            if 'grep mCurrentFocus' in cmd[-1]: return 'mCurrentFocus=Window{abc u0 org.fossify.notes/.MainActivity}'
            self.assertEqual(cmd[-3:], ['exec-out', 'screencap', '-p'])
            return png
        response = {'answers': {'action': {'choice': 'tap' if action == 'bad_coordinate' else action}, 'x': {'choice': '1001' if action == 'bad_coordinate' else '12'}, 'y': {'choice': '12'}}}
        def request(req, **kwargs):
            body = json.loads(req.data)
            self.assertEqual(body['state']['screenshot'], '<image>')
            self.assertEqual(body['think_mode'], 'never')
            self.assertTrue(body['images'][0].startswith('data:image/png;base64,'))
            self.assertNotIn('ui_tree', json.dumps(body))
            return contextlib.closing(io.BytesIO(json.dumps(response).encode()))
        argv = ['run.py','--adb','fixture-adb','--endpoint','http://fixture','--goal','test','--package','org.fossify.notes','--out',str(out),'--policy',policy]
        with patch.object(sys, 'argv', argv), patch.object(run.subprocess, 'check_output', side_effect=observe), patch.object(run.subprocess, 'run') as execute, patch.object(run.urllib.request, 'urlopen', side_effect=request), patch.object(run.time, 'sleep'):
            if action in ('malformed', 'bad_coordinate'):
                with self.assertRaises(ValueError): run.main()
                result = json.loads((out/'result.json').read_text())
                self.assertEqual(result['terminal'], 'runtime_error')
                self.assertEqual(result['error_type'], 'ValueError')
                self.assertEqual(result['steps'], 0)
            else:
                run.main()
                result = json.loads((out/'result.json').read_text())
                self.assertIsNone(result['accepted'])
                self.assertEqual(result['terminal'], 'model_claimed_done')
            execute.assert_not_called()
    def test_model_done_is_not_business_success(self): self.invoke('done')
    def test_native_coordinate_outside_screen_never_executes(self): self.invoke('bad_coordinate', 'agentcpm')
    def test_unadvertised_action_never_executes(self): self.invoke('malformed')

if __name__ == '__main__': unittest.main()
