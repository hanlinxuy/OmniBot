import ast
import dataclasses
import pathlib
import types
import unittest
import zipfile

HERE = pathlib.Path(__file__).parent

def source_expression():
    tree = ast.parse((HERE / 'engine.py').read_text())
    for node in ast.walk(tree):
        if isinstance(node, ast.Dict):
            for key, value in zip(node.keys, node.values):
                if isinstance(key, ast.Constant) and key.value == 'recent_actions':
                    return compile(ast.Expression(value), 'engine.py', 'eval')
    raise AssertionError('recent_actions projection not found')

def turn_text():
    tree = ast.parse((HERE / 'planner.py').read_text())
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == '_turn_text')
    code = compile(ast.Module(body=[node], type_ignores=[]), 'planner.py', 'exec')
    ns = {'Any': object, 'analyze_page_context': lambda state: types.SimpleNamespace(useful=False)}
    exec(code, ns)
    return ns['_turn_text']

class HistoryTests(unittest.TestCase):
    def test_device_observation_date_reaches_planner_text(self):
        tree = ast.parse((HERE / 'planner.py').read_text())
        node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'build_model_turn_request')
        ns = {'Any': object, 'Function': object, '_turn_text': turn_text(), '_state_image_data_uri': lambda state: None,
              'function_tools': lambda functions: [], 'vlm_action_tools': lambda: [], 'CONTROL_TOOLS': [], 'deepcopy': __import__('copy').deepcopy,
              'constrain_open_app_tool': lambda tools, apps: tools, 'SYSTEM_PROMPT': 'system'}
        future = ast.ImportFrom(module='__future__', names=[ast.alias(name='annotations')], level=0)
        exec(compile(ast.fix_missing_locations(ast.Module(body=[future, node], type_ignores=[])), 'planner.py', 'exec'), ns)
        archive = HERE.parents[2] / 'artifacts/omniflow-gui-runtime-2.1.8.zip'
        with zipfile.ZipFile(archive) as runtime:
            source = runtime.read('scripts/runtime/python/omniflow/core/model.py')
        runtime_ns = {'__name__': __name__, 'dataclass': dataclasses.dataclass, 'field': dataclasses.field, 'Any': object}
        runtime_tree = ast.parse(source)
        observation_node = next(n for n in runtime_tree.body if isinstance(n, ast.ClassDef) and n.name == 'Observation')
        exec(compile(ast.fix_missing_locations(ast.Module(body=[future, observation_node], type_ignores=[])), 'model.py', 'exec'), runtime_ns)
        observation = runtime_ns['Observation'].from_value({'extra': {'device_time': {
            'local_date': '2027-01-01', 'timezone': 'Pacific/Kiritimati', 'day_of_week': 'FRIDAY'
        }}})
        state_node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == '_planner_state')
        exec(compile(ast.fix_missing_locations(ast.Module(body=[future, state_node], type_ignores=[])), 'planner.py', 'exec'), ns)
        request = ns['build_model_turn_request'](goal='set reminder', model='test', turn_index=0, state=ns['_planner_state'](observation))
        self.assertIn('Device local date: 2027-01-01; timezone: Pacific/Kiritimati; day of week: FRIDAY', request['messages'][1]['content'][0]['text'])

    def test_recent_trace_is_bounded_and_excludes_input_values(self):
        trace = [{'action': {'tool': 'input_text', 'args': {'target_description': f'field{i}', 'text': 'SECRET'}}, 'result': {'success': i % 2 == 0}} for i in range(22)]
        projected = eval(source_expression(), {'trace': trace})
        self.assertEqual(len(projected), 20)
        self.assertEqual(projected[0]['target'], 'field2')
        self.assertNotIn('SECRET', repr(projected))
        text = turn_text()(goal='task', state={'extra': {'recent_actions': projected}})
        self.assertIn('dispatch success does not establish task completion', text)
        self.assertIn('target=field2 result=dispatched', text)
        self.assertIn('target=field3 result=failed', text)
        self.assertNotIn('SECRET', text)
        self.assertNotIn('target=field1 result=', text)

    def test_empty_new_run_has_no_history(self):
        self.assertEqual(eval(source_expression(), {'trace': []}), [])
        text = turn_text()(goal='new', state={'extra': {}})
        self.assertNotIn('Recent actions', text)

    def test_host_guidance_only_for_matching_package_and_keeps_explicit_host_tasks(self):
        render = turn_text()
        host = render(goal='operate host', state={'package_name': 'test.host', 'extra': {'execution_host_package': 'test.host'}})
        self.assertIn("this run's host app", host)
        self.assertIn('explicitly asks to operate the host app', host)
        other = render(goal='task', state={'package_name': 'other.app', 'extra': {'execution_host_package': 'test.host'}})
        self.assertNotIn("this run's host app", other)

