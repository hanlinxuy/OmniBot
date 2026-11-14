#!/usr/bin/env python3
"""Screenshot-only decision-policy experiment; completion is independently graded.
Run beside the dedicated AVD, not against a personal phone. No UI tree/OCR/API
business state is included in model requests. Candidate text is user supplied.
"""
import argparse
import base64
import json
import pathlib
import subprocess
import time
import urllib.request


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--endpoint', required=True)
    p.add_argument('--goal', required=True)
    p.add_argument('--out', type=pathlib.Path, required=True)
    p.add_argument('--text', action='append', default=[])
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
    for step in range(args.steps):
        if time.monotonic() - started >= args.deadline:
            terminal = 'deadline'
            break
        t = time.monotonic()
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
        axis = {str(i): str(round((i + .5) / 24, 5)) for i in range(24)}
        body = {'state': {'screenshot': '<image>', 'goal': args.goal, 'screen': {'width': width, 'height': height},
                          'recent_actions': history[-8:],
                          'instructions': 'Use only the screenshot. Choose the next action that advances the goal. Do not repeat a successful action. If tapping, choose the center of its target. Coordinates are fractions from the left/top edge.'},
                'images': ['data:image/png;base64,' + base64.b64encode(png).decode()],
                'questions': {'action': {'type': 'choice', 'instructions': 'What should happen next?', 'criteria': actions},
                              'x': {'type': 'choice', 'instructions': 'Horizontal center of the next tap target, fraction from left.', 'criteria': axis},
                              'y': {'type': 'choice', 'instructions': 'Vertical center of the same next tap target, fraction from top.', 'criteria': axis}},
                'think_mode': 'never', 'allow_none': False}
        (args.out / f'{step:03d}-request.json').write_text(json.dumps(body))
        request = urllib.request.Request(args.endpoint.rstrip('/') + '/v1/systemone', data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
        t = time.monotonic()
        with urllib.request.urlopen(request, timeout=min(90, max(1, args.deadline - (t - started)))) as response:
            answer = json.load(response)
        decision_seconds = time.monotonic() - t
        calls += 1
        (args.out / f'{step:03d}-response.json').write_text(json.dumps(answer, indent=2))
        # The API boundary owns answer validation; no fallback action on malformed output.
        choices = answer['answers']
        action = choices['action']['choice']
        xi, yi = choices['x']['choice'], choices['y']['choice']
        if action not in actions or xi not in axis or yi not in axis:
            raise ValueError('Decision is outside advertised candidates')
        x, y = int(float(axis[xi]) * width), int(float(axis[yi]) * height)
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
    (args.out / 'final.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=15))
    (args.out / 'result.json').write_text(json.dumps({'terminal': terminal, 'wall_seconds': time.monotonic() - started,
        'http_requests': calls, 'questions_per_request': 3, 'steps': len(history), 'accepted': None,
        'acceptance': 'Requires separate app-state readback; model done is not success.'}, indent=2))


if __name__ == '__main__':
    main()
