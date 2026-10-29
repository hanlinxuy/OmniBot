#!/usr/bin/env python3
"""Plan or run scoped Android regressions; private evidence, no replay on uncertainty."""
import argparse
import base64
from collections import Counter
from datetime import datetime, timedelta
import fcntl
import hashlib
import zipfile
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

from regression_evidence import verify_alarm, verify_calendar, verify_clock_timer, verify_ocr_lines, verify_runlog, verify_note_lines, verify_todo, verify_weather_note, verify_zip_turn

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'docs/testing/android-regression-cases.json'
OMNI = 'cn.com.omnimind.bot'
CLOCK = 'com.android.BBKClock'
NOTES = 'com.android.notes'
CALC = 'com.android.bbkcalculator'
PACKAGES = {'calculator': CALC, 'notes': NOTES, 'timer': CLOCK, 'alarm': CLOCK, 'calendar': OMNI, 'stopwatch': CLOCK, 'todo': NOTES, 'weather_notes': NOTES, 'zip_lines': OMNI}


def read_cases(path=MANIFEST):
    data = json.loads(Path(path).read_text())
    cases = data['cases']
    if len({c['id'] for c in cases}) != len(cases):
        raise ValueError('duplicate_case_id')
    for case in cases:
        if case['adapter'] not in PACKAGES or case['layer'] not in ('normal_chat', 'gui_component'):
            raise ValueError('unsupported_case_boundary')
        if '{marker}' not in case['prompt_template']:
            raise ValueError('case_requires_unique_marker')
    return cases


def provider_rows(text):
    if text.strip() == 'No result found.':
        return []
    if not text.strip():
        raise RuntimeError('provider_output_empty')
    rows = []
    for line in text.splitlines():
        match = re.fullmatch(r'Row: \d+ (.+)', line.strip())
        if not match:
            raise RuntimeError('provider_query_failed_or_unknown_format')
        fields = [field.split('=', 1) for field in re.split(r', (?=[A-Za-z_]+[A-Za-z_0-9]*=)', match[1])]
        if any(len(field) != 2 for field in fields) or len({field[0] for field in fields}) != len(fields):
            raise RuntimeError('provider_fields_invalid_or_duplicate')
        rows.append(dict(fields))
    return rows


def acp_terminal(entries):
    """Read the existing reducer's owning PromptResponse projection, not UI prose."""
    owners = set()
    reasons = set()
    tools = []
    final_texts = []
    for entry in entries:
        # Raw notifications and user input are not the reducer's PromptResponse projection.
        if entry['type'] not in ('assistant_message', 'ui_card', 'tool_event'):
            continue
        payload = entry['payload']
        meta = payload.get('streamMeta') or {}
        session = payload.get('sessionId') or meta.get('sessionId')
        turn = payload.get('turnId') or meta.get('turnId')
        if session and turn:
            owners.add((session, turn))
        if meta.get('stopReason'):
            if not session or not turn:
                raise RuntimeError('terminal_without_acp_owner')
            reasons.add(meta['stopReason'])
        if entry['type'] == 'assistant_message' and meta.get('isFinal') and meta.get('stopReason') == 'end_turn':
            final_texts.append((payload.get('content') or {}).get('text'))
        if entry['type'] == 'tool_event':
            if not session or not turn:
                raise RuntimeError('tool_without_acp_owner')
            raw = json.loads(payload.get('rawResultJson') or '{}')
            arguments = payload.get('args') or {}
            if isinstance(arguments, str):
                arguments = json.loads(arguments)
            if not isinstance(arguments, dict):
                raise RuntimeError('tool_arguments_not_object')
            tools.append({'tool': payload.get('toolName'), 'status': entry['status'],
                          'args': arguments, 'result': raw.get('result'),
                          'success': payload.get('success'), 'sessionId': session, 'turnId': turn})
    if len(owners) > 1:
        raise RuntimeError('multiple_acp_turns_in_single_submission')
    return {'terminal': bool(reasons), 'passed': reasons == {'end_turn'},
            'owners': [list(x) for x in owners], 'stop_reasons': sorted(reasons), 'tools': tools, 'final_texts': final_texts}


def status_for(execution, acceptance, cleanup, submitted):
    if cleanup.get('passed') is not True:
        return 'cleanup_failed' if submitted else 'blocked'
    if execution.get('passed') and acceptance.get('passed'):
        return 'passed'
    return 'failed' if submitted else 'blocked'