if __name__ == '__main__': unittest.main()

class PlannerHistoryLifecycleTests(unittest.IsolatedAsyncioTestCase):
    async def test_real_calls_and_receipts_pair_without_replaying_images(self):
        import copy, json
        tree = ast.parse((HERE / 'planner.py').read_text())
        cls = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == 'VLMPlanner')
        module = ast.Module(body=[ast.ImportFrom(module='__future__', names=[ast.alias(name='annotations')], level=0), cls], type_ignores=[])
        requests = []
        def transport(envelope):
            requests.append(copy.deepcopy(envelope['request']))
            result = {'tool_calls': [{'id': 'call' + str(len(requests)), 'type': 'function', 'function': {'name': 'click', 'arguments': '{"x":12}'}}], 'content': None}
            if len(requests) != 3:
                result['reasoning'] = '' if len(requests) == 2 else '  real reasoning  '
            return result
        class Usage:
            def __init__(self, **kwargs): pass
            def start_call(self): pass
            def record_response(self, response): pass
            def record_failure(self): pass
        ns = {'LLMUsageTracker': Usage, 'resolve_openai_compatible_config': lambda **kw: (None, None),
              '_planner_state': lambda obs: {'extra': obs.extra},
              'build_model_turn_request': lambda **kw: {'messages': [{'role': 'system', 'content': 'system'}, {'role': 'user', 'content': [{'type': 'image_url', 'image_url': {'url': 'private image'}}, {'type': 'text', 'text': 'current observation'}]}]},
              '_normalize_response': lambda response, **kw: response,
              'parse_model_turn_response': lambda response, **kw: ('action', {}),
              'deepcopy': copy.deepcopy, 'json': json}
        exec(compile(ast.fix_missing_locations(module), 'planner.py', 'exec'), ns)
        planner = ns['VLMPlanner'](model='m', transport=transport)
        obs = types.SimpleNamespace(extra={})
        await planner.one_step_tool_call('task', obs)
        planner.record_action_result(True, None)
        await planner.one_step_tool_call('task', obs)
        messages = requests[-1]['messages']
        self.assertEqual([m['role'] for m in messages], ['system', 'user', 'assistant', 'tool', 'user'])
        self.assertEqual(messages[2]['reasoning_content'], '  real reasoning  ')
        self.assertEqual(messages[2]['tool_calls'][0]['function']['arguments'], '{"x":12}')
        self.assertEqual(messages[3]['tool_call_id'], 'call1')
        self.assertNotIn('private image', repr(messages[1]))
        self.assertIn('private image', repr(messages[-1]))
        planner.record_action_result(False, 'gesture_failed', {'reason': 'target_ambiguous', 'candidates': [{'labels': ['Focus', 'Sound'], 'bounds': [0, 0, 100, 100]}]})
        await planner.one_step_tool_call('task', obs)
        self.assertEqual(requests[-1]['messages'][-2]['tool_call_id'], 'call2')
        self.assertEqual(requests[-1]['messages'][-3]['reasoning_content'], '')
        self.assertIn('gesture_failed', requests[-1]['messages'][-2]['content'])
        self.assertIn('semantic_ambiguity', requests[-1]['messages'][-2]['content'])
        self.assertIn('Sound', requests[-1]['messages'][-2]['content'])
        self.assertEqual(requests[-1]['messages'][-3]['tool_calls'][0]['function']['arguments'], '{\"x\":12}')
        planner.record_action_result(True, None)
        await planner.one_step_tool_call('task', obs)
        self.assertNotIn('reasoning_content', requests[-1]['messages'][-3])
        # No receipt for call4: unknown branch clears the conversation history.
        await planner.one_step_tool_call('task', obs)
        self.assertEqual([m['role'] for m in requests[-1]['messages']], ['system', 'user'])
        fresh = ns['VLMPlanner'](model='m', transport=transport)
        await fresh.one_step_tool_call('task', obs)
        self.assertEqual([m['role'] for m in requests[-1]['messages']], ['system', 'user'])
