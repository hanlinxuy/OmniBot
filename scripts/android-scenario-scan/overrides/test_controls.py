"""Offline local-loop tests against the pinned runtime's real model dataclasses."""
import ast
import asyncio
import dataclasses
import importlib.util
import pathlib
import sys
import types
import unittest
import zipfile
from unittest.mock import patch

HERE = pathlib.Path(__file__).parent
with zipfile.ZipFile(HERE.parents[2] / 'artifacts/omniflow-gui-runtime-2.1.8.zip') as archive:
    tree = ast.parse(archive.read('scripts/runtime/python/omniflow/core/model.py'))
model = types.ModuleType('controls_test_model')
sys.modules[model.__name__] = model
model.__dict__.update(dataclass=dataclasses.dataclass, field=dataclasses.field)
classes = [n for n in tree.body if isinstance(n, ast.ClassDef) and n.name in ('Action', 'Observation')]
future = ast.ImportFrom(module='__future__', names=[ast.alias(name='annotations')], level=0)
exec(compile(ast.fix_missing_locations(ast.Module(body=[future, *classes], type_ignores=[])), 'model.py', 'exec'), model.__dict__)
spec = importlib.util.spec_from_file_location('local_controls', HERE / 'controls.py')
controls = importlib.util.module_from_spec(spec)
with patch.dict(sys.modules, {'omniflow.core.model': model}):
    spec.loader.exec_module(controls)


def screen(body, package='example.app'):
    return model.Observation.from_value({'package_name': package, 'xml': '<hierarchy>'+body+'</hierarchy>',
        'extra': {'display': {'width': 1000, 'height': 1000}}})


def node(text, left=0):
    return f'<node text="{text}" enabled="true" clickable="true" bounds="[{left},0][{left+100},100]"/>'


class ControlsTests(unittest.IsolatedAsyncioTestCase):
    async def run_control(self, name, args, frames, success=True):
        controls.validate_control(name, args)
        pending = iter(frames)
        current = frames[0]
        actions = []
        async def observe():
            nonlocal current
            current = next(pending, current)
            return current
        async def dispatch(action, before):
            actions.append(action)
            return types.SimpleNamespace(success=success, error=None if success else 'rejected', after=current)
        result = await controls.execute_control(name, args, observation=current, observe=observe, dispatch=dispatch)
        return result, actions

    async def test_batch_resolves_each_target_in_new_tree(self):
        args = {'package_name': 'example.app', 'steps': [
            {'operation': 'click', 'selector': {'text': '1'}}, {'operation': 'click', 'selector': {'text': '2'}}]}
        actions = []
        observations = []
        async def observe():
            observations.append(1)
            return screen(node('1'))
        async def dispatch(action, before):
            actions.append(action)
            return types.SimpleNamespace(success=True, error=None, after=screen(node('2', 400)))
        result = await controls.execute_control('ui_steps', args, observation=screen(node('1')), observe=observe, dispatch=dispatch)
        self.assertTrue(result[0]); self.assertEqual(result[2]['completed'], 2)
        self.assertEqual([a.args['x'] for a in actions], [50, 450])
        self.assertEqual(len(observations), 1)

    async def test_batch_refreshes_when_dispatch_has_no_post_action_observation(self):
        args = {'package_name': 'example.app', 'steps': [
            {'operation': 'click', 'selector': {'text': '1'}}, {'operation': 'click', 'selector': {'text': '2'}}]}
        frames = iter([screen(node('1')), screen(node('2', 400))])
        async def observe(): return next(frames)
        async def dispatch(action, before): return types.SimpleNamespace(success=True, error=None, after=None)
        result = await controls.execute_control('ui_steps', args, observation=screen(node('1')), observe=observe, dispatch=dispatch)
        self.assertTrue(result[0]); self.assertEqual(result[2]['timing_ms']['refresh_count'], 2)

    async def test_batch_stops_on_dispatch_rejection_without_replay(self):
        result, actions = await self.run_control('ui_steps', {'package_name': 'example.app', 'steps': [
            {'operation': 'click', 'selector': {'text': '1'}}, {'operation': 'click', 'selector': {'text': '1'}}]},
            [screen(node('1'))], success=False)
        self.assertFalse(result[0]); self.assertEqual(len(actions), 1)
        self.assertEqual(result[2]['stopped_index'], 0)

    async def test_ambiguous_target_never_dispatches(self):
        result, actions = await self.run_control('ui_steps', {'package_name': 'example.app', 'steps': [
            {'operation': 'click', 'selector': {'text': '1'}}]}, [screen(node('1')+node('1', 400))])
        self.assertFalse(result[0]); self.assertEqual(actions, [])

    async def test_empty_tree_does_not_prove_gone(self):
        result, actions = await self.run_control('wait_until', {'package_name': 'example.app', 'selector': {'text': '1'}, 'condition': 'gone'}, [screen('')])
        self.assertEqual(result[2]['error'], 'control_tree_unavailable'); self.assertEqual(actions, [])

    async def test_package_change_stops_wait(self):
        result, _ = await self.run_control('wait_until', {'package_name': 'example.app', 'selector': {'text': '1'}, 'condition': 'appears'}, [screen(node('1'), 'other.app')])
        self.assertEqual(result[2]['error'], 'control_package_changed')

    async def test_wait_observes_without_actions_and_times_out(self):
        result, actions = await self.run_control('wait_until', {'package_name': 'example.app', 'selector': {'text': '2'}, 'condition': 'appears', 'timeout_ms': 200}, [screen(node('1'))])
        self.assertEqual(result[2]['error'], 'control_wait_timeout'); self.assertEqual(actions, [])

    async def test_wheel_requires_stable_actual_readback(self):
        def wheel(value):
            return screen(f'<node resource-id="wheel" bounds="[0,0][100,300]">'+''.join(
                f'<node text="{v}" bounds="[0,{i*100}][100,{(i+1)*100}]"/>' for i,v in enumerate([value-1,value,value+1]))+'</node>')
        result, actions = await self.run_control('set_wheel', {'package_name': 'example.app', 'selector': {'resource_id': 'wheel'}, 'value': '8'}, [wheel(8), wheel(9), wheel(9)])
        self.assertFalse(result[0]); self.assertEqual(result[2]['error'], 'control_wheel_not_moving')
        self.assertEqual(len(actions), 1)

    def test_nested_and_unbounded_arguments_rejected(self):
        for args in [ {'package_name': 'example.app', 'steps': [{'operation': 'ui_steps'}]},
                      {'package_name': 'example.app', 'steps': [{'operation': 'click','selector': {'text': '1'}}]*9},
                      {'package_name': 'example.app', 'steps': [{'operation': 'click','selector': {'index': 0}}]} ]:
            with self.assertRaises(ValueError): controls.validate_control('ui_steps', args)

