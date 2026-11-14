#!/usr/bin/env python3
"""Screenshot-only decision-policy experiment; completion is independently graded.
Run beside the dedicated AVD, not against a personal phone. No UI tree/OCR/API
business state is included in model requests. Candidate text is user supplied.
"""
import argparse
import base64
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.request


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--endpoint', required=True)
    p.add_argument('--goal', required=True)
    p.add_argument('--package', required=True, choices=['org.fossify.math', 'org.fossify.notes', 'com.android.deskclock'])
    p.add_argument('--out', type=pathlib.Path, required=True)
    p.add_argument('--text', action='append', default=[])
    p.add_argument('--policy', choices=['axes', 'regions', 'agentcpm'], default='axes')
    p.add_argument('--steps', type=int, default=30)
    p.add_argument('--deadline', type=float, default=240)
    args = p.parse_args()
    if not 1 <= args.steps <= 100 or not 1 <= args.deadline <= 1800:
        p.error('steps must be 1..100; deadline must be 1..1800 seconds')
    if len(args.text) > 8 or any(not s.isascii() or not s or len(s) > 200 or '%' in s for s in args.text):
        p.error('This initial input adapter supports up to eight short ASCII literals without percent signs')
    adb = [args.adb, '-s', 'emulator-5580']
    name = subprocess.check_output(adb + ['emu', 'avd', 'name'], text=True).splitlines()[0]
    if name != 'omnibot-lab':
        p.error('Only the dedicated omnibot-lab AVD is allowed')
    args.out.mkdir(parents=True, exist_ok=False)
    (args.out / 'request.json').write_text(json.dumps(vars(args), default=str, indent=2))
    started = time.monotonic()
    history = []
    terminal = 'step_limit'
    calls = 0
    try:
        for step in range(args.steps):
            if time.monotonic() - started >= args.deadline:
                terminal = 'deadline'
                break
            t = time.monotonic()
            focus = subprocess.check_output(adb + ['shell', 'dumpsys window | grep mCurrentFocus'], text=True, timeout=10)
            foreground = re.search(r'\bu\d+\s+([^ /}]+)/', focus)
            if not foreground or foreground.group(1) != args.package:
                terminal = 'foreground_outside_test_app'
                break
            png = subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=15)
            if png[:8] != b'\x89PNG\r\n\x1a\n':
                raise RuntimeError('Screenshot is not a PNG')
            width = int.from_bytes(png[16:20], 'big')
            height = int.from_bytes(png[20:24], 'big')
            (args.out / f'{step:03d}.png').write_bytes(png)
            observe_seconds = time.monotonic() - t
            actions = {'tap': 'Tap a visible control', 'up': 'Swipe up', 'down': 'Swipe down',
                       'back': 'Android back', 'wait': 'Wait for the current transition',
                       'done': 'The requested task is visibly complete; stop'}
            actions.update({f'text_{i}': f'Type this user-provided literal into the focused field: {s}' for i, s in enumerate(args.text)})
            # Coordinate candidates are generic screen positions, not detected controls or task answers.
            axis = ({str(i): str(i / 1000) for i in range(1001)} if args.policy == 'agentcpm'
                    else {str(i): str(round((i + .5) / 24, 5)) for i in range(24)})
            body = {'state': {'screenshot': '<image>', 'goal': args.goal, 'screen': {'width': width, 'height': height},
                              'recent_actions': history[-8:],
                              'instructions': 'Use only the screenshot. Choose the next action that advances the goal. Do not repeat a successful action. If tapping, choose the center of its target. Coordinates are fractions from the left/top edge.'},
                    'images': ['data:image/png;base64,' + base64.b64encode(png).decode()],
                    'questions': {'action': {'type': 'choice', 'instructions': 'What should happen next?', 'criteria': actions},
                                  'x': {'type': 'choice', 'instructions': 'Horizontal center of the next tap target, fraction from left.', 'criteria': axis},
                                  'y': {'type': 'choice', 'instructions': 'Vertical center of the same next tap target, fraction from top.', 'criteria': axis}},
                    'think_mode': 'never', 'allow_none': False}
            if args.policy == 'agentcpm':
                body.pop('questions')
                body['text_literals'] = args.text
            decision_started = time.monotonic()
            def decide(payload, suffix):
                nonlocal calls
                (args.out / f'{step:03d}{suffix}-request.json').write_text(json.dumps(payload))
                request = urllib.request.Request(args.endpoint.rstrip('/') + ('/act' if args.policy == 'agentcpm' else '/v1/systemone'), data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
                remaining = args.deadline - (time.monotonic() - started)
                if remaining <= 0:
                    raise TimeoutError('Run deadline reached before decision')
                with urllib.request.urlopen(request, timeout=min(90, remaining)) as response:
                    answer = json.load(response)
                calls += 1
                (args.out / f'{step:03d}{suffix}-response.json').write_text(json.dumps(answer, indent=2))
                return answer['answers']

            if args.policy in ('axes', 'agentcpm'):
                choices = decide(body, '')
                action = choices['action']['choice']
                xi, yi = choices['x']['choice'], choices['y']['choice']
                if action not in actions or xi not in axis or yi not in axis:
                    raise ValueError('Decision is outside advertised candidates')
                x, y = min(width - 1, int(float(axis[xi]) * width)), min(height - 1, int(float(axis[yi]) * height))
            else:
                # Conditional decisions keep action and both axes attached to one region.
                body['questions'] = {'action': body['questions']['action']}
                action = decide(body, '-action')['action']['choice']
                if action not in actions:
                    raise ValueError('Decision is outside advertised candidates')
                x = y = 0
                if action == 'tap':
                    columns = ['left quarter', 'center-left quarter', 'center-right quarter', 'right quarter']
                    rows = ['top sixth', 'second sixth from top', 'upper middle sixth', 'lower middle sixth', 'second sixth from bottom', 'bottom sixth']
                    regions = {str(r*4+c): f'{rows[r]}, {columns[c]}: x between {c*25}% and {(c+1)*25}%, y between {r*100/6:.1f}% and {(r+1)*100/6:.1f}%' for r in range(6) for c in range(4)}
                    body['questions'] = {'region': {'type': 'choice', 'instructions': 'Which screen region contains the CENTER of the control that should be tapped NEXT to advance the goal? Coordinates start at top left.', 'criteria': regions}}
                    region = decide(body, '-region')['region']['choice']
                    if region not in regions:
                        raise ValueError('Unknown screen region')
                    row, col = divmod(int(region), 4)
                    body['state']['selected_region'] = regions[region]
                    points = {str(r*4+c): f'Within the selected region, row {r+1} of 4 from TOP, column {c+1} of 4 from LEFT; screen pixel ({int((col+(c+.5)/4)*width/4)}, {int((row+(r+.5)/4)*height/6)})' for r in range(4) for c in range(4)}
                    body['questions'] = {'point': {'type': 'choice', 'instructions': 'Choose the point nearest the CENTER of that same target inside the selected region.', 'criteria': points}}
                    point = decide(body, '-point')['point']['choice']
                    if point not in points:
                        raise ValueError('Unknown region point')
                    r, c = divmod(int(point), 4)
                    x, y = int((col+(c+.5)/4)*width/4), int((row+(r+.5)/4)*height/6)
            decision_seconds = time.monotonic() - decision_started
            record = {'step': step, 'action': action, 'x': x, 'y': y,
                      'observe_seconds': observe_seconds, 'decision_seconds': decision_seconds}
            t = time.monotonic()
            if action == 'done':
                terminal = 'model_claimed_done'
            elif action == 'tap':
                subprocess.run(adb + ['shell', 'input', 'tap', str(x), str(y)], check=True, timeout=10)
            elif action in ('up', 'down'):
                a, b = (.75, .25) if action == 'up' else (.25, .75)
                subprocess.run(adb + ['shell', 'input', 'swipe', str(width//2), str(int(height*a)), str(width//2), str(int(height*b)), '250'], check=True, timeout=10)
            elif action == 'back':
                subprocess.run(adb + ['shell', 'input', 'keyevent', '4'], check=True, timeout=10)
            elif action.startswith('text_'):
                import shlex
                literal = args.text[int(action[5:])].replace(' ', '%s')
                subprocess.run(adb + ['shell', 'input text ' + shlex.quote(literal)], check=True, timeout=10)
            # Fixed execution budget is explicit; not a hidden model call or semantic waiter.
            time.sleep(.5 if action == 'wait' else .15)
            record['execute_seconds'] = time.monotonic() - t
            history.append(record)
            (args.out / 'steps.json').write_text(json.dumps(history, indent=2))
            if action == 'done':
                break
        focus = subprocess.check_output(adb + ['shell', 'dumpsys window | grep mCurrentFocus'], text=True, timeout=10)
        foreground = re.search(r'\bu\d+\s+([^ /}]+)/', focus)
        if foreground and foreground.group(1) == args.package:
            (args.out / 'final.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=15))
    finally:
        failure = sys.exc_info()[0]
        (args.out / 'result.json').write_text(json.dumps({'terminal': 'runtime_error' if failure else terminal, 'error_type': failure.__name__ if failure else None, 'wall_seconds': time.monotonic() - started,
            'http_requests': calls, 'questions_per_request': 3 if args.policy == 'axes' else 1, 'steps': len(history), 'accepted': None,
            'acceptance': 'Requires separate app-state readback; model done is not success.'}, indent=2))


if __name__ == '__main__':
    main()
