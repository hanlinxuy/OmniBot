import unittest
from regression_evidence import calendar_read_scope_matches, parse_screen, verify_clock_timer, verify_alarm, verify_calendar, verify_ocr_lines, verify_note_lines, verify_runlog, verify_todo, verify_weather_note, verify_zip_turn

class EvidenceTests(unittest.TestCase):
    def test_calendar_query_scope_rejects_missing_or_wrong_calendar(self):
        read = {'tool': 'calendar_event_list', 'args': {'calendarId': '1', 'query': 'marker'}}
        self.assertTrue(calendar_read_scope_matches([], 1))  # no out-of-scope read; not proof of a read
        self.assertTrue(calendar_read_scope_matches([read], 1))
        self.assertFalse(calendar_read_scope_matches([{**read, 'args': {'query': 'marker'}}], 1))
        self.assertFalse(calendar_read_scope_matches([read, {**read, 'args': {'calendarId': '2'}}], 1))

    def test_zip_requires_exact_owned_read_and_final_answer(self):
        call = {'tool': 'terminal_execute', 'success': True, 'args': {'command': 'unzip -p /test/fixture.zip file_1.txt | wc -l'}, 'result': {'stdout': '6', 'success': True, 'resultCode': 0}}
        self.assertTrue(verify_zip_turn([call], ['6'], '/test/fixture.zip', 6)['passed'])
        self.assertFalse(verify_zip_turn([call], ['6'], '/other/fixture.zip', 6)['passed'])
        self.assertFalse(verify_zip_turn([call], ['4'], '/test/fixture.zip', 6)['passed'])
        self.assertFalse(verify_zip_turn([call, call], ['6'], '/test/fixture.zip', 6)['passed'])

    def test_zip_python_read_requires_exact_provenance_and_success(self):
        program = """import zipfile
p='/test/fixture.zip'
z=zipfile.ZipFile(p)
print(z.namelist())
d=z.read('file_1.txt')
print('bytes',len(d))
print('newlines',d.count(b'\\n'))
print('crlf',d.count(b'\\r\\n'))
print('endsWithNewline', d.endswith(b'\\n'))"""
        call = {'tool': 'terminal_execute', 'success': True, 'args': {'command': "python3 - <<'EOF'\n" + program + "\nEOF"}, 'result': {'success': True, 'resultCode': 0, 'stdout': "['file_1.txt']\nbytes 231\nnewlines 7\ncrlf 0\nendsWithNewline True"}}
        self.assertTrue(verify_zip_turn([call], ['7'], '/test/fixture.zip', 7)['passed'])
        self.assertFalse(verify_zip_turn([call], ['7'], '/other/fixture.zip', 7)['passed'])
        changed = {**call, 'args': {'command': call['args']['command'].replace('file_1.txt', 'other.txt')}}
        self.assertFalse(verify_zip_turn([changed], ['7'], '/test/fixture.zip', 7)['passed'])
        self.assertFalse(verify_zip_turn([{**call, 'success': False}], ['7'], '/test/fixture.zip', 7)['passed'])
        self.assertFalse(verify_zip_turn([{**call, 'args': {'command': 'echo 7'}}], ['7'], '/test/fixture.zip', 7)['passed'])

    def test_zip_python_inline_read_is_bound_to_owned_fixture(self):
        program = "import zipfile\np='/test/fixture.zip'\nz=zipfile.ZipFile(p)\nd=z.read('file_1.txt')\nprint(d.count(b'\\n'))\nprint(z.namelist())\n"
        call = {'tool': 'terminal_execute', 'success': True, 'args': {'command': 'python3 -c "' + program + '"'}, 'result': {'success': True, 'resultCode': 0, 'stdout': "9\n['file_1.txt']"}}
        self.assertTrue(verify_zip_turn([call], ['9'], '/test/fixture.zip', 9)['passed'])
        self.assertFalse(verify_zip_turn([call], ['9'], '/other/fixture.zip', 9)['passed'])
        self.assertFalse(verify_zip_turn([{**call, 'result': {**call['result'], 'resultCode': 1}}], ['9'], '/test/fixture.zip', 9)['passed'])
        self.assertFalse(verify_zip_turn([{**call, 'args': {'command': call['args']['command'].replace('file_1.txt', 'other.txt')}}], ['9'], '/test/fixture.zip', 9)['passed'])

    def test_zip_python_diagnostic_requires_owned_entry_and_nonconstant_count(self):
        program = """import zipfile
p='/test/fixture.zip'
z=zipfile.ZipFile(p)
print(z.namelist())
data=z.read('file_1.txt')
print(repr(data[-50:]))
print('newlines:', data.count(b'\\n'))
print('len:', len(data))"""
        call = {'tool': 'terminal_execute', 'success': True, 'args': {'command': "python3 - <<'EOF'\n" + program + "\nEOF"}, 'result': {'success': True, 'resultCode': 0, 'stdout': "['file_1.txt']\nb'abc\\n'\nnewlines: 11\nlen: 363"}}
        self.assertTrue(verify_zip_turn([call], ['11'], '/test/fixture.zip', 11)['passed'])
        self.assertFalse(verify_zip_turn([call], ['11'], '/other/fixture.zip', 11)['passed'])
        altered = {**call, 'args': {'command': call['args']['command'].replace("data.count(b'\\n')", '11')}}
        self.assertFalse(verify_zip_turn([altered], ['11'], '/test/fixture.zip', 11)['passed'])

    def test_weather_note_requires_both_source_lines_inside_body(self):
        weather = [('02:00', '17', '多云'), ('04:00', '16', '多云')]
        rows = [{'text': '02:00 17°C 多云', 'x': .1, 'y': .2, 'width': .4, 'height': .05}, {'text': '04:00 16°C 多云', 'x': .1, 'y': .3, 'width': .4, 'height': .05}]
        self.assertTrue(verify_weather_note(rows, (0, 0, 1, 1), weather)['passed'])
        self.assertFalse(verify_weather_note(rows, (0, 0, 1, .29), weather)['passed'])
        self.assertFalse(verify_weather_note(rows, (0, 0, 1, 1), [('02:00', '18', '多云'), weather[1]])['passed'])

    def test_todo_rejects_same_title_in_another_state(self):
        self.assertTrue(verify_todo([('MARK', '未完成,MARK')], 'MARK')['passed'])
        self.assertFalse(verify_todo([('MARK', '未完成,MARK'), ('MARK', '已完成,MARK')], 'MARK')['passed'])
        self.assertFalse(verify_todo([('MARK', '已完成,MARK')], 'MARK')['passed'])

    def test_screen_package_exact(self):
        xml = '<hierarchy><node package="a" text="one"/><node package="ab" text="two"/></hierarchy>'
        self.assertEqual([n['text'] for n in parse_screen(xml, 'a')], ['one'])

    def test_timer_requires_target_and_active_control_same_row(self):
        from pathlib import Path
        xml = Path(__file__).with_name('fixtures').joinpath('vivo-timers-synthetic.xml').read_text()
        self.assertEqual(verify_clock_timer(xml, 'MARK'), {'target_present': True, 'target_count': 1, 'active_count': 1, 'passed': True})
        first, rest = xml.split('content-desc="暂停"', 1)
        wrong_row = first + 'content-desc="开始"' + rest
        self.assertFalse(verify_clock_timer(wrong_row, 'MARK')['passed'])
        self.assertFalse(verify_clock_timer(xml.replace('cl_content_layout', 'unknown'), 'MARK')['passed'])
        self.assertFalse(verify_clock_timer(xml.replace('text="OTHER"', 'text="MARK"'), 'MARK')['passed'])
        self.assertTrue(verify_clock_timer(xml, 'MARK', 1500, 22)['passed'])
        self.assertFalse(verify_clock_timer(xml, 'MARK', 60, 22)['passed'])
        self.assertFalse(verify_clock_timer(xml.replace('剩余24分钟38秒', '剩余未知'), 'MARK', 1500, 22)['passed'])
        with self.assertRaises(ValueError):
            verify_clock_timer(xml, 'MARK', 1500)

    def test_alarm_exact_marker_time_enabled_daily_and_uniqueness(self):
        xml = '<hierarchy><node package="com.android.BBKClock" content-desc="已开启,07:00,MARK,每天,开关"/></hierarchy>'
        self.assertTrue(verify_alarm(xml, 'MARK')['passed'])
        self.assertFalse(verify_alarm(xml.replace('MARK', 'MARK-other'), 'MARK')['passed'])
        self.assertFalse(verify_alarm(xml.replace('已开启', '已关闭'), 'MARK')['passed'])
        self.assertFalse(verify_alarm(xml.replace('com.android.BBKClock', 'other.app'), 'MARK')['passed'])
        self.assertFalse(verify_alarm(xml.replace('每天', '周一'), 'MARK')['passed'])
        self.assertFalse(verify_alarm(xml.replace('</hierarchy>', '<node package="com.android.BBKClock" content-desc="已开启,07:00,MARK,每天,开关"/></hierarchy>'), 'MARK')['passed'])

    def test_calendar_reminder_belongs_to_exact_unique_event(self):
        event = {'id': 7, 'title': 'MARK', 'start_ms': 10, 'end_ms': 20, 'calendar_id': 4}
        reminder = {'event_id': 7, 'minutes': 60}
        self.assertTrue(verify_calendar([event], [reminder], 'MARK', 10, 20, 4)['passed'])
        self.assertFalse(verify_calendar([event], [{'event_id': 8, 'minutes': 60}], 'MARK', 10, 20, 4)['passed'])
        self.assertFalse(verify_calendar([event, event], [reminder], 'MARK', 10, 20, 4)['passed'])
        self.assertFalse(verify_calendar([event], [reminder], 'MARK', 10, 21, 4)['passed'])
        self.assertFalse(verify_calendar([{**event, 'id': None}], [{'minutes': 60}], 'MARK', 10, 20, 4)['passed'])
        self.assertTrue(verify_calendar([{**event, 'timezone': 'Asia/Shanghai'}], [reminder], 'MARK', 10, 20, 4, 'Asia/Shanghai')['passed'])
        self.assertFalse(verify_calendar([event], [reminder], 'MARK', 10, 20, 4, 'Asia/Shanghai')['passed'])
        self.assertFalse(verify_calendar([{**event, 'timezone': 'UTC'}], [reminder], 'MARK', 10, 20, 4, 'Asia/Shanghai')['passed'])

    def test_ocr_line_equality_not_numeric_substring(self):
        self.assertTrue(verify_ocr_lines('  total  408\n done ', ['total 408', 'done'])['passed'])
        self.assertFalse(verify_ocr_lines('total 14080\ndone', ['total 408', 'done'])['passed'])
        self.assertFalse(verify_ocr_lines('abc', [])['passed'])

    def test_note_body_region_order_and_exact_lines(self):
        first = {'text': '番 茄', 'x': .1, 'y': .3, 'width': .2, 'height': .03}
        second = {'text': '鸡蛋', 'x': .1, 'y': .4, 'width': .2, 'height': .03}
        rect = (0, .2, 1, .8)
        self.assertTrue(verify_note_lines([second, first], rect)['passed'])
        self.assertFalse(verify_note_lines([first, {**second, 'y': .25}], rect)['passed'])
        self.assertFalse(verify_note_lines([first, second], (0, .2, 1, .35))['passed'])
        self.assertFalse(verify_note_lines([first, second])['passed'])
        self.assertFalse(verify_note_lines([first, second, {**second, 'y': .5}], rect)['passed'])
        self.assertTrue(verify_note_lines([first, second], rect, ['番茄', '鸡蛋'])['passed'])
        self.assertFalse(verify_note_lines([first, second], rect, ['鸡蛋', '番茄'])['passed'])

    def test_runlog_identity_and_terminal_success_are_only_subgate(self):
        run = {'goal': 'MARK calculate', 'run_id': 'r1', 'status': 'succeeded', 'success': True}
        self.assertTrue(verify_runlog(run, 'MARK')['passed'])
        self.assertFalse(verify_runlog(run, 'MARK', ['r1'])['passed'])
        self.assertFalse(verify_runlog({**run, 'status': 'running'}, 'MARK')['passed'])
        self.assertFalse(verify_runlog({**run, 'goal': 'old'}, 'MARK')['passed'])
        self.assertFalse(verify_runlog({**run, 'success': False}, 'MARK')['passed'])
        with self.assertRaises(ValueError):
            verify_runlog(run, '')

if __name__ == '__main__': unittest.main()
