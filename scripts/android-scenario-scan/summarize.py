#!/usr/bin/env python3
"""Summarize private regression reports using a strict public-field allowlist."""
import argparse
import json
import re
from pathlib import Path

MANIFEST = Path(__file__).resolve().parents[2] / 'docs/testing/android-regression-cases.json'
IDENTIFIER = re.compile(r'^[A-Za-z0-9_-]{1,100}$')
STATUSES = {'passed', 'failed', 'blocked', 'cleanup_failed', 'incomplete'}
LAYERS = {'normal_chat', 'gui_component'}
ACTORS = {'host_automation', 'normal_chat', 'host_automation_and_gui_component', 'gui_component', 'human', 'none'}


def summarize(manifest, reports):
    cases = manifest['cases']
    if any(not isinstance(case.get(key), str) or not IDENTIFIER.fullmatch(case[key]) for case in cases for key in ('id', 'source_id')):
        raise ValueError('invalid manifest identifier')
    lookup = {case['id']: case for case in cases}
    if len(lookup) != len(cases):
        raise ValueError('duplicate manifest case')
    grouped = {case['id']: [] for case in cases}
    seen = set()
    for report in reports:
        if not isinstance(report.get('attempts'), list):
            raise ValueError('report must contain attempts array')
        for attempt in report['attempts']:
            cid = attempt.get('case_id')
            case = lookup.get(cid)
            if not case or attempt.get('source_id') != case['source_id']:
                raise ValueError('unknown case or source mismatch')
            aid = attempt.get('attempt_id')
            if not isinstance(aid, str) or not IDENTIFIER.fullmatch(aid):
                raise ValueError('invalid attempt identifier')
            key = (cid, aid)
            if key in seen:
                raise ValueError('duplicate attempt identifier')
            seen.add(key)
            status = attempt.get('status')
            layer = attempt.get('execution_layer')
            actor = attempt.get('cleanup_actor')
            if status not in STATUSES or layer not in LAYERS or actor not in ACTORS:
                raise ValueError('invalid public status, execution layer or cleanup actor')
            gates = {}
            for name in ('execution', 'acceptance', 'cleanup'):
                gate = attempt.get(name)
                value = gate.get('passed') if isinstance(gate, dict) else None
                if value is not None and type(value) is not bool:
                    raise ValueError('gate must be boolean or absent')
                gates[name] = value
            if status == 'passed' and any(value is not True for value in gates.values()):
                raise ValueError('passed requires all three gates')
            execution = attempt.get('execution') or {}
            steps = execution.get('action_count')
            duration = execution.get('duration_ms')
            for value in (steps, duration):
                if value is not None and (type(value) is not int or value < 0):
                    raise ValueError('steps and duration must be nonnegative integers')
            grouped[cid].append({'case_id': cid, 'source_id': case['source_id'], 'attempt_id': aid,
                                 'status': status, 'execution_layer': layer, 'cleanup_actor': actor,
                                 'execution': gates['execution'], 'acceptance': gates['acceptance'],
                                 'cleanup': gates['cleanup'], 'steps': steps, 'duration_ms': duration})
    return {'cases': [{'case_id': case['id'], 'source_id': case['source_id'],
                       'status': 'attempted' if grouped[case['id']] else 'not_run',
                       'attempts': grouped[case['id']]} for case in cases]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', action='append', required=True, type=Path)
    parser.add_argument('--format', choices=['json', 'markdown'], default='json')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    result = summarize(manifest, [json.loads(path.read_text()) for path in args.report])
    if args.format == 'json':
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print('| case_id | source_id | attempt_id | status | execution_layer | cleanup_actor | execution | acceptance | cleanup | steps | duration_ms |')
        print('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
        for case in result['cases']:
            rows = case['attempts'] or [dict(case_id=case['case_id'], source_id=case['source_id'], status='not_run')]
            for row in rows:
                print('| ' + ' | '.join(str(row.get(key, '') if row.get(key) is not None else '') for key in
                      ('case_id','source_id','attempt_id','status','execution_layer','cleanup_actor','execution','acceptance','cleanup','steps','duration_ms')) + ' |')


if __name__ == '__main__':
    main()
