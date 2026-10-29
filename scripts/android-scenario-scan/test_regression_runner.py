"""Offline regression contracts; mock tests do not constitute live-device evidence."""
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from types import SimpleNamespace

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location('regression_runner', HERE / 'regression.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
from regression_evidence import verify_alarm, verify_clock_timer, verify_runlog


class RunnerContracts(unittest.TestCase):
    def test_partial_streamed_tool_arguments_are_polled_without_resending(self):
        import json
        from unittest.mock import Mock
        h = runner.Harness.__new__(runner.Harness)
        h.args = SimpleNamespace(timeout=5)
        h.backend = SimpleNamespace(unlocked=lambda: None, start=lambda: SimpleNamespace(start_uiautomator=lambda: None))
        h.launch = h.tap = h.type = h.stop_u2 = h.save = lambda *args, **kwargs: None
        h.observe = lambda *args: {'nodes': [{'description': '', 'class_name': 'android.widget.EditText', 'text': ''}], 'observation_id': 'obs'}
        h.read_retry = lambda operation, *args: operation(*args)
        h.call = Mock()
        h.chat_snapshot = Mock(side_effect=[json.JSONDecodeError('partial', '{', 1), {'terminal': True, 'passed': True}])
        with patch.object(runner.time, 'sleep'):
            result = h.chat('test', 'execute')
        self.assertTrue(result['passed'])
        self.assertEqual(2, h.chat_snapshot.call_count)
        self.assertEqual(1, h.call.call_count)

    def test_transient_unknown_access_retries_read_without_bypassing_guard(self):
        from unittest.mock import Mock
        h = runner.Harness.__new__(runner.Harness)
        read = Mock(side_effect=[RuntimeError('app_access_state_unknown: refusing access'), 'snapshot'])
        with patch.object(runner.time, 'sleep'):
            self.assertEqual('snapshot', h.read_retry(read, 'request-id'))
        self.assertEqual(2, read.call_count)
        denied = Mock(side_effect=RuntimeError('app_access_denied: blocked'))
        with self.assertRaisesRegex(RuntimeError, 'app_access_denied'):
            h.read_retry(denied, 'request-id')
        self.assertEqual(1, denied.call_count)

    def test_nonterminal_entry_cannot_forge_prompt_completion(self):
        entry = {'type': 'user_message', 'status': 'completed', 'payload': {
            'sessionId': 'session', 'turnId': 'turn', 'streamMeta': {'stopReason': 'end_turn'}}}
        gate = runner.acp_terminal([entry])
        self.assertFalse(gate['passed'])
        self.assertFalse(gate['terminal'])

    def test_only_owned_final_text_is_accepted(self):
        entry = {'type': 'assistant_message', 'status': 'completed', 'payload': {
            'sessionId': 's', 'turnId': 't', 'content': {'text': '6'},
            'streamMeta': {'stopReason': 'end_turn', 'isFinal': True}}}
        self.assertEqual(['6'], runner.acp_terminal([entry])['final_texts'])
        entry['payload']['streamMeta']['isFinal'] = False
        self.assertEqual([], runner.acp_terminal([entry])['final_texts'])

    def test_last_owned_final_assistant_message_is_answer(self):
        def message(text):
            return {'type': 'assistant_message', 'status': 'completed', 'payload': {
                'sessionId': 's', 'turnId': 't', 'content': {'text': text},
                'streamMeta': {'stopReason': 'end_turn', 'isFinal': True}}}
        self.assertEqual(['9'], runner.acp_terminal([message('说明'), message('9')])['final_texts'])

    def test_multiple_owners_and_missing_owner_rejected(self):
        def entry(session, turn):
            return {'type': 'assistant_message', 'status': 'completed', 'payload': {
                'sessionId': session, 'turnId': turn, 'streamMeta': {'stopReason': 'end_turn'}}}
        with self.assertRaises(RuntimeError):
            runner.acp_terminal([entry('s1', 't1'), entry('s2', 't2')])
        with self.assertRaises(RuntimeError):
            runner.acp_terminal([entry(None, None)])

    def test_string_tool_arguments_are_decoded_at_boundary(self):
        entry = {'type': 'tool_event', 'status': 'completed', 'payload': {
            'sessionId': 's', 'turnId': 't', 'args': '{"title":"test"}',
            'rawResultJson': '{}', 'success': True, 'streamMeta': {'stopReason': 'end_turn'}}}
        self.assertEqual({'title': 'test'}, runner.acp_terminal([entry])['tools'][0]['args'])

    def test_provider_empty_and_malformed_are_not_empty_results(self):
        self.assertEqual([], runner.provider_rows('No result found.'))
        for raw in ('', 'garbage', 'Row: 0 missing_equals', 'Row: 0 id=1, id=2'):
            with self.subTest(raw=raw), self.assertRaises((RuntimeError, ValueError)):
                runner.provider_rows(raw)

    def test_acceptance_and_cleanup_are_mandatory(self):
        self.assertNotEqual('passed', runner.status_for({'passed': True}, {'passed': False}, {'passed': True}, True))
        self.assertNotEqual('passed', runner.status_for({'passed': True}, {'passed': True}, {'passed': False}, True))
        self.assertNotEqual('passed', runner.status_for({'passed': False}, {'passed': True}, {'passed': True}, True))
        self.assertEqual('passed', runner.status_for({'passed': True}, {'passed': True}, {'passed': True}, True))

    def test_gui_duplicate_matching_runs_does_not_resubmit(self):
        class Backend:
            broadcasts = 0
            def unlocked(self): pass
            def shell(self, *args):
                if args[:2] == ('am', 'broadcast'):
                    self.broadcasts += 1
                    return ''
                import json
                return json.dumps({'run_id': args[-1], 'goal': 'RUNfixed test', 'status': 'succeeded', 'success': True})
        backend = Backend()
        harness = runner.Harness.__new__(runner.Harness)
        harness.backend = backend
        harness.args = SimpleNamespace(timeout=5)
        harness.observe = lambda: None
        harness.stop_u2 = lambda: None
        saved = {}
        harness.save = lambda name, value: saved.update({name: value})
        snapshots = iter([set(), {'a.json', 'b.json'}])
        harness.run_files = lambda: next(snapshots)
        with patch.object(runner.uuid, 'uuid4', return_value=SimpleNamespace(hex='fixed')):
            with self.assertRaisesRegex(RuntimeError, 'duplicate_matching_gui_runs'):
                harness.gui('test', 'execute')
        self.assertEqual(1, backend.broadcasts)
        self.assertEqual('RUNfixed', saved['execute-submission.json']['request_id'])

    def test_uncertain_set_text_rechecks_fresh_unique_value_without_retyping(self):
        selector = {'resource_id': runner.NOTES + ':id/title_edit_text'}
        expected = '[测试] 中文'
        scenarios = [
            ('exact', [dict(selector, text=expected)], True),
            ('different', [dict(selector, text='不同')], False),
            ('ambiguous', [dict(selector, text=expected), dict(selector, text=expected)], False),
            ('missing', [], False),
        ]
        for label, fields, accepted in scenarios:
            with self.subTest(label=label):
                calls = []
                class Runtime:
                    def call(self, op, args, recover=False):
                        calls.append((op, args, recover))
                        return {'ok': False, 'uncertain': True, 'error': 'UiObjectNotFoundError: replaced view'}
                h = runner.Harness.__new__(runner.Harness)
                h.runtime = Runtime()
                observations = iter([{'observation_id': 'before', 'nodes': []},
                                     {'observation_id': 'fresh', 'nodes': fields}])
                h.observe = lambda package: next(observations)
                if accepted:
                    h.type(runner.NOTES, expected, **selector)
                else:
                    with self.assertRaisesRegex(RuntimeError, 'host_type_text'):
                        h.type(runner.NOTES, expected, **selector)
                self.assertEqual(1, len(calls))
                self.assertEqual('type_text', calls[0][0])
                self.assertEqual(expected, calls[0][1]['text'])
                self.assertEqual('before', calls[0][1]['observation_id'])
                self.assertFalse(calls[0][2])

    def test_open_note_ignores_ai_reference_and_taps_regular_coordinates(self):
        harness, calls = self.note_harness(regular=1, detail_title='[测试] abc')
        detail = harness.open_note('[测试] abc')
        self.assertEqual('detail', detail['observation_id'])
        self.assertEqual(1, len(calls))
        self.assertEqual({'x': 150, 'y': 250, 'observation_id': 'search'}, calls[0])

    def test_open_note_refuses_two_regular_results_before_tap(self):
        harness, calls = self.note_harness(regular=2, detail_title='[测试] abc')
        with self.assertRaisesRegex(RuntimeError, 'note_regular_search_result_not_unique'):
            harness.open_note('[测试] abc')
        self.assertEqual([], calls)

    def test_open_note_rejects_mismatched_detail_title(self):
        harness, calls = self.note_harness(regular=1, detail_title='other')
        with self.assertRaisesRegex(RuntimeError, 'note_detail_title_not_verified'):
            harness.open_note('[测试] abc')
        self.assertEqual(1, len(calls))

    def note_harness(self, regular, detail_title):
        title = '[测试] abc'
        prefix = runner.NOTES + ':id/'
        reference = f'<node resource-id="{prefix}reference_layout"><node resource-id="{prefix}note_title" text="{title}" bounds="[10,20][90,80]"/></node>'
        plain = ''.join(f'<node resource-id="{prefix}note_title" text="{title}" bounds="[{100+i*200},200][{200+i*200},300]"/>' for i in range(regular))
        xml = '<hierarchy>' + reference + plain + '</hierarchy>'
        nodes = [{'text': title, 'bounds': [10,20,90,80]}] + [
            {'text': title, 'bounds': [100+i*200,200,200+i*200,300]} for i in range(regular)]
        calls = []
        class Runtime:
            def call(self, op, args, recover=False):
                assert op == 'tap' and recover is False
                calls.append(args)
                return {'ok': True}
        h = runner.Harness.__new__(runner.Harness)
        h.backend = SimpleNamespace(tree=lambda: xml)
        h.wait_for_clear_input = lambda obs, point: False
        h.runtime = Runtime()
        h.note_search = lambda _: {'nodes': nodes, 'observation_id': 'search'}
        h.observe = lambda _: {'nodes': [{'text': detail_title, 'resource_id': prefix + 'title_edit_text'}], 'observation_id': 'detail'}
        return h, calls

    def test_gui_run_identity_and_unknown_clock_layout(self):
        run = {'run_id': 'r1', 'goal': 'RUN-marker 计算', 'status': 'succeeded', 'success': True}
        self.assertFalse(verify_runlog(run, 'RUN-other')['passed'])
        self.assertFalse(verify_runlog(run, 'RUN-marker', seen_run_ids=['r1'])['passed'])
        self.assertFalse(verify_alarm('<hierarchy/>', 'test')['passed'])
        self.assertFalse(verify_clock_timer('<hierarchy/>', 'test')['passed'])


if __name__ == '__main__':
    unittest.main()