class SettleTests(unittest.IsolatedAsyncioTestCase):
    async def test_only_confirmed_host_stability_skips_duplicate_sleep(self):
        tree = ast.parse((HERE / 'core.py').read_text())
        fn = next(n for n in tree.body if isinstance(n, ast.AsyncFunctionDef) and n.name == 'execute_action')
        for state, result, expected_sleeps in [('host_completed','stable',0), ('host_completed','completed_by_action',0),
                ('host_completed','runtime_delegated',1), ('runtime_delegated','timeout',1), ('','',1)]:
            sleeps = []
            async def sleep(seconds): sleeps.append(seconds)
            async def prepare(action, **kwargs): return types.SimpleNamespace(kind='ready', action=action, detail={})
            async def await_value(value): return value
            namespace = {'prepare_action': prepare, '_await': await_value, '_ACTION_SETTLE_SECONDS': 1,
                'asyncio': types.SimpleNamespace(sleep=sleep), 'time': __import__('time'), 'Observation': model.Observation,
                'ActionResult': types.SimpleNamespace(from_value=lambda x: x),
                'StepResult': lambda success, **kw: types.SimpleNamespace(success=success, **kw)}
            exec(compile(ast.fix_missing_locations(ast.Module(body=[future, fn], type_ignores=[])), 'core.py', 'exec'), namespace)
            obs = screen(node('1'))
            host = types.SimpleNamespace(act=lambda a: types.SimpleNamespace(success=True, error=None,
                extra={'diagnostics': {'state_stabilization': state, 'state_stabilization_result': result}}), observe=lambda **kw: obs)
            step = await namespace['execute_action'](model.Action('click',{}), observation=obs, host=host, plugins=None)
            self.assertTrue(step.success)
            self.assertEqual(len(sleeps), expected_sleeps)

    def test_vivo_single_value_seekbar_and_displayed_date(self):
        node = __import__('xml.etree.ElementTree', fromlist=['fromstring']).fromstring(
            '<node class="android.widget.SeekBar" text="11月6日周五" bounds="[0,0][100,300]" actions="4096,8192"/>')
        reading, rows = controls.wheel_read(node)
        self.assertEqual(reading[0], controls.wheel_value('11月6日'))
        self.assertEqual(len(rows), 1)

