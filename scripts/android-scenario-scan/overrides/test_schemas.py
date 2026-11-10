import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('live_node_schema', Path(__file__).with_name('schemas.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.load_canonical_action_schema = lambda: json.loads(Path('plugins/omni-vlm-lite/schemas/oob/oob_canonical_actions.v1.json').read_text())


class LiveNodeSchemaTest(unittest.TestCase):
    def test_planner_can_send_live_id_but_replay_does_not_persist_it(self):
        tools = {x['function']['name']:x['function'] for x in module.vlm_action_tools()}
        for name in ['click','input_text']:
            self.assertIn('node_id', tools[name]['parameters']['properties'])
            self.assertIn('x', tools[name]['parameters']['required'])
            self.assertIn('y', tools[name]['parameters']['required'])
        action = {'tool':'click','args':{'node_id':'7','x':100,'y':200}}
        self.assertEqual('7', module.canonicalize_action(action,persisted_only=False)['args']['node_id'])
        self.assertNotIn('node_id', module.canonicalize_action(action)['args'])

    def test_unknown_arguments_still_fail(self):
        with self.assertRaisesRegex(ValueError,'unknown'):
            module.canonicalize_action({'tool':'click','args':{'x':100,'y':200,'made_up_id':'7'}},persisted_only=False)

    def test_abort_is_visible_with_reason_and_is_not_replayable(self):
        tools = {x['function']['name']: x['function'] for x in module.vlm_action_tools()}
        self.assertIn('abort', tools)
        self.assertEqual({'value'}, set(tools['abort']['parameters']['properties']))
        action = {'tool': 'abort', 'args': {'value': 'Attachment could not be verified'}}
        self.assertEqual(action, module.canonicalize_action(action, persisted_only=False, allow_non_action=True))
        with self.assertRaises(ValueError):
            module.canonicalize_action(action, replayable_only=True)
