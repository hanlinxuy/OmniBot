"""Publish source predicates separately from independently accepted API runs."""
import argparse
import json
import pathlib


def collect(artifacts):
    runs = []
    for directory in sorted(artifacts.glob('ios-android-mw-*')):
        files = {name: json.loads((directory / (name + '.json')).read_text())
                 if (directory / (name + '.json')).exists() else {}
                 for name in ['task', 'result', 'failure', 'cleanup', 'backend-report', 'independent-review']}
        report = files['backend-report']
        if not files['result'] and not files['failure']:
            continue
        cleanup = files['cleanup']
        restored = bool(report.get('backendRestored') and report.get('baselineHash')
                        and report.get('baselineHash') == report.get('restoredHash'))
        predicate = report.get('sourceJudge')
        predicate_score = predicate[0] if isinstance(predicate, list) else predicate
        terminal = files['result'].get('promptResult', {}).get('stopReason') == 'end_turn'
        reviewed = files['independent-review'].get('passed') is True
        passed = bool(predicate_score == 1 and terminal and reviewed and restored
                      and cleanup.get('workspaceAbsent') and cleanup.get('reverseRemoved'))
        runs.append({'artifact': directory.name,
                     'sourceTask': files['task'].get('sourceTask'),
                     'sourceClass': report.get('sourceTask'),
                     'provider': '35a3', 'officialBenchmarkScore': None,
                     'sourcePredicateScore': predicate_score,
                     'terminalCompleted': terminal,
                     'providerRequests': files['result'].get('providerRequests', files['failure'].get('providerRequests', 0)),
                     'phoneCleanup': cleanup, 'backendRestored': restored,
                     'reviewed': bool(files['independent-review']),
                     'passed': passed,
                     'reviewReason': files['independent-review'].get('reason')})
    return {'scope': 'Original MobileWorld isolated backend, Android phone Pi API adaptation; not official GUI scores',
            'attemptedTasks': len({run['sourceTask'] for run in runs}),
            'tasksWithAnyPass': len({run['sourceTask'] for run in runs if run['passed']}),
            'runs': runs}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifacts', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = collect(args.artifacts)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'runs'}))
