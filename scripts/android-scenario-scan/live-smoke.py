#!/usr/bin/env python3
"""BA19 single-submit smoke: plan by default; live requires explicit device selection."""
import argparse
import json
from pathlib import Path
import re
import shutil
import sys
import time
import uuid
import xml.etree.ElementTree as ET

PROMPT = '在计算器里算 12 × 34。只调用一轮 run_gui；失败就直接报告并停止，不要再次调用 run_gui，不要调用 terminal、get_override 或修改运行环境，不要自行修复，不要编造成功。'
OMNIBOT = 'cn.com.omnimind.bot'
CALCULATOR = 'com.android.bbkcalculator'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--scenario', choices=['BA19'], default='BA19')
    parser.add_argument('--device-runtime-root', type=Path)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--observer', choices=['omniflow'], default='omniflow')
    parser.add_argument('--timeout', type=int, default=180)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--composer-already-new', action='store_true', help='caller confirms current conversation is new and empty')
    args = parser.parse_args()
    plan = {'scenario_id': 'BA19', 'mode': 'plan', 'prompt': PROMPT,
            'steps': ['confirm idle OmniBot and new empty conversation', 'snapshot run IDs',
                      'type and verify exact prompt', 'send once', 'observe new run and calculator'],
            'acceptance': 'new matching run, calculator foreground and fresh transition to 408',
            'locking': 'Runtime per-call phone.lock; caller owns exclusive session coordination'}
    if not args.live:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return
    if not args.serial or not args.device_runtime_root or not args.output or args.timeout < 1:
        parser.error('--live requires --serial, --device-runtime-root, --output and positive --timeout')
    adb = shutil.which(args.adb)
    if not adb:
        parser.error('adb not found')
    sys.path.insert(0, str(args.device_runtime_root.resolve()))
    from vivo_phone_use.backend import AndroidBackend
    from vivo_phone_use.runtime import Runtime

    attempt = {'scenario_id': 'BA19', 'attempt_id': uuid.uuid4().hex, 'status': 'incomplete', 'evidence': [],
               'submitted_once': False, 'fresh_non_408_observed': False, 'run_id': None}
    marker = 'SCAN-' + attempt['attempt_id']
    prompt = marker + ' ' + PROMPT
    attempt.update(marker=marker, prompt=prompt)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    runtime = Runtime(AndroidBackend(args.serial, adb), args.device_runtime_root.resolve(),
                      run=args.output.parent / (args.output.stem + '-runtime'))

    def call(op, payload):
        result = runtime.call(op, payload, recover=False)
        if not result.get('ok'):
            raise RuntimeError('runtime_' + op + '_failed:' + str(result.get('error', 'unknown')))
        return result

    def observe():
        result = call('observe', {'mode': 'tree', 'max_nodes': 500, 'archive_image': False})
        if result.get('context_changed_during_capture') or result.get('truncated'):
            raise RuntimeError('observation_unstable_or_truncated')
        return result

    def shell_guard():
        if runtime.pause_file.exists():
            raise RuntimeError('authentication_paused')
        runtime.backend.unlocked()
        runtime.backend.ensure_primary_display()
        runtime.backend.check_access()

    def native_observe():
        request_id = uuid.uuid4().hex
        result_path = 'files/debug-omniflow-observe-result-' + request_id + '.json'
        with runtime.lock():
            shell_guard()
            runtime.backend.shell('am', 'broadcast', '-a', 'cn.com.omnimind.bot.debug.OBSERVE_OMNIFLOW',
                                  '-n', OMNIBOT + '/.debug.DebugOmniFlowObserveReceiver',
                                  '--es', 'requestId', request_id, '--ez', 'includeScreenshot', 'false')
            deadline = time.monotonic() + 8
            while time.monotonic() < deadline:
                try:
                    raw = runtime.backend.shell('run-as', OMNIBOT, 'cat', result_path)
                    payload = json.loads(raw)
                    break
                except (RuntimeError, json.JSONDecodeError):
                    time.sleep(0.25)
            else:
                raise RuntimeError('native_observe_timeout')
        if payload.get('request_id') != request_id or payload.get('success') is not True:
            raise RuntimeError('native_observe_failed')
        state = payload.get('state') or {}
        package = state.get('package_name') or state.get('packageName')
        xml = state.get('xml')
        if not package or not xml:
            raise RuntimeError('native_observe_incomplete')
        root = ET.fromstring(xml)
        nodes = [{'text': n.get('text', '')} for n in root.iter('node') if n.get('package') == package]
        if not nodes:
            raise RuntimeError('native_observe_incomplete')
        return {'context': {'package': package}, 'nodes': nodes}

    def runfiles():
        with runtime.lock():
            shell_guard()
            raw = runtime.backend.shell('run-as', OMNIBOT, 'ls', '-1', 'files/run_logs')
        return {x for x in raw.splitlines() if re.fullmatch(r'[a-f0-9]{16}_[A-Za-z0-9_.-]+\.json', x)}

    def readrun(name):
        with runtime.lock():
            shell_guard()
            return json.loads(runtime.backend.shell('run-as', OMNIBOT, 'cat', 'files/run_logs/' + name))

    def nodes_with(obs, key, value):
        return [n for n in obs['nodes'] if n.get(key) == value and n.get('visible')]

    try:
        obs = observe()
        if obs['context']['package'] != OMNIBOT or nodes_with(obs, 'description', '停止'):
            raise RuntimeError('omnibot_not_foreground_or_busy')
        if not args.composer_already_new:
            call('tap', {'selector': {'description': '新对话'}, 'observation_id': obs['observation_id'], 'recover': False})
            obs = observe()
        edit = nodes_with(obs, 'class_name', 'android.widget.EditText')
        if obs['context']['package'] != OMNIBOT or nodes_with(obs, 'description', '停止') or len(edit) != 1 or edit[0]['text']:
            raise RuntimeError('new_composer_not_empty_or_busy')
        before = runfiles()
        typed = call('type_text', {'selector': {'class_name': 'android.widget.EditText'}, 'text': prompt,
                                   'verify': True, 'observation_id': obs['observation_id'], 'recover': False})
        if not typed.get('value_verified'):
            raise RuntimeError('prompt_readback_mismatch')
        obs = observe()
        if obs['context']['package'] != OMNIBOT or nodes_with(obs, 'description', '停止') or len(nodes_with(obs, 'description', '发送')) != 1:
            raise RuntimeError('send_not_ready')
        # Mark before dispatch: an exception may mean the tap reached the device.
        attempt['submitted_once'] = True
        call('tap', {'selector': {'description': '发送'}, 'observation_id': obs['observation_id'], 'recover': False})
        with runtime.lock():
            runtime.guard()
            runtime.backend.start().stop_uiautomator()
        deadline = time.monotonic() + args.timeout
        transition_started = None
        while time.monotonic() < deadline:
            try:
                obs = native_observe()
                names = runfiles() - before
                runs = [readrun(name) for name in sorted(names)]
                transition_started = None
            except RuntimeError as exc:
                if 'app_access_state_unknown' not in str(exc):
                    raise
                if transition_started is None:
                    transition_started = time.monotonic()
                if time.monotonic() - transition_started >= 8:
                    raise RuntimeError('app_access_state_unknown_persisted')
                time.sleep(0.25)
                continue
            matching = []
            for run in runs:
                goal = run.get('goal', '')
                if '12' in goal and '34' in goal and ('计算' in goal or 'calculator' in goal.lower()):
                    matching.append(run)
            if len(matching) > 1:
                raise RuntimeError('multiple_matching_new_runs')
            if matching:
                run = matching[0]
                attempt['run_id'] = run.get('run_id')
                attempt['run_status'] = run.get('status')
                attempt['run_error'] = run.get('error')
                evidence = args.output.parent / (args.output.stem + '-run.json')
                evidence.write_text(json.dumps(run, ensure_ascii=False, indent=2) + '\n')
                attempt['run_evidence'] = str(evidence)
                attempt['evidence'] = [str(evidence)]
                if run.get('status') in ('failed', 'failure', 'error'):
                    if run.get('error') == 'android_gui_accessibility_not_ready' and not run.get('steps'):
                        attempt['status'] = 'blocked'
                        attempt['reason'] = 'android_gui_accessibility_not_ready'
                    else:
                        attempt['status'] = 'failed'
                        attempt['reason'] = 'new_run_failed'
                    break
            if obs['context']['package'] == CALCULATOR:
                values = [n.get('text', '').strip() for n in obs['nodes'] if n.get('text')]
                has_408 = '408' in values
                if not has_408:
                    attempt['fresh_non_408_observed'] = True
                elif matching and matching[0].get('run_id') and matching[0].get('success') is True and attempt['fresh_non_408_observed']:
                    attempt['status'] = 'independent_success'
                    attempt['reason'] = 'new_run_and_fresh_calculator_transition'
                    break
            time.sleep(2)
        else:
            attempt['reason'] = 'timeout_or_insufficient_fresh_evidence'
    except (RuntimeError, ValueError, OSError, json.JSONDecodeError, ET.ParseError) as exc:
        attempt['reason'] = str(exc)
    finally:
        if attempt['submitted_once'] and attempt['status'] != 'independent_success':
            attempt['cancel_status'] = 'unverified'
            try:
                obs = observe()
                if obs['context']['package'] != OMNIBOT:
                    call('launch_app', {'package': OMNIBOT, 'verify': True, 'recover': False})
                    obs = observe()
                # Never stop a task unless the visible conversation identifies our prompt.
                visible_text = ' '.join(n.get('text', '') + ' ' + n.get('description', '') for n in obs['nodes'])
                stop = nodes_with(obs, 'description', '停止')
                if marker not in visible_text:
                    attempt['cancel_reason'] = 'test_conversation_not_verified'
                elif len(stop) == 1:
                    attempt['cancel_status'] = 'requested_unverified'
                    call('tap', {'selector': {'description': '停止'}, 'observation_id': obs['observation_id'], 'recover': False})
                    cancel_deadline = time.monotonic() + 20
                    while time.monotonic() < cancel_deadline:
                        obs = observe()
                        visible_text = ' '.join(n.get('text', '') + ' ' + n.get('description', '') for n in obs['nodes'])
                        if obs['context']['package'] == OMNIBOT and marker in visible_text and not nodes_with(obs, 'description', '停止') and nodes_with(obs, 'description', '发送'):
                            attempt['cancel_status'] = 'ui_stopped_verified'
                            break
                        time.sleep(1)
                elif not stop and nodes_with(obs, 'description', '发送'):
                    attempt['cancel_status'] = 'ui_already_stopped_verified'
                else:
                    attempt['cancel_reason'] = 'stop_control_not_unique_or_missing'
            except (RuntimeError, ValueError, OSError, json.JSONDecodeError, ET.ParseError) as exc:
                attempt['cancel_reason'] = str(exc)
        args.output.write_text(json.dumps(attempt, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(attempt, ensure_ascii=False, indent=2))
    return 0 if attempt['status'] == 'independent_success' else 1


if __name__ == '__main__':
    sys.exit(main())