class DateAndParserTests(unittest.TestCase):
    def test_date_aliases_use_observed_device_date_without_inventing_year(self):
        target = controls.wheel_value('2026-11-06', '2026-10-31')
        self.assertEqual(target, controls.wheel_value('11月6日周五', '2026-10-31'))
        self.assertEqual(controls.wheel_value('今天周六', '2026-10-31'), controls.wheel_value('10月31日', '2026-10-31'))
        with self.assertRaises(ValueError): controls.wheel_value('2027-11-06', '2026-10-31')
        with self.assertRaises(ValueError): controls.wheel_value('今天周六')

    def test_semantic_wheel_failure_returns_to_existing_loop(self):
        # A well-typed model value must not kill planning before a tool receipt can explain it.
        controls.validate_control('set_wheel', {'package_name': 'example.app', 'selector': {'resource_id': 'wheel'}, 'value': 'unreadable date'})

    def test_planner_exposes_and_parses_control_without_coordinate_adapter(self):
        tree = ast.parse((HERE / 'planner.py').read_text())
        definitions = [n for n in tree.body if isinstance(n, (ast.ClassDef,ast.FunctionDef)) and n.name in ('ModelToolCallError','parse_model_turn_response')]
        namespace = {'Any': object, 'json': __import__('json'), 'ToolCall': lambda name, arguments: types.SimpleNamespace(name=name, arguments=arguments),
            'vlm_action_tools': lambda: [], 'CONTROL_NAMES': controls.CONTROL_NAMES, 'validate_control': controls.validate_control}
        exec(compile(ast.fix_missing_locations(ast.Module(body=[future,*definitions],type_ignores=[])), 'planner.py', 'exec'), namespace)
        call, _ = namespace['parse_model_turn_response']({'requested_model': 'test', 'tool_calls': [{'function': {'name': 'wait_until', 'arguments': '{"package_name":"example.app","condition":"stable"}'}}]}, requested_model='test',turn_index=1)
        self.assertEqual(call.name,'wait_until')

class ProviderContractTests(unittest.TestCase):
    def test_existing_engine_handler_allows_only_one_no_dispatch_correction(self):
        tree = ast.parse((HERE / 'engine.py').read_text())
        handler = next(n for n in ast.walk(tree) if isinstance(n, ast.ExceptHandler)
                       and any(isinstance(x, ast.Constant) and x.value == 'provider_contract_corrections' for x in ast.walk(n)))
        body = ast.parse('def exercise(errors):\n provider_contract_corrected=False\n planner_diagnostics={}\n model_calls=0\n for error in errors:\n  pass\n return planner_diagnostics, model_calls\n').body[0]
        body.body[3].body = handler.body
        class ContractError(ValueError):
            code = 'provider_tool_call_contract_violation:expected_one_native_tool_call:got_2'
        ns = {'ModelToolCallError': ContractError, '_take_planner_metadata': lambda p: {},
              '_merge_planner_diagnostics': lambda d,m: None, '_take_llm_usage': lambda p: {},
              'merge_usage': lambda *a,**kw: None, '_usage_model_calls': lambda *a,**kw: 1,
              'self': types.SimpleNamespace(planner=None), 'llm_usage': {}, 'profile':None, 'trace':[],
              'function_session':types.SimpleNamespace(selected_id=None, failed_id=None),
              'actions_executed':0, 'fallback_steps':0, 'observation':None,
              'finish':lambda success,**kw: ('stopped',kw['model_calls'])}
        exec(compile(ast.fix_missing_locations(ast.Module(body=[body],type_ignores=[])), 'engine_handler.py','exec'),ns)
        self.assertEqual(ns['exercise']([ContractError()]), ({'provider_contract_corrections':1},1))
        self.assertEqual(ns['exercise']([ContractError(),ContractError()]), ('stopped',2))
        self.assertEqual(ns['exercise']([ValueError('other error')]), ('stopped',1))
