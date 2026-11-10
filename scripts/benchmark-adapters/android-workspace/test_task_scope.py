import io
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch
import workspace_mcp


class TaskScopeTest(unittest.TestCase):
    def test_estimate_only_scope_rejects_dispatch_even_if_called_directly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root/'work').mkdir()
            state = root/'work/cityride.json'
            state.write_text('{"estimates":[],"state":{"trips":[]}}')
            before = state.read_bytes()
            (root/'task-policy.json').write_text('{"deniedTools":["request_ride"]}')
            requests = [{'id': 1, 'method': 'tools/list'},
                        {'id': 2, 'method': 'tools/call', 'params': {'name': 'request_ride', 'arguments': {'file': 'cityride.json', 'estimate_id': 'estimate'}}}]
            output = io.StringIO()
            with patch.object(workspace_mcp, '__file__', str(root/'workspace_mcp.py')), patch('sys.stdin', io.StringIO('\n'.join(json.dumps(row) for row in requests))), patch('sys.stdout', output):
                workspace_mcp.main()
            responses = [json.loads(line)['result'] for line in output.getvalue().splitlines()]
            names = {tool['name'] for tool in responses[0]['tools']}
            self.assertIn('query_records', names)
            self.assertNotIn('request_ride', names)
            self.assertTrue(responses[1]['isError'])
            self.assertEqual(state.read_bytes(), before)
