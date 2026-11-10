"""Actual execution entry point, with recording host dispatch boundaries."""
import ast
import asyncio
from dataclasses import dataclass, replace
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock
from test_semantic_grounding import Action, Observation, module as grounding

@dataclass
class StepResult:
    success: bool
    action: object = None
    before: object = None
    error: str = None
    origin: str = None
    function_id: str = None
    detail: object = None

class ExecutionNodeIdTests(unittest.TestCase):
    def run_action(self, args, *, replay=False):
        source = ast.parse(Path(__file__).with_name('execution.py').read_text())
        selected = [n for n in source.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
                    and n.name in {'execute_robust_action', '_merge_action_detail'}]
        prepare = AsyncMock(side_effect=lambda action, **kw: SimpleNamespace(kind='allow', action=action, detail=None))
        dispatch = AsyncMock(return_value=StepResult(True))
        namespace = dict(resolve_semantic_action=grounding.resolve_semantic_action,
                         StepResult=StepResult, replace=replace, prepare_action=prepare,
                         _dispatch_prepared=dispatch, _action_uses_transfer_target=lambda action: False)
        exec('from __future__ import annotations\n' + ast.unparse(ast.Module(body=selected, type_ignores=[])), namespace)
        observation = Observation('<root><node id="7" clickable="true" bounds="[100,200][200,300]"/></root>',
                                  {'display': {'width': 1000, 'height': 1000}})
        result = asyncio.run(namespace['execute_robust_action'](
            Action('click', args), observation=observation, host=None, plugins=None,
            source_state=observation if replay else None))
        return result, prepare, dispatch

    def test_missing_live_node_never_dispatches_coordinates(self):
        result, prepare, dispatch = self.run_action({'node_id': '41', 'x': 20, 'y': 30})
        self.assertFalse(result.success)
        self.assertIn('node_id_not_resolved', result.error)
        self.assertEqual(result.detail['semantic_grounding']['reason'], 'node_id_missing')
        prepare.assert_not_called()
        dispatch.assert_not_called()

    def test_valid_live_node_replaces_coordinates_and_dispatches(self):
        result, _, dispatch = self.run_action({'node_id': '7', 'x': 20, 'y': 30})
        self.assertTrue(result.success)
        action = dispatch.call_args.args[0]
        self.assertEqual((action.args['x'], action.args['y']), (150, 250))

    def test_explicit_visual_action_remains_available(self):
        result, _, dispatch = self.run_action({'x': 20, 'y': 30})
        self.assertTrue(result.success)
        dispatch.assert_awaited_once()

    def test_recorded_transfer_keeps_existing_policy(self):
        result, prepare, dispatch = self.run_action({'node_id': '41', 'x': 20, 'y': 30}, replay=True)
        self.assertTrue(result.success)
        self.assertIsNotNone(prepare.call_args.kwargs['source_state'])
        dispatch.assert_awaited_once()

if __name__ == '__main__':
    unittest.main()