class Harness:
    def __init__(self, args, output, backend=None):
        sys.path.insert(0, str(args.device_runtime_root.resolve()))
        from vivo_phone_use.backend import AndroidBackend
        from vivo_phone_use.runtime import Runtime
        self.args, self.output = args, output
        self.runtime = Runtime(backend or AndroidBackend(args.serial, args.adb), args.device_runtime_root,
                               run=output / 'host-observations')
        self.backend = self.runtime.backend
        self.running = False
        self.submitted = False
        self.active_marker = None
        self.last_observation = None
        self.serialized_evidence = []

    def save(self, name, value):
        path = self.output / name
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
        self.serialized_evidence.append(str(path))
        return value

    def call(self, op, args):
        value = self.runtime.call(op, args, recover=False)
        if not value.get('ok'):
            raise RuntimeError('host_' + op + ':' + str(value.get('error')))
        return value

    def observe(self, package=None):
        if self.running:
            raise RuntimeError('observation_during_execution_forbidden')
        for attempt in range(4):
            obs = self.runtime.call('observe', {'mode': 'tree', 'max_nodes': 500, 'archive_image': False}, recover=False)
            if obs.get('ok'):
                break
            error = str(obs.get('error'))
            if attempt == 3 or not error.startswith(('app_access_state_unknown:', 'input_context_changed:')):
                raise RuntimeError('host_observe:' + error)
            time.sleep(.5)  # only repeat observation; never replay a dispatched mutation
        if obs.get('truncated') or obs.get('context_changed_during_capture'):
            raise RuntimeError('incomplete_or_unstable_observation')
        if package and obs['context']['package'] != package:
            raise RuntimeError('unexpected_foreground_package')
        self.last_observation = obs
        return obs

    def screen(self, name, package):
        obs = self.observe(package)
        with self.runtime.lock():
            self.runtime.guard()
            xml = self.backend.tree()
            png = self.backend.screenshot()
        after = self.observe(package)
        if after['context'] != obs['context']:
            raise RuntimeError('capture_context_changed')
        if package == NOTES:
            ids = {NOTES + ':id/title_edit_text', NOTES + ':id/line_edit_text'}
            before_fields = [(n['resource_id'], n['text'], n['bounds']) for n in obs['nodes'] if n['resource_id'] in ids]
            after_fields = [(n['resource_id'], n['text'], n['bounds']) for n in after['nodes'] if n['resource_id'] in ids]
            if before_fields != after_fields:
                raise RuntimeError('note_capture_bounds_or_title_changed')
        (self.output / (name + '.xml')).write_text(xml)
        (self.output / (name + '.png')).write_bytes(png)
        self.save(name + '.json', obs)
        return obs, xml

    def ocr(self, name):
        output = subprocess.check_output(['swift', str(Path(__file__).with_name('ocr.swift')),
                                          str(self.output / (name + '.png'))], timeout=60)
        return self.save(name + '-ocr.json', json.loads(output))

    def calculator_blank(self, name):
        from PIL import Image
        with Image.open(self.output / (name + '.png')) as image:
            w, h = image.size
            area = image.convert('RGB').crop((int(w*.45), int(h*.16), int(w*.95), int(h*.34)))
            extrema = area.getextrema()
        return self.save(name + '-display.json', {'passed': all(low >= 240 and high-low <= 3 for low, high in extrema), 'channel_extrema': extrema})

    def wait_for_clear_input(self, obs, point):
        from vivo_phone_use.notification_policy import window_metadata, check_input_metadata
        for wait_attempt in range(10):
            try:
                check_input_metadata(window_metadata(self.backend), obs['context'], [point])
                return wait_attempt > 0
            except RuntimeError as error:
                if wait_attempt == 9 or not str(error).startswith('notification_interference:'):
                    raise
                time.sleep(1)  # wait before the first input; never dismiss or replay

    def tap(self, package, postcondition=None, **selector):
        obs = self.observe(package)
        matches = [n for n in obs['nodes'] if all(n.get(k) == v for k, v in selector.items())]
        if len(matches) == 1:
            x1, y1, x2, y2 = matches[0]['bounds']
            if self.wait_for_clear_input(obs, ((x1+x2)//2, (y1+y2)//2)):
                obs = self.observe(package)
        value = self.runtime.call('tap', {'selector': selector, 'observation_id': obs['observation_id']}, recover=False)
        if not value.get('ok'):
            if str(value.get('error')).startswith('notification_interference:'):
                from vivo_phone_use.notification_policy import window_metadata
                self.save('blocked-input-window-metadata.json', {'selector': selector, 'metadata': window_metadata(self.backend)})
            if postcondition and value.get('uncertain') and str(value.get('error')).startswith('app_access_state_unknown:'):
                fresh = self.observe(package)
                conditions = postcondition if isinstance(postcondition, list) else [postcondition]
                if any(sum(all(n.get(k) == v for k, v in condition.items()) for n in fresh['nodes']) == 1 for condition in conditions):
                    return  # independently verified navigation; no tap replay
            raise RuntimeError('host_tap:' + str(value.get('error')))

    def type(self, package, text, **selector):
        obs = self.observe(package)
        result = self.runtime.call('type_text', {'selector': selector, 'text': text, 'verify': True,
                                                'allow_newlines': '\n' in text, 'observation_id': obs['observation_id']}, recover=False)
        if not result.get('ok'):
            if result.get('uncertain') and str(result.get('error')).startswith('UiObjectNotFoundError:'):
                fresh = self.observe(package)
                fields = [n for n in fresh['nodes'] if all(n.get(k) == v for k, v in selector.items())]
                if len(fields) == 1 and fields[0]['text'] == text:
                    return  # setText refreshed the view; verify the new field, never retype
            raise RuntimeError('host_type_text:' + str(result.get('error')))
        if not result.get('value_verified'):
            raise RuntimeError('typed_value_not_verified')

    def launch(self, package):
        if self.read_retry(self.backend.context)['package'] != package:
            self.call('launch_app', {'package': package, 'verify': True})

    def stop_u2(self):
        device = self.backend.start()
        device.stop_uiautomator()
        if device._check_alive():
            raise RuntimeError('host_ui_service_not_stopped')
        time.sleep(1)  # allow Android to rebind the app accessibility service after U2 detaches

    def read_retry(self, operation, *args):
        for attempt in range(10):
            try:
                return operation(*args)
            except RuntimeError as error:
                if attempt == 9 or not str(error).startswith('app_access_state_unknown:'):
                    raise
                time.sleep(.5)

    def run_files(self):
        return {n for n in self.read_retry(self.backend.shell, 'run-as', OMNI, 'ls', 'files/run_logs').splitlines()
                if re.fullmatch(r'[A-Za-z0-9_.-]+\.json', n)}

    def gui(self, goal, label):
        self.observe()
        before = self.run_files()
        self.stop_u2()
        token = 'RUN' + uuid.uuid4().hex
        prompt = f'{token}：{goal}。只执行本轮任务，不操作银行、支付、微信、huawei.lock或com.huawei.works。'
        self.active_marker = token
        self.save(label + '-submission.json', {'request_id': token})
        self.running = self.submitted = True
        self.backend.shell('am', 'broadcast', '--receiver-foreground', '-n', OMNI + '/.debug.DebugVlmTaskReceiver',
                           '--es', 'goalBase64', base64.b64encode(prompt.encode()).decode())
        deadline = time.monotonic() + self.args.timeout
        while time.monotonic() < deadline:
            self.read_retry(self.backend.unlocked)
            found = []
            for filename in self.run_files() - before:
                raw = self.read_retry(self.backend.shell, 'run-as', OMNI, 'cat', 'files/run_logs/' + filename)
                # A read can race the existing RunLog writer. Retry the read, never dispatch.
                if not raw.strip().endswith('}'):
                    continue
                run = json.loads(raw)
                if token in run.get('goal', ''):
                    found.append(run)
            if len(found) > 1:
                raise RuntimeError('duplicate_matching_gui_runs')
            if found:
                gate = verify_runlog(found[0], token)
                if gate['terminal']:
                    self.running = False
                    self.backend.start().start_uiautomator()
                    self.save(label + '-run.json', found[0])
                    return dict(gate, run_id=found[0]['run_id'], layer='gui_component',
                                action_count=len(found[0]['steps']),
                                duration_ms=found[0]['finished_at_ms'] - found[0]['started_at_ms'])
            time.sleep(2)
        raise RuntimeError('gui_timeout_no_replay')

    def chat_snapshot(self, token):
        # Read the existing Room projection, then retain only this uniquely marked conversation.
        # Temporary database copies stay in the private output directory and are removed.
        with tempfile.TemporaryDirectory(dir=self.output, prefix='db-') as temp:
            folder = Path(temp)
            db = 'omnibot_cache_databaseoss'
            for suffix in ('', '-wal'):
                (folder / (db + suffix)).write_bytes(self.backend.adb_call('exec-out', 'run-as', OMNI,
                                                                          'cat', 'databases/' + db + suffix))
            with sqlite3.connect('file:' + str(folder / db) + '?mode=ro', uri=True) as connection:
                ids = connection.execute("SELECT DISTINCT conversationId FROM agent_conversation_entries "
                                         "WHERE entryType='user_message' AND payloadJson LIKE ?", ('%' + token + '%',)).fetchall()
                if len(ids) > 1:
                    raise RuntimeError('ambiguous_test_conversation')
                if not ids:
                    return None
                rows = connection.execute('SELECT entryType,status,payloadJson FROM agent_conversation_entries '
                                          'WHERE conversationId=? ORDER BY id', (ids[0][0],)).fetchall()
        entries = [{'type': t, 'status': s, 'payload': json.loads(p)} for t, s, p in rows]
        gate = acp_terminal(entries)
        return dict(gate, conversation_id=ids[0][0], layer='normal_chat')

    def chat(self, prompt, label):
        self.launch(OMNI)
        obs = self.observe(OMNI)
        if any(n['description'] == '停止' for n in obs['nodes']):
            raise RuntimeError('existing_chat_busy')
        if any(n['description'] == '返回会话列表' for n in obs['nodes']):
            self.tap(OMNI, description='返回会话列表')
        self.tap(OMNI, description='新对话')
        obs = self.observe(OMNI)
        editors = [n for n in obs['nodes'] if n['class_name'] == 'android.widget.EditText']
        if len(editors) != 1 or editors[0]['text']:
            raise RuntimeError('new_composer_not_empty')
        token = 'REQ' + uuid.uuid4().hex
        self.type(OMNI, f'请求ID {token}。{prompt}',
                  class_name='android.widget.EditText')
        obs = self.observe(OMNI)
        self.active_marker = token
        self.save(label + '-submission.json', {'request_id': token})
        self.running = self.submitted = True  # mark before dispatch; uncertain send is never replayed
        self.call('tap', {'selector': {'description': '发送'}, 'observation_id': obs['observation_id']})
        self.stop_u2()
        deadline = time.monotonic() + self.args.timeout
        while time.monotonic() < deadline:
            self.read_retry(self.backend.unlocked)
            gate = None
            for read_attempt in range(4):
                try:
                    gate = self.chat_snapshot(token)
                    break
                except sqlite3.DatabaseError:
                    if read_attempt == 3:
                        raise
                    time.sleep(.5)  # recopy a changing WAL snapshot, never resubmit the chat
            if gate and gate['terminal']:
                self.running = False
                self.backend.start().start_uiautomator()
                self.save(label + '-acp.json', gate)
                return gate
            time.sleep(3)
        raise RuntimeError('acp_timeout_no_replay')

    def query(self, table, columns, where=None):
        command = ['content', 'query', '--uri', 'content://com.android.calendar/' + table,
                   '--projection', ':'.join(columns)]
        if where:
            command += ['--where', where]
        return provider_rows(self.backend.shell(*command))

    def calendar(self, title, calendar_id):
        events = self.query('events', ['_id', 'calendar_id', 'title', 'dtstart', 'dtend', 'eventTimezone'],
                            f"calendar_id={calendar_id} AND title='{title}'")
        normalized, reminders = [], []
        for event in events:
            normalized.append({'id': int(event['_id']), 'calendar_id': int(event['calendar_id']),
                               'title': event['title'], 'timezone': event['eventTimezone'], 'start_ms': int(event['dtstart']), 'end_ms': int(event['dtend'])})
            reminders.extend({'event_id': int(r['event_id']), 'minutes': int(r['minutes'])}
                             for r in self.query('reminders', ['event_id', 'minutes'], 'event_id=' + event['_id']))
        return normalized, reminders

    def clock_tab(self, title):
        self.launch(CLOCK)
        # Tab description is unique even when a text heading repeats the title.
        obs = self.observe(CLOCK)
        if any(n['resource_id'] == CLOCK + ':id/originui_vtoolbar_normal_title_rom14_0' and n['text'] == title for n in obs['nodes']):
            return
        tabs = [n for n in obs['nodes'] if n['description'] == title and n['bounds'][1] > obs['context']['height']*.85]
        if len(tabs) != 1:
            raise RuntimeError('clock_tab_not_unique')
        self.tap(CLOCK, description=title, class_name=tabs[0]['class_name'])

    def picker(self, obs):
        values = []
        for unit in ('小时', '分', '秒'):
            candidates = [n['description'] for n in obs['nodes'] if re.fullmatch(r'\d+' + unit, n['description'])]
            if len(candidates) != 1:
                raise RuntimeError('timer_picker_not_unique_or_not_idle')
            values.append(int(candidates[0][:-len(unit)]))
        return values

    def alarm_rows(self, xml):
        return [n.get('content-desc') for n in ET.fromstring(xml).iter('node')
                if n.get('package') == CLOCK and n.get('resource-id') == CLOCK + ':id/alarm_item_content']

    def note_search(self, title):
        self.launch(NOTES)
        obs = self.observe(NOTES)
        if any(n['description'] == '返回' for n in obs['nodes']):
            self.tap(NOTES, description='返回', postcondition=[{'description': '搜索'}, {'resource_id': NOTES + ':id/search_et'}])
            obs = self.observe(NOTES)
        if not any(n['resource_id'] == NOTES + ':id/search_et' for n in obs['nodes']):
            self.tap(NOTES, description='搜索', postcondition={'class_name': 'android.widget.EditText'})
        self.type(NOTES, title, resource_id=NOTES + ':id/search_et')
        time.sleep(1)
        return self.observe(NOTES)


    def open_note(self, title):
        obs = self.note_search(title)
        root = ET.fromstring(self.backend.tree())
        targets = []
        stack = [(root, False)]
        while stack:
            node, reference = stack.pop()
            reference |= node.get('resource-id') == NOTES + ':id/reference_layout'
            if not reference and node.get('resource-id') == NOTES + ':id/note_title' and node.get('text') == title:
                targets.append(node)
            stack.extend((child, reference) for child in node)
        if len(targets) != 1:
            raise RuntimeError('note_regular_search_result_not_unique')
        bounds = list(map(int, re.findall(r'\d+', targets[0].get('bounds', ''))))
        if len(bounds) != 4 or not any(n['text'] == title and n['bounds'] == bounds for n in obs['nodes']):
            raise RuntimeError('note_search_result_changed')
        x1, y1, x2, y2 = bounds
        if self.wait_for_clear_input(obs, ((x1+x2)//2, (y1+y2)//2)):
            obs = self.observe(NOTES)
            if not any(n['text'] == title and n['bounds'] == bounds for n in obs['nodes']):
                raise RuntimeError('note_result_moved_while_waiting')
        result = self.runtime.call('tap', {'x': (x1+x2)//2, 'y': (y1+y2)//2,
                                          'observation_id': obs['observation_id']}, recover=False)
        if not result.get('ok') and not (result.get('uncertain') and str(result.get('error')).startswith('app_access_state_unknown:')):
            raise RuntimeError('note_result_open_failed')
        detail = self.observe(NOTES)
        if not any(n['text'] == title and n['resource_id'] == NOTES + ':id/title_edit_text' for n in detail['nodes']):
            raise RuntimeError('note_detail_title_not_verified')
        return detail


def run_case(harness, case):
    h = harness
    adapter = case['adapter']
    marker = 'REG' + uuid.uuid4().hex[:12]
    title = '[测试] ' + marker
    now = datetime.now(ZoneInfo('Asia/Shanghai'))
    days = (2 - now.weekday()) % 7 or 7
    start = (now + timedelta(days=days)).replace(hour=15, minute=0, second=0, microsecond=0)
    end = start + timedelta(minutes=30)
    alarm_at = now.replace(hour=7, minute=0, second=0, microsecond=0)
    if alarm_at <= now:
        alarm_at += timedelta(days=1)
    values = {'marker': marker, 'start_at': start.isoformat(), 'end_at': end.isoformat(), 'alarm_at': alarm_at.isoformat()}
    report = {'schema_version': '1.0', 'case_id': case['id'], 'source_id': case['source_id'],
              'attempt_id': marker, 'execution_layer': case['layer'], 'cleanup_actor': 'host_automation',
              'execution': {}, 'acceptance': {}, 'cleanup': {'passed': True, 'required': False},
              'status': 'blocked', 'started_at': now.isoformat()}
    baseline = {}
    event_id = None
    prepared = False
    fixture_created = False
    try:
        h.backend.unlocked()
        h.backend.ensure_primary_display()
        if adapter == 'calculator':
            h.launch(CALC)
            h.tap(CALC, text='AC')
            h.screen('baseline', CALC)
            if not h.calculator_blank('baseline')['passed']:
                raise RuntimeError('calculator_baseline_not_blank')
        elif adapter == 'todo':
            h.launch(NOTES)
            obs = h.observe(NOTES)
            if not any(n['resource_id'] == NOTES + ':id/todo_list' for n in obs['nodes']):
                h.tap(NOTES, resource_id=NOTES + ':id/tab_todo')
                obs = h.observe(NOTES)
            if not any(n['resource_id'] == NOTES + ':id/todo_list' for n in obs['nodes']):
                raise RuntimeError('todo_list_not_visible')
            baseline['todo_rows'] = [(n['text'], n['description']) for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/content_tv']
            if any(title in text for text, _ in baseline['todo_rows']):
                raise RuntimeError('test_todo_already_exists')
        elif adapter == 'zip_lines':
            fixture = h.output / 'fixture.zip'
            with zipfile.ZipFile(fixture, 'w') as archive:
                archive.writestr('file_1.txt', 'one\ntwo\nthree\nfour\nfive\nsix\n')
            baseline['zip_hash'] = hashlib.sha256(fixture.read_bytes()).hexdigest()
            baseline['zip_dir'] = '/sdcard/Download/OBZIP' + marker
            h.backend.shell('mkdir', baseline['zip_dir'])
            fixture_created = True
            h.backend.adb_call('push', str(fixture), baseline['zip_dir'] + '/fixture.zip')
            if h.backend.shell('sha256sum', baseline['zip_dir'] + '/fixture.zip').split()[0] != baseline['zip_hash']:
                raise RuntimeError('fixture_hash_mismatch')
        elif adapter == 'weather_notes':
            obs = h.note_search(title)
            if any(n['text'] == title and n['class_name'] != 'android.widget.EditText' for n in obs['nodes']):
                raise RuntimeError('test_note_already_exists')
            h.launch('com.vivo.weather')
            obs = h.observe('com.vivo.weather')
            hourly = []
            for n in obs['nodes']:
                match = re.match(r'^(\d{2}:\d{2}),(\d+)度,([^,]+),', n['description'])
                if match:
                    hourly.append(match.groups())
            if len(hourly) < 2 or hourly[0][0] == hourly[1][0]:
                raise RuntimeError('weather_hourly_source_not_available')
            baseline['weather'] = hourly[:2]
        elif adapter == 'notes':
            obs = h.note_search(title)
            if any(n['text'] == title and n['class_name'] != 'android.widget.EditText' for n in obs['nodes']):
                raise RuntimeError('test_note_already_exists')
        elif adapter in ('alarm', 'timer', 'stopwatch'):
            h.clock_tab({'alarm': '闹钟', 'timer': '计时器', 'stopwatch': '秒表'}[adapter])
            obs, xml = h.screen('baseline', CLOCK)
            if adapter == 'alarm':
                baseline['alarms'] = h.alarm_rows(xml)
                if any(title in row for row in baseline['alarms']):
                    raise RuntimeError('test_alarm_already_exists')
            elif adapter == 'timer':
                if any(n['description'] == '暂停' or n['text'] == '正在计时' for n in obs['nodes']):
                    raise RuntimeError('existing_active_timer')
                baseline['picker'] = h.picker(obs)
                baseline['presets'] = [(n.get('resource-id'), n.get('text')) for n in ET.fromstring(xml).iter('node') if n.get('resource-id') in (CLOCK + ':id/tv_time', CLOCK + ':id/tv_label')]
            else:
                baseline['controls'] = sorted(n['description'] for n in obs['nodes']
                                              if n['description'] in ('开始', '暂停', '继续', '重置', '复位', '计次'))
                if '暂停' in baseline['controls']:
                    raise RuntimeError('existing_running_stopwatch')
                baseline['texts'] = [n['description'] for n in obs['nodes'] if n['resource_id'] == CLOCK + ':id/tv_stopwatch_time_view' and re.fullmatch(r'(?:\d+小时)?\d+分钟\d+秒', n['description'])]
                if not baseline['controls'] or not baseline['texts'] or not any(n['text'] == '秒表' for n in obs['nodes']):
                    raise RuntimeError('stopwatch_layout_not_recognized')
        else:
            calendars = h.query('calendars', ['_id', 'account_type', 'calendar_displayName', 'calendar_access_level'],
                                "account_type='LOCAL'")
            matches = [c for c in calendars if (int(c['_id']) == h.args.calendar_id if h.args.calendar_id else
                       c['calendar_displayName'] == 'Local calendar') and int(c['calendar_access_level']) >= 500]
            if len(matches) != 1:
                raise RuntimeError('select_unique_writable_local_calendar_with_calendar_id')
            baseline['calendar_id'] = int(matches[0]['_id'])
            if h.calendar(title, baseline['calendar_id'])[0]:
                raise RuntimeError('test_calendar_event_already_exists')
        prepared = True
        h.save('baseline-checks.json', baseline)
        prompt = case['prompt_template'].format(**values)
        if adapter == 'zip_lines':
            prompt += f" 使用terminal_execute执行只读命令 `unzip -p {baseline['zip_dir']}/fixture.zip file_1.txt | wc -l`，依据结果回答；不访问网络或其他路径，不解压到存储，不修改文件。"
        if adapter == 'weather_notes':
            prompt += f" 读取{baseline['weather'][0][0]}和{baseline['weather'][1][0]}两条，各写一行，格式为“时:分 温度°C 天气”，不要补充其他正文。"
        if adapter == 'calendar':
            prompt += f' 只使用本地calendarId={baseline["calendar_id"]}，时区Asia/Shanghai，不邀请他人。使用calendar_event_create，随后calendar_event_list核对本次读取的提醒字段。'
        if adapter in ('alarm', 'timer'):
            prompt += ' 使用已有原生系统工具，不通过滚轮设置。'
        prompt += ' 不修改其他对象，不重复创建，核对完成后直接结束，不运行终端命令或自行修改环境。'
        dispatched_at = time.monotonic()
        report['execution'] = h.chat(prompt, 'execute') if case['layer'] == 'normal_chat' else h.gui(prompt, 'execute')
        if not report['execution'].get('passed'):
            raise RuntimeError('execution_failed')
        if case['layer'] == 'normal_chat' and adapter != 'zip_lines':
            expected_tool = {'alarm': 'alarm_reminder_create', 'timer': 'timer_create', 'calendar': 'calendar_event_create'}[adapter]
            creates = [tool for tool in report['execution']['tools'] if tool['tool'] == expected_tool]
            report['execution']['creation_calls'] = len(creates)
            if len(creates) != 1 or creates[0]['success'] is not True or (creates[0]['args'] or {}).get('title') != title:
                report['execution']['passed'] = False
                raise RuntimeError('single_owned_native_creation_not_proven')
        if adapter == 'zip_lines':
            final = report['execution'].pop('final_texts', [])
            report['acceptance'] = verify_zip_turn(report['execution']['tools'], final, baseline['zip_dir'] + '/fixture.zip', 6)
        elif adapter == 'todo':
            obs = h.observe(NOTES)
            rows = [(n['text'], n['description']) for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/content_tv']
            report['acceptance'] = verify_todo(rows, title)
            report['acceptance']['visible_baseline_prefix_preserved'] = not bool(Counter(map(tuple, baseline['todo_rows'][:-1])) - Counter(rows))
            report['acceptance']['passed'] &= report['acceptance']['visible_baseline_prefix_preserved']
        elif adapter in ('calculator', 'notes', 'weather_notes'):
            if adapter in ('notes', 'weather_notes'):
                h.open_note(title)
            obs, xml = h.screen('acceptance', PACKAGES[adapter])
            rows = h.ocr('acceptance')
            if adapter == 'calculator':
                texts = [r['text'] for r in rows if r['x'] > .45 and .15 < r['y'] < .4]
                report['acceptance'] = verify_ocr_lines('\n'.join(texts), ['408'])
            else:
                bodies = [n for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/line_edit_text']
                rectangle = None
                if len(bodies) == 1:
                    x1, y1, x2, y2 = bodies[0]['bounds']
                    w, ht = obs['context']['width'], obs['context']['height']
                    rectangle = (x1/w, y1/ht, x2/w, y2/ht)
                if adapter == 'weather_notes':
                    report['acceptance'] = verify_weather_note(rows, rectangle, baseline['weather'])
                else:
                    report['acceptance'] = verify_note_lines(rows, rectangle)
                report['acceptance']['title_matches'] = any(n['text'] == title for n in obs['nodes'])
                report['acceptance']['passed'] &= report['acceptance']['title_matches']
        elif adapter in ('alarm', 'timer'):
            h.clock_tab('闹钟' if adapter == 'alarm' else '计时器')
            obs, xml = h.screen('acceptance', CLOCK)
            if adapter == 'alarm':
                report['acceptance'] = verify_alarm(xml, title)
            else:
                elapsed = time.monotonic() - dispatched_at
                # Creation happened inside this measured interval, not necessarily at submission.
                report['acceptance'] = verify_clock_timer(xml, title, 1500, elapsed/2, elapsed/2 + 5)
                report['acceptance']['submission_to_observation_seconds'] = elapsed
                report['acceptance']['passed'] &= creates[0]['args'].get('durationSeconds') == 1500
        elif adapter == 'calendar':
            events, reminders = h.calendar(title, baseline['calendar_id'])
            h.save('provider-acceptance.json', {'events': events, 'reminders': reminders})
            report['acceptance'] = verify_calendar(events, reminders, title, int(start.timestamp()*1000),
                                                   int(end.timestamp()*1000), baseline['calendar_id'], expected_timezone='Asia/Shanghai')
            if len(events) == 1:
                event_id = events[0]['id']
        else:
            obs, xml = h.screen('acceptance', CLOCK)
            controls = sorted(n['description'] for n in obs['nodes'] if n['description'] in ('开始', '暂停', '继续', '重置', '复位', '计次'))
            texts = [n['description'] for n in obs['nodes'] if n['resource_id'] == CLOCK + ':id/tv_stopwatch_time_view' and re.fullmatch(r'(?:\d+小时)?\d+分钟\d+秒', n['description'])]
            run = json.loads((h.output / 'execute-run.json').read_text())
            safe_actions = all(
                step['action']['tool'] == 'wait' or
                (step['action']['tool'] == 'click' and step['action']['args'].get('target_description') == '秒表'
                 and step.get('metadata', {}).get('transfer', {}).get('semantic_grounding', {}).get('status') == 'resolved')
                for step in run['steps'])
            report['acceptance'] = {'passed': controls == baseline['controls'] and texts == baseline['texts'] and safe_actions,
                                    'unchanged_controls': controls == baseline['controls'], 'unchanged_time': texts == baseline['texts'],
                                    'no_stopwatch_control_actions': safe_actions}
    except (RuntimeError, ValueError, OSError, sqlite3.Error, subprocess.SubprocessError) as error:
        report['error'] = str(error)[:240]
    finally:
        if (prepared and h.submitted) or fixture_created:
            report['cleanup'] = {'passed': False, 'required': adapter != 'stopwatch'}
            try:
                if h.running:
                    # Never launch another task over a possibly live action. Operator recovery is explicit.
                    raise RuntimeError('execution_terminal_unconfirmed_cleanup_blocked')
                if adapter == 'zip_lines':
                    names = h.backend.shell('ls', '-A', baseline['zip_dir']).splitlines()
                    if names != ['fixture.zip'] or h.backend.shell('sha256sum', baseline['zip_dir'] + '/fixture.zip').split()[0] != baseline['zip_hash']:
                        raise RuntimeError('fixture_cleanup_ownership_or_content_mismatch')
                    h.backend.shell('rm', baseline['zip_dir'] + '/fixture.zip')
                    h.backend.shell('rmdir', baseline['zip_dir'])
                    parent_names = h.backend.shell('ls', '-A', '/sdcard/Download').splitlines()
                    report['cleanup'] = {'passed': baseline['zip_dir'].rsplit('/', 1)[-1] not in parent_names, 'fixture_hash_unchanged': True}
                elif adapter == 'calendar':
                    events, _ = h.calendar(title, baseline['calendar_id'])
                    if events:
                        if len(events) != 1:
                            raise RuntimeError('cleanup_event_not_unique')
                        event_id = events[0]['id']
                        gate = h.chat(f'只删除本地calendarId={baseline["calendar_id"]}中eventId={event_id}且标题精确为“{title}”的本轮测试事件。先查标题和ID一致再删除，之后查空，不操作其他事件。', 'cleanup')
                        if not gate['passed']:
                            raise RuntimeError('calendar_cleanup_turn_failed')
                    events, _ = h.calendar(title, baseline['calendar_id'])
                    orphaned = h.query('reminders', ['event_id', 'minutes'], f'event_id={event_id}') if event_id else []
                    report['cleanup'] = {'passed': not events and not orphaned, 'required': True}
                    report['cleanup_actor'] = 'normal_chat'
                elif adapter == 'calculator':
                    h.launch(CALC)
                    h.tap(CALC, text='AC')
                    h.screen('cleanup', CALC)
                    report['cleanup'] = h.calculator_blank('cleanup')
                elif adapter == 'todo':
                    obs = h.observe(NOTES)
                    targets = [n for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/content_tv' and n['text'] == title and n['description'] == '未完成,' + title]
                    if len(targets) != 1 or sum(n['text'] == title and n['resource_id'] == NOTES + ':id/content_tv' for n in obs['nodes']) != 1:
                        raise RuntimeError('cleanup_todo_not_unique_incomplete')
                    target = targets[0]
                    x1, y1, x2, y2 = target['bounds']
                    width = obs['context']['width']
                    region = {'x': int(width * .055), 'y': y1 - 40, 'width': int(width * .89), 'height': y2 - y1 + 80}
                    h.call('swipe', {'direction': 'left', 'region': region, 'observation_id': obs['observation_id']})
                    obs = h.observe(NOTES)
                    targets = [n for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/content_tv' and n['text'] == title]
                    if len(targets) != 1 or targets[0]['bounds'][0] >= x1:
                        raise RuntimeError('cleanup_todo_swipe_not_verified')
                    foregrounds = [n for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/item_content_fg' and n['bounds'][1] <= y1 and n['bounds'][3] >= y2]
                    buttons = [n for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/bt_todo_item_delete' and n['bounds'][1] <= y1 and n['bounds'][3] >= y2]
                    if len(buttons) != 1 or len(foregrounds) != 1 or foregrounds[0]['bounds'][2] > buttons[0]['bounds'][0]:
                        raise RuntimeError('cleanup_todo_delete_not_exposed_on_owned_row')
                    x1, y1, x2, y2 = buttons[0]['bounds']
                    point = ((x1 + x2)//2, (y1 + y2)//2)
                    if h.wait_for_clear_input(obs, point):
                        raise RuntimeError('cleanup_todo_observation_changed')
                    result = h.runtime.call('tap', {'x': point[0], 'y': point[1], 'observation_id': obs['observation_id']}, recover=False)
                    if not result.get('ok'):
                        raise RuntimeError('cleanup_todo_delete_tap_unconfirmed')
                    obs = h.observe(NOTES)
                    if not any(n['text'] == '从手机、云端及其他同步设备中删除此待办？' for n in obs['nodes']):
                        raise RuntimeError('cleanup_todo_confirmation_missing')
                    h.tap(NOTES, resource_id='android:id/button1', description='删除')
                    obs = h.observe(NOTES)
                    rows = [(n['text'], n['description']) for n in obs['nodes'] if n['resource_id'] == NOTES + ':id/content_tv']
                    report['cleanup'] = {'passed': not any(title in text for text, _ in rows) and not (Counter(map(tuple, baseline['todo_rows'])) - Counter(rows)),
                                         'visible_baseline_preserved': not bool(Counter(map(tuple, baseline['todo_rows'])) - Counter(rows))}
                elif adapter in ('notes', 'weather_notes'):
                    # Only delete the exact marked detail page, never a list selection by position.
                    h.launch(NOTES)
                    obs = h.observe(NOTES)
                    if not any(n['text'] == title and n['resource_id'] == NOTES + ':id/title_edit_text' for n in obs['nodes']):
                        h.open_note(title)
                    obs = h.observe(NOTES)
                    if not any(n['text'] == title and n['resource_id'] == NOTES + ':id/title_edit_text' for n in obs['nodes']):
                        raise RuntimeError('cleanup_note_detail_not_proven')
                    h.tap(NOTES, description='更多')
                    h.tap(NOTES, text='删除')
                    h.tap(NOTES, text='删除', postcondition=[{'description': '搜索'}, {'resource_id': NOTES + ':id/search_et'}])
                    obs = h.note_search(title)
                    report['cleanup'] = {'passed': not any(n['text'] == title and n['class_name'] != 'android.widget.EditText' for n in obs['nodes'])}
                elif adapter == 'alarm':
                    h.clock_tab('闹钟')
                    obs, xml = h.screen('cleanup-before', CLOCK)
                    target = [n for n in obs['nodes'] if n['description'] in h.alarm_rows(xml) and title in n['description'].split(',')]
                    if len(target) != 1:
                        raise RuntimeError('cleanup_alarm_ownership_not_unique_visible')
                    h.tap(CLOCK, description='更多')
                    h.tap(CLOCK, text='编辑')
                    obs = h.observe(CLOCK)
                    row = [n for n in obs['nodes'] if n['description'] == target[0]['description']]
                    moved = len(row) == 1 and sum(row[0]['bounds'][1::2])/2 > obs['context']['height']*.8
                    if moved:
                        h.call('swipe', {'direction': 'up', 'observation_id': obs['observation_id']})
                    h.tap(CLOCK, description=target[0]['description'])
                    obs = h.observe(CLOCK)
                    if not any(n['description'] == '已选择1项' or n['text'] == '已选择1项' for n in obs['nodes']):
                        raise RuntimeError('cleanup_alarm_selection_not_one')
                    h.tap(CLOCK, description='删除')
                    obs = h.observe(CLOCK)
                    if not any(n['text'] == '删除此闹钟？' for n in obs['nodes']):
                        raise RuntimeError('cleanup_alarm_confirmation_missing')
                    h.tap(CLOCK, description='删除')
                    if moved:
                        obs = h.observe(CLOCK)
                        h.call('swipe', {'direction': 'down', 'observation_id': obs['observation_id']})
                    obs, xml = h.screen('cleanup', CLOCK)
                    rows = h.alarm_rows(xml)
                    report['cleanup'] = {'passed': not any(title in r.split(',') for r in rows) and set(baseline['alarms']).issubset(rows)}
                elif adapter == 'timer':
                    h.clock_tab('计时器')
                    obs, xml = h.screen('cleanup-before', CLOCK)
                    check = verify_clock_timer(xml, title)
                    if not check['passed'] or sum(n['description'] == '关闭' for n in obs['nodes']) != 1:
                        raise RuntimeError('cleanup_timer_ownership_not_unique')
                    h.tap(CLOCK, description='关闭')
                    obs = h.observe(CLOCK)
                    if h.picker(obs) != baseline['picker']:
                        hours, minutes, seconds = baseline['picker']
                        gate = h.gui(f'只恢复当前空闲计时选择器为{hours}小时{minutes}分{seconds}秒。不要启动、保存、修改常用计时器、闹钟或勿扰。', 'cleanup-picker')
                        if not gate['passed']:
                            raise RuntimeError('timer_picker_restore_failed')
                    obs, xml = h.screen('cleanup', CLOCK)
                    presets = [(n.get('resource-id'), n.get('text')) for n in ET.fromstring(xml).iter('node') if n.get('resource-id') in (CLOCK + ':id/tv_time', CLOCK + ':id/tv_label')]
                    report['cleanup'] = {'passed': h.picker(obs) == baseline['picker'] and presets == baseline['presets'] and not any(n['description'] == '暂停' for n in obs['nodes']), 'visible_presets_unchanged': presets == baseline['presets']}
                    report['cleanup_actor'] = 'host_automation_and_gui_component'
                else:
                    report['cleanup'] = {'passed': report['acceptance'].get('passed', False), 'required': False}
            except (RuntimeError, ValueError, OSError, sqlite3.Error, subprocess.SubprocessError) as error:
                report['cleanup']['error'] = str(error)[:240]
        report['submission_attempted'] = h.submitted
        report['status'] = status_for(report['execution'], report['acceptance'], report['cleanup'], h.submitted)
        report['finished_at'] = datetime.now(ZoneInfo('Asia/Shanghai')).isoformat()
        report['evidence'] = h.serialized_evidence
        h.save('attempt.json', report)
        print(json.dumps({'case': case['id'], 'status': report['status'], 'acceptance': report['acceptance'],
                          'cleanup': report['cleanup'], 'error': report.get('error')}, ensure_ascii=False), flush=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--case', action='append', choices=[c['id'] for c in read_cases()])
    selection.add_argument('--suite', choices=['stable', 'all'])
    parser.add_argument('--live', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--device-runtime-root', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--calendar-id', type=int)
    parser.add_argument('--timeout', type=int, default=240)
    args = parser.parse_args()
    cases = [next(c for c in read_cases() if c['id'] == cid) for cid in dict.fromkeys(args.case)] if args.case else [c for c in read_cases() if args.suite == 'all' or c['status'] != 'unverified']
    if not args.live:
        print(json.dumps({'mode': 'plan', 'cases': cases, 'success_rule': 'execution AND independent acceptance AND cleanup; layer and cleanup actor remain explicit'}, ensure_ascii=False, indent=2))
        return 0
    args.adb = shutil.which(args.adb)
    if not args.serial or not args.adb or not args.device_runtime_root or not args.output or args.timeout < 1:
        parser.error('live requires serial, adb, runtime root, output and positive timeout')
    if any(c['adapter'] in ('calculator', 'notes', 'weather_notes') for c in cases) and not shutil.which('swift'):
        parser.error('calculator/notes visual acceptance requires local macOS Vision via swift')
    args.output = args.output.resolve()
    if args.output.is_relative_to(ROOT):
        ignored = subprocess.run(['git', 'check-ignore', '-q', str(args.output)], cwd=ROOT).returncode == 0
        if not ignored:
            parser.error('private output inside repository must be git-ignored')
    if args.output.exists():
        parser.error('use a fresh output directory; existing evidence is never overwritten')
    os.umask(0o077)
    args.output.mkdir(parents=True)
    args.device_runtime_root = args.device_runtime_root.resolve()
    lock = (args.device_runtime_root / '.tools/android-regression.lock').open('a')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    reports = []
    h = None
    original = None
    shared_backend = None
    try:
        for case in cases:
            directory = args.output / case['id']
            directory.mkdir()
            h = Harness(args, directory, backend=shared_backend)
            shared_backend = h.backend
            h.read_retry(h.backend.unlocked)
            if original is None:
                original = h.read_retry(h.backend.shell, 'settings', 'get', 'global', 'stay_on_while_plugged_in')
                if not original.isdigit():
                    raise RuntimeError('keep_awake_baseline_unknown')
                h.backend.shell('svc', 'power', 'stayon', 'usb')
            print(json.dumps({'case': case['id'], 'phase': 'starting'}, ensure_ascii=False), flush=True)
            report = run_case(h, case)
            reports.append(report)
            if h.running or not report['cleanup'].get('passed'):
                break  # leave evidence and ownership for explicit recovery, never continue over a dirty device
        (args.output / 'report.json').write_text(json.dumps({'schema_version': '1.0', 'attempts': reports}, ensure_ascii=False, indent=2))
    finally:
        if h and original is not None:
            h.backend.shell('settings', 'put', 'global', 'stay_on_while_plugged_in', original)
        lock.close()
    return 0 if len(reports) == len(cases) and all(r['status'] == 'passed' for r in reports) else 1


if __name__ == '__main__':
    sys.exit(main())
